package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chart_of_accounts",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index(value = ["businessId", "code"], unique = true)]
)
data class ChartOfAccountEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val code: String,
    val name: String,
    val accountType: String, // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    val normalBalance: String, // DEBIT, CREDIT
    val isSystemAccount: Boolean = true,
    val isActive: Boolean = true,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "journal_entries",
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
        Index(value = ["businessId", "entryNumber"], unique = true),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val entryNumber: String,
    val entryDateEpoch: Long,
    val description: String,
    val sourceType: String, // RENT, PAYMENT, CHEQUE, DEPOSIT, PURCHASE, EXPENSE, INCOME, MAINTENANCE, MANUAL, REVERSAL
    val sourceId: String? = null,
    val idempotencyKey: String,
    val totalDebitFils: Long,
    val totalCreditFils: Long,
    val status: String = "POSTED", // POSTED, REVERSED
    val reversedEntryId: String? = null,
    val createdByUserId: String,
    val approvedByUserId: String? = null,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ChartOfAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("journalEntryId"), Index("accountId")]
)
data class JournalLineEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val journalEntryId: String,
    val accountId: String,
    val debitFils: Long,
    val creditFils: Long,
    val memo: String = ""
)

@Entity(
    tableName = "income_records",
    indices = [Index("businessId"), Index("propertyId")]
)
data class IncomeRecordEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val category: String,
    val description: String,
    val amountFils: Long,
    val accountId: String,
    val journalEntryId: String,
    val recordedAtEpoch: Long,
    val createdByUserId: String
)

@Entity(
    tableName = "expense_records",
    indices = [Index("businessId"), Index("propertyId")]
)
data class ExpenseRecordEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val category: String,
    val vendorName: String,
    val description: String,
    val amountFils: Long,
    val paymentMethod: String,
    val status: String = "POSTED", // PENDING_APPROVAL, POSTED, REVERSED
    val accountId: String,
    val journalEntryId: String? = null,
    val recordedAtEpoch: Long,
    val createdByUserId: String,
    val approvedByUserId: String? = null
)

@Entity(
    tableName = "bank_accounts",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId")]
)
data class BankAccountEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val bankName: String,
    val accountTitle: String,
    val ibanOrNumber: String,
    val currency: String = "QAR",
    val linkedGlAccountId: String,
    val currentBalanceFils: Long = 0L,
    val isActive: Boolean = true,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "cashbook_entries",
    indices = [Index("businessId"), Index("journalEntryId")]
)
data class CashbookEntryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val direction: String, // INFLOW, OUTFLOW
    val amountFils: Long,
    val description: String,
    val reference: String,
    val journalEntryId: String,
    val entryDateEpoch: Long,
    val createdByUserId: String
)

@Entity(
    tableName = "petty_cash_entries",
    indices = [Index("businessId"), Index("journalEntryId")]
)
data class PettyCashEntryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val custodianEmployeeId: String? = null,
    val direction: String, // REPLENISH, SPEND
    val amountFils: Long,
    val description: String,
    val receiptReference: String = "",
    val journalEntryId: String,
    val entryDateEpoch: Long,
    val createdByUserId: String
)

@Entity(
    tableName = "bank_transactions",
    foreignKeys = [
        ForeignKey(
            entity = BankAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("bankAccountId")]
)
data class BankTransactionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val bankAccountId: String,
    val direction: String, // CREDIT_IN, DEBIT_OUT
    val amountFils: Long,
    val bankReference: String,
    val description: String,
    val isReconciled: Boolean = false,
    val reconciliationId: String? = null,
    val journalEntryId: String? = null,
    val transactionDateEpoch: Long
)

@Entity(
    tableName = "bank_reconciliations",
    foreignKeys = [
        ForeignKey(
            entity = BankAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("bankAccountId")]
)
data class BankReconciliationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val bankAccountId: String,
    val statementDateEpoch: Long,
    val statementEndingBalanceFils: Long,
    val bookBalanceFils: Long,
    val differenceFils: Long,
    val status: String, // RECONCILED, DISCREPANCY
    val notes: String = "",
    val reconciledByUserId: String,
    val reconciledAtEpoch: Long
)

@Entity(
    tableName = "approval_requests",
    indices = [Index("businessId"), Index("status")]
)
data class ApprovalRequestEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val module: String, // EXPENSE, PURCHASE, JOB, DISCOUNT, REFUND, PERIOD_REOPEN
    val entityId: String,
    val title: String,
    val amountFils: Long = 0L,
    val requestedByUserId: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val reviewedByUserId: String? = null,
    val reviewNotes: String = "",
    val createdAtEpoch: Long,
    val reviewedAtEpoch: Long? = null
)

@Entity(
    tableName = "fiscal_periods",
    indices = [Index("businessId"), Index(value = ["businessId", "periodCode"], unique = true)]
)
data class FiscalPeriodEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val periodCode: String, // e.g. 2026-11
    val startDateEpoch: Long,
    val endDateEpoch: Long,
    val status: String = "OPEN", // OPEN, CLOSED, REOPENED
    val closedByUserId: String? = null,
    val closedAtEpoch: Long? = null,
    val reopenReason: String = ""
)
