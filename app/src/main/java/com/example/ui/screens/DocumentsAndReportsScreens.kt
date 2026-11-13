package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.document.DocumentCategoryOption
import com.example.domain.document.DocumentTargetType
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.DOCUMENTS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var selectedTab by remember { mutableIntStateOf(0) }
    var showScannerStudio by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().testTag("documents_screen")) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Vault Files (${data.documents.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Access Audit (${data.documentAccessLogs.size})") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                SectionHeader(
                    title = strings.documents,
                    subtitle = "Camera scan, multi-page PDF, Photo Picker & encrypted vault",
                    actionLabel = if (ctx.hasPermission(AppModule.DOCUMENTS, ActionType.CREATE)) "Scan / Upload" else null,
                    actionTag = "upload_document_button",
                    onAction = { showScannerStudio = true }
                )
            }
            if (selectedTab == 0) {
                if (data.documents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Document Vault Empty",
                            description = "Scan Qatar ID, Passport, Agreements, Receipts, Invoices, Cheques or Property Records cleanly.",
                            icon = Icons.Default.DocumentScanner,
                            actionLabel = if (ctx.hasPermission(AppModule.DOCUMENTS, ActionType.CREATE)) "Open Scanner & Upload" else null,
                            testTag = "empty_documents",
                            onAction = { showScannerStudio = true }
                        )
                    }
                } else {
                    items(data.documents, key = { it.id }) { doc ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("doc_card_${doc.id}")) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${doc.category} • ${doc.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${doc.entityType} • ${doc.mimeType} • ${doc.fileSizeBytes} bytes", style = MaterialTheme.typography.bodySmall)
                                        Text("SHA-256: ${doc.sha256Checksum.take(18)}...", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                                    }
                                    StatusBadge(if (doc.isRestricted) "RESTRICTED" else "STANDARD")
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.logDocumentPreview(doc.id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                        Text("Log Verified Preview")
                                    }
                                    if (ctx.hasPermission(AppModule.DOCUMENTS, ActionType.ARCHIVE)) {
                                        OutlinedButton(onClick = { vm.deleteVaultDocument(doc.id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Delete", color = CrimsonError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(data.documentAccessLogs, key = { it.id }) { log ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${log.accessAction} • Doc ${log.documentId.take(8)}", style = MaterialTheme.typography.titleSmall)
                                Text(QatarFormatters.formatDateTime(log.accessedAtEpoch), style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(log.outcome)
                        }
                    }
                }
            }
        }
    }

    if (showScannerStudio) {
        DocumentScannerStudioDialog(
            uiState = uiState,
            vm = vm,
            initialTargetType = DocumentTargetType.TENANT,
            initialCategory = DocumentCategoryOption.QID,
            onDismiss = { showScannerStudio = false }
        )
    }
}

@Composable
fun NotificationsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val strings = LocalAppStrings.current
    val list = uiState.data.notifications
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("notifications_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(strings.notifications, "${list.count { !it.isRead }} unread • ${list.size} total")
        }
        if (list.isEmpty()) {
            item { EmptyStateCard("All Caught Up", "Alerts for rent due, bounced cheques, low stock, approvals, and security events appear here.") }
        } else {
            items(list, key = { it.id }) { notif ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(notif.category)
                                StatusBadge(notif.deliveryStatus)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(notif.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(notif.message, style = MaterialTheme.typography.bodySmall)
                            Text(QatarFormatters.formatDateTime(notif.createdAtEpoch), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!notif.isRead) {
                            IconButton(onClick = { vm.markNotificationRead(notif.id) }) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = "Mark as read")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(uiState: OneHomeUiState) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.REPORTS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val data = uiState.data
    val tb = uiState.reports.trialBalance
    val pnl = uiState.reports.profitAndLoss
    val bs = uiState.reports.balanceSheet
    var selectedReportIdx by remember { mutableIntStateOf(0) }

    val reportTitles = listOf(
        "1. Occupancy & Vacancy",
        "2. Property Revenue & Collections",
        "3. Tenant Outstanding & Aging",
        "4. Overdue Rent Report",
        "5. Monthly Collection Summary",
        "6. Deposit Held / Deduction / Refund",
        "7. Agreement Expiry & Renewal",
        "8. Cheque Status & Bounced",
        "9. Trial Balance",
        "10. Profit & Loss (P&L)",
        "11. Balance Sheet",
        "12. General Ledger & Journal Audit",
        "13. Cashbook, Bank & Petty Cash",
        "14. Maintenance Cost & SLA",
        "15. Inventory Valuation & Low Stock",
        "16. Purchase & Supplier Payables",
        "17. Staff Attendance & Activity Audit"
    )

    val reportLines: List<String> = remember(selectedReportIdx, data, tb, pnl, bs) {
        when (selectedReportIdx) {
            0 -> {
                val tot = data.rooms.size
                val occ = data.rooms.count { it.status == "OCCUPIED" }
                val vac = data.rooms.count { it.status == "VACANT" }
                val maint = data.rooms.count { it.status == "MAINTENANCE" }
                listOf("Total Rooms: $tot", "Occupied: $occ", "Vacant: $vac", "Maintenance: $maint", "Occupancy Rate: ${if (tot == 0) 0 else (occ * 100 / tot)}%") +
                    data.rooms.map { "Room ${it.roomNumber}: ${it.status} • Rate ${QatarFormatters.formatQar(it.monthlyRateFils)}" }
            }
            1 -> data.properties.map { p ->
                val billed = data.monthlyRents.filter { it.propertyId == p.id }.sumOf { it.amountDueFils }
                val paid = data.monthlyRents.filter { it.propertyId == p.id }.sumOf { it.amountPaidFils }
                "${p.code} ${p.name}: Billed ${QatarFormatters.formatQar(billed)} | Collected ${QatarFormatters.formatQar(paid)}"
            }
            2 -> data.customers.map { c ->
                val due = data.monthlyRents.filter { it.customerId == c.id }.sumOf { (it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) }
                "${c.customerCode} ${c.fullName}: Outstanding ${QatarFormatters.formatQar(due)}"
            }
            3 -> data.monthlyRents.filter { it.status != "PAID" && (it.status == "OVERDUE" || it.dueDateEpoch < System.currentTimeMillis()) }
                .map { "${it.periodKey}: Due ${QatarFormatters.formatQar(it.amountDueFils - it.amountPaidFils)}" }
            4 -> data.payments.map { "${it.referenceNumber} (${it.method}): ${QatarFormatters.formatQar(it.amountFils)} [${it.status}]" }
            5 -> data.deposits.map { "Expected ${QatarFormatters.formatQar(it.expectedFils)} | Held ${QatarFormatters.formatQar(it.heldFils)} | Deducted ${QatarFormatters.formatQar(it.deductedFils)} | Refunded ${QatarFormatters.formatQar(it.refundedFils)}" }
            6 -> data.agreements.map { "${it.agreementNumber}: ${QatarFormatters.formatDate(it.startDateEpoch)} -> ${QatarFormatters.formatDate(it.endDateEpoch)} [${it.status}]" }
            7 -> data.cheques.map { "Cheque #${it.chequeNumber} (${it.bankName}): ${QatarFormatters.formatQar(it.amountFils)} [${it.status}]" }
            8 -> tb.map { "${it.code} ${it.name}: DR ${QatarFormatters.formatQar(it.totalDebitFils)} | CR ${QatarFormatters.formatQar(it.totalCreditFils)}" }
            9 -> listOf(
                "Total Income: ${QatarFormatters.formatQar(pnl?.totalIncomeFils ?: 0L)}",
                "Total Expense: ${QatarFormatters.formatQar(pnl?.totalExpenseFils ?: 0L)}",
                "Net Profit: ${QatarFormatters.formatQar(pnl?.netProfitFils ?: 0L)}"
            )
            10 -> listOf(
                "Total Assets: ${QatarFormatters.formatQar(bs?.totalAssetsFils ?: 0L)}",
                "Total Liabilities: ${QatarFormatters.formatQar(bs?.totalLiabilitiesFils ?: 0L)}",
                "Equity + Earnings: ${QatarFormatters.formatQar(bs?.totalEquityPlusCurrentEarningsFils ?: 0L)}",
                "Balanced: ${bs?.isBalanced ?: true}"
            )
            11 -> data.journals.map { "${it.entryNumber} [${it.sourceType}]: ${it.description} (${QatarFormatters.formatQar(it.totalDebitFils)})" }
            12 -> data.bankAccounts.map { "${it.bankName} ${it.accountTitle}: ${QatarFormatters.formatQar(it.currentBalanceFils)}" } +
                data.cashbookEntries.map { "Cashbook ${it.direction}: ${QatarFormatters.formatQar(it.amountFils)} (${it.description})" }
            13 -> data.complaints.map { "${it.complaintNumber} ${it.title} [${it.priority} / ${it.status}]" }
            14 -> data.items.map { "${it.sku} ${it.name}: Qty ${it.currentStockQty} @ ${QatarFormatters.formatQar(it.weightedAvgCostFils)} (Reorder <= ${it.reorderLevel})" }
            15 -> data.purchases.map { "${it.purchaseNumber}: Total ${QatarFormatters.formatQar(it.totalAmountFils)} | Unpaid ${QatarFormatters.formatQar(it.totalAmountFils - it.paidAmountFils)} [${it.status}]" }
            else -> data.attendance.map { "${it.dateKey}: Emp ${it.employeeId.take(6)} [${it.status}]" } +
                data.auditLogs.take(20).map { "${it.action} ${it.entityType}: ${it.summary}" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader("17 Financial & Operational Reports", "Live computed from QAR SQLite/GL records")
        }
        item {
            DropdownSelector(
                label = "Select Report",
                items = reportTitles.indices.toList(),
                selectedItem = selectedReportIdx,
                itemLabel = { reportTitles[it] },
                onSelect = { selectedReportIdx = it },
                testTag = "report_selector_dropdown"
            )
        }
        if (reportLines.isEmpty()) {
            item { EmptyStateCard("No Rows For This Report", "Real records will appear here once entered.") }
        } else {
            items(reportLines) { line ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
