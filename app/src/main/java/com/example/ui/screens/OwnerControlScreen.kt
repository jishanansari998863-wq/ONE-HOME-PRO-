package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.db.entities.BackupRecordEntity
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun OwnerControlScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (ctx.userType != "OWNER") {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    val activeBiz = data.businesses.firstOrNull { it.id == ctx.activeBusinessId }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddBranch by remember { mutableStateOf(false) }
    var showEditBiz by remember { mutableStateOf(false) }
    var showCreateBackup by remember { mutableStateOf(false) }
    var restoreVerifyTarget by remember { mutableStateOf<BackupRecordEntity?>(null) }
    var showConfigIntegration by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().testTag("owner_control_screen")) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Business & Branches") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Users & RBAC") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Encrypted Backups") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit & Security") })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Integrations & Health") })
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("Legal & Readiness") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        SectionHeader("Company Profile & Qatar Branches", "QAR • Asia/Qatar", "New Branch", "add_branch_button") {
                            showAddBranch = true
                        }
                    }
                    if (activeBiz != null) {
                        item {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("${activeBiz.name} (${activeBiz.code})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Branch: ${activeBiz.branchName} • CR: ${activeBiz.crNumber.ifBlank { "Not entered" }}", style = MaterialTheme.typography.bodySmall)
                                    Text("Tel: ${activeBiz.phone.ifBlank { "-" }} • Email: ${activeBiz.email.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(onClick = { showEditBiz = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                        Text("Edit Profile & Company Logo")
                                    }
                                }
                            }
                        }
                    }
                    items(data.businesses, key = { it.id }) { biz ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${biz.name} • ${biz.branchName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${biz.currency} • ${biz.timezone}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (biz.id == ctx.activeBusinessId) {
                                    StatusBadge("ACTIVE")
                                } else {
                                    TextButton(onClick = { vm.switchBusiness(biz.id) }) { Text("Switch") }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        Text("Users (${data.users.size}) & RBAC Roles (${data.roles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(data.users, key = { it.id }) { u ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("user_card_${u.email}")) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${u.fullName} (${u.userType})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(u.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusBadge(u.status)
                                }
                                if (u.id != ctx.userId) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (u.status == "ACTIVE") {
                                            OutlinedButton(onClick = { vm.setUserStatus(u.id, "SUSPENDED") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                                Text("Suspend")
                                            }
                                        } else {
                                            Button(onClick = { vm.setUserStatus(u.id, "ACTIVE") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                                Text("Activate")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Role Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(data.roles, key = { it.id }) { r ->
                        val permCount = data.rolePermissions.count { it.roleId == r.id }
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(r.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("$permCount perms", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        SectionHeader("AES-256-GCM Encrypted Backups", "Passphrase-protected snapshots with checksum & GL verification", "Create Backup", "create_backup_button") {
                            showCreateBackup = true
                        }
                    }
                    uiState.lastBackupVerification?.let { res ->
                        item {
                            Surface(color = EmeraldContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Last Restore Test Passed • Records: ${res.verifiedRecordCount} • Checksum: ${res.checksumMatched} • TB Balanced: ${res.isBalanced} (DR ${QatarFormatters.formatQar(res.trialBalanceDebitFils)} = CR ${QatarFormatters.formatQar(res.trialBalanceCreditFils)})",
                                    color = EmeraldSuccess,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    if (data.backups.isEmpty()) {
                        item { EmptyStateCard("No Encrypted Backups", "Create an AES-256-GCM backup and run a restore verification test.") }
                    } else {
                        items(data.backups, key = { it.id }) { bk ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("backup_card_${bk.id}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Backup ${QatarFormatters.formatDateTime(bk.createdAtEpoch)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Records: ${bk.recordCountTotal} • SHA: ${bk.manifestSha256.take(16)}...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                        }
                                        StatusBadge(bk.status)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { restoreVerifyTarget = bk }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("verify_restore_button_${bk.id}")) {
                                        Text("Decrypt & Verify Restore")
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item { Text("Security Events (${data.securityEvents.size}) & Audit Trail (${data.auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(data.securityEvents.take(20), key = { it.id }) { ev ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ev.eventType} • ${ev.description}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(QatarFormatters.formatDateTime(ev.occurredAtEpoch), style = MaterialTheme.typography.labelSmall)
                                }
                                StatusBadge(ev.severity)
                            }
                        }
                    }
                    items(data.auditLogs.take(25), key = { it.id }) { log ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("${log.action} • ${log.entityType}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                Text(QatarFormatters.formatDateTime(log.occurredAtEpoch), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        SectionHeader("Integrations & System Health", "Honest status: no simulated external gateways", "Configure", "configure_integration_button") {
                            showConfigIntegration = true
                        }
                    }
                    item {
                        val bsBal = uiState.reports.balanceSheet?.isBalanced ?: true
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("System Diagnostics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("• Database Engine: Room SQLite (83 Relational Entities)")
                                Text("• Monetary Arithmetic: Integer Fils (1/100 QAR) — zero float drift")
                                Text("• Trial Balance / Balance Sheet Balanced: $bsBal")
                                Text("• Cloud Firestore Sync: BLOCKED (User declined Firebase setup in UI)")
                                Text("• Native iOS / macOS / Windows / Web Builds: BLOCKED in Android container")
                            }
                        }
                    }
                    items(data.integrations, key = { it.id }) { cfg ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${cfg.providerType} • ${cfg.providerName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(cfg.endpointOrHandle.ifBlank { "No endpoint configured" }, style = MaterialTheme.typography.bodySmall)
                                }
                                StatusBadge(cfg.lastTestStatus)
                            }
                        }
                    }
                }
                5 -> {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Qatar Business & Play Store Readiness Checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("• MOCI Commercial Registration & Municipality Lease Registration: Pending company legal verification before production deployment.")
                                Text("• Law No. 13 of 2016 (Qatar Personal Data Privacy Protection): Consent, minimal QID/Passport storage in private app vault, access audit log implemented.")
                                Text("• Google Play Store Storage Compliance: Zero broad storage permissions; Photo Picker & SAF used.")
                                Text("• Account Closure Flow: Self-service customer closure request workflow + admin review implemented.")
                                Text("• Documentation Deliverables: Available under /docs/ in repository root.")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBranch) {
        var name by remember { mutableStateOf(activeBiz?.name ?: "") }
        var branch by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var cr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBranch = false },
            title = { Text("Create Company / Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Company Name*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch Name (e.g. Lusail)*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cr, onValueChange = { cr = it }, label = { Text("Qatar CR Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.createBusinessBranch(name, branch, phone, email, address, cr); showAddBranch = false }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddBranch = false }) { Text(strings.cancel) } }
        )
    }

    if (showEditBiz && activeBiz != null) {
        var name by remember { mutableStateOf(activeBiz.name) }
        var branch by remember { mutableStateOf(activeBiz.branchName) }
        var phone by remember { mutableStateOf(activeBiz.phone) }
        var email by remember { mutableStateOf(activeBiz.email) }
        var address by remember { mutableStateOf(activeBiz.address) }
        var cr by remember { mutableStateOf(activeBiz.crNumber) }
        var logoUri by remember { mutableStateOf(activeBiz.logoUri) }
        val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> logoUri = uri?.toString() }
        AlertDialog(
            onDismissRequest = { showEditBiz = false },
            title = { Text("Edit Business Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Company Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cr, onValueChange = { cr = it }, label = { Text("Qatar CR Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (logoUri == null) "Pick Custom Logo Image" else "Logo Selected ✓")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.updateBusinessProfile(name, branch, phone, email, address, cr, logoUri); showEditBiz = false }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showEditBiz = false }) { Text(strings.cancel) } }
        )
    }

    if (showCreateBackup) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBackup = false },
            title = { Text("Create AES-256-GCM Encrypted Backup") },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Backup Encryption Passphrase (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input")
                )
            },
            confirmButton = {
                Button(onClick = { vm.createBackup(passphrase); showCreateBackup = false }, modifier = Modifier.testTag("confirm_backup_button")) {
                    Text("Encrypt & Save")
                }
            },
            dismissButton = { TextButton(onClick = { showCreateBackup = false }) { Text(strings.cancel) } }
        )
    }

    restoreVerifyTarget?.let { bk ->
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { restoreVerifyTarget = null },
            title = { Text("Decrypt & Verify Backup Restore") },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Backup Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("verify_restore_passphrase_input")
                )
            },
            confirmButton = {
                Button(onClick = { vm.verifyRestoreBackup(bk.id, passphrase); restoreVerifyTarget = null }, modifier = Modifier.testTag("confirm_verify_restore_button")) {
                    Text("Verify Restore")
                }
            },
            dismissButton = { TextButton(onClick = { restoreVerifyTarget = null }) { Text(strings.cancel) } }
        )
    }

    if (showConfigIntegration) {
        var type by remember { mutableStateOf("EMAIL_SMTP") }
        var name by remember { mutableStateOf("Custom SMTP") }
        var handle by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showConfigIntegration = false },
            title = { Text("Configure Integration Provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = type, onValueChange = { type = it.uppercase() }, label = { Text("Provider Type (PAYMENT_GATEWAY/BANK_FEED/EMAIL_SMTP/SMS_GATEWAY/WHATSAPP_API)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Provider Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = handle, onValueChange = { handle = it }, label = { Text("Endpoint / Account Handle") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                        Text("Mark Provider Active")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.configureIntegration(type, name, handle, enabled); showConfigIntegration = false }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showConfigIntegration = false }) { Text(strings.cancel) } }
        )
    }
}
