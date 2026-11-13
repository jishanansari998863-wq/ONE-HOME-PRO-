package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    @Query("SELECT * FROM chart_of_accounts WHERE businessId = :businessId ORDER BY code ASC")
    fun observeChartOfAccounts(businessId: String): Flow<List<ChartOfAccountEntity>>

    @Query("SELECT * FROM chart_of_accounts WHERE businessId = :businessId ORDER BY code ASC")
    suspend fun getAccountsByBusiness(businessId: String): List<ChartOfAccountEntity>

    @Query("SELECT * FROM chart_of_accounts WHERE businessId = :businessId AND code = :code LIMIT 1")
    suspend fun getAccountByCode(businessId: String, code: String): ChartOfAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(entity: ChartOfAccountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccounts(entities: List<ChartOfAccountEntity>)

    @Query("SELECT * FROM journal_entries WHERE businessId = :businessId ORDER BY entryDateEpoch DESC, createdAtEpoch DESC")
    fun observeJournalEntries(businessId: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE businessId = :businessId")
    suspend fun getJournalEntriesByBusiness(businessId: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalEntryById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getJournalByIdempotencyKey(key: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalEntry(entity: JournalEntryEntity)

    @Update
    suspend fun updateJournalEntry(entity: JournalEntryEntity)

    @Query("SELECT * FROM journal_lines WHERE businessId = :businessId")
    fun observeJournalLines(businessId: String): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_lines WHERE businessId = :businessId")
    suspend fun getJournalLinesByBusiness(businessId: String): List<JournalLineEntity>

    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :entryId")
    suspend fun getLinesForJournal(entryId: String): List<JournalLineEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalLines(lines: List<JournalLineEntity>)

    @Query("SELECT * FROM income_records WHERE businessId = :businessId ORDER BY recordedAtEpoch DESC")
    fun observeIncomeRecords(businessId: String): Flow<List<IncomeRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIncomeRecord(entity: IncomeRecordEntity)

    @Query("SELECT * FROM expense_records WHERE businessId = :businessId ORDER BY recordedAtEpoch DESC")
    fun observeExpenseRecords(businessId: String): Flow<List<ExpenseRecordEntity>>

    @Query("SELECT * FROM expense_records WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpenseRecord(entity: ExpenseRecordEntity)

    @Update
    suspend fun updateExpenseRecord(entity: ExpenseRecordEntity)

    @Query("SELECT * FROM bank_accounts WHERE businessId = :businessId ORDER BY createdAtEpoch ASC")
    fun observeBankAccounts(businessId: String): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE businessId = :businessId")
    suspend fun getBankAccountsByBusiness(businessId: String): List<BankAccountEntity>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getBankAccountById(id: String): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankAccount(entity: BankAccountEntity)

    @Update
    suspend fun updateBankAccount(entity: BankAccountEntity)

    @Query("SELECT * FROM cashbook_entries WHERE businessId = :businessId ORDER BY entryDateEpoch DESC")
    fun observeCashbookEntries(businessId: String): Flow<List<CashbookEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashbookEntry(entity: CashbookEntryEntity)

    @Query("SELECT * FROM petty_cash_entries WHERE businessId = :businessId ORDER BY entryDateEpoch DESC")
    fun observePettyCashEntries(businessId: String): Flow<List<PettyCashEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPettyCashEntry(entity: PettyCashEntryEntity)

    @Query("SELECT * FROM bank_transactions WHERE businessId = :businessId ORDER BY transactionDateEpoch DESC")
    fun observeBankTransactions(businessId: String): Flow<List<BankTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankTransaction(entity: BankTransactionEntity)

    @Update
    suspend fun updateBankTransaction(entity: BankTransactionEntity)

    @Query("SELECT * FROM bank_reconciliations WHERE businessId = :businessId ORDER BY reconciledAtEpoch DESC")
    fun observeBankReconciliations(businessId: String): Flow<List<BankReconciliationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankReconciliation(entity: BankReconciliationEntity)

    @Query("SELECT * FROM approval_requests WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeApprovalRequests(businessId: String): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT * FROM approval_requests WHERE id = :id")
    suspend fun getApprovalRequestById(id: String): ApprovalRequestEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApprovalRequest(entity: ApprovalRequestEntity)

    @Update
    suspend fun updateApprovalRequest(entity: ApprovalRequestEntity)

    @Query("SELECT * FROM fiscal_periods WHERE businessId = :businessId ORDER BY periodCode DESC")
    fun observeFiscalPeriods(businessId: String): Flow<List<FiscalPeriodEntity>>

    @Query("SELECT * FROM fiscal_periods WHERE businessId = :businessId AND periodCode = :periodCode LIMIT 1")
    suspend fun getFiscalPeriodByCode(businessId: String, periodCode: String): FiscalPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFiscalPeriod(entity: FiscalPeriodEntity)

    @Update
    suspend fun updateFiscalPeriod(entity: FiscalPeriodEntity)
}
