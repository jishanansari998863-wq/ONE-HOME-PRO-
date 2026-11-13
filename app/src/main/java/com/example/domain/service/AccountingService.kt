package com.example.domain.service

import androidx.room.withTransaction
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.security.AuthContext
import com.example.domain.security.AuthorizationGuard
import com.example.domain.security.CryptoUtil
import com.example.domain.util.QatarFormatters

data class JournalLineInput(
    val accountCode: String,
    val debitFils: Long,
    val creditFils: Long,
    val memo: String = ""
)

data class TrialBalanceRow(
    val accountId: String,
    val code: String,
    val name: String,
    val accountType: String,
    val totalDebitFils: Long,
    val totalCreditFils: Long,
    val netBalanceFils: Long
)

data class ProfitAndLossReport(
    val incomeRows: List<TrialBalanceRow>,
    val expenseRows: List<TrialBalanceRow>,
    val totalIncomeFils: Long,
    val totalExpenseFils: Long,
    val netProfitFils: Long
)

data class BalanceSheetReport(
    val assetRows: List<TrialBalanceRow>,
    val liabilityRows: List<TrialBalanceRow>,
    val equityRows: List<TrialBalanceRow>,
    val totalAssetsFils: Long,
    val totalLiabilitiesFils: Long,
    val totalEquityPlusCurrentEarningsFils: Long,
    val isBalanced: Boolean
)

class AccountingService(private val db: OneHomeProDatabase) {
    private val dao = db.accountingDao()
    private val opsDao = db.operationsDao()

    companion object {
        const val ACC_CASH = "1000"
        const val ACC_BANK = "1010"
        const val ACC_CHEQUES_RECEIVABLE = "1020"
        const val ACC_RENT_RECEIVABLE = "1100"
        const val ACC_INVENTORY = "1200"
        const val ACC_PETTY_CASH = "1050"
        const val ACC_ACCOUNTS_PAYABLE = "2000"
        const val ACC_SECURITY_DEPOSITS_HELD = "2100"
        const val ACC_ADVANCE_RENT_UNEARNED = "2200"
        const val ACC_OWNER_EQUITY = "3000"
        const val ACC_RENTAL_INCOME = "4000"
        const val ACC_OTHER_INCOME = "4100"
        const val ACC_MAINTENANCE_EXPENSE = "5000"
        const val ACC_OPERATING_EXPENSE = "5100"
    }

    suspend fun seedDefaultChartOfAccounts(businessId: String, now: Long = System.currentTimeMillis()) {
        if (dao.getAccountsByBusiness(businessId).isNotEmpty()) return
        val defaults = listOf(
            Triple(ACC_CASH, "Cash on Hand", "ASSET" to "DEBIT"),
            Triple(ACC_BANK, "Bank Accounts", "ASSET" to "DEBIT"),
            Triple(ACC_CHEQUES_RECEIVABLE, "Cheques Receivable (Undeposited)", "ASSET" to "DEBIT"),
            Triple(ACC_PETTY_CASH, "Petty Cash", "ASSET" to "DEBIT"),
            Triple(ACC_RENT_RECEIVABLE, "Rent Receivable", "ASSET" to "DEBIT"),
            Triple(ACC_INVENTORY, "Maintenance & Spare Parts Inventory", "ASSET" to "DEBIT"),
            Triple(ACC_ACCOUNTS_PAYABLE, "Accounts Payable (Suppliers)", "LIABILITY" to "CREDIT"),
            Triple(ACC_SECURITY_DEPOSITS_HELD, "Security Deposits Held (Liability)", "LIABILITY" to "CREDIT"),
            Triple(ACC_ADVANCE_RENT_UNEARNED, "Advance Rent Unearned (Liability)", "LIABILITY" to "CREDIT"),
            Triple(ACC_OWNER_EQUITY, "Owner Capital & Retained Equity", "EQUITY" to "CREDIT"),
            Triple(ACC_RENTAL_INCOME, "Rental Income", "INCOME" to "CREDIT"),
            Triple(ACC_OTHER_INCOME, "Other Property & Deposit Fee Income", "INCOME" to "CREDIT"),
            Triple(ACC_MAINTENANCE_EXPENSE, "Maintenance & Material Expense", "EXPENSE" to "DEBIT"),
            Triple(ACC_OPERATING_EXPENSE, "General Operating Expense", "EXPENSE" to "DEBIT")
        ).map { (code, name, typePair) ->
            ChartOfAccountEntity(
                id = CryptoUtil.newUuid(),
                businessId = businessId,
                code = code,
                name = name,
                accountType = typePair.first,
                normalBalance = typePair.second,
                isSystemAccount = true,
                isActive = true,
                createdAtEpoch = now
            )
        }
        dao.insertAccounts(defaults)
    }

    suspend fun postJournalInternal(
        businessId: String,
        propertyId: String?,
        entryDateEpoch: Long,
        description: String,
        sourceType: String,
        sourceId: String?,
        idempotencyKey: String,
        createdByUserId: String,
        approvedByUserId: String? = null,
        lines: List<JournalLineInput>
    ): JournalEntryEntity {
        val existing = dao.getJournalByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing

        require(lines.size >= 2) { "Journal entry requires at least 2 lines" }
        var totalDebit = 0L
        var totalCredit = 0L
        lines.forEach {
            require(it.debitFils >= 0 && it.creditFils >= 0) { "Negative debit/credit forbidden" }
            require((it.debitFils > 0) xor (it.creditFils > 0)) { "Each line must have either debit or credit > 0" }
            totalDebit += it.debitFils
            totalCredit += it.creditFils
        }
        require(totalDebit == totalCredit && totalDebit > 0L) {
            "Unbalanced journal entry: debit=$totalDebit credit=$totalCredit"
        }

        val periodCode = QatarFormatters.periodKey(entryDateEpoch)
        val period = dao.getFiscalPeriodByCode(businessId, periodCode)
        if (period != null && period.status == "CLOSED") {
            throw IllegalStateException("Fiscal period $periodCode is CLOSED. Owner must reopen it before posting.")
        }

        val now = System.currentTimeMillis()
        val entryId = CryptoUtil.newUuid()
        val entryNumber = "JE-${periodCode.replace("-", "")}-${entryId.take(6).uppercase()}"
        val entry = JournalEntryEntity(
            id = entryId,
            businessId = businessId,
            propertyId = propertyId,
            entryNumber = entryNumber,
            entryDateEpoch = entryDateEpoch,
            description = description,
            sourceType = sourceType,
            sourceId = sourceId,
            idempotencyKey = idempotencyKey,
            totalDebitFils = totalDebit,
            totalCreditFils = totalCredit,
            status = "POSTED",
            createdByUserId = createdByUserId,
            approvedByUserId = approvedByUserId,
            createdAtEpoch = now
        )

        val accountEntities = lines.map { line ->
            val acc = dao.getAccountByCode(businessId, line.accountCode)
                ?: throw IllegalArgumentException("Unknown GL account code ${line.accountCode}")
            JournalLineEntity(
                id = CryptoUtil.newUuid(),
                businessId = businessId,
                journalEntryId = entryId,
                accountId = acc.id,
                debitFils = line.debitFils,
                creditFils = line.creditFils,
                memo = line.memo
            )
        }

        dao.insertJournalEntry(entry)
        dao.insertJournalLines(accountEntities)
        opsDao.insertAuditLog(
            AuditLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = businessId,
                actorUserId = createdByUserId,
                action = "POST_JOURNAL",
                entityType = "JOURNAL_ENTRY",
                entityId = entry.id,
                summary = "$entryNumber: $description (${QatarFormatters.formatQar(totalDebit)})",
                occurredAtEpoch = now
            )
        )
        return entry
    }

    suspend fun postManualJournal(
        ctx: AuthContext,
        description: String,
        propertyId: String?,
        lines: List<JournalLineInput>
    ): JournalEntryEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.POST, ctx.activeBusinessId, propertyId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                entryDateEpoch = now,
                description = description,
                sourceType = "MANUAL",
                sourceId = null,
                idempotencyKey = "MANUAL-${CryptoUtil.newUuid()}",
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId,
                lines = lines
            )
        }
    }

    suspend fun reverseJournalEntry(ctx: AuthContext, journalEntryId: String, reason: String): JournalEntryEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.REVERSE, ctx.activeBusinessId)
        require(reason.isNotBlank()) { "Reversal reason is required" }
        return db.withTransaction {
            val orig = dao.getJournalEntryById(journalEntryId)
                ?: throw IllegalArgumentException("Journal entry not found")
            require(orig.businessId == ctx.activeBusinessId) { "Cross-business reversal blocked" }
            require(orig.status != "REVERSED") { "Journal entry already reversed" }
            val origLines = dao.getLinesForJournal(orig.id)
            val accountsById = dao.getAccountsByBusiness(ctx.activeBusinessId).associateBy { it.id }
            val reversedLines = origLines.map { line ->
                val acc = accountsById[line.accountId] ?: error("Missing account")
                JournalLineInput(
                    accountCode = acc.code,
                    debitFils = line.creditFils,
                    creditFils = line.debitFils,
                    memo = "Reversal: ${line.memo}"
                )
            }
            val revEntry = postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = orig.propertyId,
                entryDateEpoch = System.currentTimeMillis(),
                description = "REVERSAL of ${orig.entryNumber}: $reason",
                sourceType = "REVERSAL",
                sourceId = orig.id,
                idempotencyKey = "REV-${orig.id}",
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId,
                lines = reversedLines
            )
            dao.updateJournalEntry(orig.copy(status = "REVERSED", reversedEntryId = revEntry.id))
            revEntry
        }
    }

    suspend fun createBankAccount(
        ctx: AuthContext,
        bankName: String,
        accountTitle: String,
        ibanOrNumber: String,
        openingBalanceFils: Long
    ): BankAccountEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.CREATE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val bankGl = dao.getAccountByCode(ctx.activeBusinessId, ACC_BANK)
                ?: error("Bank GL account missing")
            val account = BankAccountEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                bankName = bankName.trim(),
                accountTitle = accountTitle.trim(),
                ibanOrNumber = ibanOrNumber.trim(),
                linkedGlAccountId = bankGl.id,
                currentBalanceFils = openingBalanceFils,
                createdAtEpoch = now
            )
            dao.insertBankAccount(account)
            if (openingBalanceFils > 0L) {
                postJournalInternal(
                    businessId = ctx.activeBusinessId,
                    propertyId = null,
                    entryDateEpoch = now,
                    description = "Opening bank balance: $bankName ($accountTitle)",
                    sourceType = "MANUAL",
                    sourceId = account.id,
                    idempotencyKey = "OPEN-BANK-${account.id}",
                    createdByUserId = ctx.userId,
                    approvedByUserId = ctx.userId,
                    lines = listOf(
                        JournalLineInput(ACC_BANK, openingBalanceFils, 0L, "Opening bank balance"),
                        JournalLineInput(ACC_OWNER_EQUITY, 0L, openingBalanceFils, "Owner opening capital")
                    )
                )
            }
            account
        }
    }

    suspend fun recordIncome(
        ctx: AuthContext,
        propertyId: String?,
        category: String,
        description: String,
        amountFils: Long,
        receiveToBank: Boolean
    ): IncomeRecordEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.POST, ctx.activeBusinessId, propertyId)
        require(amountFils > 0) { "Amount must be positive" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val cashOrBankCode = if (receiveToBank) ACC_BANK else ACC_CASH
            val incomeAcc = dao.getAccountByCode(ctx.activeBusinessId, ACC_OTHER_INCOME)!!
            val journal = postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                entryDateEpoch = now,
                description = "Income ($category): $description",
                sourceType = "INCOME",
                sourceId = null,
                idempotencyKey = "INC-${CryptoUtil.newUuid()}",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(cashOrBankCode, amountFils, 0L, description),
                    JournalLineInput(ACC_OTHER_INCOME, 0L, amountFils, category)
                )
            )
            val rec = IncomeRecordEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                category = category,
                description = description,
                amountFils = amountFils,
                accountId = incomeAcc.id,
                journalEntryId = journal.id,
                recordedAtEpoch = now,
                createdByUserId = ctx.userId
            )
            dao.insertIncomeRecord(rec)
            if (!receiveToBank) {
                dao.insertCashbookEntry(
                    CashbookEntryEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        direction = "INFLOW",
                        amountFils = amountFils,
                        description = "Income: $description",
                        reference = journal.entryNumber,
                        journalEntryId = journal.id,
                        entryDateEpoch = now,
                        createdByUserId = ctx.userId
                    )
                )
            }
            rec
        }
    }

    suspend fun recordExpense(
        ctx: AuthContext,
        propertyId: String?,
        category: String,
        vendorName: String,
        description: String,
        amountFils: Long,
        paymentMethod: String
    ): ExpenseRecordEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.POST, ctx.activeBusinessId, propertyId)
        require(amountFils > 0) { "Amount must be positive" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val creditAccount = when (paymentMethod) {
                "BANK_TRANSFER", "CARD" -> ACC_BANK
                "PETTY_CASH" -> ACC_PETTY_CASH
                else -> ACC_CASH
            }
            val expAcc = dao.getAccountByCode(ctx.activeBusinessId, ACC_OPERATING_EXPENSE)!!
            val journal = postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                entryDateEpoch = now,
                description = "Expense ($category - $vendorName): $description",
                sourceType = "EXPENSE",
                sourceId = null,
                idempotencyKey = "EXP-${CryptoUtil.newUuid()}",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(ACC_OPERATING_EXPENSE, amountFils, 0L, description),
                    JournalLineInput(creditAccount, 0L, amountFils, paymentMethod)
                )
            )
            val rec = ExpenseRecordEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                category = category,
                vendorName = vendorName,
                description = description,
                amountFils = amountFils,
                paymentMethod = paymentMethod,
                status = "POSTED",
                accountId = expAcc.id,
                journalEntryId = journal.id,
                recordedAtEpoch = now,
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId
            )
            dao.insertExpenseRecord(rec)
            if (paymentMethod == "CASH") {
                dao.insertCashbookEntry(
                    CashbookEntryEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        direction = "OUTFLOW",
                        amountFils = amountFils,
                        description = "Expense: $vendorName - $description",
                        reference = journal.entryNumber,
                        journalEntryId = journal.id,
                        entryDateEpoch = now,
                        createdByUserId = ctx.userId
                    )
                )
            } else if (paymentMethod == "PETTY_CASH") {
                dao.insertPettyCashEntry(
                    PettyCashEntryEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        direction = "SPEND",
                        amountFils = amountFils,
                        description = "$vendorName: $description",
                        receiptReference = journal.entryNumber,
                        journalEntryId = journal.id,
                        entryDateEpoch = now,
                        createdByUserId = ctx.userId
                    )
                )
            }
            rec
        }
    }

    suspend fun replenishPettyCash(ctx: AuthContext, amountFils: Long, notes: String): PettyCashEntryEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.POST, ctx.activeBusinessId)
        require(amountFils > 0)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val journal = postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = null,
                entryDateEpoch = now,
                description = "Petty Cash Replenishment: $notes",
                sourceType = "MANUAL",
                sourceId = null,
                idempotencyKey = "PETTY-${CryptoUtil.newUuid()}",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(ACC_PETTY_CASH, amountFils, 0L, notes),
                    JournalLineInput(ACC_CASH, 0L, amountFils, "Transfer to petty cash")
                )
            )
            val entry = PettyCashEntryEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                direction = "REPLENISH",
                amountFils = amountFils,
                description = notes,
                receiptReference = journal.entryNumber,
                journalEntryId = journal.id,
                entryDateEpoch = now,
                createdByUserId = ctx.userId
            )
            dao.insertPettyCashEntry(entry)
            entry
        }
    }

    suspend fun reconcileBankAccount(
        ctx: AuthContext,
        bankAccountId: String,
        statementEndingBalanceFils: Long,
        notes: String
    ): BankReconciliationEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.ACCOUNTING, ActionType.APPROVE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val bank = dao.getBankAccountById(bankAccountId)
                ?: throw IllegalArgumentException("Bank account not found")
            require(bank.businessId == ctx.activeBusinessId) { "Cross-business access blocked" }
            val tb = computeTrialBalance(ctx.activeBusinessId)
            val bankGlRow = tb.firstOrNull { it.code == ACC_BANK }
            val bookBalanceFils = bankGlRow?.netBalanceFils ?: bank.currentBalanceFils
            val diff = statementEndingBalanceFils - bookBalanceFils
            val rec = BankReconciliationEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                bankAccountId = bankAccountId,
                statementDateEpoch = now,
                statementEndingBalanceFils = statementEndingBalanceFils,
                bookBalanceFils = bookBalanceFils,
                differenceFils = diff,
                status = if (diff == 0L) "RECONCILED" else "DISCREPANCY",
                notes = notes,
                reconciledByUserId = ctx.userId,
                reconciledAtEpoch = now
            )
            dao.insertBankReconciliation(rec)
            rec
        }
    }

    suspend fun closeFiscalPeriod(ctx: AuthContext, periodCode: String): FiscalPeriodEntity {
        AuthorizationGuard.requireOwner(ctx)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = dao.getFiscalPeriodByCode(ctx.activeBusinessId, periodCode)
            if (existing != null) {
                val updated = existing.copy(status = "CLOSED", closedByUserId = ctx.userId, closedAtEpoch = now)
                dao.updateFiscalPeriod(updated)
                updated
            } else {
                val created = FiscalPeriodEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    periodCode = periodCode,
                    startDateEpoch = now,
                    endDateEpoch = now,
                    status = "CLOSED",
                    closedByUserId = ctx.userId,
                    closedAtEpoch = now
                )
                dao.insertFiscalPeriod(created)
                created
            }
        }
    }

    suspend fun reopenFiscalPeriod(ctx: AuthContext, periodCode: String, reason: String): FiscalPeriodEntity {
        AuthorizationGuard.requireOwner(ctx)
        require(reason.isNotBlank()) { "Reopen reason required" }
        return db.withTransaction {
            val existing = dao.getFiscalPeriodByCode(ctx.activeBusinessId, periodCode)
                ?: throw IllegalArgumentException("Period not found")
            val updated = existing.copy(status = "REOPENED", reopenReason = reason)
            dao.updateFiscalPeriod(updated)
            opsDao.insertAuditLog(
                AuditLogEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    actorUserId = ctx.userId,
                    action = "REOPEN_FISCAL_PERIOD",
                    entityType = "FISCAL_PERIOD",
                    entityId = updated.id,
                    summary = "Reopened $periodCode: $reason",
                    occurredAtEpoch = System.currentTimeMillis()
                )
            )
            updated
        }
    }

    suspend fun computeTrialBalance(businessId: String): List<TrialBalanceRow> {
        val accounts = dao.getAccountsByBusiness(businessId)
        val lines = dao.getJournalLinesByBusiness(businessId)
        val grouped = lines.groupBy { it.accountId }
        return accounts.map { acc ->
            val accLines = grouped[acc.id].orEmpty()
            val debit = accLines.sumOf { it.debitFils }
            val credit = accLines.sumOf { it.creditFils }
            val net = if (acc.normalBalance == "DEBIT") debit - credit else credit - debit
            TrialBalanceRow(
                accountId = acc.id,
                code = acc.code,
                name = acc.name,
                accountType = acc.accountType,
                totalDebitFils = debit,
                totalCreditFils = credit,
                netBalanceFils = net
            )
        }
    }

    suspend fun computeProfitAndLoss(businessId: String): ProfitAndLossReport {
        val tb = computeTrialBalance(businessId)
        val income = tb.filter { it.accountType == "INCOME" }
        val expense = tb.filter { it.accountType == "EXPENSE" }
        val totalIncome = income.sumOf { it.netBalanceFils }
        val totalExpense = expense.sumOf { it.netBalanceFils }
        return ProfitAndLossReport(
            incomeRows = income,
            expenseRows = expense,
            totalIncomeFils = totalIncome,
            totalExpenseFils = totalExpense,
            netProfitFils = totalIncome - totalExpense
        )
    }

    suspend fun computeBalanceSheet(businessId: String): BalanceSheetReport {
        val tb = computeTrialBalance(businessId)
        val assets = tb.filter { it.accountType == "ASSET" }
        val liabilities = tb.filter { it.accountType == "LIABILITY" }
        val equity = tb.filter { it.accountType == "EQUITY" }
        val pnl = computeProfitAndLoss(businessId)
        val totalAssets = assets.sumOf { it.netBalanceFils }
        val totalLiabilities = liabilities.sumOf { it.netBalanceFils }
        val totalEquityPlusEarnings = equity.sumOf { it.netBalanceFils } + pnl.netProfitFils
        return BalanceSheetReport(
            assetRows = assets,
            liabilityRows = liabilities,
            equityRows = equity,
            totalAssetsFils = totalAssets,
            totalLiabilitiesFils = totalLiabilities,
            totalEquityPlusCurrentEarningsFils = totalEquityPlusEarnings,
            isBalanced = totalAssets == (totalLiabilities + totalEquityPlusEarnings)
        )
    }
}
