package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.OneHomeProDatabase
import com.example.domain.service.AuthResult
import com.example.localization.AppLanguage
import com.example.ui.OneHomeProViewModel
import com.example.ui.components.createTempCameraImageUri
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AdaptiveLayoutAndCameraRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: OneHomeProApp
    private lateinit var db: OneHomeProDatabase
    private lateinit var container: AppContainer
    private var activeVm: OneHomeProViewModel? = null

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        container = AppContainer(app, db)
        runBlocking {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
            val res = container.authService.setupInitialOwnerAndBusiness(
                fullName = "Hamad Al-Kuwari",
                email = "hamad@onehome.qa",
                password = "QatarSecure#2026",
                businessName = "Lusail Estates WLL",
                branchName = "Lusail Marina",
                enableMfa = false,
                rememberMe = true,
                deviceName = "AdaptiveTestDevice"
            )
            assertTrue(res is AuthResult.Authenticated)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            activeVm?.awaitStopObservers()
            activeVm = null
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        db.close()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h780dp")
    fun `phone layout displays bottom navigation bar and hides side rail`() {
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        composeRule.setContent {
            OneHomeProAppRoot(vm)
        }
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bottom_nav_bar").assertExists()
        composeRule.onNodeWithTag("nav_tab_DASHBOARD").assertExists()
        composeRule.onNodeWithTag("nav_tab_PROPERTIES").assertExists()
        composeRule.onNodeWithTag("nav_tab_MORE").assertExists()
        composeRule.onAllNodesWithTag("side_nav_rail").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h768dp")
    fun `tablet layout displays side navigation rail and hides bottom bar`() {
        val vm = OneHomeProViewModel(container, splashDelayMs = 0L).also { activeVm = it }
        composeRule.setContent {
            OneHomeProAppRoot(vm)
        }
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("side_nav_rail").assertExists()
        composeRule.onNodeWithTag("rail_tab_DASHBOARD").assertExists()
        composeRule.onNodeWithTag("rail_tab_PROPERTIES").assertExists()
        composeRule.onNodeWithTag("rail_tab_MORE").assertExists()
        composeRule.onAllNodesWithTag("bottom_nav_bar").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [36])
    fun `zero-permission FileProvider camera URI resolves cleanly`() {
        val uri = createTempCameraImageUri(app)
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.fileprovider", uri.authority)
        assertTrue(uri.toString().contains("camera_captures"))
    }
}
