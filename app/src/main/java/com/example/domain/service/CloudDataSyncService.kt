package com.example.domain.service

import android.util.Base64
import androidx.room.withTransaction
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.security.*
import com.example.localization.AppLanguage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class CloudSyncPayload(
    val version: Int = 1,
    val business: BusinessEntity? = null,
    val properties: List<PropertyEntity> = emptyList(),
    val buildings: List<BuildingEntity> = emptyList(),
    val floors: List<FloorEntity> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val reservations: List<RoomReservationEntity> = emptyList(),
    val inspections: List<PropertyInspectionEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val familyMembers: List<FamilyMemberEntity> = emptyList(),
    val agreements: List<AgreementEntity> = emptyList(),
    val agreementMembers: List<AgreementMemberEntity> = emptyList(),
    val tenancyHistory: List<TenancyHistoryEntity> = emptyList(),
    val monthlyRents: List<MonthlyRentEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val paymentAllocations: List<PaymentAllocationEntity> = emptyList(),
    val receipts: List<ReceiptEntity> = emptyList(),
    val securityDeposits: List<SecurityDepositEntity> = emptyList(),
    val depositTransactions: List<DepositTransactionEntity> = emptyList(),
    val cheques: List<ChequeEntity> = emptyList(),
    val advanceRents: List<AdvanceRentEntity> = emptyList(),
    val accounts: List<ChartOfAccountEntity> = emptyList(),
    val journals: List<JournalEntryEntity> = emptyList(),
    val journalLines: List<JournalLineEntity> = emptyList(),
    val incomeRecords: List<IncomeRecordEntity> = emptyList(),
    val expenseRecords: List<ExpenseRecordEntity> = emptyList(),
    val bankAccounts: List<BankAccountEntity> = emptyList(),
    val cashbookEntries: List<CashbookEntryEntity> = emptyList(),
    val pettyCashEntries: List<PettyCashEntryEntity> = emptyList(),
    val bankTransactions: List<BankTransactionEntity> = emptyList(),
    val bankReconciliations: List<BankReconciliationEntity> = emptyList(),
    val approvalRequests: List<ApprovalRequestEntity> = emptyList(),
    val fiscalPeriods: List<FiscalPeriodEntity> = emptyList(),
    val departments: List<DepartmentEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val teams: List<TeamEntity> = emptyList(),
    val teamMembers: List<TeamMemberEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val employeeWorkHistory: List<EmployeeWorkHistoryEntity> = emptyList(),
    val complaints: List<ComplaintEntity> = emptyList(),
    val complaintAttachments: List<ComplaintAttachmentEntity> = emptyList(),
    val jobAssignments: List<JobAssignmentEntity> = emptyList(),
    val workReports: List<WorkReportEntity> = emptyList(),
    val workReportPhotos: List<WorkReportPhotoEntity> = emptyList(),
    val materialUsage: List<MaterialUsageEntity> = emptyList(),
    val jobApprovals: List<JobApprovalEntity> = emptyList(),
    val complaintStatusHistory: List<ComplaintStatusHistoryEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val itemCategories: List<ItemCategoryEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val stockLocations: List<StockLocationEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val purchaseItems: List<PurchaseItemEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val stockTransfers: List<StockTransferEntity> = emptyList(),
    val stockTransferLines: List<StockTransferLineEntity> = emptyList(),
    val stockAdjustments: List<StockAdjustmentEntity> = emptyList(),
    val stockCounts: List<StockCountEntity> = emptyList(),
    val stockCountLines: List<StockCountLineEntity> = emptyList(),
    val supplierPayments: List<SupplierPaymentEntity> = emptyList(),
    val supplierPaymentAllocations: List<SupplierPaymentAllocationEntity> = emptyList(),
    val purchaseApprovals: List<PurchaseApprovalEntity> = emptyList(),
    val goodsReceiptDetails: List<GoodsReceiptDetailEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val documentAccessLogs: List<DocumentAccessLogEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val backupRecords: List<BackupRecordEntity> = emptyList(),
    val integrations: List<IntegrationConfigEntity> = emptyList(),
    val appLanguageCode: String = "en",
    val darkTheme: Boolean = false
) {
    fun totalRecordCount(): Int =
        (if (business != null) 1 else 0) +
            properties.size + buildings.size + floors.size + rooms.size +
            reservations.size + inspections.size + customers.size + familyMembers.size +
            agreements.size + agreementMembers.size + tenancyHistory.size + monthlyRents.size + payments.size +
            paymentAllocations.size + receipts.size + securityDeposits.size +
            depositTransactions.size + cheques.size + advanceRents.size +
            accounts.size + journals.size + journalLines.size +
            incomeRecords.size + expenseRecords.size + bankAccounts.size +
            cashbookEntries.size + pettyCashEntries.size + bankTransactions.size +
            bankReconciliations.size + approvalRequests.size + fiscalPeriods.size +
            departments.size + employees.size + teams.size + teamMembers.size + attendance.size + employeeWorkHistory.size +
            complaints.size + complaintAttachments.size + jobAssignments.size + workReports.size +
            workReportPhotos.size + materialUsage.size + jobApprovals.size + complaintStatusHistory.size +
            suppliers.size + itemCategories.size + items.size + stockLocations.size +
            purchases.size + purchaseItems.size + stockMovements.size + stockTransfers.size +
            stockTransferLines.size + stockAdjustments.size + stockCounts.size + stockCountLines.size +
            supplierPayments.size + supplierPaymentAllocations.size + purchaseApprovals.size + goodsReceiptDetails.size +
            documents.size + documentAccessLogs.size + notifications.size + auditLogs.size + backupRecords.size + integrations.size
}

data class CloudRestoreResult(
    val businessId: String,
    val restoredRecordCount: Int,
    val syncedAtEpoch: Long,
    val syncedFromDeviceName: String
)

class CloudDataSyncService(
    private val db: OneHomeProDatabase,
    private val centralAuthority: CentralAuthorityGateway,
    private val sessionStore: SessionStore
) {
    private val coreDao = db.coreAndTenancyDao()
    private val accDao = db.accountingDao()
    private val hrDao = db.securityAndHrDao()
    private val opsDao = db.operationsDao()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(CloudSyncPayload::class.java)

    suspend fun syncBusinessToCloud(
        ctx: AuthContext,
        deviceName: String,
        language: AppLanguage,
        darkTheme: Boolean
    ): CloudBusinessSnapshot {
        AuthorizationGuard.requirePermission(ctx, AppModule.BACKUPS, ActionType.EXPORT, ctx.activeBusinessId)
        val bizId = ctx.activeBusinessId
        val uid = centralAuthority.currentIdentity()?.uid ?: ctx.userId

        val payload = CloudSyncPayload(
            business = coreDao.getBusinessById(bizId),
            properties = coreDao.getPropertiesByBusiness(bizId),
            buildings = coreDao.getBuildingsByBusiness(bizId),
            floors = coreDao.getFloorsByBusiness(bizId),
            rooms = coreDao.getRoomsByBusiness(bizId),
            reservations = coreDao.getReservationsByBusiness(bizId),
            inspections = coreDao.getInspectionsByBusiness(bizId),
            customers = coreDao.getCustomersByBusiness(bizId),
            familyMembers = coreDao.getFamilyMembersByBusiness(bizId),
            agreements = coreDao.getAgreementsByBusiness(bizId),
            agreementMembers = coreDao.getAgreementMembersByBusiness(bizId),
            tenancyHistory = coreDao.getTenancyHistoryByBusiness(bizId),
            monthlyRents = coreDao.getMonthlyRentsByBusiness(bizId),
            payments = coreDao.getPaymentsByBusiness(bizId),
            paymentAllocations = coreDao.getPaymentAllocationsByBusiness(bizId),
            receipts = coreDao.getReceiptsByBusiness(bizId),
            securityDeposits = coreDao.getSecurityDepositsByBusiness(bizId),
            depositTransactions = coreDao.getDepositTransactionsByBusiness(bizId),
            cheques = coreDao.getChequesByBusiness(bizId),
            advanceRents = coreDao.getAdvanceRentsByBusiness(bizId),
            accounts = accDao.getAccountsByBusiness(bizId),
            journals = accDao.getJournalEntriesByBusiness(bizId),
            journalLines = accDao.getJournalLinesByBusiness(bizId),
            incomeRecords = accDao.getIncomeRecordsByBusiness(bizId),
            expenseRecords = accDao.getExpenseRecordsByBusiness(bizId),
            bankAccounts = accDao.getBankAccountsByBusiness(bizId),
            cashbookEntries = accDao.getCashbookEntriesByBusiness(bizId),
            pettyCashEntries = accDao.getPettyCashEntriesByBusiness(bizId),
            bankTransactions = accDao.getBankTransactionsByBusiness(bizId),
            bankReconciliations = accDao.getBankReconciliationsByBusiness(bizId),
            approvalRequests = accDao.getApprovalRequestsByBusiness(bizId),
            fiscalPeriods = accDao.getFiscalPeriodsByBusiness(bizId),
            departments = hrDao.getDepartmentsByBusiness(bizId),
            employees = hrDao.getAllEmployeesRaw(bizId),
            teams = hrDao.getTeamsByBusiness(bizId),
            teamMembers = hrDao.getTeamMembersByBusiness(bizId),
            attendance = hrDao.getAttendanceByBusiness(bizId),
            employeeWorkHistory = hrDao.getEmployeeWorkHistoryByBusiness(bizId),
            complaints = opsDao.getComplaintsByBusiness(bizId),
            complaintAttachments = opsDao.getComplaintAttachmentsByBusiness(bizId),
            jobAssignments = opsDao.getJobAssignmentsByBusiness(bizId),
            workReports = opsDao.getWorkReportsByBusiness(bizId),
            workReportPhotos = opsDao.getWorkReportPhotosByBusiness(bizId),
            materialUsage = opsDao.getMaterialUsageByBusiness(bizId),
            jobApprovals = opsDao.getJobApprovalsByBusiness(bizId),
            complaintStatusHistory = opsDao.getComplaintStatusHistoryByBusiness(bizId),
            suppliers = opsDao.getSuppliersByBusiness(bizId),
            itemCategories = opsDao.getItemCategoriesByBusiness(bizId),
            items = opsDao.getItemsByBusiness(bizId),
            stockLocations = opsDao.getStockLocationsByBusiness(bizId),
            purchases = opsDao.getPurchasesByBusiness(bizId),
            purchaseItems = opsDao.getPurchaseItemsByBusiness(bizId),
            stockMovements = opsDao.getStockMovementsByBusiness(bizId),
            stockTransfers = opsDao.getStockTransfersByBusiness(bizId),
            stockTransferLines = opsDao.getStockTransferLinesByBusiness(bizId),
            stockAdjustments = opsDao.getStockAdjustmentsByBusiness(bizId),
            stockCounts = opsDao.getStockCountsByBusiness(bizId),
            stockCountLines = opsDao.getStockCountLinesByBusiness(bizId),
            supplierPayments = opsDao.getSupplierPaymentsByBusiness(bizId),
            supplierPaymentAllocations = opsDao.getSupplierPaymentAllocationsByBusiness(bizId),
            purchaseApprovals = opsDao.getPurchaseApprovalsByBusiness(bizId),
            goodsReceiptDetails = opsDao.getGoodsReceiptDetailsByBusiness(bizId),
            documents = opsDao.getDocumentsByBusiness(bizId),
            documentAccessLogs = opsDao.getDocumentAccessLogsByBusiness(bizId),
            notifications = opsDao.getNotificationsByBusiness(bizId),
            auditLogs = opsDao.getAuditLogsByBusiness(bizId),
            backupRecords = opsDao.getBackupRecordsByBusiness(bizId),
            integrations = opsDao.getIntegrationsByBusiness(bizId),
            appLanguageCode = language.code,
            darkTheme = darkTheme
        )

        val jsonBytes = adapter.toJson(payload).toByteArray(Charsets.UTF_8)
        val sha256 = CryptoUtil.sha256Hex(jsonBytes)
        val compressed = CryptoUtil.gzipCompress(jsonBytes)
        val encoded = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val now = System.currentTimeMillis()

        val snapshot = CloudBusinessSnapshot(
            id = "latest",
            businessId = bizId,
            payloadBase64 = encoded,
            sha256Checksum = sha256,
            recordCount = payload.totalRecordCount(),
            syncedByUid = uid,
            syncedByDeviceName = deviceName,
            appLanguageCode = language.code,
            darkTheme = darkTheme,
            syncedAtEpoch = now
        )
        centralAuthority.uploadCloudBusinessSnapshot(snapshot)
        return snapshot
    }

    suspend fun hasLocalCoreBusinessData(businessId: String): Boolean {
        return coreDao.getPropertiesByBusiness(businessId).isNotEmpty() ||
            coreDao.getCustomersByBusiness(businessId).isNotEmpty() ||
            coreDao.getAgreementsByBusiness(businessId).isNotEmpty() ||
            accDao.getJournalEntriesByBusiness(businessId).isNotEmpty()
    }

    suspend fun restoreBusinessFromCloudIfEmpty(ctx: AuthContext): CloudRestoreResult? {
        if (hasLocalCoreBusinessData(ctx.activeBusinessId)) return null
        return restoreBusinessFromCloud(ctx)
    }

    suspend fun restoreBusinessFromCloud(ctx: AuthContext): CloudRestoreResult? {
        AuthorizationGuard.requirePermission(ctx, AppModule.BACKUPS, ActionType.RESTORE, ctx.activeBusinessId)
        val snapshot = centralAuthority.downloadCloudBusinessSnapshot(ctx.activeBusinessId) ?: return null
        val compressed = Base64.decode(snapshot.payloadBase64, Base64.NO_WRAP)
        val jsonBytes = CryptoUtil.gzipDecompress(compressed)
        val actualSha = CryptoUtil.sha256Hex(jsonBytes)
        require(actualSha == snapshot.sha256Checksum) {
            "Cloud snapshot integrity verification failed (checksum mismatch)."
        }
        val payload = adapter.fromJson(String(jsonBytes, Charsets.UTF_8))
            ?: throw IllegalStateException("Failed to decode cloud snapshot payload.")

        db.withTransaction {
            payload.business?.let { coreDao.upsertBusinesses(listOf(it)) }
            if (payload.properties.isNotEmpty()) coreDao.upsertProperties(payload.properties)
            if (payload.buildings.isNotEmpty()) coreDao.upsertBuildings(payload.buildings)
            if (payload.floors.isNotEmpty()) coreDao.upsertFloors(payload.floors)
            if (payload.rooms.isNotEmpty()) coreDao.upsertRooms(payload.rooms)
            if (payload.reservations.isNotEmpty()) coreDao.upsertReservations(payload.reservations)
            if (payload.inspections.isNotEmpty()) coreDao.upsertInspections(payload.inspections)
            if (payload.customers.isNotEmpty()) coreDao.upsertCustomers(payload.customers)
            if (payload.familyMembers.isNotEmpty()) coreDao.upsertFamilyMembers(payload.familyMembers)
            if (payload.agreements.isNotEmpty()) coreDao.upsertAgreements(payload.agreements)
            if (payload.agreementMembers.isNotEmpty()) coreDao.upsertAgreementMembers(payload.agreementMembers)
            if (payload.tenancyHistory.isNotEmpty()) coreDao.upsertTenancyHistory(payload.tenancyHistory)
            if (payload.monthlyRents.isNotEmpty()) coreDao.upsertMonthlyRents(payload.monthlyRents)
            if (payload.payments.isNotEmpty()) coreDao.upsertPayments(payload.payments)
            if (payload.paymentAllocations.isNotEmpty()) coreDao.upsertPaymentAllocations(payload.paymentAllocations)
            if (payload.receipts.isNotEmpty()) coreDao.upsertReceipts(payload.receipts)
            if (payload.securityDeposits.isNotEmpty()) coreDao.upsertSecurityDeposits(payload.securityDeposits)
            if (payload.depositTransactions.isNotEmpty()) coreDao.upsertDepositTransactions(payload.depositTransactions)
            if (payload.cheques.isNotEmpty()) coreDao.upsertCheques(payload.cheques)
            if (payload.advanceRents.isNotEmpty()) coreDao.upsertAdvanceRents(payload.advanceRents)

            var accountsToUpsert = payload.accounts
            var journalLinesToUpsert = payload.journalLines
            var incomeToUpsert = payload.incomeRecords
            var expenseToUpsert = payload.expenseRecords
            if (payload.accounts.isNotEmpty()) {
                val existingByCode = accDao.getAccountsByBusiness(ctx.activeBusinessId).associateBy { it.code }
                val remappedIds = mutableMapOf<String, String>()
                val filteredAccounts = mutableListOf<ChartOfAccountEntity>()
                for (incoming in payload.accounts) {
                    val existing = existingByCode[incoming.code]
                    if (existing != null && existing.id != incoming.id) {
                        val refCount = accDao.countJournalLinesForAccount(existing.id)
                        if (refCount == 0) {
                            accDao.deleteAccountById(existing.id)
                            filteredAccounts += incoming
                        } else {
                            remappedIds[incoming.id] = existing.id
                        }
                    } else {
                        filteredAccounts += incoming
                    }
                }
                accountsToUpsert = filteredAccounts
                if (remappedIds.isNotEmpty()) {
                    journalLinesToUpsert = journalLinesToUpsert.map { line ->
                        val mapped = remappedIds[line.accountId]
                        if (mapped != null) line.copy(accountId = mapped) else line
                    }
                    incomeToUpsert = incomeToUpsert.map { inc ->
                        val mapped = remappedIds[inc.accountId]
                        if (mapped != null) inc.copy(accountId = mapped) else inc
                    }
                    expenseToUpsert = expenseToUpsert.map { exp ->
                        val mapped = remappedIds[exp.accountId]
                        if (mapped != null) exp.copy(accountId = mapped) else exp
                    }
                }
            }

            if (accountsToUpsert.isNotEmpty()) accDao.upsertAccounts(accountsToUpsert)
            if (payload.journals.isNotEmpty()) accDao.upsertJournalEntries(payload.journals)
            if (journalLinesToUpsert.isNotEmpty()) accDao.upsertJournalLines(journalLinesToUpsert)
            if (incomeToUpsert.isNotEmpty()) accDao.upsertIncomeRecords(incomeToUpsert)
            if (expenseToUpsert.isNotEmpty()) accDao.upsertExpenseRecords(expenseToUpsert)
            if (payload.bankAccounts.isNotEmpty()) accDao.upsertBankAccounts(payload.bankAccounts)
            if (payload.cashbookEntries.isNotEmpty()) accDao.upsertCashbookEntries(payload.cashbookEntries)
            if (payload.pettyCashEntries.isNotEmpty()) accDao.upsertPettyCashEntries(payload.pettyCashEntries)
            if (payload.bankTransactions.isNotEmpty()) accDao.upsertBankTransactions(payload.bankTransactions)
            if (payload.bankReconciliations.isNotEmpty()) accDao.upsertBankReconciliations(payload.bankReconciliations)
            if (payload.approvalRequests.isNotEmpty()) accDao.upsertApprovalRequests(payload.approvalRequests)
            if (payload.fiscalPeriods.isNotEmpty()) accDao.upsertFiscalPeriods(payload.fiscalPeriods)

            if (payload.departments.isNotEmpty()) hrDao.upsertDepartments(payload.departments)
            if (payload.employees.isNotEmpty()) hrDao.upsertEmployees(payload.employees)
            if (payload.teams.isNotEmpty()) hrDao.upsertTeams(payload.teams)
            if (payload.teamMembers.isNotEmpty()) hrDao.upsertTeamMembers(payload.teamMembers)
            if (payload.attendance.isNotEmpty()) hrDao.upsertAttendance(payload.attendance)
            if (payload.employeeWorkHistory.isNotEmpty()) hrDao.upsertEmployeeWorkHistory(payload.employeeWorkHistory)

            if (payload.suppliers.isNotEmpty()) opsDao.upsertSuppliers(payload.suppliers)
            if (payload.itemCategories.isNotEmpty()) opsDao.upsertItemCategories(payload.itemCategories)
            if (payload.stockLocations.isNotEmpty()) opsDao.upsertStockLocations(payload.stockLocations)
            if (payload.items.isNotEmpty()) opsDao.upsertItems(payload.items)
            if (payload.purchases.isNotEmpty()) opsDao.upsertPurchases(payload.purchases)
            if (payload.purchaseItems.isNotEmpty()) opsDao.upsertPurchaseItems(payload.purchaseItems)
            if (payload.purchaseApprovals.isNotEmpty()) opsDao.upsertPurchaseApprovals(payload.purchaseApprovals)
            if (payload.stockMovements.isNotEmpty()) opsDao.upsertStockMovements(payload.stockMovements)
            if (payload.stockTransfers.isNotEmpty()) opsDao.upsertStockTransfers(payload.stockTransfers)
            if (payload.stockTransferLines.isNotEmpty()) opsDao.upsertStockTransferLines(payload.stockTransferLines)
            if (payload.stockAdjustments.isNotEmpty()) opsDao.upsertStockAdjustments(payload.stockAdjustments)
            if (payload.stockCounts.isNotEmpty()) opsDao.upsertStockCounts(payload.stockCounts)
            if (payload.stockCountLines.isNotEmpty()) opsDao.upsertStockCountLines(payload.stockCountLines)
            if (payload.goodsReceiptDetails.isNotEmpty()) opsDao.upsertGoodsReceiptDetails(payload.goodsReceiptDetails)
            if (payload.complaints.isNotEmpty()) opsDao.upsertComplaints(payload.complaints)
            if (payload.complaintAttachments.isNotEmpty()) opsDao.upsertComplaintAttachments(payload.complaintAttachments)
            if (payload.jobAssignments.isNotEmpty()) opsDao.upsertJobAssignments(payload.jobAssignments)
            if (payload.workReports.isNotEmpty()) opsDao.upsertWorkReports(payload.workReports)
            if (payload.workReportPhotos.isNotEmpty()) opsDao.upsertWorkReportPhotos(payload.workReportPhotos)
            if (payload.materialUsage.isNotEmpty()) opsDao.upsertMaterialUsage(payload.materialUsage)
            if (payload.jobApprovals.isNotEmpty()) opsDao.upsertJobApprovals(payload.jobApprovals)
            if (payload.complaintStatusHistory.isNotEmpty()) opsDao.upsertComplaintStatusHistory(payload.complaintStatusHistory)
            if (payload.supplierPayments.isNotEmpty()) opsDao.upsertSupplierPayments(payload.supplierPayments)
            if (payload.supplierPaymentAllocations.isNotEmpty()) opsDao.upsertSupplierPaymentAllocations(payload.supplierPaymentAllocations)
            if (payload.documents.isNotEmpty()) opsDao.upsertDocuments(payload.documents)
            if (payload.documentAccessLogs.isNotEmpty()) opsDao.upsertDocumentAccessLogs(payload.documentAccessLogs)
            if (payload.notifications.isNotEmpty()) opsDao.upsertNotifications(payload.notifications)
            if (payload.auditLogs.isNotEmpty()) opsDao.upsertAuditLogs(payload.auditLogs)
            if (payload.backupRecords.isNotEmpty()) opsDao.upsertBackupRecords(payload.backupRecords)
            if (payload.integrations.isNotEmpty()) opsDao.upsertIntegrations(payload.integrations)
        }

        // Restore app language and theme settings onto the new device
        val restoredLang = AppLanguage.entries.firstOrNull {
            it.code.equals(payload.appLanguageCode, ignoreCase = true)
        } ?: AppLanguage.EN
        sessionStore.setLanguage(restoredLang)
        sessionStore.setDarkMode(payload.darkTheme)

        return CloudRestoreResult(
            businessId = snapshot.businessId,
            restoredRecordCount = payload.totalRecordCount(),
            syncedAtEpoch = snapshot.syncedAtEpoch,
            syncedFromDeviceName = snapshot.syncedByDeviceName
        )
    }
}
