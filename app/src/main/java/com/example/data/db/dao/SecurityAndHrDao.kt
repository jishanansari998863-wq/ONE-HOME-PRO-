package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityAndHrDao {
    // Users & Auth
    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAtEpoch DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(entity: UserEntity)

    @Update
    suspend fun updateUser(entity: UserEntity)

    // Sessions & History & Devices & MFA & Resets
    @Query("SELECT * FROM login_sessions WHERE tokenHash = :tokenHash LIMIT 1")
    suspend fun getSessionByTokenHash(tokenHash: String): LoginSessionEntity?

    @Query("SELECT * FROM login_sessions WHERE userId = :userId ORDER BY createdAtEpoch DESC")
    fun observeSessionsForUser(userId: String): Flow<List<LoginSessionEntity>>

    @Query("SELECT * FROM login_sessions WHERE userId = :userId AND isRevoked = 0")
    suspend fun getActiveSessionsForUser(userId: String): List<LoginSessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(entity: LoginSessionEntity)

    @Update
    suspend fun updateSession(entity: LoginSessionEntity)

    @Query("UPDATE login_sessions SET isRevoked = 1, revokedReason = :reason WHERE userId = :userId AND isRevoked = 0")
    suspend fun revokeAllSessionsForUser(userId: String, reason: String)

    @Query("UPDATE login_sessions SET isRevoked = 1, revokedReason = :reason WHERE id = :sessionId")
    suspend fun revokeSessionById(sessionId: String, reason: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoginHistory(entity: LoginHistoryEntity)

    @Query("SELECT * FROM login_history WHERE userId = :userId ORDER BY occurredAtEpoch DESC LIMIT 50")
    fun observeLoginHistoryForUser(userId: String): Flow<List<LoginHistoryEntity>>

    @Query("SELECT * FROM user_devices WHERE userId = :userId AND deviceId = :deviceId LIMIT 1")
    suspend fun getUserDevice(userId: String, deviceId: String): UserDeviceEntity?

    @Query("SELECT * FROM user_devices WHERE userId = :userId ORDER BY lastSeenAtEpoch DESC")
    fun observeUserDevices(userId: String): Flow<List<UserDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserDevice(entity: UserDeviceEntity)

    @Update
    suspend fun updateUserDevice(entity: UserDeviceEntity)

    @Query("SELECT * FROM mfa_credentials WHERE userId = :userId LIMIT 1")
    suspend fun getMfaCredential(userId: String): MfaCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMfaCredential(entity: MfaCredentialEntity)

    @Query("SELECT * FROM recovery_codes WHERE userId = :userId AND isUsed = 0")
    suspend fun getUnusedRecoveryCodes(userId: String): List<RecoveryCodeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecoveryCodes(codes: List<RecoveryCodeEntity>)

    @Update
    suspend fun updateRecoveryCode(entity: RecoveryCodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPasswordResetRequest(entity: PasswordResetRequestEntity)

    @Query("SELECT * FROM password_reset_requests WHERE email = :email AND isConsumed = 0 ORDER BY createdAtEpoch DESC LIMIT 1")
    suspend fun getLatestResetRequest(email: String): PasswordResetRequestEntity?

    @Update
    suspend fun updatePasswordResetRequest(entity: PasswordResetRequestEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecurityEvent(entity: SecurityEventEntity)

    @Query("SELECT * FROM security_events ORDER BY occurredAtEpoch DESC LIMIT 100")
    fun observeSecurityEvents(): Flow<List<SecurityEventEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccountClosureRequest(entity: AccountClosureRequestEntity)

    @Query("SELECT * FROM account_closure_requests WHERE businessId = :businessId ORDER BY requestedAtEpoch DESC")
    fun observeClosureRequests(businessId: String): Flow<List<AccountClosureRequestEntity>>

    @Update
    suspend fun updateAccountClosureRequest(entity: AccountClosureRequestEntity)

    // Roles & Permissions
    @Query("SELECT * FROM roles WHERE businessId = :businessId ORDER BY name ASC")
    fun observeRoles(businessId: String): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE businessId = :businessId AND name = :name LIMIT 1")
    suspend fun getRoleByName(businessId: String, name: String): RoleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRole(entity: RoleEntity)

    @Query("SELECT * FROM permissions")
    suspend fun getAllPermissions(): List<PermissionEntity>

    @Query("SELECT * FROM permissions")
    fun observePermissions(): Flow<List<PermissionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPermissions(entities: List<PermissionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRolePermission(entity: RolePermissionEntity)

    @Query("DELETE FROM role_permissions WHERE roleId = :roleId")
    suspend fun clearRolePermissions(roleId: String)

    @Query(
        "SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permissionId " +
            "INNER JOIN user_roles ur ON rp.roleId = ur.roleId " +
            "WHERE ur.userId = :userId AND ur.businessId = :businessId"
    )
    suspend fun getPermissionsForUserInBusiness(userId: String, businessId: String): List<PermissionEntity>

    @Query("SELECT * FROM role_permissions WHERE businessId = :businessId")
    fun observeRolePermissions(businessId: String): Flow<List<RolePermissionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserRole(entity: UserRoleEntity)

    @Query("SELECT * FROM user_roles WHERE businessId = :businessId")
    fun observeUserRoles(businessId: String): Flow<List<UserRoleEntity>>

    @Query("SELECT * FROM user_roles WHERE userId = :userId AND businessId = :businessId")
    suspend fun getUserRolesForBusiness(userId: String, businessId: String): List<UserRoleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserBusinessAccess(entity: UserBusinessAccessEntity)

    @Query("SELECT * FROM user_business_access WHERE userId = :userId AND isActive = 1")
    suspend fun getBusinessAccessForUser(userId: String): List<UserBusinessAccessEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserPropertyAccess(entity: UserPropertyAccessEntity)

    @Query("SELECT * FROM user_property_access WHERE userId = :userId AND businessId = :businessId AND isActive = 1")
    suspend fun getPropertyAccessForUser(userId: String, businessId: String): List<UserPropertyAccessEntity>

    // Employees, Departments, Teams, Attendance
    @Query("SELECT * FROM employees WHERE businessId = :businessId ORDER BY hiredAtEpoch DESC")
    suspend fun getAllEmployeesRaw(businessId: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE businessId = :businessId ORDER BY hiredAtEpoch DESC")
    fun observeEmployees(businessId: String): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmployee(entity: EmployeeEntity)

    @Update
    suspend fun updateEmployee(entity: EmployeeEntity)

    @Query("SELECT * FROM departments WHERE businessId = :businessId ORDER BY name ASC")
    fun observeDepartments(businessId: String): Flow<List<DepartmentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepartment(entity: DepartmentEntity)

    @Query("SELECT * FROM teams WHERE businessId = :businessId ORDER BY name ASC")
    fun observeTeams(businessId: String): Flow<List<TeamEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTeam(entity: TeamEntity)

    @Query("SELECT * FROM team_members WHERE businessId = :businessId")
    fun observeTeamMembers(businessId: String): Flow<List<TeamMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTeamMember(entity: TeamMemberEntity)

    @Query("SELECT * FROM attendance WHERE businessId = :businessId ORDER BY recordedAtEpoch DESC")
    fun observeAttendance(businessId: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttendance(entity: AttendanceEntity)

    @Query("SELECT * FROM employee_work_history WHERE businessId = :businessId ORDER BY occurredAtEpoch DESC")
    fun observeWorkHistory(businessId: String): Flow<List<EmployeeWorkHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmployeeWorkHistory(entity: EmployeeWorkHistoryEntity)
}
