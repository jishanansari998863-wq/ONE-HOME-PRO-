package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.OneHomeProDatabase
import com.example.localization.AppLanguage
import com.example.ui.OneHomeProViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeUiFlowRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: OneHomeProDatabase
    private lateinit var container: AppContainer
    private lateinit var vm: OneHomeProViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<OneHomeProApp>()
        db = Room.inMemoryDatabaseBuilder(app, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        container = AppContainer(app, db)
        runBlocking {
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        vm = OneHomeProViewModel(container, splashDelayMs = 0L)
    }

    @After
    fun tearDown() {
        runBlocking {
            vm.awaitStopObservers()
            container.sessionStore.clearSessionToken()
            container.sessionStore.setLanguage(AppLanguage.EN)
        }
        db.close()
    }

    @Test
    fun `initial clean launch reaches Welcome Setup with zero fake records and supports Owner initialization and Arabic RTL switch`() {
        composeRule.setContent {
            OneHomeProAppRoot(vm)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("welcome_setup_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Fill Owner setup form
        composeRule.onNodeWithTag("setup_full_name_input").performScrollTo().performTextInput("Khalid Al-Mansoor")
        composeRule.onNodeWithTag("setup_email_input").performScrollTo().performTextInput("khalid@onehome.qa")
        composeRule.onNodeWithTag("setup_password_input").performScrollTo().performTextInput("QatarSecure#2026")
        composeRule.onNodeWithTag("setup_business_name_input").performScrollTo().performTextInput("One Home Qatar WLL")
        composeRule.onNodeWithTag("setup_submit_button").performScrollTo().performClick()

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
}
