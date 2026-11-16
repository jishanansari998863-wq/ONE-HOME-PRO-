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
    val fiscalPeriods: List<FiscalPeriodEntity> = emptyList(),
    val departments: List<DepartmentEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val teams: List<TeamEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val complaints: List<ComplaintEntity> = emptyList(),
    val jobAssignments: List<JobAssignmentEntity> = emptyList(),
    val workReports: List<WorkReportEntity> = emptyList(),
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
    val notifications: List<NotificationEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val integrations: List<IntegrationConfigEntity> = emptyList(),
    val appLanguageCode: String = "en",
    val darkTheme: Boolean = false
) {
    fun totalRecordCount(): Int =
        (if (business != null) 1 else 0) +
            properties.size + buildings.size + floors.size + rooms.size +
            reservations.size + inspections.size + customers.size + familyMembers.size +
            agreements.size + tenancyHistory.size + monthlyRents.size + payments.size +
            paymentAllocations.size + receipts.size + securityDeposits.size +
            depositTransactions.size + cheques.size + advanceRents.size +
            accounts.size + journals.size + journalLines.size +
            incomeRecords.size + expenseRecords.size + bankAccounts.size +
            cashbookEntries.size + pettyCashEntries.size + bankTransactions.size +
            bankReconciliations.size + fiscalPeriods.size +
            departments.size + employees.size + teams.size + attendance.size +
            complaints.size + jobAssignments.size + workReports.size + materialUsage.size +
            suppliers.size + itemCategories.size + items.size + stockLocations.size +
            purchases.size + purchaseItems.size + stockMovements.size + supplierPayments.size +
            documents.size + notifications.size + auditLogs.size + integrations.size
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
            fiscalPeriods = accDao.getFiscalPeriodsByBusiness(bizId),
            departments = hrDao.getDepartmentsByBusiness(bizId),
            employees = hrDao.getAllEmployeesRaw(bizId),
            teams = hrDao.getTeamsByBusiness(bizId),
            attendance = hrDao.getAttendanceByBusiness(bizId),
            complaints = opsDao.getComplaintsByBusiness(bizId),
            jobAssignments = opsDao.getJobAssignmentsByBusiness(bizId),
            workReports = opsDao.getWorkReportsByBusiness(bizId),
            materialUsage = opsDao.getMaterialUsageByBusiness(bizId),
            suppliers = opsDao.getSuppliersByBusiness(bizId),
            itemCategories = opsDao.getItemCategoriesByBusiness(bizId),
            items = opsDao.getItemsByBusiness(bizId),
            stockLocations = opsDao.getStockLocationsByBusiness(bizId),
            purchases = opsDao.getPurchasesByBusiness(bizId),
            purchaseItems = opsDao.getPurchaseItemsByBusiness(bizId),
            stockMovements = opsDao.getStockMovementsByBusiness(bizId),
            supplierPayments = opsDao.getSupplierPaymentsByBusiness(bizId),
            documents = opsDao.getDocumentsByBusiness(bizId),
            notifications = opsDao.getNotificationsByBusiness(bizId),
            auditLogs = opsDao.getAuditLogsByBusiness(bizId),
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
            if (payload.fiscalPeriods.isNotEmpty()) accDao.upsertFiscalPeriods(payload.fiscalPeriods)

            if (payload.departments.isNotEmpty()) hrDao.upsertDepartments(payload.departments)
            if (payload.employees.isNotEmpty()) hrDao.upsertEmployees(payload.employees)
            if (payload.teams.isNotEmpty()) hrDao.upsertTeams(payload.teams)
            if (payload.attendance.isNotEmpty()) hrDao.upsertAttendance(payload.attendance)

            if (payload.suppliers.isNotEmpty()) opsDao.upsertSuppliers(payload.suppliers)
            if (payload.itemCategories.isNotEmpty()) opsDao.upsertItemCategories(payload.itemCategories)
            if (payload.stockLocations.isNotEmpty()) opsDao.upsertStockLocations(payload.stockLocations)
            if (payload.items.isNotEmpty()) opsDao.upsertItems(payload.items)
            if (payload.purchases.isNotEmpty()) opsDao.upsertPurchases(payload.purchases)
            if (payload.purchaseItems.isNotEmpty()) opsDao.upsertPurchaseItems(payload.purchaseItems)
            if (payload.stockMovements.isNotEmpty()) opsDao.upsertStockMovements(payload.stockMovements)
            if (payload.complaints.isNotEmpty()) opsDao.upsertComplaints(payload.complaints)
            if (payload.jobAssignments.isNotEmpty()) opsDao.upsertJobAssignments(payload.jobAssignments)
            if (payload.workReports.isNotEmpty()) opsDao.upsertWorkReports(payload.workReports)
            if (payload.materialUsage.isNotEmpty()) opsDao.upsertMaterialUsage(payload.materialUsage)
            if (payload.supplierPayments.isNotEmpty()) opsDao.upsertSupplierPayments(payload.supplierPayments)
            if (payload.documents.isNotEmpty()) opsDao.upsertDocuments(payload.documents)
            if (payload.notifications.isNotEmpty()) opsDao.upsertNotifications(payload.notifications)
            if (payload.auditLogs.isNotEmpty()) opsDao.upsertAuditLogs(payload.auditLogs)
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
