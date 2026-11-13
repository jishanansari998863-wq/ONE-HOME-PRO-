package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localization.LocalAppStrings
import com.example.localization.stringsFor
import com.example.ui.AppDestination
import com.example.ui.AuthScreenStep
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.OneHomeProTopBar
import com.example.ui.screens.*
import com.example.ui.theme.OneHomeProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as OneHomeProApp).container
        setContent {
            val vm: OneHomeProViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return OneHomeProViewModel(appContainer) as T
                    }
                }
            )
            OneHomeProAppRoot(vm)
        }
    }
}

@Composable
fun OneHomeProAppRoot(vm: OneHomeProViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val strings = remember(uiState.language) { stringsFor(uiState.language) }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        OneHomeProTheme(darkTheme = uiState.darkTheme, language = uiState.language) {
            val ctx = uiState.authContext
            if (ctx == null) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (uiState.authStep) {
                        AuthScreenStep.SPLASH -> BrandedSplashScreen()
                        AuthScreenStep.WELCOME_SETUP -> WelcomeSetupScreen(
                            uiState = uiState,
                            onChangeLanguage = vm::setLanguage,
                            onSetupOwner = vm::setupOwnerAndBusiness,
                            onDismissBanner = vm::clearBanners
                        )
                        AuthScreenStep.LOGIN -> LoginScreen(
                            uiState = uiState,
                            onChangeLanguage = vm::setLanguage,
                            onSignIn = vm::signIn,
                            onGoForgotPassword = { vm.setAuthStep(AuthScreenStep.FORGOT_PASSWORD) },
                            onDismissBanner = vm::clearBanners
                        )
                        AuthScreenStep.FORGOT_PASSWORD -> ForgotPasswordScreen(
                            uiState = uiState,
                            onRequestReset = vm::requestPasswordReset,
                            onCompleteReset = vm::completePasswordReset,
                            onBackToLogin = { vm.setAuthStep(AuthScreenStep.LOGIN) },
                            onDismissBanner = vm::clearBanners
                        )
                        AuthScreenStep.DEVICE_CHALLENGE -> DeviceChallengeScreen(
                            uiState = uiState,
                            onVerifyDevice = vm::verifyNewDevice,
                            onCancel = { vm.setAuthStep(AuthScreenStep.LOGIN) },
                            onDismissBanner = vm::clearBanners
                        )
                        AuthScreenStep.MFA_CHALLENGE -> MfaChallengeScreen(
                            uiState = uiState,
                            onVerifyMfa = vm::verifyMfa,
                            onCancel = { vm.setAuthStep(AuthScreenStep.LOGIN) },
                            onDismissBanner = vm::clearBanners
                        )
                    }
                }
            } else {
                AuthenticatedWorkspaceScaffold(uiState = uiState, vm = vm)
            }
        }
    }
}

@Composable
private fun AuthenticatedWorkspaceScaffold(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    val strings = LocalAppStrings.current
    val unreadNotifications = remember(uiState.data.notifications, ctx.userId) {
        uiState.data.notifications.count { !it.isRead && (it.recipientUserId == ctx.userId || ctx.userType == "OWNER") }
    }

    BackHandler(enabled = uiState.backStack.isNotEmpty()) {
        vm.navigateBack()
    }

    val destinationTitle = when (uiState.currentDestination) {
        AppDestination.DASHBOARD -> strings.appName
        AppDestination.PROPERTIES -> strings.navProperties
        AppDestination.TENANTS -> strings.navTenants
        AppDestination.PAYMENTS -> strings.navPayments
        AppDestination.MORE -> strings.navMore
        AppDestination.AGREEMENTS -> strings.agreements
        AppDestination.CHEQUES -> strings.cheques
        AppDestination.DEPOSITS -> strings.securityDeposits
        AppDestination.ACCOUNTING -> strings.accounting
        AppDestination.WORKFORCE -> strings.workforceHr
        AppDestination.MAINTENANCE -> strings.maintenance
        AppDestination.INVENTORY -> strings.purchaseInventory
        AppDestination.DOCUMENTS -> strings.documents
        AppDestination.NOTIFICATIONS -> strings.notifications
        AppDestination.REPORTS -> strings.reportsAnalytics
        AppDestination.OWNER_CONTROL -> strings.ownerControlCenter
        AppDestination.ACCOUNT_SECURITY -> "Account & Security"
        AppDestination.CUSTOMER_PORTAL -> strings.customerPortal
    }

    val isStaffPortal = !ctx.isCustomerPortalOnly()
    val primaryTabs = listOf(
        Triple(AppDestination.DASHBOARD, strings.navHome, Icons.Default.Dashboard),
        Triple(AppDestination.PROPERTIES, strings.navProperties, Icons.Default.Apartment),
        Triple(AppDestination.TENANTS, strings.navTenants, Icons.Default.People),
        Triple(AppDestination.PAYMENTS, strings.navPayments, Icons.Default.Payments),
        Triple(AppDestination.MORE, strings.navMore, Icons.Default.Apps)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp && isStaffPortal

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                OneHomeProTopBar(
                    title = destinationTitle,
                    authContext = ctx,
                    businesses = uiState.data.businesses,
                    language = uiState.language,
                    darkTheme = uiState.darkTheme,
                    unreadNotifications = unreadNotifications,
                    canNavigateBack = uiState.backStack.isNotEmpty(),
                    onBack = { vm.navigateBack() },
                    onSwitchBusiness = vm::switchBusiness,
                    onChangeLanguage = vm::setLanguage,
                    onToggleTheme = vm::setDarkTheme,
                    onOpenNotifications = { vm.navigateTo(AppDestination.NOTIFICATIONS) },
                    onOpenSecurity = { vm.navigateTo(AppDestination.ACCOUNT_SECURITY) }
                )
            },
            bottomBar = {
                if (isStaffPortal && !useNavRail) {
                    NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                        primaryTabs.forEach { (dest, label, icon) ->
                            NavigationBarItem(
                                selected = uiState.currentDestination == dest,
                                onClick = { vm.navigateTab(dest) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${dest.name}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.testTag("side_nav_rail")) {
                        primaryTabs.forEach { (dest, label, icon) ->
                            NavigationRailItem(
                                selected = uiState.currentDestination == dest,
                                onClick = { vm.navigateTab(dest) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_tab_${dest.name}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    FeedbackBanner(
                        error = uiState.bannerError,
                        success = uiState.bannerSuccess,
                        onDismiss = vm::clearBanners
                    )
                    if (uiState.isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (uiState.currentDestination) {
                            AppDestination.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                onSelectPropertyFilter = vm::setPropertyFilter,
                                onSearchChange = vm::setGlobalSearchQuery,
                                onNavigate = vm::navigateTo,
                                onGenerateMonthlyRent = vm::generateMonthlyRent
                            )
                            AppDestination.PROPERTIES -> PropertiesScreen(uiState, vm)
                            AppDestination.TENANTS -> TenantsScreen(uiState, vm)
                            AppDestination.PAYMENTS -> PaymentsScreen(uiState, vm)
                            AppDestination.MORE -> MoreHubScreen(uiState, vm::navigateTo, vm::logout)
                            AppDestination.AGREEMENTS -> AgreementsScreen(uiState, vm)
                            AppDestination.CHEQUES -> ChequesScreen(uiState, vm)
                            AppDestination.DEPOSITS -> DepositsScreen(uiState, vm)
                            AppDestination.ACCOUNTING -> AccountingScreen(uiState, vm)
                            AppDestination.WORKFORCE -> WorkforceScreen(uiState, vm)
                            AppDestination.MAINTENANCE -> MaintenanceScreen(uiState, vm)
                            AppDestination.INVENTORY -> InventoryScreen(uiState, vm)
                            AppDestination.DOCUMENTS -> DocumentsScreen(uiState, vm)
                            AppDestination.NOTIFICATIONS -> NotificationsScreen(uiState, vm)
                            AppDestination.REPORTS -> ReportsScreen(uiState)
                            AppDestination.OWNER_CONTROL -> OwnerControlScreen(uiState, vm)
                            AppDestination.ACCOUNT_SECURITY -> AccountSecurityScreen(
                                uiState = uiState,
                                onEnableMfa = vm::enableMfa,
                                onDisableMfa = vm::disableMfa,
                                onRevokeSession = vm::revokeSession,
                                onLogout = vm::logout,
                                onLogoutAllDevices = vm::logoutAllDevices,
                                onSubmitClosureRequest = vm::submitAccountClosureRequest
                            )
                            AppDestination.CUSTOMER_PORTAL -> CustomerPortalScreen(uiState, vm)
                        }
                    }
                }
            }
        }
    }
}
