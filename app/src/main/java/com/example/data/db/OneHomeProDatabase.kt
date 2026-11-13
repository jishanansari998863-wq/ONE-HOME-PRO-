package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.db.dao.*
import com.example.data.db.entities.*

@Database(
    entities = [
        // Phase 1: Core Property & Tenancy (12)
        BusinessEntity::class,
        PropertyEntity::class,
        BuildingEntity::class,
        FloorEntity::class,
        RoomEntity::class,
        RoomReservationEntity::class,
        PropertyInspectionEntity::class,
        CustomerEntity::class,
        FamilyMemberEntity::class,
        AgreementEntity::class,
        AgreementMemberEntity::class,
        TenancyHistoryEntity::class,
        // Phase 2: Rent, Payments, Cheques & Security Deposits (8)
        MonthlyRentEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        ReceiptEntity::class,
        SecurityDepositEntity::class,
        DepositTransactionEntity::class,
        ChequeEntity::class,
        AdvanceRentEntity::class,
        // Phase 3: Double-Entry Accounting & Treasury (12)
        ChartOfAccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        IncomeRecordEntity::class,
        ExpenseRecordEntity::class,
        BankAccountEntity::class,
        CashbookEntryEntity::class,
        PettyCashEntryEntity::class,
        BankTransactionEntity::class,
        BankReconciliationEntity::class,
        ApprovalRequestEntity::class,
        FiscalPeriodEntity::class,
        // Phase 4: Employees, RBAC & Security (21)
        EmployeeEntity::class,
        DepartmentEntity::class,
        TeamEntity::class,
        TeamMemberEntity::class,
        UserEntity::class,
        RoleEntity::class,
        PermissionEntity::class,
        RolePermissionEntity::class,
        UserRoleEntity::class,
        UserBusinessAccessEntity::class,
        UserPropertyAccessEntity::class,
        AttendanceEntity::class,
        EmployeeWorkHistoryEntity::class,
        LoginSessionEntity::class,
        LoginHistoryEntity::class,
        AccountClosureRequestEntity::class,
        MfaCredentialEntity::class,
        RecoveryCodeEntity::class,
        SecurityEventEntity::class,
        UserDeviceEntity::class,
        PasswordResetRequestEntity::class,
        // Phase 5: Maintenance & Complaints (8)
        ComplaintEntity::class,
        ComplaintAttachmentEntity::class,
        JobAssignmentEntity::class,
        WorkReportEntity::class,
        WorkReportPhotoEntity::class,
        MaterialUsageEntity::class,
        JobApprovalEntity::class,
        ComplaintStatusHistoryEntity::class,
        // Phase 6: Purchase & Inventory (16)
        SupplierEntity::class,
        ItemCategoryEntity::class,
        ItemEntity::class,
        StockLocationEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        StockMovementEntity::class,
        StockTransferEntity::class,
        StockTransferLineEntity::class,
        StockAdjustmentEntity::class,
        StockCountEntity::class,
        StockCountLineEntity::class,
        SupplierPaymentEntity::class,
        SupplierPaymentAllocationEntity::class,
        PurchaseApprovalEntity::class,
        GoodsReceiptDetailEntity::class,
        // Governance, Documents, Notifications, Audit & Backups (6)
        DocumentEntity::class,
        DocumentAccessLogEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        BackupRecordEntity::class,
        IntegrationConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OneHomeProDatabase : RoomDatabase() {
    abstract fun coreAndTenancyDao(): CoreAndTenancyDao
    abstract fun accountingDao(): AccountingDao
    abstract fun securityAndHrDao(): SecurityAndHrDao
    abstract fun operationsDao(): OperationsDao

    companion object {
        @Volatile
        private var INSTANCE: OneHomeProDatabase? = null

        fun getInstance(context: Context): OneHomeProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OneHomeProDatabase::class.java,
                    "one_home_pro_qatar.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
