package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.FirebaseCentralAuthorityGateway
import com.example.domain.security.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SuperAdminRulesTest : FirestoreEmulatorTestBase() {

    private lateinit var gateway: FirebaseCentralAuthorityGateway

    @Before
    fun initGateway() {
        gateway = FirebaseCentralAuthorityGateway(appContext, firestore, auth)
        runBlocking {
            clearFirestoreEmulatorData()
        }
    }

    private fun unwrapFirestoreError(t: Throwable): FirebaseFirestoreException? =
        t.unwrapFirestoreException()

    @Test
    fun unauthenticated_caller_is_rejected_with_permission_denied() = runBlocking {
        auth.signOut()
        val unauthEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("access_grants").document("any_uid").get().await()
            }
        }.exceptionOrNull()
        assertNotNull("Expected PERMISSION_DENIED when signed out", unauthEx)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unwrapFirestoreError(unauthEx!!)?.code
        )

        val unauthAuditEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("admin_audit_logs").get().await()
            }
        }.exceptionOrNull()
        assertNotNull(unauthAuditEx)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unwrapFirestoreError(unauthAuditEx!!)?.code
        )
    }

    @Test
    fun stranger_cannot_bootstrap_super_admin_self_approve_or_create_business() = runBlocking {
        val strangerUid = signInTestUser("stranger@attacker.com")

        // Stranger cannot bootstrap platform_config/global
        val bootstrapEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("platform_config").document("global").set(
                    mapOf(
                        "superAdminUid" to strangerUid,
                        "superAdminEmail" to SuperAdminConfig.SUPER_ADMIN_EMAIL,
                        "mfaEnabled" to true,
                        "passwordSalt" to "1234567890",
                        "passwordHash" to "12345678901234567890",
                        "totpSecret" to "JBSWY3DPEHPK3PXP",
                        "recoveryCodeHashes" to listOf("hash1")
                    )
                ).await()
            }
        }.exceptionOrNull()
        assertNotNull(bootstrapEx)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unwrapFirestoreError(bootstrapEx!!)?.code
        )

        // Stranger cannot create their own access grant or business
        val selfGrantEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("access_grants").document(strangerUid).set(
                    mapOf(
                        "uid" to strangerUid,
                        "email" to "stranger@attacker.com",
                        "fullName" to "Attacker",
                        "authorityRole" to "OWNER",
                        "status" to "ACTIVE",
                        "activeBusinessId" to "biz_hack",
                        "allowedBusinessIds" to listOf("biz_hack"),
                        "allowedPropertyIds" to emptyList<String>(),
                        "restrictToAssignedProperties" to false,
                        "permissionKeys" to listOf("DASHBOARD:VIEW"),
                        "approvedByUid" to strangerUid,
                        "minSessionValidAfterEpoch" to 0L
                    )
                ).await()
            }
        }.exceptionOrNull()
        assertNotNull(selfGrantEx)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unwrapFirestoreError(selfGrantEx!!)?.code
        )

        // Stranger cannot request SUPER_ADMIN role
        val superReqEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                gateway.submitAccessRequest(
                    AccessRequestSubmission(
                        fullName = "Attacker",
                        phone = "+97455550000",
                        requestedRole = "SUPER_ADMIN",
                        requestedBusinessName = "Hack Corp",
                        requestedBranchName = "HQ",
                        qidNumber = "29000000001",
                        passportNumber = null,
                        identityDocSummary = null,
                        deviceId = "dev_attacker",
                        deviceName = "AttackerPhone"
                    )
                )
            }
        }.exceptionOrNull()
        assertTrue(superReqEx is AuthorizationException)
    }

    @Test
    fun real_super_admin_bootstrap_mfa_approval_isolation_revocation_and_emergency_lock_succeed() = runBlocking {
        // 1. Pre-authorized Super Admin signs in (verified email) and bootstraps once
        val superUid = signInTestUser(SuperAdminConfig.SUPER_ADMIN_EMAIL)
        val (totpSecret, recoveryCodes) = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.bootstrapSuperAdmin("MasterPass#2026Qatar!")
        }
        assertTrue(totpSecret.isNotBlank())
        assertEquals(6, recoveryCodes.size)

        // TOTP verification passes and single-use recovery code works
        val currentTotp = CryptoUtil.currentTotpCode(totpSecret)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("MasterPass#2026Qatar!", currentTotp)
        })
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("WrongPassword!", currentTotp)
        })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("MasterPass#2026Qatar!", recoveryCodes.first())
        })
        // Reusing same single-use recovery code must fail!
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("MasterPass#2026Qatar!", recoveryCodes.first())
        })

        // 2. Stranger signs in and submits a valid PENDING_APPROVAL access request with QID + Passport
        val applicantUid = signInTestUser("applicant.doha@onehome.qa")
        val submitted = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.submitAccessRequest(
                AccessRequestSubmission(
                    fullName = "Tariq Al-Kuwari",
                    phone = "+97455123456",
                    requestedRole = "OWNER",
                    requestedBusinessName = "Al Rayyan Properties WLL",
                    requestedBranchName = "Lusail Branch",
                    qidNumber = "29012345678",
                    passportNumber = "QA998877",
                    identityDocSummary = "QID Photo Selected",
                    deviceId = "dev_phone_1",
                    deviceName = "Pixel 9"
                )
            )
        }
        assertEquals("PENDING_APPROVAL", submitted.status)
        assertNull(withTimeout(DEFAULT_TIMEOUT_MS) { gateway.getMyAccessGrant() })

        // Pending applicant CANNOT open an active session even if supplying forged businessId
        val deniedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = applicantUid,
                email = "applicant.doha@onehome.qa",
                businessId = "biz_superadmin_hq",
                deviceId = "dev_phone_1",
                deviceName = "Pixel 9",
                rememberMe = true
            )
        }
        assertTrue(deniedResult is CentralVerificationResult.Denied)

        // 3. Super Admin signs back in, reviews list, and approves applicant into isolated business
        signInTestUser(SuperAdminConfig.SUPER_ADMIN_EMAIL)
        val requests = withTimeout(DEFAULT_TIMEOUT_MS) { gateway.listAllAccessRequests() }
        assertTrue(requests.any { it.uid == applicantUid && it.status == "PENDING_APPROVAL" })

        val (grant, biz) = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.approveAccessRequest(
                requestUid = applicantUid,
                assignedRole = "OWNER",
                existingBusinessId = null,
                permissionKeys = listOf("DASHBOARD:VIEW", "PROPERTIES:VIEW", "PROPERTIES:CREATE"),
                allowedPropertyIds = emptyList(),
                note = "QID verified"
            )
        }
        assertEquals("ACTIVE", grant.status)
        assertEquals(superUid, grant.approvedByUid)
        assertNotEquals("biz_superadmin_hq", biz.id)

        // 4. Approved applicant opens central session & can validate it
        signInTestUser("applicant.doha@onehome.qa")
        val openRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = applicantUid,
                email = "applicant.doha@onehome.qa",
                businessId = biz.id,
                deviceId = "dev_phone_1",
                deviceName = "Pixel 9",
                rememberMe = true
            )
        }
        assertTrue("Expected approved applicant session, got $openRes", openRes is CentralVerificationResult.Approved)
        val sessionId = (openRes as CentralVerificationResult.Approved).serverSessionId

        val validated = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.validateExistingCentralSession(sessionId, applicantUid, "applicant.doha@onehome.qa")
        }
        assertTrue(validated is CentralVerificationResult.Approved)

        // Approved user CANNOT read Super Admin HQ business or global config!
        val crossBizEx = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("businesses").document("biz_superadmin_hq").get().await()
            }
        }.exceptionOrNull()
        assertNotNull(crossBizEx)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unwrapFirestoreError(crossBizEx!!)?.code
        )

        // 5. Super Admin revokes user -> subsequent session check is DENIED
        signInTestUser(SuperAdminConfig.SUPER_ADMIN_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.setUserGrantStatus(applicantUid, SuperAdminConfig.GRANT_REVOKED, "Terminated contract")
        }
        signInTestUser("applicant.doha@onehome.qa")
        val afterRevoke = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.validateExistingCentralSession(sessionId, applicantUid, "applicant.doha@onehome.qa")
        }
        assertTrue(afterRevoke is CentralVerificationResult.Denied)

        // 6. Super Admin activates Emergency Lock -> snapshot listener immediately emits lock message
        signInTestUser(SuperAdminConfig.SUPER_ADMIN_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.setEmergencyLock(true, "Emergency freeze for audit")
        }
        signInTestUser("applicant.doha@onehome.qa")
        val gateNotice = withTimeout(FLOW_TIMEOUT_MS) {
            gateway.observeGateRevocation(applicantUid, biz.id, sessionId).first()
        }
        assertNotNull(gateNotice)
    }

    @Test
    fun super_admin_lost_phone_recovery_blocks_old_device_reauthorizes_on_return_and_isolates_cloud_snapshots() = runBlocking {
        // 1. Super Admin bootstraps on Old Phone 1 with Security Question & Master Recovery Key
        val superUid = signInTestUser(SuperAdminConfig.SUPER_ADMIN_EMAIL)
        val kit1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.bootstrapSuperAdminWithRecovery(
                masterPassword = "OldMasterPass#2026!",
                securityQuestion = "First business branch city?",
                securityAnswer = "Doha West Bay",
                deviceId = "phone_1_lost",
                deviceName = "Pixel 8 (Old)"
            )
        }
        assertTrue(kit1.masterRecoveryKey.startsWith("OHP-KEY-"))

        val oldPhoneOpen = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = superUid,
                email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
                businessId = null,
                deviceId = "phone_1_lost",
                deviceName = "Pixel 8 (Old)",
                rememberMe = true
            )
        }
        assertTrue(oldPhoneOpen is CentralVerificationResult.Approved)
        val oldSessionId = (oldPhoneOpen as CentralVerificationResult.Approved).serverSessionId

        // Super Admin uploads encrypted cloud snapshot from Old Phone 1
        withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.uploadCloudBusinessSnapshot(
                CloudBusinessSnapshot(
                    id = "latest",
                    businessId = "biz_superadmin_hq",
                    payloadBase64 = "H4sIAAAAAAAA/w==",
                    sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    recordCount = 42,
                    syncedByUid = superUid,
                    syncedByDeviceName = "Pixel 8 (Old)",
                    appLanguageCode = "hi",
                    darkTheme = true,
                    syncedAtEpoch = System.currentTimeMillis()
                )
            )
        }

        // 2. Dual loss! Phone 1 is lost AND Master Password forgotten -> recover on New Phone 2 via Security Answer
        assertEquals("First business branch city?", withTimeout(DEFAULT_TIMEOUT_MS) { gateway.getSuperAdminSecurityQuestion() })
        val kit2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.recoverSuperAdminAccount(
                SuperAdminRecoveryRequest(
                    recoveryProof = "Doha West Bay",
                    newMasterPassword = "NewMasterPass#2027!",
                    revokeOtherDevices = true,
                    currentDeviceId = "phone_2_new",
                    currentDeviceName = "Galaxy S25 (New)"
                )
            )
        }
        assertNotEquals(kit1.masterRecoveryKey, kit2.masterRecoveryKey)

        // New password + new TOTP succeed on New Phone 2; old password fails
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("OldMasterPass#2026!", CryptoUtil.currentTotpCode(kit2.totpSecret))
        })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifySuperAdminChallenge("NewMasterPass#2027!", CryptoUtil.currentTotpCode(kit2.totpSecret))
        })

        val newPhoneOpen = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = superUid,
                email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
                businessId = null,
                deviceId = "phone_2_new",
                deviceName = "Galaxy S25 (New)",
                rememberMe = true
            )
        }
        assertTrue(newPhoneOpen is CentralVerificationResult.Approved)

        // New Phone 2 downloads the synced cloud snapshot
        val downloaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.downloadCloudBusinessSnapshot("biz_superadmin_hq")
        }
        assertNotNull(downloaded)
        assertEquals(42, downloaded!!.recordCount)
        assertEquals("hi", downloaded.appLanguageCode)

        // 3. Old Phone 1 is found and tries to open session OR validate its old session -> BOTH DENIED!
        val oldSessionCheck = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.validateExistingCentralSession(oldSessionId, superUid, SuperAdminConfig.SUPER_ADMIN_EMAIL)
        }
        assertTrue(oldSessionCheck is CentralVerificationResult.Denied)

        val oldPhoneReopenDenied = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = superUid,
                email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
                businessId = null,
                deviceId = "phone_1_lost",
                deviceName = "Pixel 8 (Old)",
                rememberMe = true
            )
        }
        assertTrue(oldPhoneReopenDenied is CentralVerificationResult.Denied)
        assertEquals(SuperAdminConfig.DEVICE_REVOKED_LOST, (oldPhoneReopenDenied as CentralVerificationResult.Denied).status)

        // 4. Phone 2 explicitly re-authorizes returned Old Phone 1 with Master Password -> now Phone 1 works again
        withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.reauthorizeAdminDevice("phone_1_lost", "NewMasterPass#2027!", "phone_2_new")
        }
        val oldPhoneAfterReauth = withTimeout(DEFAULT_TIMEOUT_MS) {
            gateway.verifyAndOpenApprovedSession(
                uid = superUid,
                email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
                businessId = null,
                deviceId = "phone_1_lost",
                deviceName = "Pixel 8 (Old)",
                rememberMe = true
            )
        }
        assertTrue(oldPhoneAfterReauth is CentralVerificationResult.Approved)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
