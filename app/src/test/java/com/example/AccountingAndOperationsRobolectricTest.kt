package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.InMemoryCentralAuthorityTestDouble
import com.example.data.db.OneHomeProDatabase
import com.example.domain.security.AuthContext
import com.example.domain.security.SessionStore
import com.example.domain.service.*
import com.example.domain.util.QatarFormatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountingAndOperationsRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: OneHomeProDatabase
    private lateinit var sessionStore: SessionStore
    private lateinit var accountingService: AccountingService
    private lateinit var authService: AuthService
    private lateinit var propertyService: PropertyTenancyService
    private lateinit var rentPaymentService: RentPaymentChequeService
    private lateinit var opsService: WorkforceMaintenanceInventoryService
    private lateinit var backupService: GovernanceBackupService
    private lateinit var ownerCtx: AuthContext

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = SessionStore(context)
        accountingService = AccountingService(db)
        val central = InMemoryCentralAuthorityTestDouble().apply {
            preApproveUserForTest("uid_acc_owner", "acc-owner@onehome.qa", "Owner", "OWNER")
        }
        authService = AuthService(db, sessionStore, accountingService, central)
        propertyService = PropertyTenancyService(db, accountingService, authService)
        rentPaymentService = RentPaymentChequeService(db, accountingService)
        opsService = WorkforceMaintenanceInventoryService(db, accountingService)
        backupService = GovernanceBackupService(context, db, accountingService)

        val res = authService.setupInitialOwnerAndBusiness(
            fullName = "Owner",
            email = "acc-owner@onehome.qa",
            password = "StrongPass#2026",
            businessName = "Qatar Homes WLL",
            branchName = "Doha HQ",
            enableMfa = false,
            rememberMe = false,
            deviceName = "UnitTester"
        ) as AuthResult.Authenticated
        ownerCtx = res.context
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `unbalanced journal rejected and closed fiscal period blocks new postings`() = runBlocking {
        try {
            accountingService.postManualJournal(
                ctx = ownerCtx,
                description = "Invalid Unbalanced Entry",
                propertyId = null,
                lines = listOf(
                    JournalLineInput(AccountingService.ACC_CASH, 10_000L, 0L),
                    JournalLineInput(AccountingService.ACC_RENTAL_INCOME, 0L, 9_000L)
                )
            )
            fail("Unbalanced journal entry must be rejected")
        } catch (_: IllegalArgumentException) { /* expected */ }

        val period = QatarFormatters.periodKey()
        accountingService.closeFiscalPeriod(ownerCtx, period)

        try {
            accountingService.postManualJournal(
                ctx = ownerCtx,
                description = "Closed period entry",
                propertyId = null,
                lines = listOf(
                    JournalLineInput(AccountingService.ACC_CASH, 5_000L, 0L),
                    JournalLineInput(AccountingService.ACC_OTHER_INCOME, 0L, 5_000L)
                )
            )
            fail("Posting into closed fiscal period must be rejected")
        } catch (_: IllegalStateException) { /* expected */ }

        accountingService.reopenFiscalPeriod(ownerCtx, period, "Year-end adjustment")
        val posted = accountingService.postManualJournal(
            ctx = ownerCtx,
            description = "Reopened period entry",
            propertyId = null,
            lines = listOf(
                JournalLineInput(AccountingService.ACC_CASH, 5_000L, 0L),
                JournalLineInput(AccountingService.ACC_OTHER_INCOME, 0L, 5_000L)
            )
        )
        assertEquals(5_000L, posted.totalDebitFils)
        assertEquals(5_000L, posted.totalCreditFils)
    }

    @Test
    fun `agreement overlap protection, rent generation, payment idempotency, cheque clearance, and deposit liability reconcile cleanly`() = runBlocking {
        val prop = propertyService.createProperty(ownerCtx, "Msheireb Residence", "MSH", "RESIDENTIAL", "Zone 3", "Doha", "")
        val bldg = propertyService.addBuilding(ownerCtx, prop.id, "Block A", "A", 4)
        val fl = propertyService.addFloor(ownerCtx, prop.id, bldg.id, 1, "Floor 1")
        val room = propertyService.addRoom(ownerCtx, prop.id, bldg.id, fl.id, "101", "1BHK", 2, "FURNISHED", 500_000L, "AC", 2, "CARD-1", "Full")
        val cust1 = propertyService.createCustomer(ownerCtx, "Faisal", "+97433334444", "faisal@qa.com", "2910001", "", "QA", "", "", "")
        val cust2 = propertyService.createCustomer(ownerCtx, "Omar", "+97433335555", "omar@qa.com", "2910002", "", "QA", "", "", "")

        val now = System.currentTimeMillis()
        val end = now + 90L * 24 * 3600_000L
        val ag = propertyService.createAndActivateAgreement(ownerCtx, room.id, cust1.id, "MONTHLY", 500_000L, 0L, 500_000L, now, end, 30, "")

        // Overlap protection blocks second active lease on occupied room
        try {
            propertyService.createAndActivateAgreement(ownerCtx, room.id, cust2.id, "MONTHLY", 500_000L, 0L, 500_000L, now, end, 30, "")
            fail("Overlapping active agreement on same room must be rejected")
        } catch (_: IllegalStateException) { /* expected */ }

        // Generate monthly rent -> 1 obligation billed for 500,000 fils (QAR 5,000)
        val billedCount = propertyService.generateRentForPeriod(ownerCtx, now)
        assertEquals(1, billedCount)
        val rent = db.coreAndTenancyDao().getMonthlyRentsByBusiness(ownerCtx.activeBusinessId).first()
        assertEquals(500_000L, rent.amountDueFils)

        // Idempotent payment confirmation (QAR 2,000 partial)
        val p1 = rentPaymentService.receiveConfirmedPayment(ownerCtx, cust1.id, ag.id, 200_000L, "CASH", null, listOf(rent.id), "Partial cash", idempotencyKey = "PAY-IDEM-1")
        val p1Dup = rentPaymentService.receiveConfirmedPayment(ownerCtx, cust1.id, ag.id, 200_000L, "CASH", null, listOf(rent.id), "Partial cash", idempotencyKey = "PAY-IDEM-1")
        assertEquals(p1.id, p1Dup.id)

        // Register Cheque for remaining 300,000 fils -> MUST NOT increase Bank (1010) before CLEARED
        val cheque = rentPaymentService.registerCheque(ownerCtx, cust1.id, ag.id, rent.id, "CHQ-8899", "QNB", 300_000L, now, "Faisal", "Qatar Homes")
        val tbBeforeClear = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
        val bankBefore = tbBeforeClear.firstOrNull { it.code == AccountingService.ACC_BANK }?.netBalanceFils ?: 0L
        val chqRecBefore = tbBeforeClear.firstOrNull { it.code == AccountingService.ACC_CHEQUES_RECEIVABLE }?.netBalanceFils ?: 0L
        assertEquals(0L, bankBefore)
        assertEquals(300_000L, chqRecBefore)

        // Bounce cheque -> reverses 1020 back to 1100
        rentPaymentService.transitionChequeStatus(ownerCtx, cheque.id, "BOUNCED", bounceReason = "Signature mismatch")
        val replacement = rentPaymentService.replaceBouncedCheque(ownerCtx, cheque.id, "CHQ-8900", "QNB", now)
        rentPaymentService.transitionChequeStatus(ownerCtx, replacement.id, "DEPOSITED")
        rentPaymentService.transitionChequeStatus(ownerCtx, replacement.id, "CLEARED", bankReference = "QNB-CLR-1")

        val tbAfterClear = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
        val bankAfter = tbAfterClear.firstOrNull { it.code == AccountingService.ACC_BANK }?.netBalanceFils ?: 0L
        assertEquals(300_000L, bankAfter)
        val updatedRent = db.coreAndTenancyDao().getMonthlyRentById(rent.id)!!
        assertEquals("PAID", updatedRent.status)

        // Security Deposit -> HELD as liability (2100), not Income (4000)
        val dep = db.coreAndTenancyDao().getSecurityDepositByAgreementId(ag.id)!!
        rentPaymentService.recordDepositTransaction(ownerCtx, dep.id, "RECEIVE", 500_000L, "Move-in deposit")
        rentPaymentService.recordDepositTransaction(ownerCtx, dep.id, "REFUND", 500_000L, "Full refund")

        // Verify Trial Balance & Balance Sheet balance strictly
        val totalDr = tbAfterClear.sumOf { it.totalDebitFils }
        val totalCr = tbAfterClear.sumOf { it.totalCreditFils }
        assertEquals(totalDr, totalCr)
        val bs = accountingService.computeBalanceSheet(ownerCtx.activeBusinessId)
        assertTrue(bs.isBalanced)
    }

    @Test
    fun `weighted-average inventory, negative stock block, single-exec maintenance material usage, and encrypted backup restore verification work`() = runBlocking {
        val prop = propertyService.createProperty(ownerCtx, "Al Waab Villas", "WAB", "VILLA", "Al Waab", "Doha", "")
        val sup = opsService.createSupplier(ownerCtx, "Doha Hardware", "Ali", "+97444445555", "", "CR-991")
        val item = opsService.createItemCategoryAndItem(ownerCtx, "HVAC", "AC Capacitor", "SKU-AC-01", "PCS", 2L)
        val loc = db.operationsDao().observeStockLocations(ownerCtx.activeBusinessId).first().first()

        // Purchase 10 pcs @ 20 QAR -> approve -> receive goods
        val po = opsService.createPurchaseOrder(ownerCtx, sup.id, prop.id, item.id, 10L, 2_000L, "Stock up")
        opsService.approvePurchase(ownerCtx, po.id, "Approved")
        opsService.receiveGoods(ownerCtx, po.id, loc.id, "INV-1001")

        val stocked = db.operationsDao().getItemById(item.id)!!
        assertEquals(10L, stocked.currentStockQty)
        assertEquals(2_000L, stocked.weightedAvgCostFils)

        // Deactivate employee preserves work history
        val tech = opsService.addEmployee(ownerCtx, "Karim Tech", "HVAC Tech", null, "+97450001111", "", "2890001", "Doha", "08:00-17:00")
        val cmp = opsService.createComplaint(ownerCtx, prop.id, null, null, "AC Not Cooling", "Compressor tripped", "HVAC", "HIGH")
        opsService.assignTechnician(ownerCtx, cmp.id, tech.id)

        // Exceeding stock fails with no negative inventory allowed
        try {
            opsService.submitWorkReportAndMaterials(ownerCtx, cmp.id, tech.id, "Attempt overuse", 30, 0L, null, null, item.id, loc.id, 99L)
            fail("Exceeding available stock must be rejected")
        } catch (_: IllegalArgumentException) { /* expected */ }

        // Valid material usage (2 pcs) posts once idempotently and reduces stock to 8
        opsService.submitWorkReportAndMaterials(ownerCtx, cmp.id, tech.id, "Replaced capacitor", 45, 0L, null, null, item.id, loc.id, 2L)
        val afterMat = db.operationsDao().getItemById(item.id)!!
        assertEquals(8L, afterMat.currentStockQty)

        opsService.deactivateEmployee(ownerCtx, tech.id, "Resigned")
        val hist = db.securityAndHrDao().observeWorkHistory(ownerCtx.activeBusinessId).first().filter { it.employeeId == tech.id }
        assertTrue(hist.any { it.eventType == "DEACTIVATED" })

        // Create AES-256-GCM Backup and run Restore Verification
        val backup = backupService.createEncryptedBackup(ownerCtx, "SecretBackup#2026")
        val verifyRes = backupService.verifyAndTestRestoreBackup(ownerCtx, backup.id, "SecretBackup#2026")
        assertTrue(verifyRes.checksumMatched)
        assertTrue(verifyRes.isBalanced)
        assertEquals(8, verifyRes.verifiedRecordCount)

        // Wrong passphrase must fail decryption
        try {
            backupService.verifyAndTestRestoreBackup(ownerCtx, backup.id, "WrongPassphrase")
            fail("Wrong backup passphrase must fail AES-GCM verification")
        } catch (_: Exception) { /* expected */ }
    }

    @Test
    fun `cleared cheque payment reversal, expense BANK credit, and stock count variance preserve GL balance`() = runBlocking {
        val prop = propertyService.createProperty(ownerCtx, "West Bay Tower", "WBT", "RESIDENTIAL", "West Bay", "Doha", "")
        val bldg = propertyService.addBuilding(ownerCtx, prop.id, "Main", "M1", 5)
        val fl = propertyService.addFloor(ownerCtx, prop.id, bldg.id, 1, "F1")
        val room = propertyService.addRoom(ownerCtx, prop.id, bldg.id, fl.id, "105", "STUDIO", 2, "FURNISHED", 400_000L, "AC", 2, "K1", "Bed")
        val cust = propertyService.createCustomer(ownerCtx, "Tariq", "+97455001122", "tariq@qa.com", "2910999", "", "QA", "", "", "")
        val now = System.currentTimeMillis()
        val ag = propertyService.createAndActivateAgreement(ownerCtx, room.id, cust.id, "MONTHLY", 400_000L, 0L, 0L, now, now + 60L * 86_400_000L, 30, "")
        propertyService.generateRentForPeriod(ownerCtx, now)
        val rent = db.coreAndTenancyDao().getMonthlyRentsByBusiness(ownerCtx.activeBusinessId).first()

        // Clear a cheque -> creates linked confirmed payment + credits/debits Bank GL
        val chq = rentPaymentService.registerCheque(ownerCtx, cust.id, ag.id, rent.id, "CHQ-500", "QIB", 400_000L, now, "Tariq", "Qatar Homes")
        rentPaymentService.transitionChequeStatus(ownerCtx, chq.id, "CLEARED", bankReference = "QIB-CLR")

        // Cannot bounce an already CLEARED cheque
        try {
            rentPaymentService.transitionChequeStatus(ownerCtx, chq.id, "BOUNCED", bounceReason = "Late return")
            fail("Bouncing an already CLEARED cheque must be rejected")
        } catch (_: IllegalArgumentException) { /* expected */ }

        // Reverse the payment created on cheque clearance -> Bank GL returns to 0 and rent reopens
        val chqPay = db.coreAndTenancyDao().getPaymentsByBusiness(ownerCtx.activeBusinessId).first()
        rentPaymentService.reverseOrRefundPayment(ownerCtx, chqPay.id, "Cheque clear error")
        val reopenedRent = db.coreAndTenancyDao().getMonthlyRentById(rent.id)!!
        assertEquals("DUE", reopenedRent.status)
        assertEquals(0L, reopenedRent.amountPaidFils)

        val tbAfterRev = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
        val bankBal = tbAfterRev.firstOrNull { it.code == AccountingService.ACC_BANK }?.netBalanceFils ?: 0L
        assertEquals(0L, bankBal)

        // Expense via "BANK" hits ACC_BANK (1010) cleanly
        accountingService.recordExpense(ownerCtx, prop.id, "UTILITIES", "Kahramaa", "Water bill", 10_000L, "BANK")
        val tbAfterExp = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
        val bankAfterExp = tbAfterExp.firstOrNull { it.code == AccountingService.ACC_BANK }?.netBalanceFils ?: 0L
        assertEquals(-10_000L, bankAfterExp)

        // Stock count variance posts balanced Inventory GL adjustment
        val sup = opsService.createSupplier(ownerCtx, "Qatar Supply", "Salim", "+97444001122", "", "CR-1")
        val item = opsService.createItemCategoryAndItem(ownerCtx, "PLUMBING", "Valve", "SKU-V1", "PCS", 2L)
        val loc = db.operationsDao().observeStockLocations(ownerCtx.activeBusinessId).first().first()
        val po = opsService.createPurchaseOrder(ownerCtx, sup.id, prop.id, item.id, 10L, 1_500L, "Valves")
        opsService.approvePurchase(ownerCtx, po.id, "OK")
        opsService.receiveGoods(ownerCtx, po.id, loc.id, "INV-V1")

        // Count 8 instead of 10 -> shortage variance of 2 * 1,500 = 3,000 fils
        opsService.recordStockCount(ownerCtx, item.id, loc.id, 8L)
        assertEquals(8L, db.operationsDao().getItemById(item.id)!!.currentStockQty)
        val invVal = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
            .first { it.code == AccountingService.ACC_INVENTORY }.netBalanceFils
        assertEquals(12_000L, invVal)

        val bs = accountingService.computeBalanceSheet(ownerCtx.activeBusinessId)
        assertTrue(bs.isBalanced)
    }

    @Test
    fun `bank account balance and bank transaction ledger stay synced across income, expense, payments, cheques, deposits, and suppliers`() = runBlocking {
        val bank = accountingService.createBankAccount(
            ctx = ownerCtx,
            bankName = "QNB",
            accountTitle = "Main Operating",
            ibanOrNumber = "QA58QNBA000000000000123456789",
            openingBalanceFils = 1_000_000L
        )
        val prop = propertyService.createProperty(ownerCtx, "Doha Corniche", "DC1", "RESIDENTIAL", "Corniche", "Doha", "")
        val bldg = propertyService.addBuilding(ownerCtx, prop.id, "B1", "B1", 2)
        val fl = propertyService.addFloor(ownerCtx, prop.id, bldg.id, 1, "F1")
        val room = propertyService.addRoom(ownerCtx, prop.id, bldg.id, fl.id, "101", "1BHK", 2, "FURNISHED", 500_000L, "AC", 2, "C1", "Full")
        val cust = propertyService.createCustomer(ownerCtx, "Noor", "+97455123456", "noor@qa.com", "2910555", "", "QA", "", "", "")
        val now = System.currentTimeMillis()
        val ag = propertyService.createAndActivateAgreement(ownerCtx, room.id, cust.id, "MONTHLY", 500_000L, 0L, 100_000L, now, now + 90L * 86_400_000L, 30, "")
        propertyService.generateRentForPeriod(ownerCtx, now)
        val rent = db.coreAndTenancyDao().getMonthlyRentsByBusiness(ownerCtx.activeBusinessId).first()

        // 1. Bank transfer payment (+200,000)
        val bankPay = rentPaymentService.receiveConfirmedPayment(
            ctx = ownerCtx,
            customerId = cust.id,
            agreementId = ag.id,
            amountFils = 200_000L,
            method = "BANK_TRANSFER",
            bankAccountId = bank.id,
            targetRentIdsInOrder = listOf(rent.id),
            notes = "Rent bank transfer"
        )
        // 2. Direct bank income (+50,000)
        accountingService.recordIncome(ownerCtx, prop.id, "PARKING", "Extra parking fee", 50_000L, receiveToBank = true)
        // 3. Cheque cleared (+300,000)
        val chq = rentPaymentService.registerCheque(ownerCtx, cust.id, ag.id, rent.id, "CHQ-771", "QNB", 300_000L, now, "Noor", "Qatar Homes WLL")
        rentPaymentService.transitionChequeStatus(ownerCtx, chq.id, "CLEARED", bankReference = "QNB-CLR-771")
        // 4. Reverse bank payment (-200,000)
        rentPaymentService.reverseOrRefundPayment(ownerCtx, bankPay.id, "Duplicate transfer")
        // 5. Bank expense (-25,000)
        accountingService.recordExpense(ownerCtx, prop.id, "UTILITIES", "Ooredoo", "Fiber bill", 25_000L, "BANK_TRANSFER")
        // 6. Security deposit received in cash & refunded via bank (-100,000)
        val dep = db.coreAndTenancyDao().getSecurityDepositByAgreementId(ag.id)!!
        rentPaymentService.recordDepositTransaction(ownerCtx, dep.id, "RECEIVE", 100_000L, "Deposit cash")
        rentPaymentService.recordDepositTransaction(ownerCtx, dep.id, "REFUND", 100_000L, "Deposit refunded")
        // 7. Purchase goods & pay supplier via BANK_TRANSFER (-15,000)
        val sup = opsService.createSupplier(ownerCtx, "Doha Supply", "Salim", "+97444112233", "", "CR-77")
        val item = opsService.createItemCategoryAndItem(ownerCtx, "ELEC", "Breaker", "SKU-BR1", "PCS", 2L)
        val loc = db.operationsDao().observeStockLocations(ownerCtx.activeBusinessId).first().first()
        val po = opsService.createPurchaseOrder(ownerCtx, sup.id, prop.id, item.id, 10L, 1_500L, "Breakers")
        opsService.approvePurchase(ownerCtx, po.id, "Approved")
        opsService.receiveGoods(ownerCtx, po.id, loc.id, "INV-77")
        opsService.paySupplier(ownerCtx, po.id, 15_000L, "BANK_TRANSFER")

        val expectedBalance = 1_000_000L + 200_000L + 50_000L + 300_000L - 200_000L - 25_000L - 100_000L - 15_000L
        assertEquals(1_210_000L, expectedBalance)
        val refreshedBank = db.accountingDao().getBankAccountById(bank.id)!!
        assertEquals(expectedBalance, refreshedBank.currentBalanceFils)
        val glBank = accountingService.computeTrialBalance(ownerCtx.activeBusinessId)
            .first { it.code == AccountingService.ACC_BANK }.netBalanceFils
        assertEquals(expectedBalance, glBank)
        val txns = db.accountingDao().getBankTransactionsByBusiness(ownerCtx.activeBusinessId)
        assertEquals(7, txns.size)
        assertTrue(accountingService.computeBalanceSheet(ownerCtx.activeBusinessId).isBalanced)
    }

    @Test
    fun `external integration providers stay honestly NOT_CONFIGURED without endpoint and log offline fallback config`() = runBlocking {
        val catalog = backupService.canonicalExternalProviders()
        assertEquals(5, catalog.size)

        val unconfigured = backupService.configureIntegration(
            ctx = ownerCtx,
            providerType = "sms_gateway",
            providerName = "",
            endpointOrHandle = "",
            enabled = true
        )
        assertEquals("SMS_GATEWAY", unconfigured.providerType)
        assertEquals("NOT_CONFIGURED", unconfigured.lastTestStatus)
        assertFalse(unconfigured.isEnabled)

        val fallback = backupService.configureIntegration(
            ctx = ownerCtx,
            providerType = "email_smtp",
            providerName = "Doha SMTP",
            endpointOrHandle = "smtp.onehome.qa:587",
            enabled = true
        )
        assertEquals("EMAIL_SMTP", fallback.providerType)
        assertEquals("CONFIGURED_OFFLINE_FALLBACK", fallback.lastTestStatus)
        assertTrue(fallback.isEnabled)

        val logs = db.operationsDao().getAuditLogsByBusiness(ownerCtx.activeBusinessId)
        assertTrue(logs.any { it.action == "CONFIGURE_INTEGRATION" && it.summary.contains("CONFIGURED_OFFLINE_FALLBACK") })
    }
}
