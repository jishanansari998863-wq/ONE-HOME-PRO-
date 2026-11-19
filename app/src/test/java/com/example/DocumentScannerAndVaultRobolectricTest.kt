package com.example

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.BusinessEntity
import com.example.domain.document.*
import com.example.domain.security.AuthContext
import com.example.domain.service.AuthResult
import com.example.localization.AppLanguage
import com.example.ui.BusinessSnapshotData
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import android.os.Looper
import com.example.ui.components.DocumentScannerStudioDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentScannerAndVaultRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: OneHomeProApp
    private lateinit var db: OneHomeProDatabase
    private lateinit var container: AppContainer
    private lateinit var ownerCtx: AuthContext
    private var activeVm: OneHomeProViewModel? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val central = com.example.base.InMemoryCentralAuthorityTestDouble().apply {
            preApproveUserForTest("uid_nasser_owner", "nasser@onehome.qa", "Nasser Al-Thani", "OWNER")
            preApproveUserForTest("uid_ali_cust", "ali@example.qa", "Ali Hassan", "CUSTOMER")
            preApproveUserForTest("uid_samir_tech", "samir@onehome.qa", "Samir Tech", "EMPLOYEE")
        }
        container = AppContainer(app, db, central)
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
            val res = container.authService.setupInitialOwnerAndBusiness(
                fullName = "Nasser Al-Thani",
                email = "nasser@onehome.qa",
                password = "QatarSecure#2026",
                businessName = "Doha Realty WLL",
                branchName = "West Bay",
                enableMfa = false,
                rememberMe = true,
                deviceName = "ScannerTest"
            ) as AuthResult.Authenticated
            ownerCtx = res.context
        }
    }

    @After
    fun tearDown() {
        activeVm?.stopObservers()
        activeVm = null
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }

    private fun createSyntheticDocumentBitmap(width: Int = 400, height: Int = 300): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(30, 30, 30))
        val docPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(80f, 60f, 320f, 240f, docPaint)
        val textPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
        }
        canvas.drawLine(110f, 110f, 290f, 110f, textPaint)
        canvas.drawLine(110f, 150f, 260f, 150f, textPaint)
        canvas.drawLine(110f, 190f, 240f, 190f, textPaint)
        return bmp
    }

    private suspend fun loginBypassingDeviceChallenge(email: String, password: String, deviceName: String): AuthContext {
        return when (val res = container.authService.login(email, password, false, deviceName)) {
            is AuthResult.Authenticated -> res.context
            is AuthResult.NewDeviceVerificationRequired -> {
                (container.authService.verifyNewDeviceChallenge(
                    res.userId,
                    res.verificationCode,
                    true,
                    false,
                    deviceName
                ) as AuthResult.Authenticated).context
            }
            else -> fail("Unexpected login state: $res") as Nothing
        }
    }

    @Test
    fun `auto edge detection, crop, 90-degree rotation, readability filters, and multi-page PDF + JPG PNG encoding work`() {
        val raw = createSyntheticDocumentBitmap(400, 300)

        val detected = DocumentImageProcessor.detectDocumentBounds(raw)
        assertTrue("Left edge should trim outer margin (${detected.left})", detected.left > 0.08f)
        assertTrue("Right edge should trim outer margin (${detected.right})", detected.right < 0.92f)
        assertTrue("Top edge should trim outer margin (${detected.top})", detected.top > 0.08f)
        assertTrue("Bottom edge should trim outer margin (${detected.bottom})", detected.bottom < 0.92f)

        val cropped = DocumentImageProcessor.processPageBitmap(raw, 0, detected, ReadabilityMode.AUTO_ENHANCE)
        assertTrue(cropped.width < raw.width && cropped.height < raw.height)

        val rotated = DocumentImageProcessor.processPageBitmap(raw, 90, detected, ReadabilityMode.CRISP_BW)
        assertTrue("90° rotation swaps portrait/landscape aspect", rotated.height > rotated.width || rotated.width == cropped.height)

        val colorScan = DocumentImageProcessor.processPageBitmap(raw, 0, detected, ReadabilityMode.COLOR_SCAN)
        assertEquals(cropped.width, colorScan.width)

        val progressEvents = mutableListOf<Float>()
        val pdfBytes = DocumentImageProcessor.encodePages(listOf(cropped, colorScan), DocumentOutputFormat.PDF) { f, _ ->
            progressEvents.add(f)
        }
        assertTrue(pdfBytes.size > 100)
        assertEquals("%PDF-", pdfBytes.take(5).toByteArray().toString(Charsets.US_ASCII))
        assertTrue(progressEvents.isNotEmpty())

        val jpgBytes = DocumentImageProcessor.encodePages(listOf(cropped), DocumentOutputFormat.JPG)
        val jpegBytes = DocumentImageProcessor.encodePages(listOf(cropped), DocumentOutputFormat.JPEG)
        val pngBytes = DocumentImageProcessor.encodePages(listOf(cropped), DocumentOutputFormat.PNG)
        assertTrue(jpgBytes.isNotEmpty())
        assertTrue(jpegBytes.isNotEmpty())
        assertTrue(pngBytes.isNotEmpty())
    }

    @Test
    fun `encrypted vault enforces record linkage, RBAC permissions, AES-GCM decrypt, and deletion`() = runBlocking {
        val prop = container.propertyTenancyService.createProperty(
            ownerCtx, "Tower A", "PRPA", "RESIDENTIAL", "West Bay", "Doha", ""
        )
        val bldg = container.propertyTenancyService.addBuilding(ownerCtx, prop.id, "Block A", "A", 4)
        val fl = container.propertyTenancyService.addFloor(ownerCtx, prop.id, bldg.id, 1, "Floor 1")
        val room = container.propertyTenancyService.addRoom(
            ownerCtx, prop.id, bldg.id, fl.id, "101", "1BHK", 2, "FURNISHED", 500_000L, "AC", 2, "CARD-1", "Full"
        )
        val cust1 = container.propertyTenancyService.createCustomer(
            ownerCtx, "Ali Hassan", "+97455112233", "ali@example.qa", "29100001111", "", "QA", "", "", ""
        )
        val cust2 = container.propertyTenancyService.createCustomer(
            ownerCtx, "Omar Zaid", "+97455445566", "omar@example.qa", "29100002222", "", "QA", "", "", ""
        )
        val emp = container.workforceMaintenanceInventoryService.addEmployee(
            ownerCtx, "Samir Tech", "Technician", null, "+97455778899", "samir@onehome.qa", "28800003333", "Tower A", "08:00-17:00"
        )
        val staffAuth = container.authService.createStaffUser(
            ownerCtx, emp.id, emp.fullName, "samir@onehome.qa", "TechPass#2026", "TECHNICIAN", emptyList()
        )
        val custLogin = container.authService.createCustomerPortalUser(ownerCtx, cust1.id, "ali@example.qa", "CustPass#2026")
        val custCtx = loginBypassingDeviceChallenge("ali@example.qa", "CustPass#2026", "Phone")
        val techCtx = loginBypassingDeviceChallenge("samir@onehome.qa", "TechPass#2026", "Tablet")

        val now = System.currentTimeMillis()
        val end = now + 30L * 86_400_000L
        val ag = container.propertyTenancyService.createAndActivateAgreement(
            ownerCtx, room.id, cust1.id, "MONTHLY", 500_000L, 0L, 500_000L, now, end, 30, ""
        )
        container.propertyTenancyService.generateRentForPeriod(ownerCtx, now)
        val rent = db.coreAndTenancyDao().getMonthlyRentsByBusiness(ownerCtx.activeBusinessId).first()
        val pay = container.rentPaymentChequeService.receiveConfirmedPayment(
            ownerCtx, cust1.id, ag.id, 200_000L, "CASH", null, listOf(rent.id), "First rent", idempotencyKey = "PAY-DOC-1"
        )

        val pdfPayload = DocumentImageProcessor.encodePages(listOf(createSyntheticDocumentBitmap()), DocumentOutputFormat.PDF)

        val dTenant = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, pdfPayload, "application/pdf", "pdf", "CUSTOMER", cust1.id, "QID", "Ali QID"
        )
        val dProp = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, pdfPayload, "application/pdf", "pdf", "PROPERTY", prop.id, "PROPERTY_RECORD", "Title Deed"
        )
        val dEmp = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, pdfPayload, "application/pdf", "pdf", "EMPLOYEE", emp.id, "PASSPORT", "Samir Passport"
        )
        val dAg = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, pdfPayload, "application/pdf", "pdf", "AGREEMENT", ag.id, "AGREEMENT", "Signed Lease"
        )
        val dPay = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, pdfPayload, "application/pdf", "pdf", "PAYMENT", pay.id, "RECEIPT", "Rent Receipt"
        )

        assertEquals("CUSTOMER", dTenant.entityType)
        assertEquals(prop.id, dProp.propertyId)
        assertEquals("EMPLOYEE", dEmp.entityType)
        assertEquals(prop.id, dAg.propertyId)
        assertEquals(prop.id, dPay.propertyId)

        val rawOnDisk = java.io.File(dTenant.privateFilePath).readBytes()
        assertNotEquals("%PDF-", rawOnDisk.take(5).toByteArray().toString(Charsets.US_ASCII))
        val (_, decryptedBytes) = container.governanceBackupService.readDecryptedDocumentBytes(ownerCtx, dTenant.id)
        assertArrayEquals(pdfPayload, decryptedBytes)

        val custOwnDoc = container.governanceBackupService.storeProcessedDocumentBytes(
            custCtx, pdfPayload, "application/pdf", "pdf", "CUSTOMER", cust1.id, "PASSPORT", "My Passport"
        )
        assertEquals(cust1.id, custOwnDoc.entityId)

        var blockedOtherCustomer = false
        try {
            container.governanceBackupService.storeProcessedDocumentBytes(
                custCtx, pdfPayload, "application/pdf", "pdf", "CUSTOMER", cust2.id, "QID", "Forbidden"
            )
        } catch (_: IllegalArgumentException) {
            blockedOtherCustomer = true
        }
        assertTrue("Customer must be blocked from uploading to another tenant", blockedOtherCustomer)

        var blockedTechUpload = false
        try {
            container.governanceBackupService.storeProcessedDocumentBytes(
                techCtx, pdfPayload, "application/pdf", "pdf", "PROPERTY", prop.id, "INVOICE", "Forbidden Tech Doc"
            )
        } catch (_: SecurityException) {
            blockedTechUpload = true
        }
        assertTrue("Technician must be blocked by RBAC from uploading restricted documents", blockedTechUpload)

        container.governanceBackupService.deleteDocument(ownerCtx, dPay.id)
        assertTrue(db.operationsDao().getDocumentById(dPay.id)?.isArchived == true)
        assertFalse(java.io.File(dPay.privateFilePath).exists())
        assertTrue(staffAuth.id.isNotBlank() && custLogin.id.isNotBlank())

        // Verify encrypted MP4 video vault storage + AES-GCM decryption
        val sampleMp4 = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32)
        val dVideo = container.governanceBackupService.storeProcessedDocumentBytes(
            ownerCtx, sampleMp4, "video/mp4", "mp4", "PROPERTY", prop.id, "MAINTENANCE", "Walkthrough Clip"
        )
        assertEquals("video/mp4", dVideo.mimeType)
        assertTrue(dVideo.privateFilePath.contains(".mp4"))
        val (vidDoc, vidDecrypted) = container.governanceBackupService.readDecryptedDocumentBytes(ownerCtx, dVideo.id)
        assertEquals("video/mp4", vidDoc.mimeType)
        assertArrayEquals(sampleMp4, vidDecrypted)

        // Verify multi-currency formatter keeps QAR default and formats AED/USD cleanly
        assertEquals("QAR 500.00", com.example.domain.util.QatarFormatters.formatMoney(50_000L, "QAR"))
        assertEquals("AED 500.00", com.example.domain.util.QatarFormatters.formatMoney(50_000L, "AED"))

        // Verify automatic encrypted backup creation + restore integrity check
        val autoBackup = container.governanceBackupService.runAutomaticEncryptedBackupAndVerify(ownerCtx, force = true)
        assertNotNull(autoBackup)
        assertTrue(autoBackup!!.checksumMatched)
        assertTrue(autoBackup.isBalanced)
        val savedBackup = db.operationsDao().getBackupById(autoBackup.backupId)
        assertNotNull(savedBackup)
        assertTrue(java.io.File(savedBackup!!.filePath).exists())
    }

    @Test
    fun `scanner UI displays all four options, categories, record linkage, and disables upload until user confirms`() {
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        composeRule.setContent {
            val state = OneHomeUiState(
                authContext = ownerCtx,
                data = BusinessSnapshotData(
                    businesses = listOf(
                        BusinessEntity(
                            id = ownerCtx.activeBusinessId,
                            name = "Doha Realty WLL",
                            code = "DRW",
                            branchName = "West Bay",
                            createdAtEpoch = 0L,
                            updatedAtEpoch = 0L
                        )
                    )
                )
            )
            DocumentScannerStudioDialog(
                uiState = state,
                vm = vm,
                onDismiss = {}
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("document_scanner_studio").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_take_photo").assertExists()
        composeRule.onNodeWithTag("btn_scan_document").assertExists()
        composeRule.onNodeWithTag("btn_choose_gallery").assertExists()
        composeRule.onNodeWithTag("btn_upload_file").assertExists()
        composeRule.onNodeWithTag("cat_chip_QID").assertExists()
        composeRule.onNodeWithTag("cat_chip_PASSPORT").assertExists()
        composeRule.onNodeWithTag("cat_chip_CHEQUE").assertExists()
        composeRule.onNodeWithTag("btn_review_and_confirm_upload").assertIsNotEnabled()
    }

    @Test
    fun `clicking Take Photo when Camera permission is granted opens CameraX viewfinder dialog`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        composeRule.setContent {
            val state = OneHomeUiState(
                authContext = ownerCtx,
                data = BusinessSnapshotData(
                    businesses = listOf(
                        BusinessEntity(
                            id = ownerCtx.activeBusinessId,
                            name = "Doha Realty WLL",
                            code = "DRW",
                            branchName = "West Bay",
                            createdAtEpoch = 0L,
                            updatedAtEpoch = 0L
                        )
                    )
                )
            )
            DocumentScannerStudioDialog(
                uiState = state,
                vm = vm,
                onDismiss = {}
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("btn_take_photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("btn_take_photo").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("camerax_viewfinder_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("camerax_viewfinder_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_close_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("camerax_viewfinder_dialog").assertCountEquals(0)
    }

    @Test
    fun `audio vault encryption, expiry alert scanner, CSV+PDF report export with RBAC, and custom role creation work`() = runBlocking {
        val prop = container.propertyTenancyService.createProperty(
            ownerCtx, "Al Sadd Plaza", "ASP", "COMMERCIAL", "Al Sadd", "Doha", ""
        )
        val bldg = container.propertyTenancyService.addBuilding(ownerCtx, prop.id, "Block B", "B", 2)
        val fl = container.propertyTenancyService.addFloor(ownerCtx, prop.id, bldg.id, 1, "Floor 1")
        val room = container.propertyTenancyService.addRoom(
            ownerCtx, prop.id, bldg.id, fl.id, "202", "STUDIO", 1, "FURNISHED", 350_000L, "AC", 1, "CARD-2", "Bed"
        )
        val cust = container.propertyTenancyService.createCustomer(
            ownerCtx, "Tariq Mansoor", "+97455009988", "tariq@example.qa", "29200004444", "", "QA", "", "", ""
        )
        val emp = container.workforceMaintenanceInventoryService.addEmployee(
            ownerCtx, "Samir Tech", "Technician", null, "+97455778899", "samir2@onehome.qa", "28800003333", "Al Sadd", "08:00-17:00"
        )
        container.authService.createStaffUser(
            ownerCtx, emp.id, emp.fullName, "samir2@onehome.qa", "TechPass#2026", "TECHNICIAN", emptyList()
        )
        val techCtx = loginBypassingDeviceChallenge("samir2@onehome.qa", "TechPass#2026", "TechPhone")

        // 1. Audio MP3 encrypted storage + expiry date
        val sampleMp3 = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x11, 0x22)
        val now = System.currentTimeMillis()
        val expEpoch = now + 5L * 86_400_000L
        val dAudio = container.governanceBackupService.storeProcessedDocumentBytes(
            ctx = ownerCtx,
            bytes = sampleMp3,
            mimeType = "audio/mpeg",
            extension = "mp3",
            entityType = "CUSTOMER",
            entityId = cust.id,
            category = "OTHER",
            title = "Tenant Voice Note",
            expiryDateEpoch = expEpoch
        )
        assertEquals("audio/mpeg", dAudio.mimeType)
        assertEquals(expEpoch, dAudio.expiryDateEpoch)
        val (_, decAudio) = container.governanceBackupService.readDecryptedDocumentBytes(ownerCtx, dAudio.id)
        assertArrayEquals(sampleMp3, decAudio)

        // 2. Expiring lease & low stock + operational alert scan
        container.propertyTenancyService.createAndActivateAgreement(
            ownerCtx, room.id, cust.id, "MONTHLY", 350_000L, 0L, 350_000L, now - 25L * 86_400_000L, now + 5L * 86_400_000L, 30, ""
        )
        container.workforceMaintenanceInventoryService.createItemCategoryAndItem(
            ownerCtx, "HVAC", "Thermostat", "SKU-THM-01", "PCS", 5L
        )
        val alertsGenerated = container.governanceBackupService.scanAndGenerateOperationalAlerts(ownerCtx, now)
        assertTrue("Expected lease, document, and low-stock alerts", alertsGenerated >= 3)
        val secondRun = container.governanceBackupService.scanAndGenerateOperationalAlerts(ownerCtx, now + 60_000L)
        assertEquals("Duplicate alerts within 24h must be suppressed", 0, secondRun)

        // 3. CSV and PDF report export + RBAC enforcement
        val csvBytes = ReportExporter.buildCsvReport("1. Occupancy & Vacancy", "Doha Realty WLL", listOf("Room 202: OCCUPIED", "Rate: QAR 3,500.00"))
        val csvText = String(csvBytes, Charsets.UTF_8)
        assertTrue(csvText.contains("ONE HOME PRO"))
        assertTrue(csvText.contains("Room 202: OCCUPIED"))

        val pdfBytes = ReportExporter.buildPdfReport("1. Occupancy & Vacancy", "Doha Realty WLL", listOf("Room 202: OCCUPIED"))
        assertEquals("%PDF-", pdfBytes.take(5).toByteArray().toString(Charsets.US_ASCII))
        container.governanceBackupService.logReportExport(ownerCtx, "1. Occupancy & Vacancy", "PDF")

        var exportBlockedForTech = false
        try {
            container.governanceBackupService.logReportExport(techCtx, "9. Trial Balance", "CSV")
        } catch (_: SecurityException) {
            exportBlockedForTech = true
        }
        assertTrue("Technician without REPORTS:EXPORT permission must be blocked", exportBlockedForTech)

        // 4. Custom expandable RBAC role creation + SUPER_ADMIN protection
        val customRole = container.authService.createCustomRole(ownerCtx, "Leasing Officer", "Handles viewings and lease drafts")
        assertEquals("LEASING_OFFICER", customRole.name)
        var reservedBlocked = false
        try {
            container.authService.createCustomRole(ownerCtx, "SUPER_ADMIN", "Forbidden")
        } catch (_: IllegalArgumentException) {
            reservedBlocked = true
        }
        assertTrue("Creating SUPER_ADMIN custom role must remain blocked", reservedBlocked)
    }
}
