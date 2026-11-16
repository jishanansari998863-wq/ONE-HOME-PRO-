package com.example.base

import com.example.domain.security.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Test-only in-memory simulation of CentralAuthorityGateway located strictly under app/src/test/.
 * Mirrors firestore.rules constraints, Super Admin bootstrap lock, MFA/recovery challenge,
 * emergency lock, session revocation, and isolated per-business grants.
 */
class InMemoryCentralAuthorityTestDouble : CentralAuthorityGateway {

    private val identityState = MutableStateFlow<CentralIdentity?>(null)
    private val statusState = MutableStateFlow(
        PlatformStatus(
            id = "public",
            isBootstrapped = false,
            registrationsEnabled = true,
            emergencyLockAllNonSuperAdmin = false
        )
    )
    private val platformConfig = AtomicReference<PlatformConfig?>(null)
    private val bootstrappedFlag = AtomicBoolean(false)

    val requests = ConcurrentHashMap<String, AccessRequestRecord>()
    val grants = ConcurrentHashMap<String, AccessGrantRecord>()
    val businesses = ConcurrentHashMap<String, CentralBusinessRecord>()
    val sessions = ConcurrentHashMap<String, CentralSessionRecord>()
    val adminDevices = ConcurrentHashMap<String, AdminDeviceRecord>()
    val cloudSnapshots = ConcurrentHashMap<String, CloudBusinessSnapshot>()
    val auditLogs = mutableListOf<AdminAuditRecord>()

    private val gateVersion = MutableStateFlow(0L)

    override val authIdentityFlow: Flow<CentralIdentity?> = identityState.asStateFlow()

    override fun currentIdentity(): CentralIdentity? = identityState.value

    fun signInAsTestIdentity(
        uid: String,
        email: String,
        displayName: String = email.substringBefore("@"),
        emailVerified: Boolean = true
    ): CentralIdentity {
        val clean = email.trim().lowercase()
        val id = CentralIdentity(uid = uid, email = clean, displayName = displayName, emailVerified = emailVerified)
        identityState.value = id
        return id
    }

    fun clearTestIdentity() {
        identityState.value = null
    }

    /**
     * Strictly for legacy domain unit tests (accounting, scanner, layout) that start with an
     * authorized test Owner/User before exercising business workflows.
     */
    fun preApproveUserForTest(
        uid: String,
        email: String,
        fullName: String,
        role: String,
        businessId: String = "biz_test_hq",
        businessName: String = "Qatar Homes WLL",
        permissionKeys: List<String> = AuthorizationGuard.defaultPermissionCatalog().map { "${it.first}:${it.second}" },
        allowedPropertyIds: List<String> = emptyList()
    ) {
        val clean = email.trim().lowercase()
        businesses.putIfAbsent(
            businessId,
            CentralBusinessRecord(
                id = businessId,
                name = businessName,
                code = "BIZ-TST",
                branchName = "Doha HQ",
                ownerUid = uid,
                ownerEmail = clean,
                status = SuperAdminConfig.GRANT_ACTIVE,
                approvedByUid = "super_admin_test"
            )
        )
        grants[uid] = AccessGrantRecord(
            id = uid,
            uid = uid,
            email = clean,
            fullName = fullName,
            authorityRole = role,
            status = SuperAdminConfig.GRANT_ACTIVE,
            activeBusinessId = businessId,
            allowedBusinessIds = listOf(businessId),
            allowedPropertyIds = allowedPropertyIds,
            restrictToAssignedProperties = allowedPropertyIds.isNotEmpty(),
            permissionKeys = permissionKeys,
            approvedByUid = "super_admin_test"
        )
        if (currentIdentity() == null) {
            signInAsTestIdentity(uid, clean, fullName, emailVerified = true)
        }
    }

    private fun requireId(): CentralIdentity =
        currentIdentity() ?: throw AuthorizationException("Google Sign-In required.")

    override suspend fun getPlatformStatus(): PlatformStatus = statusState.value

    override suspend fun isPlatformBootstrapped(): Boolean = bootstrappedFlag.get()

    override suspend fun isVerifiedSuperAdminCurrent(): Boolean {
        val id = currentIdentity() ?: return false
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) return false
        val cfg = platformConfig.get() ?: return true
        return cfg.superAdminUid == id.uid && SuperAdminConfig.isSuperAdminEmail(cfg.superAdminEmail)
    }

    override suspend fun bootstrapSuperAdmin(masterPassword: String): Pair<String, List<String>> {
        val kit = bootstrapSuperAdminWithRecovery(
            masterPassword = masterPassword,
            securityQuestion = "",
            securityAnswer = "",
            deviceId = "bootstrap_primary",
            deviceName = "Primary Test Device"
        )
        return kit.totpSecret to kit.recoveryCodes
    }

    override suspend fun bootstrapSuperAdminWithRecovery(
        masterPassword: String,
        securityQuestion: String,
        securityAnswer: String,
        deviceId: String,
        deviceName: String
    ): SuperAdminProvisionKit {
        val id = requireId()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Only ${SuperAdminConfig.SUPER_ADMIN_EMAIL} can bootstrap Super Admin.")
        }
        require(masterPassword.length >= 10) { "Super Admin master password must be at least 10 characters." }
        if (platformConfig.get() != null) {
            throw AuthorizationException("Super Admin authority is already bootstrapped and locked.")
        }
        val salt = CryptoUtil.generateSaltBase64()
        val hash = CryptoUtil.hashPassword(masterPassword, salt)
        val totpSecret = CryptoUtil.generateTotpSecret()
        val recoveryCodes = CryptoUtil.generateRecoveryCodes(6)
        val masterRecoveryKey = CryptoUtil.generateMasterRecoveryKey()
        val backupSalt = CryptoUtil.generateSaltBase64()
        val backupHash = CryptoUtil.hashPassword(masterRecoveryKey, backupSalt)
        val cleanQ = securityQuestion.trim().take(250)
        val cleanA = securityAnswer.trim().lowercase()
        val ansSalt = if (cleanA.isNotBlank()) CryptoUtil.generateSaltBase64() else ""
        val ansHash = if (cleanA.isNotBlank()) CryptoUtil.hashPassword(cleanA, ansSalt) else ""

        val cfg = PlatformConfig(
            id = "global",
            superAdminUid = id.uid,
            superAdminEmail = SuperAdminConfig.SUPER_ADMIN_EMAIL,
            mfaEnabled = true,
            passwordSalt = salt,
            passwordHash = hash,
            totpSecret = totpSecret,
            recoveryCodeHashes = recoveryCodes.map { CryptoUtil.sha256Hex(it) },
            backupKeySalt = backupSalt,
            backupKeyHash = backupHash,
            securityQuestion = cleanQ,
            securityAnswerSalt = ansSalt,
            securityAnswerHash = ansHash,
            minAdminSessionValidAfterEpoch = 0L
        )
        platformConfig.set(cfg)
        bootstrappedFlag.set(true)
        statusState.value = statusState.value.copy(isBootstrapped = true)

        val hqBizId = "biz_superadmin_hq"
        businesses[hqBizId] = CentralBusinessRecord(
            id = hqBizId,
            name = "ONE HOME PRO HQ",
            code = "BIZ-OHPHQ",
            branchName = "Doha HQ",
            ownerUid = id.uid,
            ownerEmail = SuperAdminConfig.SUPER_ADMIN_EMAIL,
            status = SuperAdminConfig.GRANT_ACTIVE,
            approvedByUid = id.uid
        )
        grants[id.uid] = AccessGrantRecord(
            id = id.uid,
            uid = id.uid,
            email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
            fullName = id.displayName.ifBlank { "Super Admin" },
            authorityRole = "OWNER",
            status = SuperAdminConfig.GRANT_ACTIVE,
            activeBusinessId = hqBizId,
            allowedBusinessIds = listOf(hqBizId),
            allowedPropertyIds = emptyList(),
            restrictToAssignedProperties = false,
            permissionKeys = AuthorizationGuard.defaultPermissionCatalog().map { "${it.first}:${it.second}" },
            approvedByUid = id.uid
        )
        upsertActiveDevice(id.uid, id.email, deviceId, deviceName)
        recordAuditEvent("SUPER_ADMIN_BOOTSTRAP", id.uid, id.email, hqBizId, "Super Admin bootstrapped with recovery kit")
        return SuperAdminProvisionKit(
            totpSecret = totpSecret,
            recoveryCodes = recoveryCodes,
            masterRecoveryKey = masterRecoveryKey,
            securityQuestion = cleanQ
        )
    }

    override suspend fun verifySuperAdminChallenge(masterPassword: String, totpOrRecoveryCode: String): Boolean {
        val id = requireId()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Unauthorized Super Admin challenge.")
        }
        val cfg = platformConfig.get() ?: throw AuthorizationException("Super Admin config missing.")
        if (cfg.superAdminUid != id.uid) throw AuthorizationException("Super Admin UID mismatch.")
        if (!CryptoUtil.verifyPassword(masterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            recordAuditEvent("SUPER_ADMIN_LOGIN_FAIL", id.uid, id.email, null, "Wrong password")
            return false
        }
        val trimmed = totpOrRecoveryCode.trim()
        if (CryptoUtil.verifyTotpCode(cfg.totpSecret, trimmed, System.currentTimeMillis())) {
            recordAuditEvent("SUPER_ADMIN_VERIFIED", id.uid, id.email, null, "TOTP MFA verified")
            return true
        }
        val upper = trimmed.uppercase()
        val hash = CryptoUtil.sha256Hex(upper)
        if (cfg.recoveryCodeHashes.contains(hash)) {
            platformConfig.set(cfg.copy(recoveryCodeHashes = cfg.recoveryCodeHashes.filterNot { it == hash }))
            recordAuditEvent("SUPER_ADMIN_RECOVERY_USED", id.uid, id.email, null, "Single-use recovery code verified")
            return true
        }
        if (cfg.backupKeyHash.isNotBlank() && cfg.backupKeySalt.isNotBlank() &&
            CryptoUtil.verifyPassword(upper, cfg.backupKeySalt, cfg.backupKeyHash)
        ) {
            recordAuditEvent("SUPER_ADMIN_MASTER_KEY_LOGIN", id.uid, id.email, null, "Master recovery key verified")
            return true
        }
        return false
    }

    override suspend fun getSuperAdminSecurityQuestion(): String? {
        val id = currentIdentity() ?: return null
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) return null
        return platformConfig.get()?.securityQuestion?.takeIf { it.isNotBlank() }
    }

    override suspend fun recoverSuperAdminAccount(request: SuperAdminRecoveryRequest): SuperAdminProvisionKit {
        val id = requireId()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Only verified Super Admin (${SuperAdminConfig.SUPER_ADMIN_EMAIL}) can recover account.")
        }
        require(request.newMasterPassword.length >= 10) { "New master password must be at least 10 characters." }
        if (isDeviceRevokedLost(id.uid, request.currentDeviceId) != null) {
            throw AuthorizationException("Lost/Revoked device cannot initiate Super Admin recovery.")
        }
        val cfg = platformConfig.get() ?: throw AuthorizationException("Super Admin config missing.")
        if (cfg.superAdminUid != id.uid) throw AuthorizationException("Super Admin UID mismatch.")

        val proof = request.recoveryProof.trim()
        require(proof.isNotBlank())
        val upper = proof.uppercase()
        val matchedCode = cfg.recoveryCodeHashes.contains(CryptoUtil.sha256Hex(upper))
        val matchedKey = cfg.backupKeyHash.isNotBlank() && cfg.backupKeySalt.isNotBlank() &&
            CryptoUtil.verifyPassword(upper, cfg.backupKeySalt, cfg.backupKeyHash)
        val matchedAnswer = cfg.securityAnswerHash.isNotBlank() && cfg.securityAnswerSalt.isNotBlank() &&
            CryptoUtil.verifyPassword(proof.lowercase(), cfg.securityAnswerSalt, cfg.securityAnswerHash)
        if (!matchedCode && !matchedKey && !matchedAnswer) {
            recordAuditEvent("SUPER_ADMIN_RECOVERY_FAILED", id.uid, id.email, null, "Invalid recovery proof")
            throw AuthorizationException("Invalid Master Recovery Key, Recovery Code, or Security Answer.")
        }

        val now = System.currentTimeMillis()
        val newSalt = CryptoUtil.generateSaltBase64()
        val newHash = CryptoUtil.hashPassword(request.newMasterPassword, newSalt)
        val newTotp = CryptoUtil.generateTotpSecret()
        val newCodes = CryptoUtil.generateRecoveryCodes(6)
        val newKey = CryptoUtil.generateMasterRecoveryKey()
        val newBackupSalt = CryptoUtil.generateSaltBase64()
        val newBackupHash = CryptoUtil.hashPassword(newKey, newBackupSalt)

        val qClean = request.securityQuestion?.trim()?.take(250).orEmpty().ifBlank { cfg.securityQuestion }
        val aClean = request.securityAnswer?.trim()?.lowercase().orEmpty()
        val aSalt = if (aClean.isNotBlank()) CryptoUtil.generateSaltBase64() else cfg.securityAnswerSalt
        val aHash = if (aClean.isNotBlank()) CryptoUtil.hashPassword(aClean, aSalt) else cfg.securityAnswerHash

        platformConfig.set(
            cfg.copy(
                passwordSalt = newSalt,
                passwordHash = newHash,
                totpSecret = newTotp,
                recoveryCodeHashes = newCodes.map { CryptoUtil.sha256Hex(it) },
                backupKeySalt = newBackupSalt,
                backupKeyHash = newBackupHash,
                securityQuestion = qClean,
                securityAnswerSalt = aSalt,
                securityAnswerHash = aHash,
                minAdminSessionValidAfterEpoch = now
            )
        )
        upsertActiveDevice(id.uid, id.email, request.currentDeviceId, request.currentDeviceName)
        if (request.revokeOtherDevices) {
            revokeAllOtherAdminDevicesAndSessions(request.currentDeviceId, "SUPER_ADMIN_NEW_DEVICE_RECOVERY")
        }
        gateVersion.value += 1
        recordAuditEvent("SUPER_ADMIN_RECOVERY_COMPLETED", id.uid, id.email, null, "Recovered on ${request.currentDeviceName}")
        return SuperAdminProvisionKit(newTotp, newCodes, newKey, qClean)
    }

    override suspend fun updateSuperAdminRecoverySettings(
        currentMasterPassword: String,
        securityQuestion: String,
        securityAnswer: String,
        rotateBackupKit: Boolean
    ): SuperAdminProvisionKit? {
        val id = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val cfg = platformConfig.get() ?: throw AuthorizationException("Super Admin config missing.")
        if (!CryptoUtil.verifyPassword(currentMasterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            throw AuthorizationException("Invalid current Master Password.")
        }
        var updated = cfg
        val qClean = securityQuestion.trim().take(250)
        val aClean = securityAnswer.trim().lowercase()
        if (qClean.isNotBlank() && aClean.isNotBlank()) {
            val aSalt = CryptoUtil.generateSaltBase64()
            updated = updated.copy(
                securityQuestion = qClean,
                securityAnswerSalt = aSalt,
                securityAnswerHash = CryptoUtil.hashPassword(aClean, aSalt)
            )
        }
        var newKit: SuperAdminProvisionKit? = null
        if (rotateBackupKit) {
            val newTotp = CryptoUtil.generateTotpSecret()
            val newCodes = CryptoUtil.generateRecoveryCodes(6)
            val newKey = CryptoUtil.generateMasterRecoveryKey()
            val newBackupSalt = CryptoUtil.generateSaltBase64()
            val newBackupHash = CryptoUtil.hashPassword(newKey, newBackupSalt)
            updated = updated.copy(
                totpSecret = newTotp,
                recoveryCodeHashes = newCodes.map { CryptoUtil.sha256Hex(it) },
                backupKeySalt = newBackupSalt,
                backupKeyHash = newBackupHash
            )
            newKit = SuperAdminProvisionKit(newTotp, newCodes, newKey, updated.securityQuestion)
        }
        platformConfig.set(updated)
        return newKit
    }

    private fun upsertActiveDevice(uid: String, email: String, deviceId: String, deviceName: String) {
        val key = "${uid}_${deviceId}"
        val existing = adminDevices[key]
        if (existing?.status == SuperAdminConfig.DEVICE_REVOKED_LOST) {
            throw AuthorizationException("Device '$deviceId' is marked LOST/REVOKED.")
        }
        val now = System.currentTimeMillis()
        adminDevices[key] = AdminDeviceRecord(
            id = key,
            uid = uid,
            email = email,
            deviceId = deviceId,
            deviceName = deviceName,
            status = SuperAdminConfig.DEVICE_ACTIVE,
            lastSeenEpoch = now,
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    override suspend fun isDeviceRevokedLost(uid: String, deviceId: String): AdminDeviceRecord? {
        if (deviceId.isBlank()) return null
        return adminDevices["${uid}_${deviceId}"]?.takeIf { it.status == SuperAdminConfig.DEVICE_REVOKED_LOST }
    }

    override suspend fun listAdminDevices(): List<AdminDeviceRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return adminDevices.values.sortedByDescending { it.lastSeenEpoch }
    }

    override suspend fun markAdminDeviceLost(targetDeviceId: String, reason: String, callerDeviceId: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetDeviceId != callerDeviceId) { "Cannot block your currently active device." }
        val key = "${admin.uid}_${targetDeviceId}"
        adminDevices[key]?.let { dev ->
            adminDevices[key] = dev.copy(
                status = SuperAdminConfig.DEVICE_REVOKED_LOST,
                revokedReason = reason.ifBlank { "Marked LOST by Super Admin" },
                revokedByDeviceId = callerDeviceId
            )
        }
        sessions.entries.forEach { (sid, s) ->
            if (s.uid == admin.uid && s.deviceId == targetDeviceId) {
                sessions[sid] = s.copy(isRevoked = true, revokedReason = "DEVICE_LOST_LOCKED")
            }
        }
        gateVersion.value += 1
        recordAuditEvent("DEVICE_REVOKED_LOST", admin.uid, admin.email, null, "Marked $targetDeviceId LOST ($reason)")
    }

    override suspend fun reauthorizeAdminDevice(targetDeviceId: String, masterPassword: String, callerDeviceId: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetDeviceId != callerDeviceId) { "Locked device cannot self-reauthorize." }
        val cfg = platformConfig.get() ?: throw AuthorizationException("Config missing.")
        if (!CryptoUtil.verifyPassword(masterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            throw AuthorizationException("Master Password verification failed.")
        }
        val key = "${admin.uid}_${targetDeviceId}"
        adminDevices[key]?.let { dev ->
            adminDevices[key] = dev.copy(
                status = SuperAdminConfig.DEVICE_ACTIVE,
                revokedReason = null,
                revokedByDeviceId = null,
                lastSeenEpoch = System.currentTimeMillis()
            )
        }
        gateVersion.value += 1
        recordAuditEvent("DEVICE_REAUTHORIZED", admin.uid, admin.email, null, "Re-authorized device $targetDeviceId")
    }

    override suspend fun revokeAllOtherAdminDevicesAndSessions(callerDeviceId: String, reason: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val now = System.currentTimeMillis()
        adminDevices.entries.forEach { (key, dev) ->
            if (dev.uid == admin.uid && dev.deviceId != callerDeviceId) {
                adminDevices[key] = dev.copy(
                    status = SuperAdminConfig.DEVICE_REVOKED_LOST,
                    revokedReason = reason,
                    revokedByDeviceId = callerDeviceId
                )
            }
        }
        sessions.entries.forEach { (sid, s) ->
            if (s.uid == admin.uid && s.deviceId != callerDeviceId) {
                sessions[sid] = s.copy(isRevoked = true, revokedReason = reason)
            }
        }
        platformConfig.get()?.let { platformConfig.set(it.copy(minAdminSessionValidAfterEpoch = now)) }
        gateVersion.value += 1
        recordAuditEvent("REVOKE_ALL_OTHER_DEVICES", admin.uid, admin.email, null, "Revoked all other devices ($reason)")
    }

    override suspend fun uploadCloudBusinessSnapshot(snapshot: CloudBusinessSnapshot) {
        val id = requireId()
        cloudSnapshots[snapshot.businessId] = snapshot
        recordAuditEvent("CLOUD_SNAPSHOT_SYNC", id.uid, id.email, snapshot.businessId, "Synced ${snapshot.recordCount} records")
    }

    override suspend fun downloadCloudBusinessSnapshot(businessId: String): CloudBusinessSnapshot? {
        requireId()
        return cloudSnapshots[businessId]
    }

    override suspend fun getMyAccessRequest(): AccessRequestRecord? {
        val id = currentIdentity() ?: return null
        return requests[id.uid]
    }

    override suspend fun getMyAccessGrant(): AccessGrantRecord? {
        val id = currentIdentity() ?: return null
        return grants[id.uid] ?: grants.values.firstOrNull { it.email.equals(id.email, ignoreCase = true) }
    }

    override suspend fun submitAccessRequest(submission: AccessRequestSubmission): AccessRequestRecord {
        val id = requireId()
        if (!statusState.value.registrationsEnabled) {
            throw AuthorizationException("Registrations disabled by Super Admin.")
        }
        if (statusState.value.emergencyLockAllNonSuperAdmin && !isVerifiedSuperAdminCurrent()) {
            throw AuthorizationException("Emergency lock active.")
        }
        if (submission.requestedRole !in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES) {
            throw AuthorizationException("Cannot request SUPER_ADMIN role.")
        }
        require(submission.fullName.trim().length >= 2)
        require(submission.requestedBusinessName.trim().length >= 2)
        val existing = requests[id.uid]
        if (existing != null && existing.status != SuperAdminConfig.GRANT_PENDING) {
            throw AuthorizationException("Request cannot be overwritten when status is ${existing.status}")
        }
        val record = AccessRequestRecord(
            id = id.uid,
            uid = id.uid,
            email = id.email,
            fullName = submission.fullName.trim(),
            phone = submission.phone.trim(),
            requestedRole = submission.requestedRole,
            requestedBusinessName = submission.requestedBusinessName.trim(),
            requestedBranchName = submission.requestedBranchName.trim().ifBlank { "Doha HQ" },
            qidNumber = submission.qidNumber?.trim()?.ifBlank { null },
            passportNumber = submission.passportNumber?.trim()?.ifBlank { null },
            identityDocSummary = submission.identityDocSummary?.trim()?.ifBlank { null },
            deviceId = submission.deviceId,
            deviceName = submission.deviceName,
            status = SuperAdminConfig.GRANT_PENDING,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        requests[id.uid] = record
        recordAuditEvent("ACCESS_REQUEST_SUBMITTED", id.uid, id.email, null, "Submitted ${record.requestedRole} request")
        return record
    }

    override suspend fun verifyAndOpenApprovedSession(
        uid: String,
        email: String,
        businessId: String?,
        deviceId: String,
        deviceName: String,
        rememberMe: Boolean
    ): CentralVerificationResult {
        val id = currentIdentity() ?: return CentralVerificationResult.Denied("Sign in required", "UNAUTHENTICATED")
        val cleanEmail = email.trim().lowercase()
        val admin = isVerifiedSuperAdminCurrent()
        val revokedDev = isDeviceRevokedLost(id.uid, deviceId)
        if (revokedDev != null) {
            return CentralVerificationResult.Denied(
                revokedDev.revokedReason ?: "Device revoked as LOST/STOLEN.",
                SuperAdminConfig.DEVICE_REVOKED_LOST,
                emergencyLocked = true
            )
        }
        if (statusState.value.emergencyLockAllNonSuperAdmin && !admin) {
            return CentralVerificationResult.Denied(
                statusState.value.lockMessage ?: "Global Emergency Lock active.",
                "EMERGENCY_LOCKED",
                emergencyLocked = true
            )
        }
        val grant = grants[id.uid]
            ?: grants[uid]
            ?: grants.values.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
            ?: return CentralVerificationResult.Denied(
                "Super Admin approval required before login.",
                requests[id.uid]?.status ?: SuperAdminConfig.GRANT_PENDING
            )
        if (!grant.isActiveGrant()) {
            return CentralVerificationResult.Denied("Grant status is ${grant.status}.", grant.status)
        }
        val targetBizId = businessId?.takeIf { grant.allowedBusinessIds.contains(it) } ?: grant.activeBusinessId
        if (!grant.allowedBusinessIds.contains(targetBizId)) {
            return CentralVerificationResult.Denied("Business not allowed.", "UNAUTHORIZED_BUSINESS")
        }
        val biz = businesses[targetBizId]
            ?: return CentralVerificationResult.Denied("Approved business missing.", SuperAdminConfig.GRANT_PENDING)
        if (biz.status != SuperAdminConfig.GRANT_ACTIVE) {
            return CentralVerificationResult.Denied("Business status is ${biz.status}.", biz.status)
        }
        try {
            upsertActiveDevice(id.uid, id.email, deviceId, deviceName)
        } catch (e: AuthorizationException) {
            return CentralVerificationResult.Denied(e.message ?: "Device blocked", SuperAdminConfig.DEVICE_REVOKED_LOST, true)
        }
        val sessId = CryptoUtil.newUuid()
        sessions[sessId] = CentralSessionRecord(
            id = sessId,
            uid = grant.uid,
            businessId = targetBizId,
            deviceId = deviceId,
            deviceName = deviceName,
            isRevoked = false,
            rememberMe = rememberMe,
            expiresAtEpoch = System.currentTimeMillis() + 30L * 24 * 3600_000L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        return CentralVerificationResult.Approved(grant, biz, sessId)
    }

    override suspend fun validateExistingCentralSession(
        serverSessionId: String,
        uid: String,
        email: String
    ): CentralVerificationResult {
        val id = currentIdentity() ?: return CentralVerificationResult.Denied("Unauthenticated", "UNAUTHENTICATED")
        val admin = isVerifiedSuperAdminCurrent()
        if (statusState.value.emergencyLockAllNonSuperAdmin && !admin) {
            return CentralVerificationResult.Denied("Emergency Lock active", "EMERGENCY_LOCKED", emergencyLocked = true)
        }
        val sess = sessions[serverSessionId] ?: return CentralVerificationResult.Denied("Session not found", "REVOKED")
        if (sess.isRevoked || sess.uid != id.uid || sess.expiresAtEpoch <= System.currentTimeMillis()) {
            return CentralVerificationResult.Denied("Session revoked or identity mismatch", "REVOKED")
        }
        val revokedDev = isDeviceRevokedLost(id.uid, sess.deviceId)
        if (revokedDev != null) {
            return CentralVerificationResult.Denied(
                revokedDev.revokedReason ?: "Device revoked as LOST/STOLEN.",
                SuperAdminConfig.DEVICE_REVOKED_LOST,
                emergencyLocked = true
            )
        }
        val grant = grants[sess.uid] ?: grants[id.uid]
            ?: return CentralVerificationResult.Denied("Grant revoked", "REVOKED")
        if (!grant.isActiveGrant()) {
            return CentralVerificationResult.Denied("Grant status is ${grant.status}", grant.status)
        }
        val biz = businesses[sess.businessId] ?: return CentralVerificationResult.Denied("Business missing", "REVOKED")
        if (biz.status != SuperAdminConfig.GRANT_ACTIVE) {
            return CentralVerificationResult.Denied("Business suspended", biz.status)
        }
        return CentralVerificationResult.Approved(grant, biz, serverSessionId)
    }

    override suspend fun revokeCentralSession(serverSessionId: String, reason: String) {
        sessions[serverSessionId]?.let {
            sessions[serverSessionId] = it.copy(isRevoked = true, revokedReason = reason)
            gateVersion.value += 1
        }
    }

    override fun observeGateRevocation(uid: String, businessId: String, serverSessionId: String, deviceId: String): Flow<String?> {
        return combine(statusState, gateVersion) { status, _ ->
            val admin = SuperAdminConfig.isSuperAdminEmail(currentIdentity()?.email) && currentIdentity()?.emailVerified == true
            if (status.emergencyLockAllNonSuperAdmin && !admin) {
                return@combine status.lockMessage ?: "Global Emergency Lock activated."
            }
            if (deviceId.isNotBlank()) {
                val dev = adminDevices["${uid}_${deviceId}"]
                if (dev?.status == SuperAdminConfig.DEVICE_REVOKED_LOST) {
                    return@combine "This device was revoked as LOST/STOLEN (${dev.revokedReason ?: "Admin"})."
                }
            }
            val grant = grants[uid]
            if (grant == null || !grant.isActiveGrant()) {
                return@combine "Access grant revoked or suspended (${grant?.status ?: "REMOVED"})."
            }
            val sess = sessions[serverSessionId]
            if (sess == null || sess.isRevoked) {
                return@combine "Session revoked (${sess?.revokedReason ?: "Admin"})."
            }
            null
        }.filterNotNull()
    }

    override suspend fun listAllAccessRequests(): List<AccessRequestRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return requests.values.toList()
    }

    override suspend fun listAllAccessGrants(): List<AccessGrantRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return grants.values.toList()
    }

    override suspend fun listAllBusinesses(): List<CentralBusinessRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return businesses.values.toList()
    }

    override suspend fun listAuditLogs(): List<AdminAuditRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return auditLogs.toList()
    }

    override suspend fun approveAccessRequest(
        requestUid: String,
        assignedRole: String,
        existingBusinessId: String?,
        permissionKeys: List<String>,
        allowedPropertyIds: List<String>,
        note: String
    ): Pair<AccessGrantRecord, CentralBusinessRecord> {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Only Super Admin can approve.")
        require(assignedRole in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES) { "Cannot assign SUPER_ADMIN" }
        val req = requests[requestUid] ?: throw IllegalArgumentException("Request not found")
        val bizId = existingBusinessId ?: "biz_${CryptoUtil.newUuid().take(12)}"
        val biz = if (existingBusinessId == null) {
            CentralBusinessRecord(
                id = bizId,
                name = req.requestedBusinessName,
                code = "BIZ-${bizId.takeLast(6).uppercase()}",
                branchName = req.requestedBranchName,
                ownerUid = if (assignedRole == "OWNER") req.uid else admin.uid,
                ownerEmail = if (assignedRole == "OWNER") req.email else admin.email,
                status = SuperAdminConfig.GRANT_ACTIVE,
                approvedByUid = admin.uid
            ).also { businesses[bizId] = it }
        } else {
            businesses[existingBusinessId] ?: throw IllegalArgumentException("Business not found")
        }
        val perms = permissionKeys.ifEmpty {
            AuthorizationGuard.defaultPermissionCatalog().filter { (m, a, _) ->
                when (assignedRole) {
                    "OWNER" -> true
                    "CUSTOMER" -> m == AppModule.CUSTOMER_PORTAL.name
                    else -> m in setOf(AppModule.DASHBOARD.name, AppModule.MAINTENANCE.name, AppModule.PROPERTIES.name) &&
                        a in setOf(ActionType.VIEW.name, ActionType.CREATE.name)
                }
            }.map { "${it.first}:${it.second}" }
        }
        val grant = AccessGrantRecord(
            id = req.uid,
            uid = req.uid,
            email = req.email,
            fullName = req.fullName,
            authorityRole = assignedRole,
            status = SuperAdminConfig.GRANT_ACTIVE,
            activeBusinessId = bizId,
            allowedBusinessIds = listOf(bizId),
            allowedPropertyIds = allowedPropertyIds,
            restrictToAssignedProperties = allowedPropertyIds.isNotEmpty(),
            permissionKeys = perms,
            approvedByUid = admin.uid
        )
        grants[req.uid] = grant
        requests[req.uid] = req.copy(status = "APPROVED", reviewedByUid = admin.uid, reviewNote = note)
        gateVersion.value += 1
        recordAuditEvent("APPROVE_ACCESS", req.uid, req.email, bizId, "Approved as $assignedRole")
        return grant to biz
    }

    override suspend fun rejectAccessRequest(requestUid: String, note: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val req = requests[requestUid] ?: return
        requests[requestUid] = req.copy(status = SuperAdminConfig.GRANT_REJECTED, reviewedByUid = admin.uid, reviewNote = note)
        gateVersion.value += 1
        recordAuditEvent("REJECT_ACCESS", requestUid, req.email, null, note)
    }

    override suspend fun setUserGrantStatus(targetUid: String, newStatus: String, note: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetUid != admin.uid) { "Super Admin cannot revoke themselves." }
        val g = grants[targetUid] ?: throw IllegalArgumentException("Grant missing")
        grants[targetUid] = g.copy(status = newStatus, minSessionValidAfterEpoch = System.currentTimeMillis())
        if (newStatus != SuperAdminConfig.GRANT_ACTIVE) {
            revokeAllUserSessionsCentral(targetUid, "SUPER_ADMIN_$newStatus")
            requests[targetUid]?.let { requests[targetUid] = it.copy(status = if (newStatus == SuperAdminConfig.GRANT_REVOKED) SuperAdminConfig.GRANT_REVOKED else SuperAdminConfig.GRANT_PENDING, reviewNote = note) }
        }
        gateVersion.value += 1
        recordAuditEvent("GRANT_STATUS_$newStatus", targetUid, g.email, g.activeBusinessId, note)
    }

    override suspend fun revokeAllUserSessionsCentral(targetUid: String, reason: String) {
        if (!isVerifiedSuperAdminCurrent() && currentIdentity()?.uid != targetUid) {
            throw AuthorizationException("Only Super Admin can revoke sessions.")
        }
        sessions.entries.forEach { (id, s) ->
            if (s.uid == targetUid) {
                sessions[id] = s.copy(isRevoked = true, revokedReason = reason)
            }
        }
        gateVersion.value += 1
    }

    override suspend fun setRegistrationsEnabled(enabled: Boolean) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        statusState.value = statusState.value.copy(registrationsEnabled = enabled)
        recordAuditEvent("REGISTRATIONS_$enabled", admin.uid, admin.email, null, "Registrations set to $enabled")
    }

    override suspend fun setEmergencyLock(locked: Boolean, lockMessage: String) {
        val admin = requireId()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        statusState.value = statusState.value.copy(
            emergencyLockAllNonSuperAdmin = locked,
            lockMessage = lockMessage
        )
        gateVersion.value += 1
        recordAuditEvent("EMERGENCY_LOCK_$locked", admin.uid, admin.email, null, lockMessage)
    }

    override suspend fun recordAuditEvent(
        action: String,
        targetUid: String?,
        targetEmail: String?,
        businessId: String?,
        details: String
    ) {
        val actor = currentIdentity() ?: return
        auditLogs.add(
            AdminAuditRecord(
                id = CryptoUtil.newUuid(),
                actorUid = actor.uid,
                actorEmail = actor.email,
                action = action,
                targetUid = targetUid,
                targetEmail = targetEmail,
                businessId = businessId,
                details = details,
                createdAt = Timestamp.now()
            )
        )
    }

    override suspend fun signOutCentral() {
        identityState.value = null
    }
}
