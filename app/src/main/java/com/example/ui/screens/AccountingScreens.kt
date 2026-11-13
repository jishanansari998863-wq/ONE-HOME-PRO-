package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.db.entities.JournalEntryEntity
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun AccountingScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.ACCOUNTING, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var selectedTab by remember { mutableIntStateOf(0) }
    var showManualJournal by remember { mutableStateOf(false) }
    var reverseJournalTarget by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var showAddBank by remember { mutableStateOf(false) }
    var showReconcile by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }
    var showPettyReplenish by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf<String?>(null) } // "CLOSE" or "REOPEN"

    Column(modifier = Modifier.fillMaxSize().testTag("accounting_screen")) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Journals (${data.journals.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Trial Balance / P&L / BS") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Chart of Accounts (${data.accounts.size})") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Bank & Cashbook") })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Income & Expense") })
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("Fiscal Periods") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        SectionHeader(
                            title = "Double-Entry Journals",
                            subtitle = "Every posting enforces Sum(Debit) == Sum(Credit)",
                            actionLabel = if (ctx.hasPermission(AppModule.ACCOUNTING, ActionType.POST)) "Manual Journal" else null,
                            actionTag = "new_journal_button",
                            onAction = { showManualJournal = true }
                        )
                    }
                    if (data.journals.isEmpty()) {
                        item { EmptyStateCard("No Posted Journals", "Financial transactions automatically create balanced double-entry journals here.") }
                    } else {
                        items(data.journals, key = { it.id }) { je ->
                            val lines = data.journalLines.filter { it.journalEntryId == je.id }
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("journal_card_${je.entryNumber}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${je.entryNumber} • ${je.sourceType}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(je.description, style = MaterialTheme.typography.bodySmall)
                                            Text(QatarFormatters.formatDateTime(je.entryDateEpoch), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusBadge(je.status)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    lines.forEach { ln ->
                                        val accName = data.accounts.firstOrNull { it.id == ln.accountId }?.let { "${it.code} ${it.name}" } ?: ln.accountId
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(accName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(
                                                if (ln.debitFils > 0) "DR ${QatarFormatters.formatQar(ln.debitFils)}" else "CR ${QatarFormatters.formatQar(ln.creditFils)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                                            )
                                        }
                                    }
                                    if (je.status == "POSTED" && ctx.hasPermission(AppModule.ACCOUNTING, ActionType.REVERSE)) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(onClick = { reverseJournalTarget = je }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Reverse Entry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val tb = uiState.reports.trialBalance
                    val pnl = uiState.reports.profitAndLoss
                    val bs = uiState.reports.balanceSheet
                    item {
                        Button(onClick = { vm.refreshFinancialReports() }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Recompute Financial Statements")
                        }
                    }
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Trial Balance Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val totDr = tb.sumOf { it.totalDebitFils }
                                val totCr = tb.sumOf { it.totalCreditFils }
                                Text("Total Debits:  ${QatarFormatters.formatQar(totDr)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                Text("Total Credits: ${QatarFormatters.formatQar(totCr)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                Spacer(Modifier.height(4.dp))
                                StatusBadge(if (totDr == totCr) "BALANCED" else "DISCREPANCY")
                            }
                        }
                    }
                    if (pnl != null) {
                        item {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Profit & Loss Statement (QAR)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Income: ${QatarFormatters.formatQar(pnl.totalIncomeFils)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
                                    Text("Expense: ${QatarFormatters.formatQar(pnl.totalExpenseFils)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
                                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                    Text("Net Profit: ${QatarFormatters.formatQar(pnl.netProfitFils)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily))
                                }
                            }
                        }
                    }
                    if (bs != null) {
                        item {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Balance Sheet Summary (QAR)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Total Assets: ${QatarFormatters.formatQar(bs.totalAssetsFils)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
                                    Text("Total Liabilities: ${QatarFormatters.formatQar(bs.totalLiabilitiesFils)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
                                    Text("Equity + Current Earnings: ${QatarFormatters.formatQar(bs.totalEquityPlusCurrentEarningsFils)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
                                    Spacer(Modifier.height(6.dp))
                                    StatusBadge(if (bs.isBalanced) "ASSETS = LIAB + EQUITY" else "UNBALANCED")
                                }
                            }
                        }
                    }
                }
                2 -> {
                    items(data.accounts, key = { it.id }) { acc ->
                        val row = uiState.reports.trialBalance.firstOrNull { it.accountId == acc.id }
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${acc.code} • ${acc.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("${acc.accountType} (Normal ${acc.normalBalance})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                QarAmountText(fils = row?.netBalanceFils ?: 0L)
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAddBank = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("+ Bank Account") }
                            OutlinedButton(onClick = { showReconcile = true }, enabled = data.bankAccounts.isNotEmpty(), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Reconcile Bank") }
                            OutlinedButton(onClick = { showPettyReplenish = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("+ Petty Cash") }
                        }
                    }
                    items(data.bankAccounts, key = { it.id }) { ba ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ba.bankName} • ${ba.accountTitle}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("IBAN/No: ${ba.ibanOrNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                                QarAmountText(fils = ba.currentBalanceFils)
                            }
                        }
                    }
                    item {
                        Text("Cashbook (${data.cashbookEntries.size}) • Petty Cash (${data.pettyCashEntries.size}) • Reconciliations (${data.bankReconciliations.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(data.cashbookEntries.take(15), key = { it.id }) { cb ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${cb.direction} • ${cb.description}", style = MaterialTheme.typography.bodyMedium)
                                    Text("${cb.reference} • ${QatarFormatters.formatDate(cb.entryDateEpoch)}", style = MaterialTheme.typography.bodySmall)
                                }
                                QarAmountText(fils = cb.amountFils)
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showIncome = true }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("+ Income") }
                            Button(onClick = { showExpense = true }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("+ Expense") }
                        }
                    }
                    items(data.expenseRecords, key = { it.id }) { exp ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${exp.category} • ${exp.vendorName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(exp.description, style = MaterialTheme.typography.bodySmall)
                                }
                                QarAmountText(fils = exp.amountFils, color = CrimsonError)
                            }
                        }
                    }
                    items(data.incomeRecords, key = { it.id }) { inc ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inc.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(inc.description, style = MaterialTheme.typography.bodySmall)
                                }
                                QarAmountText(fils = inc.amountFils, color = EmeraldSuccess)
                            }
                        }
                    }
                }
                5 -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showPeriodDialog = "CLOSE" }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("Close Fiscal Period") }
                            if (ctx.userType == "OWNER") {
                                OutlinedButton(onClick = { showPeriodDialog = "REOPEN" }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("Reopen Period") }
                            }
                        }
                    }
                    items(data.fiscalPeriods, key = { it.id }) { fp ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Period ${fp.periodCode}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (fp.reopenReason.isNotBlank()) Text("Reopen Reason: ${fp.reopenReason}", style = MaterialTheme.typography.bodySmall)
                                }
                                StatusBadge(fp.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualJournal) {
        var desc by remember { mutableStateOf("") }
        var debitAcc by remember { mutableStateOf(data.accounts.firstOrNull()) }
        var creditAcc by remember { mutableStateOf(data.accounts.lastOrNull()) }
        var amountQar by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualJournal = false },
            title = { Text("Post Balanced Manual Journal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Debit Account", data.accounts, debitAcc, { "${it.code} ${it.name}" }, { debitAcc = it })
                    DropdownSelector("Credit Account", data.accounts, creditAcc, { "${it.code} ${it.name}" }, { creditAcc = it })
                    OutlinedTextField(value = amountQar, onValueChange = { amountQar = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Journal Narration") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val dr = debitAcc
                    val cr = creditAcc
                    val amt = QatarFormatters.parseQarToFils(amountQar) ?: 0L
                    if (dr != null && cr != null) vm.postManualJournal(desc, dr.code, cr.code, amt)
                    showManualJournal = false
                }) { Text("Post Journal") }
            },
            dismissButton = { TextButton(onClick = { showManualJournal = false }) { Text(strings.cancel) } }
        )
    }

    reverseJournalTarget?.let { je ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reverseJournalTarget = null },
            title = { Text("Reverse ${je.entryNumber}") },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Audit Reason") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    vm.reverseJournal(je.id, reason)
                    reverseJournalTarget = null
                }) { Text("Post Reversal") }
            },
            dismissButton = { TextButton(onClick = { reverseJournalTarget = null }) { Text(strings.cancel) } }
        )
    }

    if (showAddBank) {
        var bank by remember { mutableStateOf("QNB") }
        var title by remember { mutableStateOf("Operating Account") }
        var iban by remember { mutableStateOf("QA") }
        var opening by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showAddBank = false },
            title = { Text("Add Qatar Bank Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Account Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = iban, onValueChange = { iban = it }, label = { Text("IBAN / Account Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Balance (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createBankAccount(bank, title, iban, QatarFormatters.parseQarToFils(opening) ?: 0L)
                    showAddBank = false
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddBank = false }) { Text(strings.cancel) } }
        )
    }

    if (showReconcile) {
        var bank by remember { mutableStateOf(data.bankAccounts.firstOrNull()) }
        var stmtBal by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReconcile = false },
            title = { Text("Bank Statement Reconciliation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Bank Account", data.bankAccounts, bank, { "${it.bankName} (Book ${QatarFormatters.formatQar(it.currentBalanceFils)})" }, { bank = it })
                    OutlinedTextField(value = stmtBal, onValueChange = { stmtBal = it }, label = { Text("Bank Statement Ending Balance (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Reconciliation Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    bank?.let { vm.reconcileBank(it.id, QatarFormatters.parseQarToFils(stmtBal) ?: 0L, notes) }
                    showReconcile = false
                }) { Text("Reconcile") }
            },
            dismissButton = { TextButton(onClick = { showReconcile = false }) { Text(strings.cancel) } }
        )
    }

    if (showIncome) {
        var cat by remember { mutableStateOf("PARKING_FEE") }
        var desc by remember { mutableStateOf("") }
        var amt by remember { mutableStateOf("") }
        var toBank by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showIncome = false },
            title = { Text("Record Other Income") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = toBank, onCheckedChange = { toBank = it })
                        Text("Received into Bank (uncheck for Cash)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.recordIncome(cat, desc, QatarFormatters.parseQarToFils(amt) ?: 0L, toBank)
                    showIncome = false
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showIncome = false }) { Text(strings.cancel) } }
        )
    }

    if (showExpense) {
        var cat by remember { mutableStateOf("UTILITIES") }
        var vendor by remember { mutableStateOf("Kahramaa") }
        var desc by remember { mutableStateOf("") }
        var amt by remember { mutableStateOf("") }
        var method by remember { mutableStateOf("BANK") }
        AlertDialog(
            onDismissRequest = { showExpense = false },
            title = { Text("Record Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = vendor, onValueChange = { vendor = it }, label = { Text("Vendor / Payee") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = method, onValueChange = { method = it.uppercase() }, label = { Text("Method (BANK/CASH/PETTY_CASH)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.recordExpense(cat, vendor, desc, QatarFormatters.parseQarToFils(amt) ?: 0L, method)
                    showExpense = false
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showExpense = false }) { Text(strings.cancel) } }
        )
    }

    if (showPettyReplenish) {
        var amt by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("Petty cash float top-up") }
        AlertDialog(
            onDismissRequest = { showPettyReplenish = false },
            title = { Text("Replenish Petty Cash") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.replenishPettyCash(QatarFormatters.parseQarToFils(amt) ?: 0L, notes)
                    showPettyReplenish = false
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showPettyReplenish = false }) { Text(strings.cancel) } }
        )
    }

    showPeriodDialog?.let { mode ->
        var code by remember { mutableStateOf(QatarFormatters.periodKey()) }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPeriodDialog = null },
            title = { Text(if (mode == "CLOSE") "Close Fiscal Period" else "Reopen Fiscal Period") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Period Code (YYYY-MM)") }, modifier = Modifier.fillMaxWidth())
                    if (mode == "REOPEN") {
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Owner Audit Reason") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (mode == "CLOSE") vm.closeFiscalPeriod(code) else vm.reopenFiscalPeriod(code, reason)
                    showPeriodDialog = null
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showPeriodDialog = null }) { Text(strings.cancel) } }
        )
    }
}
