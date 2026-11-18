package com.example

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.InMemoryCentralAuthorityTestDouble
import com.example.data.db.OneHomeProDatabase
import com.example.domain.security.SuperAdminConfig
import com.example.localization.AppLanguage
import com.example.ui.OneHomeProViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeUiFlowRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: OneHomeProDatabase
    private lateinit var central: InMemoryCentralAuthorityTestDouble
    private lateinit var container: AppContainer
    private lateinit var vm: OneHomeProViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<OneHomeProApp>()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        central = InMemoryCentralAuthorityTestDouble()
        container = AppContainer(app, db, central)
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        vm = OneHomeProViewModel(container, splashDelayMs = 0L)
    }

    @After
    fun tearDown() {
        vm.stopObservers()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking(Dispatchers.IO) {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }

    @Test
    fun `clean launch enforces Restricted Gate, holds Pending Approval, enters Dashboard only after Super Admin approves, and supports Arabic RTL`() {
        composeRule.setContent {
            OneHomeProAppRoot(vm)
        }
        // 1. Fresh launch MUST show Restricted Welcome Gate (never auto-setup or dashboard)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("restricted_gate_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("google_signin_gate_button").assertExists()

        // 2. Applicant signs in with Google -> stays blocked on Access Request screen
        central.signInAsTestIdentity("uid_khalid", "khalid@onehome.qa", "Khalid Al-Mansoor")
        vm.evaluateCurrentCentralIdentity()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("access_request_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Fill Access Request form & submit -> MUST remain PENDING_APPROVAL and NOT enter Dashboard
        composeRule.onNodeWithTag("req_full_name_input").performScrollTo().performTextReplacement("Khalid Al-Mansoor")
        composeRule.onNodeWithTag("req_phone_input").performScrollTo().performTextInput("+97455110022")
        composeRule.onNodeWithTag("req_business_input").performScrollTo().performTextInput("One Home Qatar WLL")
        composeRule.onNodeWithTag("req_qid_input").performScrollTo().performTextInput("29012345678")
        composeRule.onNodeWithTag("req_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("PENDING APPROVAL").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("dashboard_screen").assertCountEquals(0)

        // 3. Super Admin approves request on central backend -> applicant clicks Check Approval -> reaches Dashboard
        runBlocking {
            central.signInAsTestIdentity("uid_superadmin", SuperAdminConfig.SUPER_ADMIN_EMAIL, "Jishan Ansari")
            central.bootstrapSuperAdmin("MasterPass#2026")
            central.approveAccessRequest(
                requestUid = "uid_khalid",
                assignedRole = "OWNER",
                existingBusinessId = null,
                permissionKeys = emptyList(),
                allowedPropertyIds = emptyList(),
                note = "Verified Qatar Owner"
            )
            central.signInAsTestIdentity("uid_khalid", "khalid@onehome.qa", "Khalid Al-Mansoor")
        }

        composeRule.onNodeWithTag("req_refresh_button").performScrollTo().performClick()

        // Wait for Dashboard with empty state (0 fake records)
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_empty_properties"))
        composeRule.onNodeWithTag("dashboard_empty_properties").assertExists()

        // Switch language to Arabic (RTL) and assert Arabic localization renders
        vm.setLanguage(AppLanguage.AR)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("الرئيسية").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("الرئيسية").assertExists()
    }

    @Test
    fun `missing Firebase config transitions from Splash to safe StartupErrorScreen instead of crashing`() {
        val app = ApplicationProvider.getApplicationContext<OneHomeProApp>()
        val unconfiguredGateway = object : com.example.domain.security.CentralAuthorityGateway by central {
            override fun isConfigured(): Boolean = false
            override fun configurationDiagnostic(): String =
                "Firebase configuration missing: app/google-services.json was not bundled in this APK."
        }
        val brokenContainer = AppContainer(app, db, unconfiguredGateway, container.sessionStore)
        val brokenVm = OneHomeProViewModel(brokenContainer, splashDelayMs = 0L)
        try {
            composeRule.setContent {
                OneHomeProAppRoot(brokenVm)
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("startup_error_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("startup_error_title").assertExists()
            composeRule.onNodeWithTag("startup_error_details").assertExists()
            composeRule.onNodeWithTag("startup_retry_button").assertExists().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("startup_error_screen").assertExists()
        } finally {
            brokenVm.stopObservers()
        }
    }

    @Test
    fun `MainActivity and OneHomeProApp cold start completes without uncaught exceptions`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            org.junit.Assert.assertNotNull(activity)
            Thread.sleep(1100L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            org.junit.Assert.assertFalse(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
