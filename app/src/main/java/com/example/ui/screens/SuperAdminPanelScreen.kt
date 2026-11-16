package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.security.*
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SuperAdminPanelScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.isSuperAdmin || !SuperAdminConfig.isSuperAdminEmail(ctx.email)) {
        PermissionDeniedCard(modifier = Modifier.testTag("super_admin_denied_card"))
        return
    }

    val strings = LocalAppStrings.current
    val status = uiState.platformStatus
    var selectedTab by remember { mutableIntStateOf(0) }
    var approveTarget by remember { mutableStateOf<AccessRequestRecord?>(null) }
    var rejectTarget by remember { mutableStateOf<AccessRequestRecord?>(null) }
    var emergencyConfirmTarget by remember { mutableStateOf<Boolean?>(null) }
    var reauthorizeDeviceTarget by remember { mutableStateOf<AdminDeviceRecord?>(null) }
    var markLostDeviceTarget by remember { mutableStateOf<AdminDeviceRecord?>(null) }
    var customLockMessage by remember(status?.lockMessage) {
        mutableStateOf(status?.lockMessage ?: "System maintenance lock enabled by Super Admin.")
    }

    LaunchedEffect(ctx.userId) {
        vm.refreshSuperAdminData()
    }

    Column(modifier = Modifier.fillMaxSize().testTag("super_admin_panel_screen")) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Gate & Emergency") })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Requests (${uiState.centralAccessRequests.count { it.status == SuperAdminConfig.GRANT_PENDING }})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Approved Users (${uiState.centralAccessGrants.size})") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Devices & Recovery (${uiState.adminDevices.size})") },
                modifier = Modifier.testTag("sa_devices_recovery_tab")
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Audit Logs (${uiState.centralAuditLogs.size})") }
            )
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
                            title = "Exclusive Super Admin Controls",
                            subtitle = "Owner: ${SuperAdminConfig.SUPER_ADMIN_EMAIL}",
                            actionLabel = "Refresh",
                            actionTag = "sa_refresh_button"
                        ) { vm.refreshSuperAdminData() }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiMetricCard(
                                title = "Pending Requests",
                                value = uiState.centralAccessRequests.count { it.status == SuperAdminConfig.GRANT_PENDING }.toString(),
                                icon = Icons.Default.HourglassTop,
                                accent = AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Active Grants",
                                value = uiState.centralAccessGrants.count { it.isActiveGrant() }.toString(),
                                icon = Icons.Default.VerifiedUser,
                                accent = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Global Emergency Lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CrimsonError)
                                        Text(
                                            "Immediately blocks all non-Super-Admin users across every device.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Switch(
                                        checked = status?.emergencyLockAllNonSuperAdmin == true,
                                        onCheckedChange = { emergencyConfirmTarget = it },
                                        modifier = Modifier.testTag("sa_emergency_lock_switch")
                                    )
                                }
                                OutlinedTextField(
                                    value = customLockMessage,
                                    onValueChange = { customLockMessage = it },
                                    label = { Text("Emergency Lock Display Message") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Allow New Access Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Controls whether new applicants can submit PENDING_APPROVAL forms.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Switch(
                                        checked = status?.registrationsEnabled != false,
                                        onCheckedChange = { vm.toggleRegistrationsEnabled(it) },
                                        modifier = Modifier.testTag("sa_registrations_switch")
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text("Authorized Central Businesses (${uiState.centralBusinesses.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(uiState.centralBusinesses, key = { it.id }) { biz ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${biz.name} (${biz.code})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Owner: ${biz.ownerEmail} • Branch: ${biz.branchName}", style = MaterialTheme.typography.bodySmall)
                                }
                                StatusBadge(biz.status)
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionHeader("Access Requests", "Only Super Admin approval activates any account", "Refresh", "sa_requests_refresh_btn") {
                            vm.refreshSuperAdminData()
                        }
                    }
                    if (uiState.centralAccessRequests.isEmpty()) {
                        item { EmptyStateCard("No Access Requests", "Incoming applicant requests will appear here for review.") }
                    } else {
                        items(uiState.centralAccessRequests, key = { it.uid }) { req ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("sa_request_card_${req.uid}")) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${req.fullName} (${req.requestedRole})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${req.email} • ${req.phone.ifBlank { "No phone" }}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        StatusBadge(req.status)
                                    }
                                    Text("Business: ${req.requestedBusinessName} • ${req.requestedBranchName}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "QID: ${req.qidNumber ?: "-"} • Passport: ${req.passportNumber ?: "-"} • Device: ${req.deviceName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    req.identityDocSummary?.let {
                                        Text("Document: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (req.status == SuperAdminConfig.GRANT_PENDING || req.status == SuperAdminConfig.GRANT_REJECTED) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { approveTarget = req },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("sa_approve_btn_${req.uid}")
                                            ) {
                                                Text(strings.approve)
                                            }
                                            OutlinedButton(
                                                onClick = { rejectTarget = req },
                                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("sa_reject_btn_${req.uid}")
                                            ) {
                                                Text("Reject")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        SectionHeader("Central Access Grants", "Instant suspend, revoke, and remote session kill", "Refresh", "sa_grants_refresh_btn") {
                            vm.refreshSuperAdminData()
                        }
                    }
                    items(uiState.centralAccessGrants, key = { it.uid }) { grant ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("sa_grant_card_${grant.uid}")) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${grant.fullName} (${grant.authorityRole})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(grant.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusBadge(grant.status)
                                }
                                Text(
                                    "Business: ${grant.activeBusinessId} • Scoped Properties: ${if (grant.restrictToAssignedProperties) grant.allowedPropertyIds.size else "All"}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (!SuperAdminConfig.isSuperAdminEmail(grant.email)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (grant.isActiveGrant()) {
                                            OutlinedButton(
                                                onClick = { vm.setCentralUserStatus(grant.uid, SuperAdminConfig.GRANT_SUSPENDED, "Suspended from console") },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_suspend_btn_${grant.uid}")
                                            ) { Text("Suspend") }
                                            Button(
                                                onClick = { vm.setCentralUserStatus(grant.uid, SuperAdminConfig.GRANT_REVOKED, "Revoked from console") },
                                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_revoke_btn_${grant.uid}")
                                            ) { Text("Revoke") }
                                        } else {
                                            Button(
                                                onClick = { vm.setCentralUserStatus(grant.uid, SuperAdminConfig.GRANT_ACTIVE, "Reactivated by Super Admin") },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_reactivate_btn_${grant.uid}")
                                            ) { Text("Reactivate") }
                                        }
                                        TextButton(
                                            onClick = { vm.revokeCentralUserSessions(grant.uid, "Force remote logout") },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) { Text("Kill Sessions") }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        SectionHeader("Devices, Recovery & Cloud Restore", "Multi-device Super Admin continuity & lost-phone blocking", "Refresh", "sa_devices_refresh_btn") {
                            vm.refreshSuperAdminData()
                        }
                    }
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Encrypted Cloud Business Sync & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Safely mirror businesses, customers, rooms, agreements, rent, accounting ledgers & settings to the central cloud so a new phone restores everything after recovery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                uiState.lastCloudSyncEpoch?.let { epoch ->
                                    Text(
                                        text = "Last Synced: ${QatarFormatters.formatDateTime(epoch)} • ${uiState.lastCloudSyncCount ?: 0} records",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { vm.syncBusinessDataToCloud() },
                                        enabled = !uiState.isBusy,
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("sa_cloud_sync_button")
                                    ) {
                                        Text("Sync to Cloud")
                                    }
                                    OutlinedButton(
                                        onClick = { vm.restoreBusinessDataFromCloud() },
                                        enabled = !uiState.isBusy,
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("sa_cloud_restore_button")
                                    ) {
                                        Text("Restore From Cloud")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        var currentPass by remember { mutableStateOf("") }
                        var question by remember(uiState.superAdminSecurityQuestion) {
                            mutableStateOf(uiState.superAdminSecurityQuestion.orEmpty())
                        }
                        var answer by remember { mutableStateOf("") }
                        var rotateKit by remember { mutableStateOf(false) }

                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Backup Recovery Kit & Security Question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                uiState.lastRecoveryKit?.let { kit ->
                                    Surface(
                                        color = ArchitecturalGoldContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("sa_panel_recovery_kit_box")
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("ROTATED RECOVERY KIT (SAVE ONCE)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("TOTP Secret: ${kit.totpSecret}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                            Text("Master Recovery Key: ${kit.masterRecoveryKey}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.Bold)
                                            Text("Recovery Codes: ${kit.recoveryCodes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = currentPass,
                                    onValueChange = { currentPass = it },
                                    label = { Text("Confirm Current Master Password*") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_settings_pw_input")
                                )
                                OutlinedTextField(
                                    value = question,
                                    onValueChange = { question = it },
                                    label = { Text("Dual-Loss Security Question") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_settings_q_input")
                                )
                                OutlinedTextField(
                                    value = answer,
                                    onValueChange = { answer = it },
                                    label = { Text("New Security Answer (leave blank to keep existing)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("sa_recovery_settings_a_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Rotate TOTP, Recovery Codes & Master Key", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = rotateKit,
                                        onCheckedChange = { rotateKit = it },
                                        modifier = Modifier.testTag("sa_rotate_kit_switch")
                                    )
                                }
                                Button(
                                    onClick = {
                                        vm.updateSuperAdminRecoverySettings(currentPass, question, answer, rotateKit)
                                        currentPass = ""
                                        answer = ""
                                        rotateKit = false
                                    },
                                    enabled = !uiState.isBusy && currentPass.isNotBlank() && ((question.isNotBlank() && answer.isNotBlank()) || rotateKit),
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_update_recovery_btn")
                                ) {
                                    Text("Save Recovery Settings")
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Registered Super Admin Devices (${uiState.adminDevices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { vm.revokeAllOtherAdminDevices("Emergency lockdown of all other Super Admin devices") },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                enabled = !uiState.isBusy,
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sa_revoke_all_other_devices_btn")
                            ) {
                                Icon(Icons.Default.PhonelinkErase, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Revoke All Other Devices & Sessions Now")
                            }
                        }
                    }
                    if (uiState.adminDevices.isEmpty()) {
                        item { EmptyStateCard("No Devices Recorded", "Super Admin devices will appear here upon login.") }
                    } else {
                        items(uiState.adminDevices, key = { "${it.uid}_${it.deviceId}" }) { dev ->
                            val isCurrent = dev.deviceId == ctx.deviceId
                            OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("sa_device_card_${dev.deviceId}")) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isCurrent) "${dev.deviceName} (This Device)" else dev.deviceName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "ID: ${dev.deviceId} • Last seen: ${QatarFormatters.formatDateTime(dev.lastSeenEpoch)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(dev.status)
                                    }
                                    dev.revokedReason?.takeIf { it.isNotBlank() }?.let { reason ->
                                        Text("Lock Reason: $reason", style = MaterialTheme.typography.bodySmall, color = CrimsonError)
                                    }
                                    if (!isCurrent) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (dev.status == SuperAdminConfig.DEVICE_REVOKED_LOST) {
                                                Button(
                                                    onClick = { reauthorizeDeviceTarget = dev },
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_reauth_dev_btn_${dev.deviceId}")
                                                ) { Text("Re-authorize Returned Device") }
                                            } else {
                                                Button(
                                                    onClick = { markLostDeviceTarget = dev },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sa_mark_lost_btn_${dev.deviceId}")
                                                ) { Text("Mark Lost & Block") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        SectionHeader("Immutable Security Audit Log", "Append-only central authority record", "Refresh", "sa_audit_refresh_btn") {
                            vm.refreshSuperAdminData()
                        }
                    }
                    items(uiState.centralAuditLogs, key = { it.id }) { log ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(log.action, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    log.createdAt?.let { ts ->
                                        Text(QatarFormatters.formatDateTime(ts.toDate().time), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                                Text("Actor: ${log.actorEmail} • Target: ${log.targetEmail ?: "-"}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    approveTarget?.let { req ->
        var assignedRole by remember { mutableStateOf(req.requestedRole.takeIf { it in SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES } ?: "OWNER") }
        var selectedBizId by remember { mutableStateOf<String?>(null) }
        var note by remember { mutableStateOf("Approved by Super Admin") }
        AlertDialog(
            onDismissRequest = { approveTarget = null },
            title = { Text("Approve Access Request") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${req.fullName} (${req.email})")
                    DropdownSelector(
                        label = "Assigned Authority Role",
                        items = SuperAdminConfig.PUBLIC_REQUESTABLE_ROLES,
                        selectedItem = assignedRole,
                        itemLabel = { it },
                        onSelect = { assignedRole = it },
                        testTag = "sa_approve_role_selector"
                    )
                    val bizOptions = listOf<CentralBusinessRecord?>(null) + uiState.centralBusinesses
                    DropdownSelector(
                        label = "Target Business",
                        items = bizOptions,
                        selectedItem = uiState.centralBusinesses.firstOrNull { it.id == selectedBizId },
                        itemLabel = { b -> b?.let { "${it.name} (${it.branchName})" } ?: "Create Isolated Business: ${req.requestedBusinessName}" },
                        onSelect = { selectedBizId = it?.id },
                        testTag = "sa_approve_biz_selector"
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Approval Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.approveCentralAccessRequest(
                            requestUid = req.uid,
                            assignedRole = assignedRole,
                            existingBusinessId = selectedBizId,
                            permissionKeys = emptyList(),
                            allowedPropertyIds = emptyList(),
                            note = note
                        )
                        approveTarget = null
                    },
                    modifier = Modifier.testTag("sa_confirm_approve_btn")
                ) { Text(strings.approve) }
            },
            dismissButton = {
                TextButton(onClick = { approveTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    rejectTarget?.let { req ->
        var note by remember { mutableStateOf("Authorization declined by Super Admin.") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Reject Access Request") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Rejection Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.rejectCentralAccessRequest(req.uid, note)
                        rejectTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                    modifier = Modifier.testTag("sa_confirm_reject_btn")
                ) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    emergencyConfirmTarget?.let { lockState ->
        AlertDialog(
            onDismissRequest = { emergencyConfirmTarget = null },
            title = { Text(if (lockState) "Activate Global Emergency Lock?" else "Disable Global Emergency Lock?") },
            text = {
                Text(
                    if (lockState) "Every non-Super-Admin user will immediately lose dashboard access across all devices."
                    else "Approved active users will be permitted to sign in again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.toggleEmergencyLock(lockState, customLockMessage)
                        emergencyConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (lockState) CrimsonError else EmeraldSuccess)
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { emergencyConfirmTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    markLostDeviceTarget?.let { dev ->
        var reason by remember { mutableStateOf("Phone lost/stolen — blocked from Super Admin console") }
        AlertDialog(
            onDismissRequest = { markLostDeviceTarget = null },
            title = { Text("Block Lost Phone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device '${dev.deviceName}' (${dev.deviceId}) will be locked permanently and all its active sessions will be killed. Even valid passwords/OTPs will fail on it unless explicitly re-authorized.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Lock Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.markAdminDeviceLost(dev.deviceId, reason)
                        markLostDeviceTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                    modifier = Modifier.testTag("sa_confirm_mark_lost_btn")
                ) { Text("Block Device Now") }
            },
            dismissButton = {
                TextButton(onClick = { markLostDeviceTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    reauthorizeDeviceTarget?.let { dev ->
        var masterPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reauthorizeDeviceTarget = null },
            title = { Text("Re-authorize Returned Phone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your current Master Password to explicitly allow returned device '${dev.deviceName}' (${dev.deviceId}) to sign in again.")
                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        label = { Text("Current Master Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sa_reauth_pw_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.reauthorizeAdminDevice(dev.deviceId, masterPassword)
                        reauthorizeDeviceTarget = null
                    },
                    enabled = masterPassword.isNotBlank(),
                    modifier = Modifier.testTag("sa_confirm_reauth_btn")
                ) { Text("Re-authorize") }
            },
            dismissButton = {
                TextButton(onClick = { reauthorizeDeviceTarget = null }) { Text(strings.cancel) }
            }
        )
    }
}
