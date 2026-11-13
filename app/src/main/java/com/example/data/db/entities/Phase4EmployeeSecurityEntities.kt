package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index(value = ["businessId", "employeeCode"], unique = true),
        Index("departmentId"),
        Index("supervisorEmployeeId")
    ]
)
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeCode: String,
    val fullName: String,
    val jobTitle: String,
    val departmentId: String? = null,
    val supervisorEmployeeId: String? = null,
    val workLocation: String = "",
    val phone: String = "",
    val email: String = "",
    val qidNumber: String = "",
    val shiftSchedule: String = "08:00 - 17:00 AST",
    val onboardingStatus: String = "COMPLETED",
    val status: String = "ACTIVE", // ACTIVE, ON_LEAVE, DEACTIVATED
    val hiredAtEpoch: Long,
    val deactivatedAtEpoch: Long? = null,
    val createdByUserId: String,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "departments",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("leaderEmployeeId")]
)
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val leaderEmployeeId: String? = null,
    val description: String = "",
    val createdAtEpoch: Long
)

@Entity(
    tableName = "teams",
    foreignKeys = [
        ForeignKey(
            entity = DepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("departmentId"), Index("leaderEmployeeId")]
)
data class TeamEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val departmentId: String,
    val name: String,
    val leaderEmployeeId: String? = null,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "team_members",
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("teamId"), Index("employeeId")]
)
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val teamId: String,
    val employeeId: String,
    val assignedRole: String = "MEMBER",
    val joinedAtEpoch: Long
)

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true), Index("employeeId"), Index("customerId")]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val fullName: String,
    val passwordHash: String,
    val passwordSalt: String,
    val userType: String, // OWNER, EMPLOYEE, CUSTOMER
    val status: String = "ACTIVE", // ACTIVE, SUSPENDED, DEACTIVATED, CLOSED
    val mfaEnabled: Boolean = false,
    val failedLoginAttempts: Int = 0,
    val lockedUntilEpoch: Long? = null,
    val employeeId: String? = null,
    val customerId: String? = null,
    val preferredLanguage: String = "EN",
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "roles",
    indices = [Index("businessId"), Index(value = ["businessId", "name"], unique = true)]
)
data class RoleEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String, // OWNER, PROPERTY_MANAGER, ACCOUNTANT, TECHNICIAN, CUSTOMER_PORTAL, CUSTOM
    val description: String,
    val isSystemRole: Boolean = true,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "permissions",
    indices = [Index(value = ["module", "action"], unique = true)]
)
data class PermissionEntity(
    @PrimaryKey val id: String,
    val module: String,
    val action: String, // VIEW, CREATE, EDIT, ARCHIVE, APPROVE, POST, REVERSE, EXPORT, RESTORE
    val description: String
)

@Entity(
    tableName = "role_permissions",
    foreignKeys = [
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PermissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["permissionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("roleId"), Index("permissionId")]
)
data class RolePermissionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val roleId: String,
    val permissionId: String
)

@Entity(
    tableName = "user_roles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("userId"), Index("roleId")]
)
data class UserRoleEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userId: String,
    val roleId: String,
    val assignedByUserId: String,
    val assignedAtEpoch: Long
)

@Entity(
    tableName = "user_business_access",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("userId"), Index("businessId")]
)
data class UserBusinessAccessEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val businessId: String,
    val isActive: Boolean = true,
    val grantedByUserId: String,
    val grantedAtEpoch: Long
)

@Entity(
    tableName = "user_property_access",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("userId"), Index("propertyId")]
)
data class UserPropertyAccessEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userId: String,
    val propertyId: String,
    val isActive: Boolean = true,
    val grantedByUserId: String,
    val grantedAtEpoch: Long
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("employeeId")]
)
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val dateKey: String, // YYYY-MM-DD
    val status: String, // PRESENT, ABSENT, LEAVE, HOLIDAY, OVERTIME
    val checkInTime: String = "",
    val checkOutTime: String = "",
    val overtimeMinutes: Int = 0,
    val notes: String = "",
    val recordedByUserId: String,
    val recordedAtEpoch: Long
)

@Entity(
    tableName = "employee_work_history",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("employeeId")]
)
data class EmployeeWorkHistoryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val employeeId: String,
    val eventType: String, // ONBOARDED, ROLE_CHANGE, JOB_COMPLETED, LEAVE, DEACTIVATED
    val summary: String,
    val actorUserId: String,
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "login_sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index(value = ["tokenHash"], unique = true)]
)
data class LoginSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val activeBusinessId: String,
    val deviceId: String,
    val deviceName: String,
    val tokenHash: String,
    val rememberMe: Boolean,
    val isRevoked: Boolean = false,
    val revokedReason: String = "",
    val createdAtEpoch: Long,
    val expiresAtEpoch: Long,
    val lastValidatedAtEpoch: Long
)

@Entity(
    tableName = "login_history",
    indices = [Index("userId"), Index("email")]
)
data class LoginHistoryEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val email: String,
    val deviceId: String,
    val deviceName: String,
    val outcome: String, // SUCCESS, WRONG_PASSWORD, LOCKED, MFA_FAILED, DEVICE_CHALLENGE, REVOKED_TOKEN, EXPIRED_TOKEN
    val details: String = "",
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "account_closure_requests",
    indices = [Index("userId"), Index("businessId")]
)
data class AccountClosureRequestEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userId: String,
    val reason: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val requestedAtEpoch: Long,
    val reviewedByUserId: String? = null,
    val reviewedAtEpoch: Long? = null
)

@Entity(
    tableName = "mfa_credentials",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"], unique = true)]
)
data class MfaCredentialEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val secretKey: String,
    val isVerified: Boolean = false,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "recovery_codes",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class RecoveryCodeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val codeHash: String,
    val isUsed: Boolean = false,
    val usedAtEpoch: Long? = null
)

@Entity(
    tableName = "security_events",
    indices = [Index("userId"), Index("businessId")]
)
data class SecurityEventEntity(
    @PrimaryKey val id: String,
    val businessId: String? = null,
    val userId: String? = null,
    val eventType: String, // LOGIN_FAIL, BRUTE_FORCE_LOCK, NEW_DEVICE, SESSION_REVOKED, PERMISSION_DENIED, PASSWORD_RESET, CROSS_TENANT_BLOCKED
    val severity: String, // INFO, WARN, CRITICAL
    val description: String,
    val deviceId: String = "",
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "user_devices",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index(value = ["userId", "deviceId"], unique = true)]
)
data class UserDeviceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val isTrusted: Boolean = false,
    val firstSeenAtEpoch: Long,
    val lastSeenAtEpoch: Long
)

@Entity(
    tableName = "password_reset_requests",
    indices = [Index("userId"), Index("email")]
)
data class PasswordResetRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val email: String,
    val otpHash: String,
    val expiresAtEpoch: Long,
    val isConsumed: Boolean = false,
    val createdAtEpoch: Long
)
