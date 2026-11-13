package com.example.ui

import com.example.data.db.entities.*
import com.example.domain.security.AuthContext
import com.example.domain.service.AuthResult
import com.example.domain.service.BackupVerificationResult
import com.example.domain.service.BalanceSheetReport
import com.example.domain.service.ProfitAndLossReport
import com.example.domain.service.TrialBalanceRow
import com.example.localization.AppLanguage

enum class AuthScreenStep {
    SPLASH,
    WELCOME_SETUP,
    LOGIN,
    FORGOT_PASSWORD,
    MFA_CHALLENGE,
    DEVICE_CHALLENGE
}

enum class AppDestination {
    DASHBOARD,
    PROPERTIES,
    TENANTS,
    PAYMENTS,
    MORE,
    AGREEMENTS,
    CHEQUES,
    DEPOSITS,
    ACCOUNTING,
    WORKFORCE,
    MAINTENANCE,
    INVENTORY,
    DOCUMENTS,
    NOTIFICATIONS,
    REPORTS,
    OWNER_CONTROL,
    ACCOUNT_SECURITY,
    CUSTOMER_PORTAL
}

data class BusinessSnapshotData(
    val businesses: List<BusinessEntity> = emptyList(),
    val properties: List<PropertyEntity> = emptyList(),
    val buildings: List<BuildingEntity> = emptyList(),
    val floors: List<FloorEntity> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val reservations: List<RoomReservationEntity> = emptyList(),
    val inspections: List<PropertyInspectionEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val agreements: List<AgreementEntity> = emptyList(),
    val tenancyHistory: List<TenancyHistoryEntity> = emptyList(),
    val monthlyRents: List<MonthlyRentEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val receipts: List<ReceiptEntity> = emptyList(),
    val deposits: List<SecurityDepositEntity> = emptyList(),
    val depositTransactions: List<DepositTransactionEntity> = emptyList(),
    val cheques: List<ChequeEntity> = emptyList(),
    val accounts: List<ChartOfAccountEntity> = emptyList(),
    val journals: List<JournalEntryEntity> = emptyList(),
    val journalLines: List<JournalLineEntity> = emptyList(),
    val incomeRecords: List<IncomeRecordEntity> = emptyList(),
    val expenseRecords: List<ExpenseRecordEntity> = emptyList(),
    val bankAccounts: List<BankAccountEntity> = emptyList(),
    val cashbookEntries: List<CashbookEntryEntity> = emptyList(),
    val pettyCashEntries: List<PettyCashEntryEntity> = emptyList(),
    val bankReconciliations: List<BankReconciliationEntity> = emptyList(),
    val fiscalPeriods: List<FiscalPeriodEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val departments: List<DepartmentEntity> = emptyList(),
    val teams: List<TeamEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val workHistory: List<EmployeeWorkHistoryEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val roles: List<RoleEntity> = emptyList(),
    val permissions: List<PermissionEntity> = emptyList(),
    val rolePermissions: List<RolePermissionEntity> = emptyList(),
    val userRoles: List<UserRoleEntity> = emptyList(),
    val complaints: List<ComplaintEntity> = emptyList(),
    val jobAssignments: List<JobAssignmentEntity> = emptyList(),
    val workReports: List<WorkReportEntity> = emptyList(),
    val workReportPhotos: List<WorkReportPhotoEntity> = emptyList(),
    val materialUsage: List<MaterialUsageEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val itemCategories: List<ItemCategoryEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val stockLocations: List<StockLocationEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val purchaseItems: List<PurchaseItemEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val supplierPayments: List<SupplierPaymentEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val documentAccessLogs: List<DocumentAccessLogEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val backups: List<BackupRecordEntity> = emptyList(),
    val integrations: List<IntegrationConfigEntity> = emptyList(),
    val securityEvents: List<SecurityEventEntity> = emptyList(),
    val activeSessions: List<LoginSessionEntity> = emptyList(),
    val userDevices: List<UserDeviceEntity> = emptyList(),
    val loginHistory: List<LoginHistoryEntity> = emptyList()
)

data class FinancialReportsState(
    val trialBalance: List<TrialBalanceRow> = emptyList(),
    val profitAndLoss: ProfitAndLossReport? = null,
    val balanceSheet: BalanceSheetReport? = null
)

data class OneHomeUiState(
    val isInitializing: Boolean = true,
    val hasExistingUsers: Boolean = false,
    val authStep: AuthScreenStep = AuthScreenStep.SPLASH,
    val pendingMfaChallenge: AuthResult.MfaRequired? = null,
    val pendingDeviceChallenge: AuthResult.NewDeviceVerificationRequired? = null,
    val pendingResetOtpNotice: String? = null,
    val authContext: AuthContext? = null,
    val currentDestination: AppDestination = AppDestination.DASHBOARD,
    val backStack: List<AppDestination> = emptyList(),
    val selectedPropertyFilterId: String? = null,
    val globalSearchQuery: String = "",
    val language: AppLanguage = AppLanguage.EN,
    val darkTheme: Boolean = false,
    val isBusy: Boolean = false,
    val bannerError: String? = null,
    val bannerSuccess: String? = null,
    val mfaProvisionInfo: Pair<String, List<String>>? = null,
    val lastBackupVerification: BackupVerificationResult? = null,
    val data: BusinessSnapshotData = BusinessSnapshotData(),
    val reports: FinancialReportsState = FinancialReportsState()
)
