package com.example.domain.security

import kotlinx.coroutines.flow.Flow

interface CentralAuthorityGateway {
    fun isConfigured(): Boolean = true
    fun configurationDiagnostic(): String? = null

    val authIdentityFlow: Flow<CentralIdentity?>
    fun currentIdentity(): CentralIdentity?

    suspend fun getPlatformStatus(): PlatformStatus
    suspend fun isPlatformBootstrapped(): Boolean
    suspend fun isVerifiedSuperAdminCurrent(): Boolean

    suspend fun bootstrapSuperAdmin(masterPassword: String): Pair<String, List<String>>
    suspend fun bootstrapSuperAdminWithRecovery(
        masterPassword: String,
        securityQuestion: String,
        securityAnswer: String,
        deviceId: String,
        deviceName: String
    ): SuperAdminProvisionKit

    suspend fun verifySuperAdminChallenge(masterPassword: String, totpOrRecoveryCode: String): Boolean
    suspend fun isInitialMfaEnrollmentPending(): Boolean
    suspend fun reissuePendingInitialMfaSetup(
        masterPassword: String,
        deviceId: String = "bootstrap_primary",
        deviceName: String = "Primary Device"
    ): SuperAdminProvisionKit
    suspend fun getSuperAdminSecurityQuestion(): String?
    suspend fun recoverSuperAdminAccount(request: SuperAdminRecoveryRequest): SuperAdminProvisionKit
    suspend fun updateSuperAdminRecoverySettings(
        currentMasterPassword: String,
        securityQuestion: String,
        securityAnswer: String,
        rotateBackupKit: Boolean
    ): SuperAdminProvisionKit?

    suspend fun isDeviceRevokedLost(uid: String, deviceId: String): AdminDeviceRecord?
    suspend fun listAdminDevices(): List<AdminDeviceRecord>
    suspend fun markAdminDeviceLost(targetDeviceId: String, reason: String, callerDeviceId: String)
    suspend fun reauthorizeAdminDevice(targetDeviceId: String, masterPassword: String, callerDeviceId: String)
    suspend fun revokeAllOtherAdminDevicesAndSessions(callerDeviceId: String, reason: String)

    suspend fun uploadCloudBusinessSnapshot(snapshot: CloudBusinessSnapshot)
    suspend fun downloadCloudBusinessSnapshot(businessId: String): CloudBusinessSnapshot?

    suspend fun getMyAccessRequest(): AccessRequestRecord?
    suspend fun getMyAccessGrant(): AccessGrantRecord?
    suspend fun submitAccessRequest(submission: AccessRequestSubmission): AccessRequestRecord

    suspend fun verifyAndOpenApprovedSession(
        uid: String,
        email: String,
        businessId: String?,
        deviceId: String,
        deviceName: String,
        rememberMe: Boolean
    ): CentralVerificationResult

    suspend fun validateExistingCentralSession(
        serverSessionId: String,
        uid: String,
        email: String
    ): CentralVerificationResult

    suspend fun revokeCentralSession(serverSessionId: String, reason: String)
    fun observeGateRevocation(uid: String, businessId: String, serverSessionId: String, deviceId: String = ""): Flow<String?>

    // Super Admin exclusive operations (enforced server-side by firestore.rules)
    suspend fun listAllAccessRequests(): List<AccessRequestRecord>
    suspend fun listAllAccessGrants(): List<AccessGrantRecord>
    suspend fun listAllBusinesses(): List<CentralBusinessRecord>
    suspend fun listAuditLogs(): List<AdminAuditRecord>

    suspend fun approveAccessRequest(
        requestUid: String,
        assignedRole: String,
        existingBusinessId: String?,
        permissionKeys: List<String>,
        allowedPropertyIds: List<String>,
        note: String
    ): Pair<AccessGrantRecord, CentralBusinessRecord>

    suspend fun rejectAccessRequest(requestUid: String, note: String)
    suspend fun setUserGrantStatus(targetUid: String, newStatus: String, note: String)
    suspend fun revokeAllUserSessionsCentral(targetUid: String, reason: String)
    suspend fun setRegistrationsEnabled(enabled: Boolean)
    suspend fun setEmergencyLock(locked: Boolean, lockMessage: String)
    suspend fun recordAuditEvent(
        action: String,
        targetUid: String?,
        targetEmail: String?,
        businessId: String?,
        details: String
    )

    suspend fun signOutCentral()
}
