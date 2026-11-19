package com.example

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.InMemoryCentralAuthorityTestDouble
import com.example.data.db.OneHomeProDatabase
import com.example.domain.security.*
import com.example.domain.service.AuthResult
import com.example.localization.AppLanguage
import com.example.ui.AppDestination
import com.example.ui.AuthScreenStep
import com.example.ui.OneHomeProViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SuperAdminSecurityRobolectricTest {

    private lateinit var app: OneHomeProApp
    private lateinit var db: OneHomeProDatabase
    private lateinit var central: InMemoryCentralAuthorityTestDouble
    private lateinit var container: AppContainer
    private var activeVm: OneHomeProViewModel? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        central = InMemoryCentralAuthorityTestDouble()
        container = AppContainer(app, db, central)
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
    }

    @After
    fun tearDown() {
        activeVm?.stopObservers()
        activeVm = null
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
        }
        db.close()
    }

    private fun awaitState(predicate: (com.example.ui.OneHomeUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate(activeVm!!.uiState.value)) return
            Thread.sleep(25L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition timed out. Current state: ${activeVm?.uiState?.value?.authStep} / ${activeVm?.uiState?.value?.authContext}", predicate(activeVm!!.uiState.value))
    }

    @Test
    fun `cases 1-5 stranger downloads APK, tries password, submits passport QID, creates business, tries second phone - all stay denied`() = runBlocking {
        // Case 1: Fresh app starts strictly on RESTRICTED_GATE with null authContext
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { !it.isInitializing }
        assertEquals(AuthScreenStep.RESTRICTED_GATE, vm.uiState.value.authStep)
        assertNull(vm.uiState.value.authContext)

        // Case 2: Stranger enters any credentials -> denied
        val loginAttempt = container.authService.login("stranger@unknown.com", "AnyPass#123", true, "Phone1")
        assertTrue(loginAttempt is AuthResult.Failed)

        // Case 3 & 4: Stranger signs in with Google -> stays blocked, cannot bootstrap Super Admin or auto-create Owner/Business
        central.signInAsTestIdentity("uid_stranger", "stranger@gmail.com", "Stranger User")
        vm.evaluateCurrentCentralIdentity()
        awaitState { it.authStep == AuthScreenStep.ACCESS_REQUEST && !it.isBusy }
        assertNull(vm.uiState.value.authContext)

        // Trying to call setupInitialOwnerAndBusiness without Super Admin grant throws AuthorizationException
        val ownerBypass = runCatching {
            container.authService.setupInitialOwnerAndBusiness(
                fullName = "Stranger",
                email = "stranger@gmail.com",
                password = "Password#2026",
                businessName = "Fake Company",
                branchName = "Doha",
                enableMfa = false,
                rememberMe = true,
                deviceName = "Phone1"
            )
        }.exceptionOrNull()
        assertTrue(ownerBypass is AuthorizationException)

        // Stranger submits full QID + Passport + Business request -> stays PENDING_APPROVAL and cannot enter dashboard
        vm.submitAccessRequest(
            fullName = "Stranger User",
            phone = "+97450001111",
            requestedRole = "OWNER",
            requestedBusinessName = "Stranger Real Estate",
            requestedBranchName = "West Bay",
            qidNumber = "29011223344",
            passportNumber = "P1234567",
            identityDocSummary = "ID_CARD.jpg"
        )
        awaitState { it.myAccessRequest?.status == SuperAdminConfig.GRANT_PENDING && !it.isBusy }
        assertNull(vm.uiState.value.authContext)
        assertEquals(AuthScreenStep.ACCESS_REQUEST, vm.uiState.value.authStep)

        // Case 5: Pending user installs app on second phone -> still blocked on ACCESS_REQUEST
        vm.stopObservers()
        val vmPhone2 = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { !it.isInitializing && it.authStep == AuthScreenStep.ACCESS_REQUEST }
        assertNull(vmPhone2.uiState.value.authContext)
    }

    @Test
    fun `cases 6-11 Super Admin bootstrap, approval, scoped login, live revocation, emergency lock, and anti-escalation work`() = runBlocking {
        // Pre-submit applicant request
        central.signInAsTestIdentity("uid_staff", "staff@dohaproperties.qa", "Ali Staff")
        central.submitAccessRequest(
            AccessRequestSubmission(
                fullName = "Ali Staff",
                phone = "+97455008899",
                requestedRole = "EMPLOYEE",
                requestedBusinessName = "Doha Properties",
                requestedBranchName = "Al Sadd",
                qidNumber = "28899001122",
                passportNumber = null,
                identityDocSummary = null,
                deviceId = "dev_staff_1",
                deviceName = "Staff Phone"
            )
        )
        central.signOutCentral()

        // Case 8: Stranger cannot create SUPER_ADMIN request
        central.signInAsTestIdentity("uid_attacker", "attacker@evil.com", "Attacker")
        val escalateReq = runCatching {
            central.submitAccessRequest(
                AccessRequestSubmission(
                    fullName = "Attacker",
                    phone = "",
                    requestedRole = SuperAdminConfig.ROLE_SUPER_ADMIN,
                    requestedBusinessName = "Evil",
                    requestedBranchName = "HQ",
                    qidNumber = null,
                    passportNumber = null,
                    identityDocSummary = null,
                    deviceId = "dev_atk",
                    deviceName = "EvilPhone"
                )
            )
        }.exceptionOrNull()
        assertTrue(escalateReq is AuthorizationException)
        central.signOutCentral()

        // Official Super Admin signs in -> lands on SUPER_ADMIN_BOOTSTRAP, bootstraps Master Password + TOTP + Recovery Codes
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val vmAdmin = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_BOOTSTRAP && !it.isInitializing }

        vmAdmin.bootstrapSuperAdmin("JishanMaster#2026")
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && it.superAdminMfaProvision != null }
        val (secret, codes) = vmAdmin.uiState.value.superAdminMfaProvision!!
        assertEquals(6, codes.size)

        // Wrong TOTP fails
        vmAdmin.verifySuperAdminChallenge("JishanMaster#2026", "000000")
        awaitState { it.bannerError != null && !it.isBusy }
        assertNull(vmAdmin.uiState.value.authContext)

        // Valid TOTP succeeds -> enters Dashboard as verified exclusive Super Admin
        val validTotp = CryptoUtil.currentTotpCode(secret)
        vmAdmin.verifySuperAdminChallenge("JishanMaster#2026", validTotp)
        awaitState { it.authContext?.isSuperAdmin == true }

        // Super Admin approves staff user with ONLY MAINTENANCE & PROPERTIES VIEW permissions on prop_1
        vmAdmin.approveCentralAccessRequest(
            requestUid = "uid_staff",
            assignedRole = "EMPLOYEE",
            existingBusinessId = null,
            permissionKeys = listOf("DASHBOARD:VIEW", "PROPERTIES:VIEW", "MAINTENANCE:VIEW", "MAINTENANCE:CREATE"),
            allowedPropertyIds = listOf("prop_1"),
            note = "Approved for Tower 1 maintenance only"
        )
        awaitState { it.centralAccessGrants.any { g -> g.uid == "uid_staff" && g.isActiveGrant() } }

        // Switch to approved staff user -> logs in cleanly and enforces scoped permissions
        vmAdmin.stopObservers()
        central.signOutCentral()
        central.signInAsTestIdentity("uid_staff", "staff@dohaproperties.qa", "Ali Staff")
        val vmStaff = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { it.authContext != null && !it.isInitializing }
        val staffCtx = vmStaff.uiState.value.authContext!!
        assertFalse(staffCtx.isSuperAdmin)
        assertTrue(staffCtx.hasPermission(AppModule.MAINTENANCE, ActionType.CREATE))
        assertFalse(staffCtx.hasPermission(AppModule.ACCOUNTING, ActionType.POST))

        // Approved staff CANNOT call Super Admin approve/emergency lock methods
        val staffEscalation = runCatching {
            AuthorizationGuard.requireSuperAdmin(staffCtx)
        }.exceptionOrNull()
        assertTrue(staffEscalation is AuthorizationException)

        // Staff CAN persist session across app restart while ACTIVE
        val savedToken = container.sessionStore.getSessionToken()
        assertNotNull(savedToken)
        assertNotNull(container.authService.validateStoredSessionOrNull())

        // Case 7: Super Admin revokes user -> live revocation immediately locks staff out + stored session rejected!
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        central.setUserGrantStatus("uid_staff", SuperAdminConfig.GRANT_REVOKED, "Revoked immediately")
        awaitState { vmStaff.uiState.value.authContext == null && vmStaff.uiState.value.authStep == AuthScreenStep.ACCESS_REQUEST }

        central.signInAsTestIdentity("uid_staff", "staff@dohaproperties.qa", "Ali Staff")
        assertNull(container.authService.validateSessionToken(savedToken!!))

        // Case 9 & 10: Emergency Lock + Pause Registrations
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        central.setEmergencyLock(true, "Audit Lockdown Active")
        central.setRegistrationsEnabled(false)

        central.signInAsTestIdentity("uid_staff", "staff@dohaproperties.qa", "Ali Staff")
        vmStaff.evaluateCurrentCentralIdentity()
        awaitState { vmStaff.uiState.value.authStep == AuthScreenStep.EMERGENCY_LOCKED }
        assertNull(vmStaff.uiState.value.authContext)

        // New registration during pause is rejected
        val pausedEx = runCatching {
            central.submitAccessRequest(
                AccessRequestSubmission(
                    fullName = "New Applicant",
                    phone = "",
                    requestedRole = "OWNER",
                    requestedBusinessName = "New Biz",
                    requestedBranchName = "HQ",
                    qidNumber = null,
                    passportNumber = null,
                    identityDocSummary = null,
                    deviceId = "dev_new",
                    deviceName = "PhoneNew"
                )
            )
        }.exceptionOrNull()
        assertTrue(pausedEx is AuthorizationException)

        // Case 11: Audit log captured all Super Admin security actions
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val auditActions = central.listAuditLogs().map { it.action }
        assertTrue(auditActions.contains("SUPER_ADMIN_BOOTSTRAP"))
        assertTrue(auditActions.contains("APPROVE_ACCESS"))
        assertTrue(auditActions.contains("GRANT_STATUS_REVOKED"))
        assertTrue(auditActions.contains("EMERGENCY_LOCK_true"))
    }

    @Test
    fun `requirement 11 rules 1-10 lost phone, new phone login, dual-loss recovery, immediate lock, returned device block, and full cloud data restore`() = runBlocking {
        // --- Rules 1, 2, 3, 4, 5, 8: Bootstrap on Old Phone 1, create real business data + settings, sync to cloud ---
        val phone1Store = SessionStore(app, "phone1_prefs_${System.nanoTime()}")
        phone1Store.setLanguage(AppLanguage.HI)
        phone1Store.setDarkMode(true)
        val phone1DeviceId = phone1Store.getOrCreateDeviceId()
        val phone1Container = AppContainer(app, db, central, phone1Store)

        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val vmPhone1 = OneHomeProViewModel(phone1Container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_BOOTSTRAP && !it.isInitializing }

        vmPhone1.bootstrapSuperAdmin(
            masterPassword = "Phone1Master#2026!",
            securityQuestion = "First property tower?",
            securityAnswer = "Lusail Pearl 7"
        )
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && it.lastRecoveryKit != null }
        val kit1 = vmPhone1.uiState.value.lastRecoveryKit!!

        // Sign in on Phone 1 using TOTP
        vmPhone1.verifySuperAdminChallenge("Phone1Master#2026!", CryptoUtil.currentTotpCode(kit1.totpSecret))
        awaitState { it.authContext?.isSuperAdmin == true }
        val adminCtx = vmPhone1.uiState.value.authContext!!
        val hqBizId = adminCtx.activeBusinessId

        // Seed Phone 1 business data via real services: Property, Building, Floor, Room, Customer, Journal
        val prop = phone1Container.propertyTenancyService.createProperty(
            adminCtx, "Pearl View Tower", "PVT-01", "RESIDENTIAL", "Pearl Qatar", "Doha", "VIP"
        )
        val bldg = phone1Container.propertyTenancyService.addBuilding(adminCtx, prop.id, "Tower A", "BLD-A", 2)
        val floor = phone1Container.propertyTenancyService.addFloor(adminCtx, prop.id, bldg.id, 1, "Floor 1")
        phone1Container.propertyTenancyService.addRoom(
            adminCtx, prop.id, bldg.id, floor.id, "101", "1BHK", 2,
            "FURNISHED", 550000L, "AC, WiFi", 2, "CARD-101", "Full"
        )
        phone1Container.propertyTenancyService.createCustomer(
            adminCtx, "Ahmed Al-Thani", "+97455112233", "ahmed@qatar.qa",
            "29011223344", "QA554433", "Qatari", "QatarEnergy", "+97455009988", "Long term"
        )
        phone1Container.accountingService.postManualJournal(
            ctx = adminCtx,
            description = "Opening Capital Injection",
            propertyId = prop.id,
            lines = listOf(
                com.example.domain.service.JournalLineInput("1010", 2500000L, 0L, "Cash"),
                com.example.domain.service.JournalLineInput("3000", 0L, 2500000L, "Equity")
            )
        )

        // Sync Phone 1's business data + settings (HI + DarkMode=true) to encrypted cloud snapshot
        vmPhone1.syncBusinessDataToCloud()
        awaitState { it.lastCloudSyncCount != null && !it.isBusy }
        assertTrue(central.cloudSnapshots.containsKey(hqBizId))

        // --- Rules 1, 2, 5, 6: Phone 1 is LOST (so local DB & TOTP authenticator are gone). Log in on NEW Phone 2 ---
        vmPhone1.stopObservers()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val phone2Db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // Simulate brand new device (isolated fresh DataStore + empty Room DB)
            val phone2Store = SessionStore(app, "phone2_prefs_${System.nanoTime()}")
            val phone2Container = AppContainer(app, phone2Db, central, phone2Store)
            phone2Container.sessionStore.setLanguage(AppLanguage.EN)
            phone2Container.sessionStore.setDarkMode(false)
            val phone2DeviceId = phone2Container.sessionStore.getOrCreateDeviceId()
            assertNotEquals(phone1DeviceId, phone2DeviceId)

            val vmPhone2 = OneHomeProViewModel(phone2Container, splashDelayMs = 0L).also { activeVm = it }
            awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && !it.isInitializing }

            // Alternative Recovery Method login on Phone 2 (without old phone's TOTP app) using Master Recovery Key
            vmPhone2.verifySuperAdminChallenge("Phone1Master#2026!", kit1.masterRecoveryKey)
            awaitState { it.authContext?.isSuperAdmin == true }

            // Rule 8: Brand-new Phone 2 automatically restored business, properties, rooms, customers, accounting & settings!
            assertEquals("Pearl View Tower", phone2Db.coreAndTenancyDao().getPropertiesByBusiness(hqBizId).single().name)
            assertEquals("101", phone2Db.coreAndTenancyDao().getRoomsByBusiness(hqBizId).single().roomNumber)
            assertEquals("Ahmed Al-Thani", phone2Db.coreAndTenancyDao().getCustomersByBusiness(hqBizId).single().fullName)
            assertTrue(phone2Db.accountingDao().getJournalEntriesByBusiness(hqBizId).isNotEmpty())
            awaitState { it.language == AppLanguage.HI && it.darkTheme }

            // Rule 9 (Dual Loss): Suppose BOTH old phone AND Master Password are forgotten -> recover via Security Answer!
            vmPhone2.openSuperAdminRecovery()
            awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_RECOVERY && it.superAdminSecurityQuestion == "First property tower?" }

            vmPhone2.recoverSuperAdminAccount(
                recoveryProof = "Lusail Pearl 7",
                newMasterPassword = "BrandNewMaster#2027!",
                newSecurityQuestion = "",
                newSecurityAnswer = "",
                revokeOtherDevices = true
            )
            awaitState { it.authContext?.isSuperAdmin == true && it.lastRecoveryKit != null && !it.isBusy }
            val kit2 = vmPhone2.uiState.value.lastRecoveryKit!!
            assertNotEquals(kit1.totpSecret, kit2.totpSecret)
            assertNotEquals(kit1.masterRecoveryKey, kit2.masterRecoveryKey)

            // Rule 6 & Rule 7: Old Phone 1 is now REVOKED_LOST! Even with valid Google + new password + new TOTP, Phone 1 stays BLOCKED!
            assertNotNull(central.isDeviceRevokedLost("uid_jishan", phone1DeviceId))
            vmPhone2.stopObservers()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Launch/verify on Phone 1 with its original phone1Store -> strictly blocked on DEVICE_REVOKED_LOCKED
            phone1Store.clearSessionToken()
            val phone1ReturnContainer = AppContainer(app, db, central, phone1Store)
            val vmReturnedPhone1 = OneHomeProViewModel(phone1ReturnContainer, splashDelayMs = 0L).also { activeVm = it }
            awaitState { it.authStep == AuthScreenStep.DEVICE_REVOKED_LOCKED && !it.isInitializing }
            assertNull(vmReturnedPhone1.uiState.value.authContext)

            // Even trying direct challenge verification on Phone 1 is blocked
            vmReturnedPhone1.verifySuperAdminChallenge("BrandNewMaster#2027!", CryptoUtil.currentTotpCode(kit2.totpSecret))
            awaitState { it.authStep == AuthScreenStep.DEVICE_REVOKED_LOCKED && !it.isBusy }
            assertNull(vmReturnedPhone1.uiState.value.authContext)

            // Rule 10: Stranger/attacker cannot self-recover or take Super Admin ownership
            central.signInAsTestIdentity("uid_attacker", "attacker@evil.com", "Attacker")
            val atkEx = runCatching {
                central.recoverSuperAdminAccount(
                    SuperAdminRecoveryRequest(
                        recoveryProof = kit2.masterRecoveryKey,
                        newMasterPassword = "HijackedPassword#99",
                        revokeOtherDevices = true,
                        currentDeviceId = "dev_attacker",
                        currentDeviceName = "AttackerPhone"
                    )
                )
            }.exceptionOrNull()
            assertTrue(atkEx is AuthorizationException)

            // Rule 7 completion: Active Super Admin Phone 2 explicitly re-authorizes returned Phone 1 -> Phone 1 can enter again
            vmReturnedPhone1.stopObservers()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
            phone2Store.clearSessionToken()
            val vmAdminPhone2 = OneHomeProViewModel(AppContainer(app, phone2Db, central, phone2Store), splashDelayMs = 0L).also { activeVm = it }
            awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && !it.isInitializing }
            vmAdminPhone2.verifySuperAdminChallenge("BrandNewMaster#2027!", CryptoUtil.currentTotpCode(kit2.totpSecret))
            awaitState { it.authContext?.isSuperAdmin == true }

            vmAdminPhone2.reauthorizeAdminDevice(phone1DeviceId, "BrandNewMaster#2027!")
            awaitState { it.adminDevices.firstOrNull { d -> d.deviceId == phone1DeviceId }?.status == SuperAdminConfig.DEVICE_ACTIVE }
            vmAdminPhone2.stopObservers()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            phone1Store.clearSessionToken()
            val vmAuthorizedPhone1 = OneHomeProViewModel(AppContainer(app, db, central, phone1Store), splashDelayMs = 0L).also { activeVm = it }
            awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && !it.isInitializing }
            vmAuthorizedPhone1.verifySuperAdminChallenge("BrandNewMaster#2027!", CryptoUtil.currentTotpCode(kit2.totpSecret))
            awaitState { it.authContext?.isSuperAdmin == true }
        } finally {
            phone2Db.close()
        }
    }

    @Test
    fun `switching or creating branches preserves Super Admin authority, serverSessionId, and resolves web client id safely`() = runBlocking {
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val vmAdmin = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_BOOTSTRAP && !it.isInitializing }

        vmAdmin.bootstrapSuperAdmin("BranchMaster#2026")
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && it.superAdminMfaProvision != null }
        val (secret, _) = vmAdmin.uiState.value.superAdminMfaProvision!!
        vmAdmin.verifySuperAdminChallenge("BranchMaster#2026", CryptoUtil.currentTotpCode(secret))
        awaitState { it.authContext?.isSuperAdmin == true }

        val initialCtx = vmAdmin.uiState.value.authContext!!
        assertNotNull(initialCtx.serverSessionId)

        // Create a second branch and switch to it
        val newBranch = container.propertyTenancyService.createBusinessBranch(
            ctx = initialCtx,
            name = "ONE HOME PRO Lusail",
            branchName = "Lusail Branch",
            phone = "+97444001122",
            email = "lusail@onehome.qa",
            address = "Marina District",
            crNumber = "CR-99881"
        )
        val switchedCtx = container.authService.switchBusiness(initialCtx, newBranch.id)
        assertTrue(switchedCtx.isSuperAdmin)
        assertEquals(initialCtx.serverSessionId, switchedCtx.serverSessionId)
        assertEquals(newBranch.id, switchedCtx.activeBusinessId)
        assertTrue(switchedCtx.allowedBusinessIds.contains(initialCtx.activeBusinessId))
        assertTrue(switchedCtx.allowedBusinessIds.contains(newBranch.id))

        // Should never throw whether google-services.json is present or absent
        val clientIdOrNull = com.example.data.remote.GoogleSignInHelper.resolveWebClientId(app)
        assertTrue(clientIdOrNull == null || clientIdOrNull.isNotBlank())
    }

    @Test
    fun `missing Firebase config, existing-update session restore, and Room integrity failures never crash startup`() = runBlocking {
        // 0. Bundled google-services.json & firebase_applet_config.xml must resolve real project resources in APK/tests:
        val bundledOptions = com.google.firebase.FirebaseOptions.fromResource(app)
        assertNotNull(bundledOptions)
        assertEquals("1:487338231669:android:9fcfa85a6ff1d8de99353c", bundledOptions!!.applicationId)
        assertEquals("gen-lang-client-0874428284", bundledOptions.projectId)
        assertTrue(bundledOptions.apiKey.isNotBlank())
        assertEquals(
            "ai-studio-android-onehomep-b7e88f8b-dd6f-442a-8d76-7b31f8e0512f",
            app.getString(R.string.firestore_database_id)
        )
        assertEquals(
            "487338231669-kbf2bvgir0fhdkv1obs45k1ee96thfjb.apps.googleusercontent.com",
            com.example.data.remote.GoogleSignInHelper.resolveWebClientId(app)
        )
        val liveConfiguredGate = com.example.data.remote.FirebaseCentralAuthorityGateway(app)
        assertTrue(liveConfiguredGate.isConfigured())
        assertNull(liveConfiguredGate.configurationDiagnostic())
        val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
        assertNotNull(com.google.firebase.auth.FirebaseAuth.getInstance(firebaseApp))
        assertNotNull(
            com.google.firebase.firestore.FirebaseFirestore.getInstance(
                firebaseApp,
                app.getString(R.string.firestore_database_id)
            )
        )

        // 1. Fresh install when FirebaseApp is deleted should still auto-recover cleanly if google-services.json is bundled,
        // or show STARTUP_ERROR safely if missing:
        try {
            com.google.firebase.FirebaseApp.getApps(app).forEach { it.delete() }
            val recoveredApp = com.example.data.remote.FirebaseConfigProvider.ensureFirebaseApp(app)
            assertNotNull(recoveredApp)
            assertNotNull(com.google.firebase.auth.FirebaseAuth.getInstance(recoveredApp!!))
            assertNotNull(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(
                    recoveredApp,
                    com.example.data.remote.FirebaseConfigProvider.resolveFirestoreDatabaseId(app)!!
                )
            )
            val realGateAfterReset = com.example.data.remote.FirebaseCentralAuthorityGateway(app)
            assertTrue(realGateAfterReset.isConfigured())
            assertNull(realGateAfterReset.configurationDiagnostic())
            assertNull(realGateAfterReset.currentIdentity())
            com.example.data.remote.GoogleSignInHelper.signOut(app)
        } finally {
            if (com.google.firebase.FirebaseApp.getApps(app).isEmpty()) {
                runCatching { com.example.data.remote.FirebaseConfigProvider.ensureFirebaseApp(app) }
            }
        }

        // 2. Existing-app update with valid saved session & active grant restores cleanly to Dashboard:
        val updateStore = SessionStore(app, "update_restore_${System.nanoTime()}")
        val updateContainer = AppContainer(app, db, central, updateStore)
        central.signInAsTestIdentity("uid_jishan", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val vmFirstLaunch = OneHomeProViewModel(updateContainer, splashDelayMs = 0L).also { activeVm = it }
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_BOOTSTRAP && !it.isInitializing }
        vmFirstLaunch.bootstrapSuperAdmin("UpdateMaster#2026")
        awaitState { it.authStep == AuthScreenStep.SUPER_ADMIN_VERIFY && it.superAdminMfaProvision != null }
        val (secret, _) = vmFirstLaunch.uiState.value.superAdminMfaProvision!!
        vmFirstLaunch.verifySuperAdminChallenge("UpdateMaster#2026", CryptoUtil.currentTotpCode(secret))
        awaitState { it.authContext?.isSuperAdmin == true }
        vmFirstLaunch.stopObservers()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val vmAfterUpdate = OneHomeProViewModel(updateContainer, splashDelayMs = 0L).also { activeVm = it }
        awaitState { !it.isInitializing && it.authContext?.isSuperAdmin == true }
        assertEquals(AppDestination.DASHBOARD, vmAfterUpdate.uiState.value.currentDestination)
        vmAfterUpdate.stopObservers()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // 3. Existing-app update with corrupted/mismatched Room DB shows safe STARTUP_ERROR (never crashes):
        val closedDb = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        closedDb.close()
        val brokenDbContainer = AppContainer(app, closedDb, central, SessionStore(app, "broken_db_${System.nanoTime()}"))
        val vmBrokenDb = OneHomeProViewModel(brokenDbContainer, splashDelayMs = 0L).also { activeVm = it }
        awaitState { !it.isInitializing }
        assertEquals(AuthScreenStep.STARTUP_ERROR, vmBrokenDb.uiState.value.authStep)
        assertNull(vmBrokenDb.uiState.value.authContext)
        assertTrue(vmBrokenDb.uiState.value.isDatabaseStartupError)
    }
}
