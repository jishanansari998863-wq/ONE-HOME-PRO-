package com.example.domain.security

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class CentralIdentity(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean
)

@IgnoreExtraProperties
data class PlatformConfig(
    @DocumentId val id: String = "",
    val superAdminUid: String = "",
    val superAdminEmail: String = "",
    val mfaEnabled: Boolean = true,
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val totpSecret: String = "",
    val recoveryCodeHashes: List<String> = emptyList(),
    val backupKeySalt: String = "",
    val backupKeyHash: String = "",
    val securityQuestion: String = "",
    val securityAnswerSalt: String = "",
    val securityAnswerHash: String = "",
    val minAdminSessionValidAfterEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SuperAdminProvisionKit(
    val totpSecret: String,
    val recoveryCodes: List<String>,
    val masterRecoveryKey: String,
    val securityQuestion: String = ""
)

@IgnoreExtraProperties
data class AdminDeviceRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val status: String = SuperAdminConfig.DEVICE_ACTIVE,
    val revokedReason: String? = null,
    val revokedByDeviceId: String? = null,
    val lastSeenEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBusinessSnapshot(
    @DocumentId val id: String = "",
    val businessId: String = "",
    val payloadBase64: String = "",
    val sha256Checksum: String = "",
    val recordCount: Int = 0,
    val syncedByUid: String = "",
    val syncedByDeviceName: String = "",
    val appLanguageCode: String = "en",
    val darkTheme: Boolean = false,
    val syncedAtEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SuperAdminRecoveryRequest(
    val recoveryProof: String,
    val newMasterPassword: String,
    val securityQuestion: String? = null,
    val securityAnswer: String? = null,
    val revokeOtherDevices: Boolean = true,
    val currentDeviceId: String,
    val currentDeviceName: String
)

@IgnoreExtraProperties
data class PlatformStatus(
    @DocumentId val id: String = "",
    val isBootstrapped: Boolean = false,
    val registrationsEnabled: Boolean = true,
    val emergencyLockAllNonSuperAdmin: Boolean = false,
    val lockMessage: String? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccessRequestRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val requestedRole: String = "OWNER",
    val requestedBusinessName: String = "",
    val requestedBranchName: String = "Doha HQ",
    val qidNumber: String? = null,
    val passportNumber: String? = null,
    val identityDocSummary: String? = null,
    val deviceId: String = "",
    val deviceName: String = "",
    val status: String = SuperAdminConfig.GRANT_PENDING,
    val reviewedByUid: String? = null,
    val reviewNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("uid", uid)
        put("email", email)
        put("fullName", fullName)
        put("phone", phone)
        put("requestedRole", requestedRole)
        put("requestedBusinessName", requestedBusinessName)
        put("requestedBranchName", requestedBranchName)
        qidNumber?.let { put("qidNumber", it) }
        passportNumber?.let { put("passportNumber", it) }
        identityDocSummary?.let { put("identityDocSummary", it) }
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("status", SuperAdminConfig.GRANT_PENDING)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class AccessGrantRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val authorityRole: String = "EMPLOYEE",
    val status: String = SuperAdminConfig.GRANT_REVOKED,
    val activeBusinessId: String = "",
    val allowedBusinessIds: List<String> = emptyList(),
    val allowedPropertyIds: List<String> = emptyList(),
    val restrictToAssignedProperties: Boolean = false,
    val permissionKeys: List<String> = emptyList(),
    val approvedByUid: String = "",
    val minSessionValidAfterEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActiveGrant(): Boolean = status == SuperAdminConfig.GRANT_ACTIVE
}

@IgnoreExtraProperties
data class CentralBusinessRecord(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val branchName: String = "Doha HQ",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val status: String = SuperAdminConfig.GRANT_ACTIVE,
    val approvedByUid: String = "",
    val currency: String = "QAR",
    val timezone: String = "Asia/Qatar",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CentralSessionRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val businessId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val isRevoked: Boolean = false,
    val revokedReason: String? = null,
    val rememberMe: Boolean = false,
    val expiresAtEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminAuditRecord(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetUid: String? = null,
    val targetEmail: String? = null,
    val businessId: String? = null,
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class AccessRequestSubmission(
    val fullName: String,
    val phone: String,
    val requestedRole: String,
    val requestedBusinessName: String,
    val requestedBranchName: String,
    val qidNumber: String?,
    val passportNumber: String?,
    val identityDocSummary: String?,
    val deviceId: String,
    val deviceName: String
)

sealed interface CentralVerificationResult {
    data class Approved(
        val grant: AccessGrantRecord,
        val business: CentralBusinessRecord,
        val serverSessionId: String
    ) : CentralVerificationResult

    data class Denied(
        val reason: String,
        val status: String,
        val emergencyLocked: Boolean = false
    ) : CentralVerificationResult
}
