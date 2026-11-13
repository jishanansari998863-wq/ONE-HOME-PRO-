package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.AppDestination
import com.example.ui.OneHomeUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    uiState: OneHomeUiState,
    onSelectPropertyFilter: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onGenerateMonthlyRent: () -> Unit
) {
    val strings = LocalAppStrings.current
    val ctx = uiState.authContext ?: return
    val data = uiState.data
    val propFilter = uiState.selectedPropertyFilterId
    val query = uiState.globalSearchQuery.trim()

    val scopedProps = remember(data.properties, ctx) {
        if (ctx.restrictToAssignedProperties && ctx.userType != "OWNER") {
            data.properties.filter { ctx.allowedPropertyIds.contains(it.id) }
        } else data.properties
    }
    val rooms = remember(data.rooms, propFilter) {
        if (propFilter == null) data.rooms else data.rooms.filter { it.propertyId == propFilter }
    }
    val rents = remember(data.monthlyRents, propFilter) {
        if (propFilter == null) data.monthlyRents else data.monthlyRents.filter { it.propertyId == propFilter }
    }
    val complaints = remember(data.complaints, propFilter) {
        if (propFilter == null) data.complaints else data.complaints.filter { it.propertyId == propFilter }
    }
    val deposits = remember(data.deposits, propFilter) {
        if (propFilter == null) data.deposits else data.deposits.filter { it.propertyId == propFilter }
    }

    val totalRooms = rooms.size
    val occupiedRooms = rooms.count { it.status == "OCCUPIED" }
    val occupancyPct = if (totalRooms == 0) 0 else (occupiedRooms * 100) / totalRooms
    val rentBilledFils = rents.sumOf { it.amountDueFils }
    val rentCollectedFils = rents.sumOf { it.amountPaidFils }
    val overdueRents = rents.filter { it.status == "OVERDUE" || (it.status != "PAID" && it.dueDateEpoch < System.currentTimeMillis()) }
    val overdueFils = overdueRents.sumOf { (it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) }
    val depositsHeldFils = deposits.sumOf { it.heldFils }
    val cashAndBankFils = uiState.reports.balanceSheet?.assetRows
        ?.filter { it.code == "1000" || it.code == "1010" || it.code == "1050" }
        ?.sumOf { it.netBalanceFils } ?: 0L
    val netProfitFils = uiState.reports.profitAndLoss?.netProfitFils ?: 0L
    val pendingApprovals = data.purchases.count { it.status == "REQUESTED" } +
        complaints.count { it.status == "AWAITING_APPROVAL" }
    val openMaintenance = complaints.count { it.status !in listOf("COMPLETED", "CLOSED") }
    val lowStockCount = data.items.count { it.currentStockQty <= it.reorderLevel }

    val searchMatches = remember(query, data) {
        if (query.isBlank()) emptyList()
        else buildList {
            data.properties.filter { it.name.contains(query, true) || it.code.contains(query, true) }
                .forEach { add(Triple("Property", "${it.code} • ${it.name}", AppDestination.PROPERTIES)) }
            data.rooms.filter { it.roomNumber.contains(query, true) }
                .forEach { add(Triple("Room", "Room ${it.roomNumber} (${it.status})", AppDestination.PROPERTIES)) }
            data.customers.filter { it.fullName.contains(query, true) || it.phone.contains(query, true) || it.qidNumber.contains(query, true) }
                .forEach { add(Triple("Tenant", "${it.fullName} • ${it.phone}", AppDestination.TENANTS)) }
            data.receipts.filter { it.receiptNumber.contains(query, true) }
                .forEach { add(Triple("Receipt", "${it.receiptNumber} (${QatarFormatters.formatQar(it.amountFils)})", AppDestination.PAYMENTS)) }
            data.cheques.filter { it.chequeNumber.contains(query, true) || it.bankName.contains(query, true) }
                .forEach { add(Triple("Cheque", "${it.chequeNumber} • ${it.bankName}", AppDestination.CHEQUES)) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("dashboard_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                // Hero Business Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(QatarNavy, QatarNavyDark)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PureWhite,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.one_home_pro_logo),
                                contentDescription = "ONE HOME PRO Emblem",
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.appName} — ${ctx.fullName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                            Text(
                                text = strings.qatarTimezoneLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = ArchitecturalGoldContainer
                            )
                            Text(
                                text = "Role: ${ctx.userType} • Currency: QAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.globalSearchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.globalSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                )
            }

            if (query.isNotBlank()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Search Results (${searchMatches.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            if (searchMatches.isEmpty()) {
                                Text("No matching records found for \"$query\".", style = MaterialTheme.typography.bodySmall)
                            } else {
                                searchMatches.take(8).forEach { (kind, label, dest) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigate(dest) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusBadge(kind)
                                        Spacer(Modifier.width(8.dp))
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (scopedProps.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = propFilter == null,
                            onClick = { onSelectPropertyFilter(null) },
                            label = { Text("All Properties (${scopedProps.size})") },
                            modifier = Modifier.testTag("filter_all_properties")
                        )
                        scopedProps.forEach { prop ->
                            FilterChip(
                                selected = propFilter == prop.id,
                                onClick = { onSelectPropertyFilter(prop.id) },
                                label = { Text(prop.name) },
                                modifier = Modifier.testTag("filter_property_${prop.code}")
                            )
                        }
                    }
                }
            }

            item {
                Text(strings.quickActions, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (ctx.hasPermission(AppModule.PROPERTIES, ActionType.CREATE)) {
                        FilledTonalButton(
                            onClick = { onNavigate(AppDestination.PROPERTIES) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_properties")
                        ) {
                            Icon(Icons.Default.Apartment, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.navProperties)
                        }
                    }
                    if (ctx.hasPermission(AppModule.TENANTS, ActionType.CREATE)) {
                        FilledTonalButton(
                            onClick = { onNavigate(AppDestination.TENANTS) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_tenants")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.navTenants)
                        }
                    }
                    if (ctx.hasPermission(AppModule.AGREEMENTS, ActionType.CREATE)) {
                        FilledTonalButton(
                            onClick = { onNavigate(AppDestination.AGREEMENTS) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_agreements")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.agreements)
                        }
                    }
                    if (ctx.hasPermission(AppModule.PAYMENTS, ActionType.POST)) {
                        FilledTonalButton(
                            onClick = onGenerateMonthlyRent,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_generate_rent")
                        ) {
                            Icon(Icons.Default.EventRepeat, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Generate Rent")
                        }
                    }
                    if (ctx.hasPermission(AppModule.MAINTENANCE, ActionType.CREATE)) {
                        FilledTonalButton(
                            onClick = { onNavigate(AppDestination.MAINTENANCE) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_maintenance")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.maintenance)
                        }
                    }
                    if (ctx.hasPermission(AppModule.ACCOUNTING, ActionType.VIEW)) {
                        FilledTonalButton(
                            onClick = { onNavigate(AppDestination.ACCOUNTING) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("qa_accounting")
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.accounting)
                        }
                    }
                }
            }

            // 12 KPI Cards in 2 or 4 columns
            val kpis = listOf(
                Triple(strings.totalProperties, scopedProps.size.toString(), Icons.Default.Apartment to { onNavigate(AppDestination.PROPERTIES) }),
                Triple(strings.totalRooms, "$occupiedRooms / $totalRooms", Icons.Default.MeetingRoom to { onNavigate(AppDestination.PROPERTIES) }),
                Triple(strings.occupancyRate, "$occupancyPct%", Icons.Default.PieChart to { onNavigate(AppDestination.REPORTS) }),
                Triple(strings.rentBilled, QatarFormatters.formatQar(rentBilledFils), Icons.Default.ReceiptLong to { onNavigate(AppDestination.PAYMENTS) }),
                Triple(strings.rentCollected, QatarFormatters.formatQar(rentCollectedFils), Icons.Default.Payments to { onNavigate(AppDestination.PAYMENTS) }),
                Triple(strings.overdueRent, QatarFormatters.formatQar(overdueFils), Icons.Default.WarningAmber to { onNavigate(AppDestination.PAYMENTS) }),
                Triple(strings.depositsHeld, QatarFormatters.formatQar(depositsHeldFils), Icons.Default.Savings to { onNavigate(AppDestination.DEPOSITS) }),
                Triple(strings.cashAndBank, QatarFormatters.formatQar(cashAndBankFils), Icons.Default.AccountBalanceWallet to { onNavigate(AppDestination.ACCOUNTING) }),
                Triple(strings.netProfit, QatarFormatters.formatQar(netProfitFils), Icons.Default.TrendingUp to { onNavigate(AppDestination.REPORTS) }),
                Triple(strings.pendingApprovals, pendingApprovals.toString(), Icons.Default.FactCheck to { onNavigate(AppDestination.INVENTORY) }),
                Triple(strings.openComplaints, openMaintenance.toString(), Icons.Default.Handyman to { onNavigate(AppDestination.MAINTENANCE) }),
                Triple(strings.lowStockItems, lowStockCount.toString(), Icons.Default.Inventory2 to { onNavigate(AppDestination.INVENTORY) })
            )
            val cols = if (isWide) 4 else 2
            items(kpis.chunked(cols)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { (label, valText, meta) ->
                        val (icon, action) = meta
                        val accent = when (label) {
                            strings.overdueRent, strings.lowStockItems -> CrimsonError
                            strings.rentCollected, strings.netProfit -> EmeraldSuccess
                            strings.depositsHeld -> ArchitecturalGoldDark
                            else -> MaterialTheme.colorScheme.primary
                        }
                        KpiMetricCard(
                            title = label,
                            value = valText,
                            icon = icon,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = action
                        )
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (scopedProps.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Welcome to your clean ONE HOME PRO workspace",
                        description = "Start by adding your first real Property, Building, Floor, and Room. Zero fake records or sample tenants exist.",
                        icon = Icons.Default.AddHomeWork,
                        actionLabel = "Add First Property",
                        testTag = "dashboard_empty_properties",
                        onAction = { onNavigate(AppDestination.PROPERTIES) }
                    )
                }
            }
        }
    }
}

private data class ModuleHubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: AppDestination,
    val requiredModule: AppModule
)

@Composable
fun MoreHubScreen(
    uiState: OneHomeUiState,
    onNavigate: (AppDestination) -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalAppStrings.current
    val ctx = uiState.authContext ?: return

    val modules = listOf(
        ModuleHubItem(strings.agreements, "Lease contracts, renewals & move-outs", Icons.Default.Description, AppDestination.AGREEMENTS, AppModule.AGREEMENTS),
        ModuleHubItem(strings.cheques, "Received, deposited, cleared & bounced cheques", Icons.Default.AccountBalanceWallet, AppDestination.CHEQUES, AppModule.CHEQUES),
        ModuleHubItem(strings.securityDeposits, "Held deposits, deductions & refunds", Icons.Default.Savings, AppDestination.DEPOSITS, AppModule.DEPOSITS),
        ModuleHubItem(strings.accounting, "Double-entry GL, Trial Balance, P&L, Bank & Cashbook", Icons.Default.AccountBalance, AppDestination.ACCOUNTING, AppModule.ACCOUNTING),
        ModuleHubItem(strings.workforceHr, "Departments, employees, roles & attendance", Icons.Default.Badge, AppDestination.WORKFORCE, AppModule.EMPLOYEES),
        ModuleHubItem(strings.maintenance, "Complaints, technician jobs, photos & materials", Icons.Default.Build, AppDestination.MAINTENANCE, AppModule.MAINTENANCE),
        ModuleHubItem(strings.purchaseInventory, "Suppliers, items, POs, GRN & physical counts", Icons.Default.Inventory, AppDestination.INVENTORY, AppModule.INVENTORY),
        ModuleHubItem(strings.documents, "QID, contracts & encrypted private vault", Icons.Default.FolderShared, AppDestination.DOCUMENTS, AppModule.DOCUMENTS),
        ModuleHubItem(strings.reportsAnalytics, "All 17 operational & financial reports", Icons.Default.Analytics, AppDestination.REPORTS, AppModule.REPORTS),
        ModuleHubItem(strings.notifications, "Alerts & local delivery inbox", Icons.Default.NotificationsActive, AppDestination.NOTIFICATIONS, AppModule.NOTIFICATIONS),
        ModuleHubItem(strings.ownerControlCenter, "Branches, RBAC, Backups, Audit & Health", Icons.Default.AdminPanelSettings, AppDestination.OWNER_CONTROL, AppModule.SETTINGS),
        ModuleHubItem("Account & Security", "MFA, sessions, devices & sign out", Icons.Default.Security, AppDestination.ACCOUNT_SECURITY, AppModule.SECURITY)
    ).filter {
        it.requiredModule == AppModule.SECURITY || ctx.hasPermission(it.requiredModule, ActionType.VIEW)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("more_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("All Business Modules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(modules, key = { it.destination.name }) { item ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(item.destination) }
                    .testTag("hub_item_${item.destination.name}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("more_logout_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.logout)
            }
        }
    }
}
