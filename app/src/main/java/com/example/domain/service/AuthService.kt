package com.example.domain.service

import androidx.room.withTransaction
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.security.*

sealed interface AuthResult {
    data class Authenticated(val context: AuthContext, val rawSessionToken: String) : AuthResult
    data class MfaRequired(
        val userId: String,
        val email: String,
        val businessId: String,
        val rememberMe: Boolean,
        val deviceTrusted: Boolean,
        val demoHintInfo: String
    ) : AuthResult
    data class NewDeviceVerificationRequired(
        val userId: String,
        val email: String,
        val businessId: String,
        val rememberMe: Boolean,
        val verificationCode: String
    ) : AuthResult
    data class Failed(val message: String) : AuthResult
}

class AuthService(
    private val db: OneHomeProDatabase,
    private val sessionStore: SessionStore,
    private val accountingService: AccountingService
) {
    private val dao = db.securityAndHrDao()
    private val coreDao = db.coreAndTenancyDao()
    private val opsDao = db.operationsDao()

    private val pendingDeviceOtpByUserId = mutableMapOf<String, Pair<String, Long>>()

    suspend fun hasAnyUsers(): Boolean = dao.countUsers() > 0

    suspend fun setupInitialOwnerAndBusiness(
        fullName: String,
        email: String,
        password: String,
        businessName: String,
        branchName: String,
        enableMfa: Boolean,
        rememberMe: Boolean,
        deviceName: String
    ): AuthResult {
        require(fullName.isNotBlank()) { "Full name is required" }
        require(email.contains("@")) { "Valid email is required" }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(businessName.isNotBlank()) { "Business name is required" }

        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        val deviceId = sessionStore.getOrCreateDeviceId()

        val (authContext, rawToken) = db.withTransaction {
            require(dao.countUsers() == 0) { "Owner already initialized. Please sign in." }
            ensurePermissionCatalogSeeded()

            val businessId = CryptoUtil.newUuid()
            val businessCode = "BIZ-${businessId.take(6).uppercase()}"
            coreDao.insertBusiness(
                BusinessEntity(
                    id = businessId,
                    name = businessName.trim(),
                    code = businessCode,
                    branchName = branchName.trim().ifBlank { "Doha HQ" },
                    currency = "QAR",
                    timezone = "Asia/Qatar",
                    createdAtEpoch = now,
                    updatedAtEpoch = now
                )
            )

            accountingService.seedDefaultChartOfAccounts(businessId, now)
            seedDefaultRolesForBusiness(businessId, now)
            opsDao.insertStockLocation(
                StockLocationEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = businessId,
                    name = "Central Store (${branchName.trim().ifBlank { "Doha HQ" }})",
                    locationType = "WAREHOUSE",
                    createdAtEpoch = now
                )
            )

            val ownerId = CryptoUtil.newUuid()
            val salt = CryptoUtil.generateSaltBase64()
            val hash = CryptoUtil.hashPassword(password, salt)
            dao.insertUser(
                UserEntity(
                    id = ownerId,
                    email = cleanEmail,
                    fullName = fullName.trim(),
                    passwordHash = hash,
                    passwordSalt = salt,
                    userType = "OWNER",
                    status = "ACTIVE",
                    mfaEnabled = enableMfa,
                    createdAtEpoch = now,
                    updatedAtEpoch = now
                )
            )

            val ownerRole = dao.getRoleByName(businessId, "OWNER")!!
            dao.insertUserRole(
                UserRoleEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = businessId,
                    userId = ownerId,
                    roleId = ownerRole.id,
                    assignedByUserId = ownerId,
                    assignedAtEpoch = now
                )
            )
            dao.insertUserBusinessAccess(
                UserBusinessAccessEntity(
                    id = CryptoUtil.newUuid(),
                    userId = ownerId,
                    businessId = businessId,
                    isActive = true,
                    grantedByUserId = ownerId,
                    grantedAtEpoch = now
                )
            )
            dao.insertUserDevice(
                UserDeviceEntity(
                    id = CryptoUtil.newUuid(),
                    userId = ownerId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    isTrusted = true,
                    firstSeenAtEpoch = now,
                    lastSeenAtEpoch = now
                )
            )

            if (enableMfa) {
                val secret = CryptoUtil.generateTotpSecret()
                dao.upsertMfaCredential(
                    MfaCredentialEntity(
                        id = CryptoUtil.newUuid(),
                        userId = ownerId,
                        secretKey = secret,
                        isVerified = true,
                        createdAtEpoch = now
                    )
                )
            }

            createSessionAndContext(
                userId = ownerId,
                activeBusinessId = businessId,
                deviceId = deviceId,
                deviceName = deviceName,
                rememberMe = rememberMe,
                now = now
            )
        }

        if (rememberMe) {
            sessionStore.saveSessionToken(rawToken)
        } else {
            sessionStore.clearSessionToken()
        }
        return AuthResult.Authenticated(authContext, rawToken)
    }

    suspend fun login(
        email: String,
        password: String,
        rememberMe: Boolean,
        deviceName: String
    ): AuthResult {
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        val deviceId = sessionStore.getOrCreateDeviceId()

        val user = dao.getUserByEmail(cleanEmail)
        if (user == null) {
            dao.insertLoginHistory(
                LoginHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    userId = null,
                    email = cleanEmail,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    outcome = "WRONG_PASSWORD",
                    details = "Account not found",
                    occurredAtEpoch = now
                )
            )
            return AuthResult.Failed("Invalid email or password.")
        }

        if (user.status != "ACTIVE") {
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    id = CryptoUtil.newUuid(),
                    userId = user.id,
                    eventType = "LOGIN_BLOCKED_STATUS",
                    severity = "WARN",
                    description = "Blocked login attempt on ${user.status} account",
                    deviceId = deviceId,
                    occurredAtEpoch = now
                )
            )
            return AuthResult.Failed("Account is ${user.status}. Contact the Owner.")
        }

        val lockUntil = user.lockedUntilEpoch
        if (lockUntil != null && lockUntil > now) {
            val mins = ((lockUntil - now) / 60_000L).coerceAtLeast(1)
            return AuthResult.Failed("Account temporarily locked due to failed attempts. Retry in $mins min.")
        }

        val passwordValid = CryptoUtil.verifyPassword(password, user.passwordSalt, user.passwordHash)
        if (!passwordValid) {
            val newFailCount = user.failedLoginAttempts + 1
            val lockTime = if (newFailCount >= 5) now + 5 * 60_000L else null
            dao.updateUser(
                user.copy(
                    failedLoginAttempts = newFailCount,
                    lockedUntilEpoch = lockTime,
                    updatedAtEpoch = now
                )
            )
            dao.insertLoginHistory(
                LoginHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    userId = user.id,
                    email = cleanEmail,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    outcome = if (lockTime != null) "LOCKED" else "WRONG_PASSWORD",
                    details = "Failed attempt #$newFailCount",
                    occurredAtEpoch = now
                )
            )
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    id = CryptoUtil.newUuid(),
                    userId = user.id,
                    eventType = if (lockTime != null) "BRUTE_FORCE_LOCK" else "LOGIN_FAIL",
                    severity = if (lockTime != null) "CRITICAL" else "WARN",
                    description = "Invalid password attempt #$newFailCount from $deviceName",
                    deviceId = deviceId,
                    occurredAtEpoch = now
                )
            )
            return AuthResult.Failed(
                if (lockTime != null) "Too many failed attempts. Account locked for 5 minutes."
                else "Invalid email or password."
            )
        }

        // Password verified -> reset failed attempts
        if (user.failedLoginAttempts > 0 || user.lockedUntilEpoch != null) {
            dao.updateUser(user.copy(failedLoginAttempts = 0, lockedUntilEpoch = null, updatedAtEpoch = now))
        }

        val accesses = dao.getBusinessAccessForUser(user.id)
        val primaryBizId = accesses.firstOrNull()?.businessId
            ?: return AuthResult.Failed("No active business access assigned to this user.")

        val existingDevice = dao.getUserDevice(user.id, deviceId)
        val deviceTrusted = existingDevice?.isTrusted == true
        if (!deviceTrusted) {
            val otp = CryptoUtil.generateSixDigitCode()
            pendingDeviceOtpByUserId[user.id] = otp to (now + 10 * 60_000L)
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = primaryBizId,
                    userId = user.id,
                    eventType = "NEW_DEVICE",
                    severity = "WARN",
                    description = "Unrecognized device login challenge: $deviceName ($deviceId)",
                    deviceId = deviceId,
                    occurredAtEpoch = now
                )
            )
            return AuthResult.NewDeviceVerificationRequired(
                userId = user.id,
                email = user.email,
                businessId = primaryBizId,
                rememberMe = rememberMe,
                verificationCode = otp
            )
        }

        if (user.mfaEnabled) {
            val cred = dao.getMfaCredential(user.id)
            val currentCode = cred?.let { CryptoUtil.currentTotpCode(it.secretKey, now) } ?: ""
            return AuthResult.MfaRequired(
                userId = user.id,
                email = user.email,
                businessId = primaryBizId,
                rememberMe = rememberMe,
                deviceTrusted = true,
                demoHintInfo = currentCode
            )
        }

        val (ctx, token) = db.withTransaction {
            dao.updateUserDevice(existingDevice.copy(lastSeenAtEpoch = now))
            createSessionAndContext(user.id, primaryBizId, deviceId, deviceName, rememberMe, now)
        }
        if (rememberMe) sessionStore.saveSessionToken(token) else sessionStore.clearSessionToken()
        return AuthResult.Authenticated(ctx, token)
    }

    suspend fun verifyNewDeviceChallenge(
        userId: String,
        code: String,
        trustDevice: Boolean,
        rememberMe: Boolean,
        deviceName: String
    ): AuthResult {
        val now = System.currentTimeMillis()
        val deviceId = sessionStore.getOrCreateDeviceId()
        val pending = pendingDeviceOtpByUserId[userId]
            ?: return AuthResult.Failed("Verification challenge expired. Please sign in again.")
        if (now > pending.second || pending.first != code.trim()) {
            return AuthResult.Failed("Invalid or expired device verification code.")
        }
        pendingDeviceOtpByUserId.remove(userId)

        val user = dao.getUserById(userId) ?: return AuthResult.Failed("User not found")
        val existingDevice = dao.getUserDevice(userId, deviceId)
        if (existingDevice == null) {
            dao.insertUserDevice(
                UserDeviceEntity(
                    id = CryptoUtil.newUuid(),
                    userId = userId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    isTrusted = trustDevice,
                    firstSeenAtEpoch = now,
                    lastSeenAtEpoch = now
                )
            )
        } else {
            dao.updateUserDevice(existingDevice.copy(isTrusted = trustDevice, lastSeenAtEpoch = now))
        }

        val primaryBizId = dao.getBusinessAccessForUser(userId).firstOrNull()?.businessId
            ?: return AuthResult.Failed("No business access")

        if (user.mfaEnabled) {
            val cred = dao.getMfaCredential(user.id)
            val currentCode = cred?.let { CryptoUtil.currentTotpCode(it.secretKey, now) } ?: ""
            return AuthResult.MfaRequired(
                userId = user.id,
                email = user.email,
                businessId = primaryBizId,
                rememberMe = rememberMe,
                deviceTrusted = trustDevice,
                demoHintInfo = currentCode
            )
        }

        val (ctx, token) = db.withTransaction {
            createSessionAndContext(userId, primaryBizId, deviceId, deviceName, rememberMe, now)
        }
        if (rememberMe) sessionStore.saveSessionToken(token) else sessionStore.clearSessionToken()
        return AuthResult.Authenticated(ctx, token)
    }

    suspend fun verifyMfaAndCompleteLogin(
        userId: String,
        codeOrRecovery: String,
        rememberMe: Boolean,
        deviceName: String
    ): AuthResult {
        val now = System.currentTimeMillis()
        val deviceId = sessionStore.getOrCreateDeviceId()
        val user = dao.getUserById(userId) ?: return AuthResult.Failed("User not found")
        val cred = dao.getMfaCredential(userId)
        val totpOk = cred != null && CryptoUtil.verifyTotpCode(cred.secretKey, codeOrRecovery, now)
        var recoveryMatched: RecoveryCodeEntity? = null
        if (!totpOk) {
            val hash = CryptoUtil.sha256Hex(codeOrRecovery.trim().uppercase())
            recoveryMatched = dao.getUnusedRecoveryCodes(userId).firstOrNull { it.codeHash == hash }
        }
        if (!totpOk && recoveryMatched == null) {
            dao.insertLoginHistory(
                LoginHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    userId = userId,
                    email = user.email,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    outcome = "MFA_FAILED",
                    details = "Invalid MFA or recovery code",
                    occurredAtEpoch = now
                )
            )
            return AuthResult.Failed("Invalid MFA verification code.")
        }

        if (recoveryMatched != null) {
            dao.updateRecoveryCode(recoveryMatched.copy(isUsed = true, usedAtEpoch = now))
        }
        val primaryBizId = dao.getBusinessAccessForUser(userId).firstOrNull()?.businessId
            ?: return AuthResult.Failed("No business access")

        val (ctx, token) = db.withTransaction {
            createSessionAndContext(userId, primaryBizId, deviceId, deviceName, rememberMe, now)
        }
        if (rememberMe) sessionStore.saveSessionToken(token) else sessionStore.clearSessionToken()
        return AuthResult.Authenticated(ctx, token)
    }

    suspend fun validateStoredSessionOrNull(): AuthContext? {
        val rawToken = sessionStore.getSessionToken() ?: return null
        return validateSessionToken(rawToken)
    }

    suspend fun validateSessionToken(rawToken: String): AuthContext? {
        val now = System.currentTimeMillis()
        val deviceId = sessionStore.getOrCreateDeviceId()
        val tokenHash = CryptoUtil.sha256Hex(rawToken)
        val session = dao.getSessionByTokenHash(tokenHash) ?: return null

        if (session.isRevoked || session.expiresAtEpoch <= now || session.deviceId != deviceId) {
            sessionStore.clearSessionToken()
            dao.insertLoginHistory(
                LoginHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    userId = session.userId,
                    email = "",
                    deviceId = deviceId,
                    deviceName = session.deviceName,
                    outcome = if (session.isRevoked) "REVOKED_TOKEN" else "EXPIRED_TOKEN",
                    details = "Auto-login rejected",
                    occurredAtEpoch = now
                )
            )
            return null
        }

        val user = dao.getUserById(session.userId)
        if (user == null || user.status != "ACTIVE") {
            sessionStore.clearSessionToken()
            return null
        }

        val device = dao.getUserDevice(user.id, deviceId)
        if (device == null || !device.isTrusted) {
            sessionStore.clearSessionToken()
            return null
        }

        dao.updateSession(session.copy(lastValidatedAtEpoch = now))
        return buildAuthContext(user, session.activeBusinessId, session.id, deviceId)
    }

    suspend fun requestPasswordReset(email: String): String {
        val clean = email.trim().lowercase()
        val user = dao.getUserByEmail(clean) ?: throw IllegalArgumentException("No account found for $clean")
        val now = System.currentTimeMillis()
        val otp = CryptoUtil.generateSixDigitCode()
        dao.insertPasswordResetRequest(
            PasswordResetRequestEntity(
                id = CryptoUtil.newUuid(),
                userId = user.id,
                email = clean,
                otpHash = CryptoUtil.sha256Hex(otp),
                expiresAtEpoch = now + 15 * 60_000L,
                createdAtEpoch = now
            )
        )
        dao.insertSecurityEvent(
            SecurityEventEntity(
                id = CryptoUtil.newUuid(),
                userId = user.id,
                eventType = "PASSWORD_RESET_REQUEST",
                severity = "INFO",
                description = "Password reset OTP generated",
                occurredAtEpoch = now
            )
        )
        return otp
    }

    suspend fun completePasswordReset(email: String, otp: String, newPassword: String) {
        require(newPassword.length >= 8) { "New password must be at least 8 characters" }
        val clean = email.trim().lowercase()
        val now = System.currentTimeMillis()
        val req = dao.getLatestResetRequest(clean) ?: throw IllegalArgumentException("No active reset request")
        if (req.isConsumed || req.expiresAtEpoch < now || req.otpHash != CryptoUtil.sha256Hex(otp.trim())) {
            throw SecurityException("Invalid or expired reset code")
        }
        val user = dao.getUserById(req.userId) ?: throw IllegalArgumentException("User not found")
        val newSalt = CryptoUtil.generateSaltBase64()
        val newHash = CryptoUtil.hashPassword(newPassword, newSalt)
        db.withTransaction {
            dao.updateUser(
                user.copy(
                    passwordSalt = newSalt,
                    passwordHash = newHash,
                    failedLoginAttempts = 0,
                    lockedUntilEpoch = null,
                    updatedAtEpoch = now
                )
            )
            dao.updatePasswordResetRequest(req.copy(isConsumed = true))
            // Critical security requirement: password change revokes all sessions!
            dao.revokeAllSessionsForUser(user.id, "PASSWORD_CHANGED")
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    id = CryptoUtil.newUuid(),
                    userId = user.id,
                    eventType = "PASSWORD_RESET",
                    severity = "WARN",
                    description = "Password changed and all sessions revoked",
                    occurredAtEpoch = now
                )
            )
        }
        sessionStore.clearSessionToken()
    }

    suspend fun enableMfaAndGenerateRecoveryCodes(ctx: AuthContext): Pair<String, List<String>> {
        val now = System.currentTimeMillis()
        val secret = CryptoUtil.generateTotpSecret()
        val codes = CryptoUtil.generateRecoveryCodes(6)
        db.withTransaction {
            val user = dao.getUserById(ctx.userId) ?: error("User missing")
            dao.updateUser(user.copy(mfaEnabled = true, updatedAtEpoch = now))
            dao.upsertMfaCredential(
                MfaCredentialEntity(
                    id = CryptoUtil.newUuid(),
                    userId = ctx.userId,
                    secretKey = secret,
                    isVerified = true,
                    createdAtEpoch = now
                )
            )
            dao.insertRecoveryCodes(
                codes.map { code ->
                    RecoveryCodeEntity(
                        id = CryptoUtil.newUuid(),
                        userId = ctx.userId,
                        codeHash = CryptoUtil.sha256Hex(code)
                    )
                }
            )
        }
        return CryptoUtil.currentTotpCode(secret, now) to codes
    }

    suspend fun disableMfa(ctx: AuthContext) {
        val user = dao.getUserById(ctx.userId) ?: return
        dao.updateUser(user.copy(mfaEnabled = false, updatedAtEpoch = System.currentTimeMillis()))
    }

    suspend fun logoutCurrentSession(ctx: AuthContext) {
        dao.revokeSessionById(ctx.sessionId, "USER_LOGOUT")
        sessionStore.clearSessionToken()
    }

    suspend fun logoutAllDevices(ctx: AuthContext) {
        dao.revokeAllSessionsForUser(ctx.userId, "USER_LOGOUT_ALL_DEVICES")
        dao.insertSecurityEvent(
            SecurityEventEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                userId = ctx.userId,
                eventType = "SESSION_REVOKED",
                severity = "INFO",
                description = "All device sessions revoked by user",
                deviceId = ctx.deviceId,
                occurredAtEpoch = System.currentTimeMillis()
            )
        )
        sessionStore.clearSessionToken()
    }

    suspend fun revokeSpecificSession(ctx: AuthContext, sessionId: String) {
        dao.revokeSessionById(sessionId, "REVOKED_FROM_SECURITY_PANEL")
    }

    suspend fun switchBusiness(ctx: AuthContext, targetBusinessId: String): AuthContext {
        if (!ctx.allowedBusinessIds.contains(targetBusinessId)) {
            throw AuthorizationException("Cross-business switch blocked")
        }
        val user = dao.getUserById(ctx.userId) ?: error("User missing")
        val raw = sessionStore.getSessionToken()
        if (raw != null) {
            val session = dao.getSessionByTokenHash(CryptoUtil.sha256Hex(raw))
            if (session != null) {
                dao.updateSession(session.copy(activeBusinessId = targetBusinessId))
            }
        }
        return buildAuthContext(user, targetBusinessId, ctx.sessionId, ctx.deviceId)
    }

    suspend fun createStaffUser(
        ctx: AuthContext,
        employeeId: String,
        fullName: String,
        email: String,
        password: String,
        roleName: String,
        assignedPropertyIds: List<String>
    ): UserEntity {
        AuthorizationGuard.requireOwner(ctx)
        require(password.length >= 8) { "Password must be at least 8 characters" }
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        return db.withTransaction {
            require(dao.getUserByEmail(cleanEmail) == null) { "Email already registered" }
            val userId = CryptoUtil.newUuid()
            val salt = CryptoUtil.generateSaltBase64()
            val user = UserEntity(
                id = userId,
                email = cleanEmail,
                fullName = fullName.trim(),
                passwordHash = CryptoUtil.hashPassword(password, salt),
                passwordSalt = salt,
                userType = "EMPLOYEE",
                status = "ACTIVE",
                employeeId = employeeId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertUser(user)
            val role = dao.getRoleByName(ctx.activeBusinessId, roleName)
                ?: throw IllegalArgumentException("Role $roleName not found")
            dao.insertUserRole(
                UserRoleEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    userId = userId,
                    roleId = role.id,
                    assignedByUserId = ctx.userId,
                    assignedAtEpoch = now
                )
            )
            dao.insertUserBusinessAccess(
                UserBusinessAccessEntity(
                    id = CryptoUtil.newUuid(),
                    userId = userId,
                    businessId = ctx.activeBusinessId,
                    grantedByUserId = ctx.userId,
                    grantedAtEpoch = now
                )
            )
            assignedPropertyIds.forEach { propId ->
                dao.insertUserPropertyAccess(
                    UserPropertyAccessEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        userId = userId,
                        propertyId = propId,
                        grantedByUserId = ctx.userId,
                        grantedAtEpoch = now
                    )
                )
            }
            user
        }
    }

    suspend fun createCustomerPortalUser(
        ctx: AuthContext,
        customerId: String,
        email: String,
        password: String
    ): UserEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.TENANTS, ActionType.EDIT, ctx.activeBusinessId)
        require(password.length >= 8) { "Password must be at least 8 characters" }
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val customer = coreDao.getCustomerById(customerId)
                ?: throw IllegalArgumentException("Customer not found")
            require(customer.businessId == ctx.activeBusinessId) { "Cross-business access blocked" }
            require(dao.getUserByEmail(cleanEmail) == null) { "Email already registered" }
            val userId = CryptoUtil.newUuid()
            val salt = CryptoUtil.generateSaltBase64()
            val user = UserEntity(
                id = userId,
                email = cleanEmail,
                fullName = customer.fullName,
                passwordHash = CryptoUtil.hashPassword(password, salt),
                passwordSalt = salt,
                userType = "CUSTOMER",
                status = "ACTIVE",
                customerId = customer.id,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertUser(user)
            coreDao.updateCustomer(customer.copy(portalUserId = userId, updatedAtEpoch = now))
            val role = dao.getRoleByName(ctx.activeBusinessId, "CUSTOMER_PORTAL")!!
            dao.insertUserRole(
                UserRoleEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    userId = userId,
                    roleId = role.id,
                    assignedByUserId = ctx.userId,
                    assignedAtEpoch = now
                )
            )
            dao.insertUserBusinessAccess(
                UserBusinessAccessEntity(
                    id = CryptoUtil.newUuid(),
                    userId = userId,
                    businessId = ctx.activeBusinessId,
                    grantedByUserId = ctx.userId,
                    grantedAtEpoch = now
                )
            )
            user
        }
    }

    suspend fun setUserAccountStatus(ctx: AuthContext, targetUserId: String, newStatus: String) {
        AuthorizationGuard.requireOwner(ctx)
        require(targetUserId != ctx.userId) { "Owner cannot suspend themselves" }
        val target = dao.getUserById(targetUserId) ?: throw IllegalArgumentException("User not found")
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.updateUser(target.copy(status = newStatus, updatedAtEpoch = now))
            if (newStatus != "ACTIVE") {
                dao.revokeAllSessionsForUser(targetUserId, "ACCOUNT_$newStatus")
            }
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    userId = targetUserId,
                    eventType = "ACCOUNT_STATUS_$newStatus",
                    severity = "WARN",
                    description = "Owner changed user ${target.email} status to $newStatus",
                    occurredAtEpoch = now
                )
            )
        }
    }

    suspend fun toggleRolePermission(ctx: AuthContext, roleId: String, permissionId: String, granted: Boolean) {
        AuthorizationGuard.requireOwner(ctx)
        val current = dao.getAllPermissions().associateBy { it.id }
        require(current.containsKey(permissionId)) { "Unknown permission" }
        // Rebuild role permissions cleanly
        val existingForBiz = dao.getRoleByName(ctx.activeBusinessId, "OWNER")
        if (existingForBiz?.id == roleId && !granted) {
            throw IllegalStateException("Cannot remove permissions from OWNER role")
        }
        // Simple insert if granted, or clear & reinsert handled in UI/service:
        if (granted) {
            dao.insertRolePermission(
                RolePermissionEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    roleId = roleId,
                    permissionId = permissionId
                )
            )
        }
    }

    suspend fun submitAccountClosureRequest(ctx: AuthContext, reason: String) {
        val req = AccountClosureRequestEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            userId = ctx.userId,
            reason = reason.trim().ifBlank { "User requested portal account closure" },
            status = "PENDING",
            requestedAtEpoch = System.currentTimeMillis()
        )
        dao.insertAccountClosureRequest(req)
    }

    suspend fun seedDefaultRolesForBusiness(businessId: String, now: Long) {
        ensurePermissionCatalogSeeded()
        val allPerms = dao.getAllPermissions()

        fun createRole(name: String, desc: String, filter: (PermissionEntity) -> Boolean) {
            // Note: called inside coroutine
        }
        val roleDefs = listOf(
            "OWNER" to "Full business & security administration",
            "PROPERTY_MANAGER" to "Properties, tenants, agreements, rent collection & maintenance",
            "ACCOUNTANT" to "Double-entry GL, payments, cheques, deposits, bank & financial reports",
            "TECHNICIAN" to "Assigned maintenance jobs, work reports, before/after photos & materials",
            "CUSTOMER_PORTAL" to "Self-service isolated tenant portal"
        )
        roleDefs.forEach { (rName, desc) ->
            if (dao.getRoleByName(businessId, rName) == null) {
                val roleId = CryptoUtil.newUuid()
                dao.insertRole(RoleEntity(roleId, businessId, rName, desc, isSystemRole = true, createdAtEpoch = now))
                val matching = allPerms.filter { p ->
                    when (rName) {
                        "OWNER" -> true
                        "PROPERTY_MANAGER" -> p.module in setOf(
                            AppModule.DASHBOARD.name, AppModule.PROPERTIES.name, AppModule.TENANTS.name,
                            AppModule.AGREEMENTS.name, AppModule.PAYMENTS.name, AppModule.CHEQUES.name,
                            AppModule.DEPOSITS.name, AppModule.MAINTENANCE.name, AppModule.DOCUMENTS.name,
                            AppModule.NOTIFICATIONS.name, AppModule.REPORTS.name
                        ) && p.action != ActionType.RESTORE.name
                        "ACCOUNTANT" -> p.module in setOf(
                            AppModule.DASHBOARD.name, AppModule.PAYMENTS.name, AppModule.CHEQUES.name,
                            AppModule.DEPOSITS.name, AppModule.ACCOUNTING.name, AppModule.REPORTS.name,
                            AppModule.INVENTORY.name, AppModule.DOCUMENTS.name
                        ) && p.action != ActionType.RESTORE.name
                        "TECHNICIAN" -> p.module in setOf(
                            AppModule.DASHBOARD.name, AppModule.MAINTENANCE.name, AppModule.INVENTORY.name,
                            AppModule.NOTIFICATIONS.name
                        ) && p.action in setOf(ActionType.VIEW.name, ActionType.CREATE.name, ActionType.EDIT.name)
                        "CUSTOMER_PORTAL" -> p.module == AppModule.CUSTOMER_PORTAL.name
                        else -> false
                    }
                }
                matching.forEach { perm ->
                    dao.insertRolePermission(
                        RolePermissionEntity(
                            id = CryptoUtil.newUuid(),
                            businessId = businessId,
                            roleId = roleId,
                            permissionId = perm.id
                        )
                    )
                }
            }
        }
    }

    private suspend fun ensurePermissionCatalogSeeded() {
        val catalog = AuthorizationGuard.defaultPermissionCatalog().map { (m, a, desc) ->
            PermissionEntity(id = "$m:$a", module = m, action = a, description = desc)
        }
        dao.insertPermissions(catalog)
    }

    private suspend fun createSessionAndContext(
        userId: String,
        activeBusinessId: String,
        deviceId: String,
        deviceName: String,
        rememberMe: Boolean,
        now: Long
    ): Pair<AuthContext, String> {
        val user = dao.getUserById(userId)!!
        val rawToken = CryptoUtil.generateSecureToken()
        val sessionId = CryptoUtil.newUuid()
        val ttlMillis = if (rememberMe) 30L * 24 * 60 * 60 * 1000L else 12L * 60 * 60 * 1000L
        dao.insertSession(
            LoginSessionEntity(
                id = sessionId,
                userId = userId,
                activeBusinessId = activeBusinessId,
                deviceId = deviceId,
                deviceName = deviceName,
                tokenHash = CryptoUtil.sha256Hex(rawToken),
                rememberMe = rememberMe,
                isRevoked = false,
                createdAtEpoch = now,
                expiresAtEpoch = now + ttlMillis,
                lastValidatedAtEpoch = now
            )
        )
        dao.insertLoginHistory(
            LoginHistoryEntity(
                id = CryptoUtil.newUuid(),
                userId = userId,
                email = user.email,
                deviceId = deviceId,
                deviceName = deviceName,
                outcome = "SUCCESS",
                details = if (rememberMe) "Remember Me session issued" else "Standard session",
                occurredAtEpoch = now
            )
        )
        return buildAuthContext(user, activeBusinessId, sessionId, deviceId) to rawToken
    }

    private suspend fun buildAuthContext(
        user: UserEntity,
        activeBusinessId: String,
        sessionId: String,
        deviceId: String
    ): AuthContext {
        val bizAccess = dao.getBusinessAccessForUser(user.id).map { it.businessId }.toSet()
        val resolvedBiz = if (bizAccess.contains(activeBusinessId)) activeBusinessId else bizAccess.first()
        val propAccess = dao.getPropertyAccessForUser(user.id, resolvedBiz).map { it.propertyId }.toSet()
        val perms = dao.getPermissionsForUserInBusiness(user.id, resolvedBiz)
            .map { "${it.module}:${it.action}" }
            .toSet()
        return AuthContext(
            userId = user.id,
            email = user.email,
            fullName = user.fullName,
            userType = user.userType,
            activeBusinessId = resolvedBiz,
            allowedBusinessIds = bizAccess,
            allowedPropertyIds = propAccess,
            restrictToAssignedProperties = propAccess.isNotEmpty(),
            customerId = user.customerId,
            employeeId = user.employeeId,
            permissionKeys = perms,
            sessionId = sessionId,
            deviceId = deviceId
        )
    }
}
