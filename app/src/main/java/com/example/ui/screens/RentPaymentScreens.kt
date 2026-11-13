package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.db.entities.*
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
fun PaymentsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.PAYMENTS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var selectedTab by remember { mutableIntStateOf(0) }
    var showReceivePayment by remember { mutableStateOf(false) }
    var reverseTarget by remember { mutableStateOf<PaymentEntity?>(null) }
    var receiptPreview by remember { mutableStateOf<ReceiptEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().testTag("payments_screen")) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Monthly Rents (${data.monthlyRents.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Payments (${data.payments.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Receipts (${data.receipts.size})") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ctx.hasPermission(AppModule.PAYMENTS, ActionType.POST)) {
                        Button(
                            onClick = { showReceivePayment = true },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("receive_payment_button")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Receive Payment")
                        }
                        OutlinedButton(
                            onClick = { vm.generateMonthlyRent() },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("payments_generate_rent_button")
                        ) {
                            Text("Generate Rent")
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (data.monthlyRents.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Monthly Rent Schedules",
                                description = "Activate an agreement and tap Generate Rent to bill the current period.",
                                icon = Icons.Default.ReceiptLong
                            )
                        }
                    } else {
                        items(data.monthlyRents, key = { it.id }) { rent ->
                            val cust = data.customers.firstOrNull { it.id == rent.customerId }?.fullName ?: "Tenant"
                            val room = data.rooms.firstOrNull { it.id == rent.roomId }?.roomNumber ?: "-"
                            val outstanding = (rent.amountDueFils - rent.amountPaidFils).coerceAtLeast(0L)
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("rent_card_${rent.periodKey}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("$cust • Room $room", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${rent.periodLabel} (${rent.periodKey})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusBadge(rent.status)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Due: ${QatarFormatters.formatQar(rent.amountDueFils)} | Paid: ${QatarFormatters.formatQar(rent.amountPaidFils)} | Bal: ${QatarFormatters.formatQar(outstanding)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (data.payments.isEmpty()) {
                        item { EmptyStateCard("No Payments Yet", "Record confirmed Cash, Bank Transfer, or Card rent receipts here.") }
                    } else {
                        items(data.payments, key = { it.id }) { pay ->
                            val cust = data.customers.firstOrNull { it.id == pay.customerId }?.fullName ?: "Tenant"
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("payment_card_${pay.referenceNumber}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${pay.referenceNumber} • $cust", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${pay.method} • ${QatarFormatters.formatDateTime(pay.receivedAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        StatusBadge(pay.status)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        QarAmountText(fils = pay.amountFils)
                                        if (pay.status == "CONFIRMED" && ctx.hasPermission(AppModule.PAYMENTS, ActionType.REVERSE)) {
                                            OutlinedButton(onClick = { reverseTarget = pay }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                                Text(strings.reverse)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (data.receipts.isEmpty()) {
                        item { EmptyStateCard("No Receipts Issued", "Confirmed payments automatically issue numbered receipts.") }
                    } else {
                        items(data.receipts, key = { it.id }) { rec ->
                            val cust = data.customers.firstOrNull { it.id == rec.customerId }?.fullName ?: "Tenant"
                            OutlinedCard(onClick = { receiptPreview = rec }, modifier = Modifier.fillMaxWidth().testTag("receipt_card_${rec.receiptNumber}")) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${rec.receiptNumber} • $cust", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${rec.method} • ${QatarFormatters.formatDateTime(rec.issuedAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        QarAmountText(fils = rec.amountFils)
                                        StatusBadge(if (rec.isVoided) "REVERSED" else "CONFIRMED")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReceivePayment) {
        var selectedCust by remember { mutableStateOf(data.customers.firstOrNull()) }
        val custAgreements = data.agreements.filter { it.customerId == selectedCust?.id && it.status == "ACTIVE" }
        var selectedAg by remember(selectedCust) { mutableStateOf(custAgreements.firstOrNull()) }
        val openRents = data.monthlyRents.filter { it.customerId == selectedCust?.id && it.status != "PAID" }
        var selectedRent by remember(selectedCust) { mutableStateOf(openRents.firstOrNull()) }
        var amountQar by remember(selectedRent) {
            mutableStateOf(
                selectedRent?.let { "%.2f".format((it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) / 100.0) } ?: ""
            )
        }
        var method by remember { mutableStateOf("CASH") }
        var selectedBank by remember { mutableStateOf(data.bankAccounts.firstOrNull()) }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReceivePayment = false },
            title = { Text("Receive Confirmed Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Tenant", data.customers, selectedCust, { it.fullName }, { selectedCust = it }, testTag = "pay_customer_dropdown")
                    if (openRents.isNotEmpty()) {
                        DropdownSelector("Allocate to Rent", openRents, selectedRent, { "${it.periodKey} (Bal ${QatarFormatters.formatQar(it.amountDueFils - it.amountPaidFils)})" }, {
                            selectedRent = it
                            amountQar = "%.2f".format((it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) / 100.0)
                        })
                    }
                    OutlinedTextField(value = amountQar, onValueChange = { amountQar = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("pay_amount_input"))
                    OutlinedTextField(value = method, onValueChange = { method = it.uppercase() }, label = { Text("Method (CASH/BANK_TRANSFER/CARD)") }, modifier = Modifier.fillMaxWidth().testTag("pay_method_input"))
                    if (method == "BANK_TRANSFER" && data.bankAccounts.isNotEmpty()) {
                        DropdownSelector("Deposit to Bank", data.bankAccounts, selectedBank, { "${it.bankName} - ${it.accountTitle}" }, { selectedBank = it })
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Bank Ref") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cust = selectedCust
                    val amt = QatarFormatters.parseQarToFils(amountQar) ?: 0L
                    if (cust != null) {
                        vm.receivePayment(
                            cust.id,
                            selectedAg?.id ?: selectedRent?.agreementId,
                            amt,
                            method,
                            if (method == "BANK_TRANSFER") selectedBank?.id else null,
                            listOfNotNull(selectedRent?.id),
                            notes
                        )
                    }
                    showReceivePayment = false
                }, modifier = Modifier.testTag("confirm_receive_payment_button")) { Text("Confirm & Issue Receipt") }
            },
            dismissButton = { TextButton(onClick = { showReceivePayment = false }) { Text(strings.cancel) } }
        )
    }

    reverseTarget?.let { pay ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("Reverse Payment ${pay.referenceNumber}") },
            text = {
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Audit Reason for Reversal") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    vm.reversePayment(pay.id, reason)
                    reverseTarget = null
                }) { Text("Post Reversal") }
            },
            dismissButton = { TextButton(onClick = { reverseTarget = null }) { Text(strings.cancel) } }
        )
    }

    receiptPreview?.let { rec ->
        val biz = data.businesses.firstOrNull { it.id == rec.businessId }
        val cust = data.customers.firstOrNull { it.id == rec.customerId }
        AlertDialog(
            onDismissRequest = { receiptPreview = null },
            title = { Text("Official Receipt • ${rec.receiptNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${biz?.name ?: "ONE HOME PRO"} (${biz?.branchName ?: "Qatar"})", fontWeight = FontWeight.Bold)
                    if (!biz?.crNumber.isNullOrBlank()) Text("CR No: ${biz?.crNumber}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Received From: ${cust?.fullName ?: "-"}")
                    Text("Amount: ${QatarFormatters.formatQar(rec.amountFils)}", fontWeight = FontWeight.Bold)
                    Text("Method: ${rec.method}")
                    Text("Issued: ${QatarFormatters.formatDateTime(rec.issuedAtEpoch)}")
                    Text("Status: ${if (rec.isVoided) "VOIDED / REVERSED" else "VALID OFFICIAL RECEIPT"}")
                }
            },
            confirmButton = {
                Button(onClick = { receiptPreview = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ChequesScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.CHEQUES, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var showAdd by remember { mutableStateOf(false) }
    var bounceTarget by remember { mutableStateOf<ChequeEntity?>(null) }
    var replaceTarget by remember { mutableStateOf<ChequeEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("cheques_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.cheques,
                subtitle = "${data.cheques.size} cheques tracked",
                actionLabel = if (ctx.hasPermission(AppModule.CHEQUES, ActionType.CREATE)) "Register Cheque" else null,
                actionTag = "add_cheque_button",
                onAction = { showAdd = true }
            )
        }
        item {
            Surface(color = ArchitecturalGoldContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Accounting Policy: Received & Deposited cheques post to 1020 Cheques Receivable and are NOT counted as cleared Bank Income until transitioned to CLEARED.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArchitecturalGoldDark,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (data.cheques.isEmpty()) {
            item { EmptyStateCard("No Cheques Registered", "Track tenant PDCs from RECEIVED -> DEPOSITED -> CLEARED or BOUNCED.", Icons.Default.AccountBalanceWallet, "Register Cheque", "empty_cheques", { showAdd = true }) }
        } else {
            items(data.cheques, key = { it.id }) { chq ->
                val cust = data.customers.firstOrNull { it.id == chq.customerId }?.fullName ?: "Tenant"
                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("cheque_card_${chq.chequeNumber}")) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cheque #${chq.chequeNumber} • ${chq.bankName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("$cust • Due ${QatarFormatters.formatDate(chq.chequeDateEpoch)}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(chq.status)
                        }
                        Spacer(Modifier.height(6.dp))
                        QarAmountText(fils = chq.amountFils)
                        if (chq.bounceReason.isNotBlank()) {
                            Text("Bounce Reason: ${chq.bounceReason}", color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                        }
                        if (ctx.hasPermission(AppModule.CHEQUES, ActionType.POST)) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (chq.status in listOf("RECEIVED", "HELD", "DUE_SOON")) {
                                    OutlinedButton(onClick = { vm.transitionCheque(chq.id, "DEPOSITED") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Deposit") }
                                }
                                if (chq.status in listOf("RECEIVED", "HELD", "DUE_SOON", "DEPOSITED")) {
                                    Button(onClick = { vm.transitionCheque(chq.id, "CLEARED", "CLR-${chq.chequeNumber}") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("clear_cheque_${chq.chequeNumber}")) { Text("Clear") }
                                    OutlinedButton(onClick = { bounceTarget = chq }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Bounce") }
                                }
                                if (chq.status == "BOUNCED") {
                                    Button(onClick = { replaceTarget = chq }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Issue Replacement") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var selectedCust by remember { mutableStateOf(data.customers.firstOrNull()) }
        var chequeNo by remember { mutableStateOf("") }
        var bankName by remember { mutableStateOf("QNB") }
        var amountQar by remember { mutableStateOf("") }
        var payer by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Register Tenant Cheque (PDC)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Tenant", data.customers, selectedCust, { it.fullName }, {
                        selectedCust = it
                        payer = it.fullName
                    })
                    OutlinedTextField(value = chequeNo, onValueChange = { chequeNo = it }, label = { Text("Cheque Number*") }, modifier = Modifier.fillMaxWidth().testTag("cheque_no_input"))
                    OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Qatar Bank Name*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountQar, onValueChange = { amountQar = it }, label = { Text("Amount (QAR)*") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("cheque_amount_input"))
                    OutlinedTextField(value = payer, onValueChange = { payer = it }, label = { Text("Payer Name") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cust = selectedCust
                    val ag = data.agreements.firstOrNull { it.customerId == cust?.id && it.status == "ACTIVE" }
                    val rent = data.monthlyRents.firstOrNull { it.customerId == cust?.id && it.status != "PAID" }
                    if (cust != null) {
                        vm.registerCheque(
                            cust.id, ag?.id, rent?.id, chequeNo, bankName,
                            QatarFormatters.parseQarToFils(amountQar) ?: 0L,
                            System.currentTimeMillis(), payer.ifBlank { cust.fullName }, "ONE HOME PRO"
                        )
                    }
                    showAdd = false
                }, modifier = Modifier.testTag("save_cheque_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(strings.cancel) } }
        )
    }

    bounceTarget?.let { chq ->
        var reason by remember { mutableStateOf("Insufficient funds") }
        AlertDialog(
            onDismissRequest = { bounceTarget = null },
            title = { Text("Mark Cheque #${chq.chequeNumber} Bounced") },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Bank Return Reason") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    vm.transitionCheque(chq.id, "BOUNCED", "", reason)
                    bounceTarget = null
                }) { Text("Confirm Bounce") }
            },
            dismissButton = { TextButton(onClick = { bounceTarget = null }) { Text(strings.cancel) } }
        )
    }

    replaceTarget?.let { chq ->
        var newNo by remember { mutableStateOf("") }
        var newBank by remember { mutableStateOf(chq.bankName) }
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("Replace Bounced Cheque #${chq.chequeNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newNo, onValueChange = { newNo = it }, label = { Text("New Cheque Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newBank, onValueChange = { newBank = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.replaceCheque(chq.id, newNo, newBank, System.currentTimeMillis())
                    replaceTarget = null
                }) { Text("Register Replacement") }
            },
            dismissButton = { TextButton(onClick = { replaceTarget = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
fun DepositsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.DEPOSITS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var txTarget by remember { mutableStateOf<Pair<SecurityDepositEntity, String>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("deposits_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.securityDeposits,
                subtitle = "Held as liability in GL 2100 (never counted as rental revenue)"
            )
        }
        if (data.deposits.isEmpty()) {
            item { EmptyStateCard("No Security Deposits", "Security deposit ledgers are created automatically when activating lease agreements.") }
        } else {
            items(data.deposits, key = { it.id }) { dep ->
                val cust = data.customers.firstOrNull { it.id == dep.customerId }?.fullName ?: "Tenant"
                val ag = data.agreements.firstOrNull { it.id == dep.agreementId }?.agreementNumber ?: "-"
                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("deposit_card_$ag")) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$cust • $ag", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Expected: ${QatarFormatters.formatQar(dep.expectedFils)} | Held: ${QatarFormatters.formatQar(dep.heldFils)} | Refunded: ${QatarFormatters.formatQar(dep.refundedFils)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                                )
                            }
                            StatusBadge(dep.status)
                        }
                        if (ctx.hasPermission(AppModule.DEPOSITS, ActionType.POST)) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { txTarget = dep to "RECEIVE" }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Receive") }
                                if (dep.heldFils > 0) {
                                    OutlinedButton(onClick = { txTarget = dep to "DEDUCTION" }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Deduct") }
                                    Button(onClick = { txTarget = dep to "REFUND" }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Refund") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    txTarget?.let { (dep, type) ->
        var amountQar by remember {
            mutableStateOf(
                if (type == "RECEIVE") "%.2f".format((dep.expectedFils - dep.receivedFils).coerceAtLeast(0L) / 100.0)
                else "%.2f".format(dep.heldFils / 100.0)
            )
        }
        var reason by remember { mutableStateOf(if (type == "RECEIVE") "Deposit received" else if (type == "REFUND") "Move-out settlement" else "Damage repair deduction") }
        AlertDialog(
            onDismissRequest = { txTarget = null },
            title = { Text("Deposit $type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amountQar, onValueChange = { amountQar = it }, label = { Text("Amount (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Audit Reason / Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.recordDepositTransaction(dep.id, type, QatarFormatters.parseQarToFils(amountQar) ?: 0L, reason)
                    txTarget = null
                }) { Text("Post $type") }
            },
            dismissButton = { TextButton(onClick = { txTarget = null }) { Text(strings.cancel) } }
        )
    }
}
