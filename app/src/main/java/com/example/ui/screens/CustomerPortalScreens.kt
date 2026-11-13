package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun CustomerPortalScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    val strings = LocalAppStrings.current
    val data = uiState.data
    val myCustId = ctx.customerId
    val myAgreements = data.agreements.filter { it.customerId == myCustId }
    val activeAg = myAgreements.firstOrNull { it.status == "ACTIVE" } ?: myAgreements.firstOrNull()
    val myRoom = data.rooms.firstOrNull { it.id == activeAg?.roomId }
    val myProp = data.properties.firstOrNull { it.id == activeAg?.propertyId }
    val myRents = data.monthlyRents.filter { it.customerId == myCustId }
    val outstandingFils = myRents.filter { it.status != "PAID" && it.status != "WAIVED" }
        .sumOf { (it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) }
    val myDeposits = data.deposits.filter { it.customerId == myCustId }
    val myPayments = data.payments.filter { it.customerId == myCustId }
    val myReceipts = data.receipts.filter { it.customerId == myCustId }
    val myComplaints = data.complaints.filter { it.customerId == myCustId }
    val myDocs = data.documents.filter { it.entityId == myCustId }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showNewComplaint by remember { mutableStateOf(false) }
    var showUploadDoc by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().testTag("customer_portal_screen")) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview & Agreement") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Rent & Receipts") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("My Complaints (${myComplaints.size})") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("My Documents (${myDocs.size})") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Welcome, ${ctx.fullName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Isolated Tenant Portal • Asia/Qatar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text("Outstanding Balance: ${QatarFormatters.formatQar(outstandingFils)}", style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily), color = if (outstandingFils > 0) CrimsonError else EmeraldSuccess)
                                if (myProp != null && myRoom != null) {
                                    Text("${strings.myRoom}: ${myProp.name} • Room ${myRoom.roomNumber} (${myRoom.roomType})", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (activeAg != null) {
                                    Text("${strings.myAgreement}: ${activeAg.agreementNumber} • Rent ${QatarFormatters.formatQar(activeAg.rentAmountFils)} • Ends ${QatarFormatters.formatDate(activeAg.endDateEpoch)}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (myDeposits.isNotEmpty()) {
                                    Text("Security Deposit Held: ${QatarFormatters.formatQar(myDeposits.sumOf { it.heldFils })}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { vm.logout() },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("customer_logout_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.logout)
                        }
                    }
                }
                1 -> {
                    item { Text(strings.myRent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (myRents.isEmpty()) {
                        item { EmptyStateCard("No Rent Schedules", "Your billed monthly rent schedules will appear here.") }
                    } else {
                        items(myRents, key = { it.id }) { rent ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${rent.periodLabel} (${rent.periodKey})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Due: ${QatarFormatters.formatQar(rent.amountDueFils)} | Paid: ${QatarFormatters.formatQar(rent.amountPaidFils)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                    }
                                    StatusBadge(rent.status)
                                }
                            }
                        }
                    }
                    item { Text("${strings.myPayments} & ${strings.myReceipts}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(myReceipts, key = { it.id }) { rec ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Receipt ${rec.receiptNumber}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${rec.method} • ${QatarFormatters.formatDateTime(rec.issuedAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                                }
                                QarAmountText(fils = rec.amountFils)
                            }
                        }
                    }
                    if (myPayments.isEmpty()) {
                        item { Text("No confirmed payments yet.", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                2 -> {
                    item {
                        SectionHeader(
                            title = strings.myComplaints,
                            subtitle = "Track repair progress and confirm completion",
                            actionLabel = if (myProp != null) strings.submitComplaint else null,
                            actionTag = "customer_submit_complaint_button",
                            onAction = { showNewComplaint = true }
                        )
                    }
                    if (myComplaints.isEmpty()) {
                        item { EmptyStateCard("No Complaints", "Report maintenance issues directly to property management.") }
                    } else {
                        items(myComplaints, key = { it.id }) { cmp ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${cmp.complaintNumber} • ${cmp.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${cmp.category} • ${cmp.priority}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        StatusBadge(cmp.status)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(cmp.description, style = MaterialTheme.typography.bodySmall)
                                    if (!cmp.customerConfirmed && cmp.status in listOf("AWAITING_CUSTOMER", "COMPLETED", "AWAITING_APPROVAL")) {
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = { vm.customerConfirmComplaint(cmp.id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Confirm Issue Resolved")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        SectionHeader("My Uploaded Documents", "Stored in private vault", "Upload File", "customer_upload_doc_button") {
                            showUploadDoc = true
                        }
                    }
                    if (myDocs.isEmpty()) {
                        item { EmptyStateCard("No Personal Documents", "Upload your QID, Passport, or signed copy securely.") }
                    } else {
                        items(myDocs, key = { it.id }) { doc ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${doc.category} • ${doc.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${doc.mimeType} • ${doc.fileSizeBytes} bytes", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusBadge("SAVED")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewComplaint && myProp != null) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("PLUMBING") }
        AlertDialog(
            onDismissRequest = { showNewComplaint = false },
            title = { Text(strings.submitComplaint) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Issue Title*") }, modifier = Modifier.fillMaxWidth().testTag("cust_complaint_title"))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cat, onValueChange = { cat = it.uppercase() }, label = { Text("Category (PLUMBING/ELECTRICAL/HVAC)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createComplaint(myProp.id, myRoom?.id, myCustId, title, desc, cat, "MEDIUM", false)
                    showNewComplaint = false
                }, modifier = Modifier.testTag("cust_complaint_submit")) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showNewComplaint = false }) { Text(strings.cancel) } }
        )
    }

    if (showUploadDoc && myCustId != null) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("QID") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pickedUri = uri }
        val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickedUri = uri }
        AlertDialog(
            onDismissRequest = { showUploadDoc = false },
            title = { Text("Upload Personal Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it.uppercase() }, label = { Text("Category (QID/PASSPORT/OTHER)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("Photo") }
                        OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)) { Text("PDF") }
                    }
                }
            },
            confirmButton = {
                Button(enabled = pickedUri != null, onClick = {
                    pickedUri?.let { vm.uploadVaultDocument(it, "CUSTOMER", myCustId, category, title) }
                    showUploadDoc = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showUploadDoc = false }) { Text(strings.cancel) } }
        )
    }
}
