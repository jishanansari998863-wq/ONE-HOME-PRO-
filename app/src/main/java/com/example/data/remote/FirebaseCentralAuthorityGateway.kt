package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.domain.security.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class FirebaseCentralAuthorityGateway private constructor(
    private val context: Context,
    private val usesInjectedInstances: Boolean,
    private val dbLazy: Lazy<FirebaseFirestore>,
    private val authLazy: Lazy<FirebaseAuth>
) : CentralAuthorityGateway {

    private val db: FirebaseFirestore by dbLazy
    private val auth: FirebaseAuth by authLazy

    constructor(
        context: Context,
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth
    ) : this(context.applicationContext ?: context, true, lazyOf(db), lazyOf(auth))

    constructor(context: Context) : this(
        context = context.applicationContext ?: context,
        usesInjectedInstances = false,
        dbLazy = lazy {
            val appCtx = context.applicationContext ?: context
            val diag = inspectFirebaseConfiguration(appCtx)
            if (diag != null) throw IllegalStateException(diag)
            val app = FirebaseConfigProvider.ensureFirebaseApp(appCtx)
                ?: throw IllegalStateException("FirebaseApp unavailable despite validated configuration.")
            val dbId = resolveFirestoreDatabaseId(appCtx)
                ?: throw IllegalStateException("Missing string resource firestore_database_id.")
            FirebaseFirestore.getInstance(app, dbId)
        },
        authLazy = lazy {
            val appCtx = context.applicationContext ?: context
            val diag = inspectFirebaseConfiguration(appCtx)
            if (diag != null) throw IllegalStateException(diag)
            val app = FirebaseConfigProvider.ensureFirebaseApp(appCtx)
                ?: throw IllegalStateException("FirebaseApp unavailable despite validated configuration.")
            FirebaseAuth.getInstance(app)
        }
    )

    override fun isConfigured(): Boolean {
        if (usesInjectedInstances) return true
        return inspectFirebaseConfiguration(context) == null
    }

    override fun configurationDiagnostic(): String? {
        if (usesInjectedInstances) return null
        return inspectFirebaseConfiguration(context)
    }

    override val authIdentityFlow: Flow<CentralIdentity?> = callbackFlow {
        if (!isConfigured()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val firebaseAuth = try {
            auth
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach AuthStateListener", e)
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            trySend(
                u?.let {
                    CentralIdentity(
                        uid = it.uid,
                        email = it.email?.trim()?.lowercase().orEmpty(),
                        displayName = it.displayName.orEmpty(),
                        emailVerified = it.isEmailVerified
                    )
                }
            )
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun currentIdentity(): CentralIdentity? {
        if (!isConfigured()) return null
        val u = try {
            auth.currentUser
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read FirebaseAuth.currentUser safely", e)
            return null
        } ?: return null
        return CentralIdentity(
            uid = u.uid,
            email = u.email?.trim()?.lowercase().orEmpty(),
            displayName = u.displayName.orEmpty(),
            emailVerified = u.isEmailVerified
        )
    }

    private fun requireIdentity(): CentralIdentity {
        configurationDiagnostic()?.let { throw IllegalStateException(it) }
        return currentIdentity()
            ?: throw AuthorizationException("Google Sign-In required before accessing central authority.")
    }

    override suspend fun getPlatformStatus(): PlatformStatus {
        requireIdentity()
        val path = "$COL_PLATFORM_STATUS/$DOC_PUBLIC"
        return try {
            val snap = db.collection(COL_PLATFORM_STATUS).document(DOC_PUBLIC).get().awaitResult()
            if (!snap.exists()) {
                PlatformStatus(
                    id = DOC_PUBLIC,
                    isBootstrapped = false,
                    registrationsEnabled = true,
                    emergencyLockAllNonSuperAdmin = false
                )
            } else {
                snap.toObject(PlatformStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: PlatformStatus()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            if (ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                PlatformStatus(id = DOC_PUBLIC, isBootstrapped = true, registrationsEnabled = false, emergencyLockAllNonSuperAdmin = false)
            } else {
                handleFirestoreError(e, OperationType.GET, path, auth)
                throw e
            }
        }
    }

    override suspend fun isPlatformBootstrapped(): Boolean {
        val identity = currentIdentity() ?: return false
        val status = getPlatformStatus()
        if (status.isBootstrapped) return true
        if (SuperAdminConfig.isSuperAdminEmail(identity.email) && identity.emailVerified) {
            val path = "$COL_PLATFORM_CONFIG/$DOC_GLOBAL"
            return try {
                db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).get().awaitResult().exists()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, path, auth)
                false
            }
        }
        return false
    }

    override suspend fun isVerifiedSuperAdminCurrent(): Boolean {
        val id = currentIdentity() ?: return false
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) return false
        val path = "$COL_PLATFORM_CONFIG/$DOC_GLOBAL"
        return try {
            val snap = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).get().awaitResult()
            if (!snap.exists()) return true // eligible to bootstrap first-time Super Admin
            val cfg = snap.toObject(PlatformConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            cfg != null && cfg.superAdminUid == id.uid && SuperAdminConfig.isSuperAdminEmail(cfg.superAdminEmail)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, auth)
            false
        }
    }

    override suspend fun bootstrapSuperAdmin(masterPassword: String): Pair<String, List<String>> {
        val kit = bootstrapSuperAdminWithRecovery(
            masterPassword = masterPassword,
            securityQuestion = "",
            securityAnswer = "",
            deviceId = "bootstrap_primary",
            deviceName = "Primary Device"
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
        val id = requireIdentity()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Only pre-authorized Super Admin account (${SuperAdminConfig.SUPER_ADMIN_EMAIL}) can bootstrap.")
        }
        require(masterPassword.length >= 10) { "Super Admin master password must be at least 10 characters." }

        val configRef = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL)
        val statusRef = db.collection(COL_PLATFORM_STATUS).document(DOC_PUBLIC)

        val existing = try {
            configRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, configRef.path, auth)
            throw e
        }
        if (existing.exists()) {
            throw AuthorizationException("Super Admin authority is already bootstrapped and locked.")
        }

        val salt = CryptoUtil.generateSaltBase64()
        val passwordHash = CryptoUtil.hashPassword(masterPassword, salt)
        val totpSecret = CryptoUtil.generateTotpSecret()
        val recoveryCodes = CryptoUtil.generateRecoveryCodes(6)
        val recoveryHashes = recoveryCodes.map { CryptoUtil.sha256Hex(it) }
        val masterRecoveryKey = CryptoUtil.generateMasterRecoveryKey()
        val backupSalt = CryptoUtil.generateSaltBase64()
        val backupHash = CryptoUtil.hashPassword(masterRecoveryKey, backupSalt)

        val cleanQuestion = securityQuestion.trim().take(250)
        val cleanAnswer = securityAnswer.trim().lowercase()
        val ansSalt = if (cleanAnswer.isNotBlank()) CryptoUtil.generateSaltBase64() else ""
        val ansHash = if (cleanAnswer.isNotBlank()) CryptoUtil.hashPassword(cleanAnswer, ansSalt) else ""

        val configPayload = mutableMapOf<String, Any>(
            "superAdminUid" to id.uid,
            "superAdminEmail" to SuperAdminConfig.SUPER_ADMIN_EMAIL,
            "mfaEnabled" to true,
            "passwordSalt" to salt,
            "passwordHash" to passwordHash,
            "totpSecret" to totpSecret,
            "recoveryCodeHashes" to recoveryHashes,
            "backupKeySalt" to backupSalt,
            "backupKeyHash" to backupHash,
            "securityQuestion" to cleanQuestion,
            "securityAnswerSalt" to ansSalt,
            "securityAnswerHash" to ansHash,
            "minAdminSessionValidAfterEpoch" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val statusPayload = mapOf(
            "isBootstrapped" to true,
            "registrationsEnabled" to true,
            "emergencyLockAllNonSuperAdmin" to false,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val hqBusinessId = "biz_superadmin_hq"
        val bizPayload = mapOf(
            "name" to "ONE HOME PRO HQ",
            "code" to "BIZ-OHPHQ",
            "branchName" to "Doha HQ",
            "ownerUid" to id.uid,
            "ownerEmail" to SuperAdminConfig.SUPER_ADMIN_EMAIL,
            "status" to SuperAdminConfig.GRANT_ACTIVE,
            "approvedByUid" to id.uid,
            "currency" to "QAR",
            "timezone" to "Asia/Qatar",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val allPerms = AuthorizationGuard.defaultPermissionCatalog().map { "${it.first}:${it.second}" }
        val grantPayload = mapOf(
            "uid" to id.uid,
            "email" to SuperAdminConfig.SUPER_ADMIN_EMAIL,
            "fullName" to id.displayName.ifBlank { "Super Admin" },
            "authorityRole" to "OWNER",
            "status" to SuperAdminConfig.GRANT_ACTIVE,
            "activeBusinessId" to hqBusinessId,
            "allowedBusinessIds" to listOf(hqBusinessId),
            "allowedPropertyIds" to emptyList<String>(),
            "restrictToAssignedProperties" to false,
            "permissionKeys" to allPerms,
            "approvedByUid" to id.uid,
            "minSessionValidAfterEpoch" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            configRef.set(configPayload).awaitResult()
            statusRef.set(statusPayload).awaitResult()
            db.collection(COL_BUSINESSES).document(hqBusinessId).set(bizPayload).awaitResult()
            db.collection(COL_ACCESS_GRANTS).document(id.uid).set(grantPayload).awaitResult()
            upsertActiveAdminDevice(id.uid, id.email, deviceId, deviceName)
            recordAuditEvent(
                action = "SUPER_ADMIN_BOOTSTRAP",
                targetUid = id.uid,
                targetEmail = id.email,
                businessId = hqBusinessId,
                details = "Exclusive Super Admin authority bootstrapped with MFA, recovery codes, and master recovery key"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, configRef.path, auth)
            throw e
        }

        return SuperAdminProvisionKit(
            totpSecret = totpSecret,
            recoveryCodes = recoveryCodes,
            masterRecoveryKey = masterRecoveryKey,
            securityQuestion = cleanQuestion
        )
    }

    override suspend fun verifySuperAdminChallenge(masterPassword: String, totpOrRecoveryCode: String): Boolean {
        val id = requireIdentity()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Unauthorized Super Admin challenge identity.")
        }
        val configRef = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL)
        val snap = try {
            configRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, configRef.path, auth)
            throw e
        }
        val cfg = snap.toObject(PlatformConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw AuthorizationException("Super Admin config missing.")
        if (cfg.superAdminUid != id.uid || !SuperAdminConfig.isSuperAdminEmail(cfg.superAdminEmail)) {
            throw AuthorizationException("Super Admin UID mismatch blocked.")
        }
        if (!CryptoUtil.verifyPassword(masterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            recordAuditEvent("SUPER_ADMIN_LOGIN_FAIL", id.uid, id.email, null, "Invalid Super Admin master password attempt")
            return false
        }
        val now = System.currentTimeMillis()
        val trimmedCode = totpOrRecoveryCode.trim()
        val totpOk = CryptoUtil.verifyTotpCode(cfg.totpSecret, trimmedCode, now)
        if (totpOk) {
            recordAuditEvent("SUPER_ADMIN_VERIFIED", id.uid, id.email, null, "Super Admin authenticated via master password + TOTP MFA")
            return true
        }
        val upperCode = trimmedCode.uppercase()
        val candidateHash = CryptoUtil.sha256Hex(upperCode)
        if (cfg.recoveryCodeHashes.contains(candidateHash)) {
            val remaining = cfg.recoveryCodeHashes.filterNot { it == candidateHash }
            if (remaining.isNotEmpty()) {
                try {
                    configRef.update(
                        mapOf(
                            "recoveryCodeHashes" to remaining,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitResult()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, configRef.path, auth)
                }
            }
            recordAuditEvent("SUPER_ADMIN_RECOVERY_USED", id.uid, id.email, null, "Super Admin authenticated using single-use recovery code")
            return true
        }
        if (cfg.backupKeyHash.isNotBlank() && cfg.backupKeySalt.isNotBlank() &&
            CryptoUtil.verifyPassword(upperCode, cfg.backupKeySalt, cfg.backupKeyHash)
        ) {
            recordAuditEvent("SUPER_ADMIN_MASTER_KEY_LOGIN", id.uid, id.email, null, "Super Admin authenticated via Master Recovery Key")
            return true
        }
        recordAuditEvent("SUPER_ADMIN_MFA_FAIL", id.uid, id.email, null, "Invalid TOTP/recovery code for Super Admin")
        return false
    }

    override suspend fun getSuperAdminSecurityQuestion(): String? {
        val id = currentIdentity() ?: return null
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) return null
        return try {
            val snap = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).get().awaitResult()
            snap.toObject(PlatformConfig::class.java)?.securityQuestion?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun recoverSuperAdminAccount(request: SuperAdminRecoveryRequest): SuperAdminProvisionKit {
        val id = requireIdentity()
        if (!id.emailVerified || !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            throw AuthorizationException("Only verified Super Admin (${SuperAdminConfig.SUPER_ADMIN_EMAIL}) can perform account recovery.")
        }
        require(request.newMasterPassword.length >= 10) { "New master password must be at least 10 characters." }

        val lostDevice = isDeviceRevokedLost(id.uid, request.currentDeviceId)
        if (lostDevice != null) {
            throw AuthorizationException("This mobile device (${lostDevice.deviceName}) was marked LOST/REVOKED and cannot initiate recovery.")
        }

        val configRef = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL)
        val snap = try {
            configRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, configRef.path, auth)
            throw e
        }
        val cfg = snap.toObject(PlatformConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw AuthorizationException("Super Admin authority is not bootstrapped yet.")
        if (cfg.superAdminUid != id.uid || !SuperAdminConfig.isSuperAdminEmail(cfg.superAdminEmail)) {
            throw AuthorizationException("Recovery denied: account ownership UID mismatch.")
        }

        val proof = request.recoveryProof.trim()
        require(proof.isNotBlank()) { "Recovery key, recovery code, or security answer is required." }

        val proofUpper = proof.uppercase()
        val matchedRecoveryCodeHash = CryptoUtil.sha256Hex(proofUpper)
            .takeIf { cfg.recoveryCodeHashes.contains(it) }
        val matchedMasterKey = cfg.backupKeyHash.isNotBlank() && cfg.backupKeySalt.isNotBlank() &&
            CryptoUtil.verifyPassword(proofUpper, cfg.backupKeySalt, cfg.backupKeyHash)
        val matchedSecurityAnswer = cfg.securityAnswerHash.isNotBlank() && cfg.securityAnswerSalt.isNotBlank() &&
            CryptoUtil.verifyPassword(proof.lowercase(), cfg.securityAnswerSalt, cfg.securityAnswerHash)

        if (matchedRecoveryCodeHash == null && !matchedMasterKey && !matchedSecurityAnswer) {
            recordAuditEvent("SUPER_ADMIN_RECOVERY_FAILED", id.uid, id.email, null, "Failed recovery proof on device ${request.currentDeviceName}")
            throw AuthorizationException("Recovery verification failed: invalid Master Recovery Key, Recovery Code, or Security Answer.")
        }

        val now = System.currentTimeMillis()
        val newSalt = CryptoUtil.generateSaltBase64()
        val newPasswordHash = CryptoUtil.hashPassword(request.newMasterPassword, newSalt)
        val newTotpSecret = CryptoUtil.generateTotpSecret()
        val newRecoveryCodes = CryptoUtil.generateRecoveryCodes(6)
        val newRecoveryHashes = newRecoveryCodes.map { CryptoUtil.sha256Hex(it) }
        val newMasterKey = CryptoUtil.generateMasterRecoveryKey()
        val newBackupSalt = CryptoUtil.generateSaltBase64()
        val newBackupHash = CryptoUtil.hashPassword(newMasterKey, newBackupSalt)

        val updates = mutableMapOf<String, Any>(
            "passwordSalt" to newSalt,
            "passwordHash" to newPasswordHash,
            "totpSecret" to newTotpSecret,
            "recoveryCodeHashes" to newRecoveryHashes,
            "backupKeySalt" to newBackupSalt,
            "backupKeyHash" to newBackupHash,
            "minAdminSessionValidAfterEpoch" to now,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val qClean = request.securityQuestion?.trim()?.take(250).orEmpty()
        val aClean = request.securityAnswer?.trim()?.lowercase().orEmpty()
        if (qClean.isNotBlank() && aClean.isNotBlank()) {
            val aSalt = CryptoUtil.generateSaltBase64()
            updates["securityQuestion"] = qClean
            updates["securityAnswerSalt"] = aSalt
            updates["securityAnswerHash"] = CryptoUtil.hashPassword(aClean, aSalt)
        }

        try {
            configRef.update(updates).awaitResult()
            upsertActiveAdminDevice(id.uid, id.email, request.currentDeviceId, request.currentDeviceName)
            if (request.revokeOtherDevices) {
                revokeAllOtherAdminDevicesAndSessions(
                    callerDeviceId = request.currentDeviceId,
                    reason = "SUPER_ADMIN_NEW_DEVICE_RECOVERY"
                )
            }
            recordAuditEvent(
                action = "SUPER_ADMIN_RECOVERY_COMPLETED",
                targetUid = id.uid,
                targetEmail = id.email,
                businessId = null,
                details = "Recovered Super Admin on '${request.currentDeviceName}' (${request.currentDeviceId}), rotated credentials & MFA, revokeOtherDevices=${request.revokeOtherDevices}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, configRef.path, auth)
            throw e
        }

        return SuperAdminProvisionKit(
            totpSecret = newTotpSecret,
            recoveryCodes = newRecoveryCodes,
            masterRecoveryKey = newMasterKey,
            securityQuestion = qClean.ifBlank { cfg.securityQuestion }
        )
    }

    override suspend fun updateSuperAdminRecoverySettings(
        currentMasterPassword: String,
        securityQuestion: String,
        securityAnswer: String,
        rotateBackupKit: Boolean
    ): SuperAdminProvisionKit? {
        val id = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val configRef = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL)
        val snap = configRef.get().awaitResult()
        val cfg = snap.toObject(PlatformConfig::class.java)
            ?: throw AuthorizationException("Super Admin config missing.")
        if (!CryptoUtil.verifyPassword(currentMasterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            throw AuthorizationException("Invalid current Master Password.")
        }

        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        val qClean = securityQuestion.trim().take(250)
        val aClean = securityAnswer.trim().lowercase()
        if (qClean.isNotBlank() && aClean.isNotBlank()) {
            val aSalt = CryptoUtil.generateSaltBase64()
            updates["securityQuestion"] = qClean
            updates["securityAnswerSalt"] = aSalt
            updates["securityAnswerHash"] = CryptoUtil.hashPassword(aClean, aSalt)
        }

        var newKit: SuperAdminProvisionKit? = null
        if (rotateBackupKit) {
            val newTotp = CryptoUtil.generateTotpSecret()
            val newCodes = CryptoUtil.generateRecoveryCodes(6)
            val newMasterKey = CryptoUtil.generateMasterRecoveryKey()
            val newBackupSalt = CryptoUtil.generateSaltBase64()
            val newBackupHash = CryptoUtil.hashPassword(newMasterKey, newBackupSalt)
            updates["totpSecret"] = newTotp
            updates["recoveryCodeHashes"] = newCodes.map { CryptoUtil.sha256Hex(it) }
            updates["backupKeySalt"] = newBackupSalt
            updates["backupKeyHash"] = newBackupHash
            newKit = SuperAdminProvisionKit(
                totpSecret = newTotp,
                recoveryCodes = newCodes,
                masterRecoveryKey = newMasterKey,
                securityQuestion = qClean.ifBlank { cfg.securityQuestion }
            )
        }

        configRef.update(updates).awaitResult()
        recordAuditEvent(
            action = "SUPER_ADMIN_RECOVERY_SETTINGS_UPDATED",
            targetUid = id.uid,
            targetEmail = id.email,
            businessId = null,
            details = "Updated Super Admin recovery settings (rotatedKit=$rotateBackupKit)"
        )
        return newKit
    }

    override suspend fun isDeviceRevokedLost(uid: String, deviceId: String): AdminDeviceRecord? {
        if (deviceId.isBlank()) return null
        val docId = "${uid}_${deviceId}"
        val path = "$COL_ADMIN_DEVICES/$docId"
        return try {
            val snap = db.collection(COL_ADMIN_DEVICES).document(docId).get().awaitResult()
            val dev = snap.toObject(AdminDeviceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            dev?.takeIf { it.status == SuperAdminConfig.DEVICE_REVOKED_LOST }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listAdminDevices(): List<AdminDeviceRecord> {
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        return try {
            db.collection(COL_ADMIN_DEVICES)
                .get()
                .awaitResult()
                .toObjects(AdminDeviceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.lastSeenEpoch }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_ADMIN_DEVICES, auth)
            emptyList()
        }
    }

    override suspend fun markAdminDeviceLost(targetDeviceId: String, reason: String, callerDeviceId: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetDeviceId != callerDeviceId) { "Use Logout instead of blocking your currently active device." }
        val docId = "${admin.uid}_${targetDeviceId}"
        val ref = db.collection(COL_ADMIN_DEVICES).document(docId)
        val snap = ref.get().awaitResult()
        val now = System.currentTimeMillis()
        if (snap.exists()) {
            ref.update(
                mapOf(
                    "status" to SuperAdminConfig.DEVICE_REVOKED_LOST,
                    "revokedReason" to reason.ifBlank { "Marked LOST/STOLEN by Super Admin" }.take(120),
                    "revokedByDeviceId" to callerDeviceId.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        }
        // Revoke all sessions originating from that lost device
        val sessions = db.collection(COL_SESSIONS)
            .whereEqualTo("uid", admin.uid)
            .get()
            .awaitResult()
        sessions.documents.forEach { sDoc ->
            if (sDoc.getString("deviceId") == targetDeviceId && sDoc.getBoolean("isRevoked") != true) {
                sDoc.reference.update(
                    mapOf(
                        "isRevoked" to true,
                        "revokedReason" to "DEVICE_LOST_LOCKED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            }
        }
        recordAuditEvent(
            action = "DEVICE_REVOKED_LOST",
            targetUid = admin.uid,
            targetEmail = admin.email,
            businessId = null,
            details = "Marked device '$targetDeviceId' LOST & revoked all its sessions ($reason)"
        )
    }

    override suspend fun reauthorizeAdminDevice(targetDeviceId: String, masterPassword: String, callerDeviceId: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetDeviceId != callerDeviceId) { "A locked device cannot self-reauthorize." }
        val cfg = db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).get().awaitResult()
            .toObject(PlatformConfig::class.java) ?: throw AuthorizationException("Super Admin config missing.")
        if (!CryptoUtil.verifyPassword(masterPassword, cfg.passwordSalt, cfg.passwordHash)) {
            throw AuthorizationException("Master Password verification failed for device re-authorization.")
        }
        val docId = "${admin.uid}_${targetDeviceId}"
        val ref = db.collection(COL_ADMIN_DEVICES).document(docId)
        ref.update(
            mapOf(
                "status" to SuperAdminConfig.DEVICE_ACTIVE,
                "revokedReason" to null,
                "revokedByDeviceId" to null,
                "lastSeenEpoch" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).awaitResult()
        recordAuditEvent(
            action = "DEVICE_REAUTHORIZED",
            targetUid = admin.uid,
            targetEmail = admin.email,
            businessId = null,
            details = "Re-authorized previously lost device '$targetDeviceId' from '$callerDeviceId'"
        )
    }

    override suspend fun revokeAllOtherAdminDevicesAndSessions(callerDeviceId: String, reason: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val now = System.currentTimeMillis()

        val deviceDocs = db.collection(COL_ADMIN_DEVICES)
            .whereEqualTo("uid", admin.uid)
            .get()
            .awaitResult()
        deviceDocs.documents.forEach { dDoc ->
            val dId = dDoc.getString("deviceId").orEmpty()
            if (dId.isNotBlank() && dId != callerDeviceId) {
                dDoc.reference.update(
                    mapOf(
                        "status" to SuperAdminConfig.DEVICE_REVOKED_LOST,
                        "revokedReason" to reason.take(120),
                        "revokedByDeviceId" to callerDeviceId.take(128),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            }
        }

        val sessionDocs = db.collection(COL_SESSIONS)
            .whereEqualTo("uid", admin.uid)
            .get()
            .awaitResult()
        sessionDocs.documents.forEach { sDoc ->
            if (sDoc.getString("deviceId") != callerDeviceId && sDoc.getBoolean("isRevoked") != true) {
                sDoc.reference.update(
                    mapOf(
                        "isRevoked" to true,
                        "revokedReason" to reason.take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            }
        }

        // Advance minAdminSessionValidAfterEpoch so old offline tokens cannot resume
        runCatching {
            db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).update(
                mapOf(
                    "minAdminSessionValidAfterEpoch" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        }

        recordAuditEvent(
            action = "REVOKE_ALL_OTHER_DEVICES",
            targetUid = admin.uid,
            targetEmail = admin.email,
            businessId = null,
            details = "Revoked all devices and sessions except '$callerDeviceId' ($reason)"
        )
    }

    override suspend fun uploadCloudBusinessSnapshot(snapshot: CloudBusinessSnapshot) {
        val id = requireIdentity()
        val ref = db.collection(COL_BUSINESSES).document(snapshot.businessId)
            .collection(COL_CLOUD_SNAPSHOTS).document("latest")
        val payload = mapOf(
            "businessId" to snapshot.businessId,
            "payloadBase64" to snapshot.payloadBase64,
            "sha256Checksum" to snapshot.sha256Checksum,
            "recordCount" to snapshot.recordCount,
            "syncedByUid" to id.uid,
            "syncedByDeviceName" to snapshot.syncedByDeviceName.take(120).ifBlank { "Android Device" },
            "appLanguageCode" to snapshot.appLanguageCode.take(10).ifBlank { "en" },
            "darkTheme" to snapshot.darkTheme,
            "syncedAtEpoch" to snapshot.syncedAtEpoch,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val existing = ref.get().awaitResult()
            if (existing.exists()) {
                val updateMap = payload.toMutableMap().apply { remove("createdAt") }
                ref.update(updateMap).awaitResult()
            } else {
                ref.set(payload).awaitResult()
            }
            recordAuditEvent(
                action = "CLOUD_SNAPSHOT_SYNC",
                targetUid = id.uid,
                targetEmail = id.email,
                businessId = snapshot.businessId,
                details = "Synced ${snapshot.recordCount} records (${snapshot.syncedByDeviceName}) to encrypted cloud snapshot"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path, auth)
            throw e
        }
    }

    override suspend fun downloadCloudBusinessSnapshot(businessId: String): CloudBusinessSnapshot? {
        requireIdentity()
        val ref = db.collection(COL_BUSINESSES).document(businessId)
            .collection(COL_CLOUD_SNAPSHOTS).document("latest")
        return try {
            val snap = ref.get().awaitResult()
            if (!snap.exists()) null
            else snap.toObject(CloudBusinessSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path, auth)
            null
        }
    }

    private suspend fun upsertActiveAdminDevice(uid: String, email: String, deviceId: String, deviceName: String) {
        val cleanDevId = deviceId.trim().ifBlank { "unknown_device" }.take(128)
        val docId = "${uid}_${cleanDevId}"
        val ref = db.collection(COL_ADMIN_DEVICES).document(docId)
        val existingSnap = runCatching { ref.get().awaitResult() }.getOrNull()
        val existingDev = existingSnap?.toObject(AdminDeviceRecord::class.java)
        if (existingDev?.status == SuperAdminConfig.DEVICE_REVOKED_LOST) {
            throw AuthorizationException("Device '$cleanDevId' is locked as LOST/REVOKED.")
        }
        val now = System.currentTimeMillis()
        if (existingSnap?.exists() == true) {
            ref.update(
                mapOf(
                    "deviceName" to deviceName.ifBlank { "Android Device" }.take(120),
                    "lastSeenEpoch" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } else {
            ref.set(
                mapOf(
                    "uid" to uid,
                    "email" to email.ifBlank { "unknown@example.com" }.take(160),
                    "deviceId" to cleanDevId,
                    "deviceName" to deviceName.ifBlank { "Android Device" }.take(120),
                    "status" to SuperAdminConfig.DEVICE_ACTIVE,
                    "lastSeenEpoch" to now,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        }
    }

    override suspend fun getMyAccessRequest(): AccessRequestRecord? {
        val id = currentIdentity() ?: return null
        val docRef = db.collection(COL_ACCESS_REQUESTS).document(id.uid)
        return try {
            val snap = docRef.get().awaitResult()
            if (!snap.exists()) null
            else snap.toObject(AccessRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            null
        }
    }

    override suspend fun getMyAccessGrant(): AccessGrantRecord? {
        val id = currentIdentity() ?: return null
        val docRef = db.collection(COL_ACCESS_GRANTS).document(id.uid)
        return try {
            val snap = docRef.get().awaitResult()
            if (!snap.exists()) null
            else snap.toObject(AccessGrantRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            null
        }
    }

    override suspend fun submitAccessRequest(submission: AccessRequestSubmission): AccessRequestRecord {
        val id = requireIdentity()
        if (submission.requestedRole == SuperAdminConfig.ROLE_SUPER_ADMIN ||
            submission.requestedRole !in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES
        ) {
            throw AuthorizationException("Public applicants cannot request or self-promote to SUPER_ADMIN.")
        }
        require(submission.fullName.trim().length >= 2) { "Full name must be at least 2 characters." }
        require(submission.requestedBusinessName.trim().length >= 2) { "Business name is required." }

        val docRef = db.collection(COL_ACCESS_REQUESTS).document(id.uid)
        val existing = getMyAccessRequest()
        if (existing != null && existing.status != SuperAdminConfig.GRANT_PENDING) {
            throw AuthorizationException("Cannot resubmit request while status is ${existing.status}. Contact Super Admin.")
        }

        try {
            if (existing == null) {
                val payload = buildMap<String, Any> {
                    put("uid", id.uid)
                    put("email", id.email.ifBlank { "unknown@example.com" })
                    put("fullName", submission.fullName.trim().take(100))
                    put("phone", submission.phone.trim().take(30))
                    put("requestedRole", submission.requestedRole)
                    put("requestedBusinessName", submission.requestedBusinessName.trim().take(120))
                    put("requestedBranchName", submission.requestedBranchName.trim().ifBlank { "Doha HQ" }.take(120))
                    submission.qidNumber?.trim()?.take(40)?.takeIf { it.isNotBlank() }?.let { put("qidNumber", it) }
                    submission.passportNumber?.trim()?.take(40)?.takeIf { it.isNotBlank() }?.let { put("passportNumber", it) }
                    submission.identityDocSummary?.trim()?.take(250)?.takeIf { it.isNotBlank() }?.let { put("identityDocSummary", it) }
                    put("deviceId", submission.deviceId.take(128))
                    put("deviceName", submission.deviceName.take(120))
                    put("status", SuperAdminConfig.GRANT_PENDING)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).awaitResult()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "fullName" to submission.fullName.trim().take(100),
                    "phone" to submission.phone.trim().take(30),
                    "requestedBusinessName" to submission.requestedBusinessName.trim().take(120),
                    "requestedBranchName" to submission.requestedBranchName.trim().ifBlank { "Doha HQ" }.take(120),
                    "deviceId" to submission.deviceId.take(128),
                    "deviceName" to submission.deviceName.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                submission.qidNumber?.trim()?.take(40)?.takeIf { it.isNotBlank() }?.let { updatePayload["qidNumber"] = it }
                submission.passportNumber?.trim()?.take(40)?.takeIf { it.isNotBlank() }?.let { updatePayload["passportNumber"] = it }
                submission.identityDocSummary?.trim()?.take(250)?.takeIf { it.isNotBlank() }?.let { updatePayload["identityDocSummary"] = it }
                docRef.update(updatePayload).awaitResult()
            }
            recordAuditEvent(
                action = "ACCESS_REQUEST_SUBMITTED",
                targetUid = id.uid,
                targetEmail = id.email,
                businessId = null,
                details = "Submitted ${submission.requestedRole} access request for business '${submission.requestedBusinessName.trim()}'"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }

        return getMyAccessRequest()
            ?: throw IllegalStateException("Access request submitted cleanly as PENDING_APPROVAL.")
    }

    override suspend fun verifyAndOpenApprovedSession(
        uid: String,
        email: String,
        businessId: String?,
        deviceId: String,
        deviceName: String,
        rememberMe: Boolean
    ): CentralVerificationResult {
        val id = currentIdentity() ?: return CentralVerificationResult.Denied(
            reason = "Central Google Sign-In required.",
            status = "UNAUTHENTICATED"
        )
        if (id.uid != uid && !SuperAdminConfig.isSuperAdminEmail(id.email)) {
            return CentralVerificationResult.Denied("Identity mismatch blocked.", "DENIED")
        }

        val revokedDev = isDeviceRevokedLost(id.uid, deviceId)
        if (revokedDev != null) {
            return CentralVerificationResult.Denied(
                reason = revokedDev.revokedReason ?: "This device ($deviceId) was permanently locked as LOST/REVOKED by Super Admin. Access denied.",
                status = SuperAdminConfig.DEVICE_REVOKED_LOST,
                emergencyLocked = true
            )
        }

        val platformStatus = getPlatformStatus()
        val isAdmin = isVerifiedSuperAdminCurrent()
        if (platformStatus.emergencyLockAllNonSuperAdmin && !isAdmin) {
            return CentralVerificationResult.Denied(
                reason = platformStatus.lockMessage ?: "Global Emergency Lock enabled by Super Admin.",
                status = "EMERGENCY_LOCKED",
                emergencyLocked = true
            )
        }

        val grant = getMyAccessGrant()
        if (grant == null) {
            val req = getMyAccessRequest()
            val reqStatus = req?.status ?: SuperAdminConfig.GRANT_PENDING
            return CentralVerificationResult.Denied(
                reason = "Super Admin approval required before accessing ONE HOME PRO.",
                status = reqStatus
            )
        }
        if (!grant.isActiveGrant()) {
            return CentralVerificationResult.Denied(
                reason = "Your central authorization status is ${grant.status}. Contact Super Admin.",
                status = grant.status
            )
        }

        val resolvedBizId = businessId?.takeIf { grant.allowedBusinessIds.contains(it) } ?: grant.activeBusinessId
        if (!grant.allowedBusinessIds.contains(resolvedBizId)) {
            return CentralVerificationResult.Denied("Business access not authorized by Super Admin.", "UNAUTHORIZED_BUSINESS")
        }

        val bizRef = db.collection(COL_BUSINESSES).document(resolvedBizId)
        val bizSnap = try {
            bizRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, bizRef.path, auth)
            return CentralVerificationResult.Denied("Central business verification failed.", "DENIED")
        }
        val business = bizSnap.toObject(CentralBusinessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: return CentralVerificationResult.Denied("Approved business record missing on server.", "PENDING_APPROVAL")

        if (business.status != SuperAdminConfig.GRANT_ACTIVE) {
            return CentralVerificationResult.Denied("Business status is ${business.status}.", business.status)
        }

        try {
            upsertActiveAdminDevice(id.uid, id.email, deviceId, deviceName)
        } catch (e: AuthorizationException) {
            return CentralVerificationResult.Denied(
                reason = e.message ?: "Device blocked.",
                status = SuperAdminConfig.DEVICE_REVOKED_LOST,
                emergencyLocked = true
            )
        }

        val sessionId = CryptoUtil.newUuid()
        val ttlMillis = if (rememberMe) 30L * 24 * 60 * 60 * 1000L else 12L * 60 * 60 * 1000L
        val sessionRef = db.collection(COL_SESSIONS).document(sessionId)
        val sessionPayload = mapOf(
            "uid" to id.uid,
            "businessId" to resolvedBizId,
            "deviceId" to deviceId.take(128),
            "deviceName" to deviceName.take(120),
            "isRevoked" to false,
            "rememberMe" to rememberMe,
            "expiresAtEpoch" to (System.currentTimeMillis() + ttlMillis),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            sessionRef.set(sessionPayload).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionRef.path, auth)
            return CentralVerificationResult.Denied("Central session creation rejected by server.", "DENIED")
        }

        return CentralVerificationResult.Approved(
            grant = grant,
            business = business,
            serverSessionId = sessionId
        )
    }

    override suspend fun validateExistingCentralSession(
        serverSessionId: String,
        uid: String,
        email: String
    ): CentralVerificationResult {
        val id = currentIdentity()
            ?: return CentralVerificationResult.Denied("Unauthenticated session rejected.", "UNAUTHENTICATED")

        val platformStatus = getPlatformStatus()
        val isAdmin = isVerifiedSuperAdminCurrent()
        if (platformStatus.emergencyLockAllNonSuperAdmin && !isAdmin) {
            return CentralVerificationResult.Denied(
                reason = platformStatus.lockMessage ?: "Global Emergency Lock active.",
                status = "EMERGENCY_LOCKED",
                emergencyLocked = true
            )
        }

        val sessRef = db.collection(COL_SESSIONS).document(serverSessionId)
        val sessSnap = try {
            sessRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, sessRef.path, auth)
            return CentralVerificationResult.Denied("Session denied by central server.", "REVOKED")
        }
        val sess = sessSnap.toObject(CentralSessionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: return CentralVerificationResult.Denied("Central session not found.", "REVOKED")

        if (sess.isRevoked || sess.uid != id.uid || sess.expiresAtEpoch <= System.currentTimeMillis()) {
            return CentralVerificationResult.Denied("Session has been revoked or expired.", "REVOKED")
        }

        val revokedDev = isDeviceRevokedLost(id.uid, sess.deviceId)
        if (revokedDev != null) {
            return CentralVerificationResult.Denied(
                reason = revokedDev.revokedReason ?: "Device revoked as LOST/STOLEN by Super Admin.",
                status = SuperAdminConfig.DEVICE_REVOKED_LOST,
                emergencyLocked = true
            )
        }

        val createdEpoch = sess.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        if (isAdmin) {
            val cfgSnap = runCatching { db.collection(COL_PLATFORM_CONFIG).document(DOC_GLOBAL).get().awaitResult() }.getOrNull()
            val minAdminEpoch = cfgSnap?.toObject(PlatformConfig::class.java)?.minAdminSessionValidAfterEpoch ?: 0L
            if (minAdminEpoch > 0L && createdEpoch < minAdminEpoch) {
                return CentralVerificationResult.Denied("Super Admin session invalidated by remote recovery or device revocation.", "REVOKED")
            }
        }

        val grant = getMyAccessGrant()
            ?: return CentralVerificationResult.Denied("Authorization grant revoked or removed.", "REVOKED")
        if (!grant.isActiveGrant()) {
            return CentralVerificationResult.Denied("Account grant status is ${grant.status}.", grant.status)
        }
        if (createdEpoch < grant.minSessionValidAfterEpoch) {
            return CentralVerificationResult.Denied("Session invalidated by Super Admin policy.", "REVOKED")
        }

        val bizSnap = try {
            db.collection(COL_BUSINESSES).document(sess.businessId).get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "$COL_BUSINESSES/${sess.businessId}", auth)
            return CentralVerificationResult.Denied("Business access denied by server.", "REVOKED")
        }
        val biz = bizSnap.toObject(CentralBusinessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: return CentralVerificationResult.Denied("Approved business record missing.", "REVOKED")
        if (biz.status != SuperAdminConfig.GRANT_ACTIVE) {
            return CentralVerificationResult.Denied("Business suspended or revoked by Super Admin.", biz.status)
        }

        return CentralVerificationResult.Approved(grant, biz, serverSessionId)
    }

    override suspend fun revokeCentralSession(serverSessionId: String, reason: String) {
        if (serverSessionId.isBlank()) return
        val docRef = db.collection(COL_SESSIONS).document(serverSessionId)
        try {
            docRef.update(
                mapOf(
                    "isRevoked" to true,
                    "revokedReason" to reason.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        }
    }

    override fun observeGateRevocation(
        uid: String,
        businessId: String,
        serverSessionId: String,
        deviceId: String
    ): Flow<String?> {
        if (!isConfigured()) return emptyFlow()
        val statusFlow = db.collection(COL_PLATFORM_STATUS).document(DOC_PUBLIC)
            .snapshots()
            .map { snap ->
                val status = snap.toObject(PlatformStatus::class.java)
                val admin = SuperAdminConfig.isSuperAdminEmail(auth.currentUser?.email) &&
                    auth.currentUser?.isEmailVerified == true
                if (status != null && status.emergencyLockAllNonSuperAdmin && !admin) {
                    status.lockMessage ?: "Global Emergency Lock activated by Super Admin."
                } else {
                    null
                }
            }
            .catch { error ->
                val ffe = error.unwrapFirestoreException()
                if (ffe == null && error is CancellationException) throw error
                if (ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && auth.currentUser != null) {
                    emit("Central access denied by security rules.")
                }
            }

        val grantFlow = db.collection(COL_ACCESS_GRANTS).document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) {
                    "Super Admin authorization grant removed."
                } else {
                    val grant = snap.toObject(AccessGrantRecord::class.java)
                    if (grant == null || !grant.isActiveGrant()) {
                        "Access status changed to ${grant?.status ?: "REVOKED"} by Super Admin."
                    } else null
                }
            }
            .catch { error ->
                val ffe = error.unwrapFirestoreException()
                if (ffe == null && error is CancellationException) throw error
                if (ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && auth.currentUser != null) {
                    emit("Central grant access revoked (Emergency Lock or Suspended).")
                }
            }

        val sessionFlow = db.collection(COL_SESSIONS).document(serverSessionId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) {
                    "Session removed on central server."
                } else {
                    val sess = snap.toObject(CentralSessionRecord::class.java)
                    if (sess == null || sess.isRevoked) {
                        "Session revoked (${sess?.revokedReason ?: "Admin action"})."
                    } else null
                }
            }
            .catch { error ->
                val ffe = error.unwrapFirestoreException()
                if (ffe == null && error is CancellationException) throw error
                if (ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && auth.currentUser != null) {
                    emit("Session denied by central server.")
                }
            }

        val deviceFlow = if (deviceId.isNotBlank()) {
            db.collection(COL_ADMIN_DEVICES).document("${uid}_${deviceId}")
                .snapshots()
                .map { snap ->
                    val dev = snap.toObject(AdminDeviceRecord::class.java)
                    if (dev != null && dev.status == SuperAdminConfig.DEVICE_REVOKED_LOST) {
                        "This device was revoked as LOST/STOLEN: ${dev.revokedReason ?: "Blocked by Super Admin"}."
                    } else null
                }
                .catch { error ->
                    val ffe = error.unwrapFirestoreException()
                    if (ffe == null && error is CancellationException) throw error
                }
        } else {
            emptyFlow()
        }

        return merge(statusFlow, grantFlow, sessionFlow, deviceFlow).filterNotNull()
    }

    override suspend fun listAllAccessRequests(): List<AccessRequestRecord> {
        val path = COL_ACCESS_REQUESTS
        return try {
            db.collection(COL_ACCESS_REQUESTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .awaitResult()
                .toObjects(AccessRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            emptyList()
        }
    }

    override suspend fun listAllAccessGrants(): List<AccessGrantRecord> {
        val path = COL_ACCESS_GRANTS
        return try {
            db.collection(COL_ACCESS_GRANTS)
                .get()
                .awaitResult()
                .toObjects(AccessGrantRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            emptyList()
        }
    }

    override suspend fun listAllBusinesses(): List<CentralBusinessRecord> {
        val path = COL_BUSINESSES
        return try {
            db.collection(COL_BUSINESSES)
                .get()
                .awaitResult()
                .toObjects(CentralBusinessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            emptyList()
        }
    }

    override suspend fun listAuditLogs(): List<AdminAuditRecord> {
        val path = COL_AUDIT_LOGS
        return try {
            db.collection(COL_AUDIT_LOGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .awaitResult()
                .toObjects(AdminAuditRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            emptyList()
        }
    }

    override suspend fun approveAccessRequest(
        requestUid: String,
        assignedRole: String,
        existingBusinessId: String?,
        permissionKeys: List<String>,
        allowedPropertyIds: List<String>,
        note: String
    ): Pair<AccessGrantRecord, CentralBusinessRecord> {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) {
            throw AuthorizationException("Only Super Admin can approve access requests.")
        }
        require(assignedRole in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES) {
            "Super Admin role cannot be assigned via approval."
        }

        val reqRef = db.collection(COL_ACCESS_REQUESTS).document(requestUid)
        val reqSnap = try {
            reqRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, reqRef.path, auth)
            throw e
        }
        val req = reqSnap.toObject(AccessRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalArgumentException("Access request not found for uid=$requestUid")

        val businessId = existingBusinessId ?: "biz_${CryptoUtil.newUuid().take(16)}"
        val bizRef = db.collection(COL_BUSINESSES).document(businessId)
        val businessRecord: CentralBusinessRecord

        if (existingBusinessId == null) {
            val bizPayload = mapOf(
                "name" to req.requestedBusinessName.ifBlank { "${req.fullName} Estates" },
                "code" to "BIZ-${businessId.takeLast(6).uppercase()}",
                "branchName" to req.requestedBranchName.ifBlank { "Doha HQ" },
                "ownerUid" to if (assignedRole == "OWNER") req.uid else admin.uid,
                "ownerEmail" to if (assignedRole == "OWNER") req.email else admin.email,
                "status" to SuperAdminConfig.GRANT_ACTIVE,
                "approvedByUid" to admin.uid,
                "currency" to "QAR",
                "timezone" to "Asia/Qatar",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                bizRef.set(bizPayload).awaitResult()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, bizRef.path, auth)
                throw e
            }
            businessRecord = CentralBusinessRecord(
                id = businessId,
                name = bizPayload["name"] as String,
                code = bizPayload["code"] as String,
                branchName = bizPayload["branchName"] as String,
                ownerUid = bizPayload["ownerUid"] as String,
                ownerEmail = bizPayload["ownerEmail"] as String,
                status = SuperAdminConfig.GRANT_ACTIVE,
                approvedByUid = admin.uid
            )
        } else {
            val existingBizSnap = bizRef.get().awaitResult()
            businessRecord = existingBizSnap.toObject(CentralBusinessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalArgumentException("Target business $existingBusinessId does not exist")
        }

        val resolvedPerms = permissionKeys.ifEmpty { defaultPermissionsForRole(assignedRole) }
        val grantRef = db.collection(COL_ACCESS_GRANTS).document(req.uid)
        val grantPayload = mapOf(
            "uid" to req.uid,
            "email" to req.email,
            "fullName" to req.fullName,
            "authorityRole" to assignedRole,
            "status" to SuperAdminConfig.GRANT_ACTIVE,
            "activeBusinessId" to businessId,
            "allowedBusinessIds" to listOf(businessId),
            "allowedPropertyIds" to allowedPropertyIds,
            "restrictToAssignedProperties" to allowedPropertyIds.isNotEmpty(),
            "permissionKeys" to resolvedPerms,
            "approvedByUid" to admin.uid,
            "minSessionValidAfterEpoch" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            grantRef.set(grantPayload).awaitResult()
            reqRef.update(
                mapOf(
                    "status" to "APPROVED",
                    "reviewedByUid" to admin.uid,
                    "reviewNote" to note.ifBlank { "Approved by Super Admin as $assignedRole" }.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
            recordAuditEvent(
                action = "APPROVE_ACCESS",
                targetUid = req.uid,
                targetEmail = req.email,
                businessId = businessId,
                details = "Approved ${req.email} as $assignedRole on business ${businessRecord.name}"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, grantRef.path, auth)
            throw e
        }

        val createdGrant = getGrantForUid(req.uid)
            ?: AccessGrantRecord(
                id = req.uid,
                uid = req.uid,
                email = req.email,
                fullName = req.fullName,
                authorityRole = assignedRole,
                status = SuperAdminConfig.GRANT_ACTIVE,
                activeBusinessId = businessId,
                allowedBusinessIds = listOf(businessId),
                allowedPropertyIds = allowedPropertyIds,
                restrictToAssignedProperties = allowedPropertyIds.isNotEmpty(),
                permissionKeys = resolvedPerms,
                approvedByUid = admin.uid
            )
        return createdGrant to businessRecord
    }

    private suspend fun getGrantForUid(uid: String): AccessGrantRecord? = try {
        db.collection(COL_ACCESS_GRANTS).document(uid).get().awaitResult()
            .toObject(AccessGrantRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (_: Exception) {
        null
    }

    override suspend fun rejectAccessRequest(requestUid: String, note: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val reqRef = db.collection(COL_ACCESS_REQUESTS).document(requestUid)
        try {
            val existing = reqRef.get().awaitResult().toObject(AccessRequestRecord::class.java)
            reqRef.update(
                mapOf(
                    "status" to SuperAdminConfig.GRANT_REJECTED,
                    "reviewedByUid" to admin.uid,
                    "reviewNote" to note.ifBlank { "Rejected by Super Admin" }.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
            recordAuditEvent(
                action = "REJECT_ACCESS",
                targetUid = requestUid,
                targetEmail = existing?.email,
                businessId = null,
                details = "Rejected access request: ${note.ifBlank { "Declined by Super Admin" }}"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path, auth)
            throw e
        }
    }

    override suspend fun setUserGrantStatus(targetUid: String, newStatus: String, note: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        require(targetUid != admin.uid) { "Super Admin cannot revoke themselves." }
        require(newStatus in setOf(SuperAdminConfig.GRANT_ACTIVE, SuperAdminConfig.GRANT_SUSPENDED, SuperAdminConfig.GRANT_REVOKED)) {
            "Invalid grant status: $newStatus"
        }
        val grantRef = db.collection(COL_ACCESS_GRANTS).document(targetUid)
        val grantSnap = try {
            grantRef.get().awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, grantRef.path, auth)
            throw e
        }
        val grant = grantSnap.toObject(AccessGrantRecord::class.java)
            ?: throw IllegalArgumentException("Grant not found for uid=$targetUid")

        val now = System.currentTimeMillis()
        try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "approvedByUid" to admin.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus != SuperAdminConfig.GRANT_ACTIVE) {
                updates["minSessionValidAfterEpoch"] = now
            }
            grantRef.update(updates).awaitResult()

            if (newStatus != SuperAdminConfig.GRANT_ACTIVE) {
                revokeAllUserSessionsCentral(targetUid, "SUPER_ADMIN_$newStatus")
                val reqRef = db.collection(COL_ACCESS_REQUESTS).document(targetUid)
                if (reqRef.get().awaitResult().exists()) {
                    reqRef.update(
                        mapOf(
                            "status" to if (newStatus == SuperAdminConfig.GRANT_REVOKED) SuperAdminConfig.GRANT_REVOKED else SuperAdminConfig.GRANT_PENDING,
                            "reviewNote" to note.ifBlank { "Status updated to $newStatus" }.take(300),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitResult()
                }
            }
            recordAuditEvent(
                action = "GRANT_STATUS_$newStatus",
                targetUid = targetUid,
                targetEmail = grant.email,
                businessId = grant.activeBusinessId,
                details = "Super Admin changed access grant status to $newStatus ($note)"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, grantRef.path, auth)
            throw e
        }
    }

    override suspend fun revokeAllUserSessionsCentral(targetUid: String, reason: String) {
        if (!isVerifiedSuperAdminCurrent() && currentIdentity()?.uid != targetUid) {
            throw AuthorizationException("Only Super Admin or session owner can revoke sessions.")
        }
        val path = COL_SESSIONS
        try {
            val docs = db.collection(COL_SESSIONS)
                .whereEqualTo("uid", targetUid)
                .get()
                .awaitResult()
            docs.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "isRevoked" to true,
                        "revokedReason" to reason.take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            }
            recordAuditEvent(
                action = "REVOKE_SESSIONS",
                targetUid = targetUid,
                targetEmail = null,
                businessId = null,
                details = "Revoked ${docs.size()} central session(s): $reason"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
        }
    }

    override suspend fun setRegistrationsEnabled(enabled: Boolean) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val statusRef = db.collection(COL_PLATFORM_STATUS).document(DOC_PUBLIC)
        try {
            statusRef.update(
                mapOf(
                    "registrationsEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
            recordAuditEvent(
                action = if (enabled) "REGISTRATIONS_ENABLED" else "REGISTRATIONS_DISABLED",
                targetUid = admin.uid,
                targetEmail = admin.email,
                businessId = null,
                details = "Super Admin set public registrationsEnabled=$enabled"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, statusRef.path, auth)
            throw e
        }
    }

    override suspend fun setEmergencyLock(locked: Boolean, lockMessage: String) {
        val admin = requireIdentity()
        if (!isVerifiedSuperAdminCurrent()) throw AuthorizationException("Super Admin only.")
        val statusRef = db.collection(COL_PLATFORM_STATUS).document(DOC_PUBLIC)
        try {
            statusRef.update(
                mapOf(
                    "emergencyLockAllNonSuperAdmin" to locked,
                    "lockMessage" to lockMessage.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
            recordAuditEvent(
                action = if (locked) "EMERGENCY_LOCK_ON" else "EMERGENCY_LOCK_OFF",
                targetUid = admin.uid,
                targetEmail = admin.email,
                businessId = null,
                details = "Super Admin set emergencyLockAllNonSuperAdmin=$locked ($lockMessage)"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, statusRef.path, auth)
            throw e
        }
    }

    override suspend fun recordAuditEvent(
        action: String,
        targetUid: String?,
        targetEmail: String?,
        businessId: String?,
        details: String
    ) {
        val id = currentIdentity() ?: return
        val logId = CryptoUtil.newUuid()
        val ref = db.collection(COL_AUDIT_LOGS).document(logId)
        val payload = buildMap<String, Any> {
            put("actorUid", id.uid)
            put("actorEmail", id.email.ifBlank { "unknown@example.com" })
            put("action", action.take(64))
            targetUid?.take(128)?.let { put("targetUid", it) }
            targetEmail?.take(160)?.let { put("targetEmail", it) }
            businessId?.take(128)?.let { put("businessId", it) }
            put("details", details.take(500).ifBlank { action })
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            ref.set(payload).awaitResult()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path, auth)
        }
    }

    override suspend fun signOutCentral() {
        GoogleSignInHelper.signOut(context)
    }

    private fun defaultPermissionsForRole(roleName: String): List<String> {
        val all = AuthorizationGuard.defaultPermissionCatalog()
        return all.filter { (m, a, _) ->
            when (roleName) {
                "OWNER" -> true
                "PROPERTY_MANAGER" -> m in setOf(
                    AppModule.DASHBOARD.name, AppModule.PROPERTIES.name, AppModule.TENANTS.name,
                    AppModule.AGREEMENTS.name, AppModule.PAYMENTS.name, AppModule.CHEQUES.name,
                    AppModule.DEPOSITS.name, AppModule.MAINTENANCE.name, AppModule.DOCUMENTS.name,
                    AppModule.NOTIFICATIONS.name, AppModule.REPORTS.name
                ) && a != ActionType.RESTORE.name
                "ACCOUNTANT" -> m in setOf(
                    AppModule.DASHBOARD.name, AppModule.PAYMENTS.name, AppModule.CHEQUES.name,
                    AppModule.DEPOSITS.name, AppModule.ACCOUNTING.name, AppModule.REPORTS.name,
                    AppModule.INVENTORY.name, AppModule.DOCUMENTS.name
                ) && a != ActionType.RESTORE.name
                "TECHNICIAN", "EMPLOYEE" -> m in setOf(
                    AppModule.DASHBOARD.name, AppModule.MAINTENANCE.name, AppModule.INVENTORY.name,
                    AppModule.NOTIFICATIONS.name
                ) && a in setOf(ActionType.VIEW.name, ActionType.CREATE.name, ActionType.EDIT.name)
                "CUSTOMER", "CUSTOMER_PORTAL" -> m == AppModule.CUSTOMER_PORTAL.name
                else -> false
            }
        }.map { "${it.first}:${it.second}" }
    }

    companion object {
        private const val TAG = "OneHomeFirebaseGate"
        private const val COL_PLATFORM_CONFIG = "platform_config"
        private const val DOC_GLOBAL = "global"
        private const val COL_PLATFORM_STATUS = "platform_status"
        private const val DOC_PUBLIC = "public"
        private const val COL_ACCESS_REQUESTS = "access_requests"
        private const val COL_ACCESS_GRANTS = "access_grants"
        private const val COL_BUSINESSES = "businesses"
        private const val COL_SESSIONS = "sessions"
        private const val COL_AUDIT_LOGS = "admin_audit_logs"
        private const val COL_ADMIN_DEVICES = "admin_devices"
        private const val COL_CLOUD_SNAPSHOTS = "cloud_snapshots"

        private fun resolveFirestoreDatabaseId(context: Context): String? =
            FirebaseConfigProvider.resolveFirestoreDatabaseId(context)

        private fun inspectFirebaseConfiguration(context: Context): String? =
            FirebaseConfigProvider.inspectDiagnostic(context)
    }
}
