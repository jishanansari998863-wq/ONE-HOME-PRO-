package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
fun WorkforceScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.EMPLOYEES, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDept by remember { mutableStateOf(false) }
    var showAddEmp by remember { mutableStateOf(false) }
    var loginTarget by remember { mutableStateOf<EmployeeEntity?>(null) }
    var deactivateTarget by remember { mutableStateOf<EmployeeEntity?>(null) }
    var attendanceTarget by remember { mutableStateOf<EmployeeEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().testTag("workforce_screen")) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Employees (${data.employees.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Departments (${data.departments.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Attendance (${data.attendance.size})") })
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
                            title = strings.workforceHr,
                            subtitle = "Employee deactivation preserves historical audits & job records",
                            actionLabel = if (ctx.hasPermission(AppModule.EMPLOYEES, ActionType.CREATE)) "Add Employee" else null,
                            actionTag = "add_employee_button",
                            onAction = { showAddEmp = true }
                        )
                    }
                    if (data.employees.isEmpty()) {
                        item { EmptyStateCard("No Employees", "Onboard property managers, accountants, or maintenance technicians.", Icons.Default.Badge, "Add Employee", "empty_employees", { showAddEmp = true }) }
                    } else {
                        items(data.employees, key = { it.id }) { emp ->
                            val hasLogin = data.users.any { it.employeeId == emp.id }
                            val deptName = data.departments.firstOrNull { it.id == emp.departmentId }?.name ?: "General"
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("employee_card_${emp.employeeCode}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${emp.employeeCode} • ${emp.fullName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${emp.jobTitle} • $deptName • ${emp.workLocation.ifBlank { "Doha" }}", style = MaterialTheme.typography.bodySmall)
                                            Text("Shift: ${emp.shiftSchedule} | QID: ${emp.qidNumber.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        StatusBadge(emp.status)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { attendanceTarget = emp }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Attendance") }
                                        if (!hasLogin && emp.status == "ACTIVE" && ctx.userType == "OWNER") {
                                            Button(onClick = { loginTarget = emp }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("create_staff_login_${emp.employeeCode}")) {
                                                Text("Create Login")
                                            }
                                        }
                                        if (emp.status == "ACTIVE" && ctx.hasPermission(AppModule.EMPLOYEES, ActionType.ARCHIVE)) {
                                            OutlinedButton(onClick = { deactivateTarget = emp }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Deactivate") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionHeader("Departments", null, "Add Department", "add_dept_button") { showAddDept = true }
                    }
                    items(data.departments, key = { it.id }) { dept ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(dept.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(dept.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                2 -> {
                    if (data.attendance.isEmpty()) {
                        item { EmptyStateCard("No Attendance Logs", "Log daily check-ins & overtime from employee cards.") }
                    } else {
                        items(data.attendance, key = { it.id }) { att ->
                            val name = data.employees.firstOrNull { it.id == att.employeeId }?.fullName ?: "Staff"
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("$name • ${att.dateKey}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Overtime: ${att.overtimeMinutes} mins • ${att.notes}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusBadge(att.status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDept) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDept = false },
            title = { Text("New Department") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Department Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.createDepartment(name, desc); showAddDept = false }) { Text(strings.save) } },
            dismissButton = { TextButton(onClick = { showAddDept = false }) { Text(strings.cancel) } }
        )
    }

    if (showAddEmp) {
        var name by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("Technician") }
        var dept by remember { mutableStateOf(data.departments.firstOrNull()) }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var qid by remember { mutableStateOf("") }
        var loc by remember { mutableStateOf("Doha HQ") }
        AlertDialog(
            onDismissRequest = { showAddEmp = false },
            title = { Text("Onboard Employee") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name*") }, modifier = Modifier.fillMaxWidth().testTag("emp_name_input"))
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Job Title*") }, modifier = Modifier.fillMaxWidth())
                    if (data.departments.isNotEmpty()) {
                        DropdownSelector("Department", data.departments, dept, { it.name }, { dept = it })
                    }
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Work Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = qid, onValueChange = { qid = it }, label = { Text("Qatar ID (QID)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Assigned Location") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addEmployee(name, title, dept?.id, phone, email, qid, loc)
                    showAddEmp = false
                }, modifier = Modifier.testTag("save_employee_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddEmp = false }) { Text(strings.cancel) } }
        )
    }

    loginTarget?.let { emp ->
        var email by remember { mutableStateOf(emp.email) }
        var pass by remember { mutableStateOf("") }
        var roleName by remember { mutableStateOf("TECHNICIAN") }
        val roles = listOf("PROPERTY_MANAGER", "ACCOUNTANT", "TECHNICIAN")
        AlertDialog(
            onDismissRequest = { loginTarget = null },
            title = { Text("Staff Login for ${emp.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Login Email*") }, modifier = Modifier.fillMaxWidth().testTag("staff_login_email"))
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password (min 8 chars)*") }, modifier = Modifier.fillMaxWidth().testTag("staff_login_password"))
                    DropdownSelector("RBAC Role", roles, roleName, { it }, { roleName = it }, testTag = "staff_role_dropdown")
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.provisionStaffLogin(emp.id, emp.fullName, email, pass, roleName)
                    loginTarget = null
                }, modifier = Modifier.testTag("save_staff_login_button")) { Text("Provision Staff User") }
            },
            dismissButton = { TextButton(onClick = { loginTarget = null }) { Text(strings.cancel) } }
        )
    }

    deactivateTarget?.let { emp ->
        var reason by remember { mutableStateOf("Contract ended") }
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            title = { Text("Deactivate ${emp.fullName}") },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (historical records preserved)") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { vm.deactivateEmployee(emp.id, reason); deactivateTarget = null }) { Text("Deactivate") } },
            dismissButton = { TextButton(onClick = { deactivateTarget = null }) { Text(strings.cancel) } }
        )
    }

    attendanceTarget?.let { emp ->
        var status by remember { mutableStateOf("PRESENT") }
        var ot by remember { mutableStateOf("0") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { attendanceTarget = null },
            title = { Text("Attendance: ${emp.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = status, onValueChange = { status = it.uppercase() }, label = { Text("Status (PRESENT/ABSENT/LEAVE/OVERTIME)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ot, onValueChange = { ot = it }, label = { Text("Overtime Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.recordAttendance(emp.id, status, ot.toIntOrNull() ?: 0, notes)
                    attendanceTarget = null
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { attendanceTarget = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
fun MaintenanceScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.MAINTENANCE, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var showCreate by remember { mutableStateOf(false) }
    var assignTarget by remember { mutableStateOf<ComplaintEntity?>(null) }
    var reportTarget by remember { mutableStateOf<ComplaintEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("maintenance_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.maintenance,
                subtitle = "${data.complaints.size} tickets • linked to Inventory & GL",
                actionLabel = if (ctx.hasPermission(AppModule.MAINTENANCE, ActionType.CREATE)) "New Ticket" else null,
                actionTag = "new_complaint_button",
                onAction = { showCreate = true }
            )
        }
        if (data.complaints.isEmpty()) {
            item { EmptyStateCard("No Maintenance Tickets", "Log tenant complaints or preventive schedules with technician job cards and material tracking.", Icons.Default.Build, "Create Ticket", "empty_maintenance", { showCreate = true }) }
        } else {
            items(data.complaints, key = { it.id }) { cmp ->
                val propName = data.properties.firstOrNull { it.id == cmp.propertyId }?.name ?: "Property"
                val matCost = data.materialUsage.filter { it.complaintId == cmp.id }.sumOf { it.totalCostFils }
                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("complaint_card_${cmp.complaintNumber}")) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cmp.complaintNumber} • ${cmp.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("$propName • ${cmp.category} • Priority: ${cmp.priority}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(cmp.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(cmp.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (matCost > 0) {
                            Text("Material Cost Posted: ${QatarFormatters.formatQar(matCost)}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cmp.status == "OPEN" && data.employees.isNotEmpty() && ctx.hasPermission(AppModule.MAINTENANCE, ActionType.EDIT)) {
                                OutlinedButton(onClick = { assignTarget = cmp }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Assign Tech") }
                            }
                            if (cmp.status in listOf("OPEN", "ASSIGNED", "IN_PROGRESS") && ctx.hasPermission(AppModule.MAINTENANCE, ActionType.EDIT)) {
                                Button(onClick = { reportTarget = cmp }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Work Report") }
                            }
                            if (cmp.status == "AWAITING_APPROVAL" && ctx.hasPermission(AppModule.MAINTENANCE, ActionType.APPROVE)) {
                                Button(onClick = { vm.approveJob(cmp.id, "APPROVED", "Verified by supervisor") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(strings.approve) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var prop by remember { mutableStateOf(data.properties.firstOrNull()) }
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("PLUMBING") }
        var prio by remember { mutableStateOf("MEDIUM") }
        var prev by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Maintenance / Complaint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Property", data.properties, prop, { it.name }, { prop = it })
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Issue Title*") }, modifier = Modifier.fillMaxWidth().testTag("complaint_title_input"))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cat, onValueChange = { cat = it.uppercase() }, label = { Text("Category (PLUMBING/ELECTRICAL/HVAC)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = prio, onValueChange = { prio = it.uppercase() }, label = { Text("Priority (LOW/MEDIUM/HIGH/URGENT)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = prev, onCheckedChange = { prev = it })
                        Text("Preventive Maintenance Schedule")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    prop?.let { vm.createComplaint(it.id, null, null, title, desc, cat, prio, prev) }
                    showCreate = false
                }, modifier = Modifier.testTag("save_complaint_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(strings.cancel) } }
        )
    }

    assignTarget?.let { cmp ->
        var tech by remember { mutableStateOf(data.employees.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text("Assign Technician") },
            text = { DropdownSelector("Technician", data.employees.filter { it.status == "ACTIVE" }, tech, { "${it.fullName} (${it.jobTitle})" }, { tech = it }) },
            confirmButton = { Button(onClick = { tech?.let { vm.assignTechnician(cmp.id, it.id) }; assignTarget = null }) { Text("Assign") } },
            dismissButton = { TextButton(onClick = { assignTarget = null }) { Text(strings.cancel) } }
        )
    }

    reportTarget?.let { cmp ->
        var summary by remember { mutableStateOf("") }
        var mins by remember { mutableStateOf("45") }
        var labourQar by remember { mutableStateOf("0") }
        var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
        var qty by remember { mutableStateOf("1") }
        var beforeUri by remember { mutableStateOf<String?>(null) }
        var afterUri by remember { mutableStateOf<String?>(null) }

        val pickBefore = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> beforeUri = uri?.toString() }
        val pickAfter = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> afterUri = uri?.toString() }

        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Submit Job Work Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Work Completed Summary*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mins, onValueChange = { mins = it }, label = { Text("Labour Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = labourQar, onValueChange = { labourQar = it }, label = { Text("External Labour Cost (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickBefore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                            Text(if (beforeUri == null) "Before Photo" else "Before Set ✓")
                        }
                        OutlinedButton(onClick = { pickAfter.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                            Text(if (afterUri == null) "After Photo" else "After Set ✓")
                        }
                    }
                    if (data.items.isNotEmpty() && data.stockLocations.isNotEmpty()) {
                        DropdownSelector("Material Used (Optional)", data.items, selectedItem, { "${it.name} (Stock: ${it.currentStockQty})" }, { selectedItem = it })
                        if (selectedItem != null) {
                            OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity Consumed") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val techId = ctx.employeeId ?: data.employees.firstOrNull()?.id ?: ""
                    val locId = data.stockLocations.firstOrNull()?.id
                    vm.submitWorkReport(
                        cmp.id, techId, summary, mins.toIntOrNull() ?: 30,
                        QatarFormatters.parseQarToFils(labourQar) ?: 0L,
                        beforeUri, afterUri, selectedItem?.id, locId, qty.toLongOrNull() ?: 1L
                    )
                    reportTarget = null
                }) { Text("Submit Report") }
            },
            dismissButton = { TextButton(onClick = { reportTarget = null }) { Text(strings.cancel) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.INVENTORY, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddSupplier by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var showCreatePo by remember { mutableStateOf(false) }
    var grnTarget by remember { mutableStateOf<PurchaseEntity?>(null) }
    var payTarget by remember { mutableStateOf<PurchaseEntity?>(null) }
    var countTarget by remember { mutableStateOf<ItemEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().testTag("inventory_screen")) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Items (${data.items.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Purchases (${data.purchases.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Suppliers (${data.suppliers.size})") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        SectionHeader("Inventory Catalog", "Weighted-average valuation • Negative stock blocked", "Add Item", "add_item_button") { showAddItem = true }
                    }
                    if (data.items.isEmpty()) {
                        item { EmptyStateCard("No Inventory Items", "Add spare parts, plumbing/HVAC supplies, and cleaning materials.", Icons.Default.Inventory2, "Add Item", "empty_items", { showAddItem = true }) }
                    } else {
                        items(data.items, key = { it.id }) { item ->
                            val low = item.currentStockQty <= item.reorderLevel
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("item_card_${item.sku}")) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.sku} • ${item.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Qty: ${item.currentStockQty} ${item.unit} | Avg Cost: ${QatarFormatters.formatQar(item.weightedAvgCostFils)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                    }
                                    if (low) StatusBadge("LOW STOCK")
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { countTarget = item }) {
                                        Icon(Icons.Default.FactCheck, contentDescription = "Physical Stock Count")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionHeader("Purchase Orders", "Request -> Approve -> Goods Receipt -> Pay Supplier", "New PO", "new_po_button") { showCreatePo = true }
                    }
                    if (data.purchases.isEmpty()) {
                        item { EmptyStateCard("No Purchase Orders", "Create a supplier PO after adding a supplier and inventory item.") }
                    } else {
                        items(data.purchases, key = { it.id }) { po ->
                            val sup = data.suppliers.firstOrNull { it.id == po.supplierId }?.name ?: "Supplier"
                            val unpaid = (po.totalAmountFils - po.paidAmountFils).coerceAtLeast(0L)
                            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("po_card_${po.purchaseNumber}")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${po.purchaseNumber} • $sup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Total: ${QatarFormatters.formatQar(po.totalAmountFils)} | Paid: ${QatarFormatters.formatQar(po.paidAmountFils)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                        }
                                        StatusBadge(po.status)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (po.status == "REQUESTED" && ctx.hasPermission(AppModule.INVENTORY, ActionType.APPROVE)) {
                                            Button(onClick = { vm.approvePurchase(po.id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Approve") }
                                        }
                                        if (po.status == "APPROVED" && ctx.hasPermission(AppModule.INVENTORY, ActionType.POST)) {
                                            Button(onClick = { grnTarget = po }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Receive Goods") }
                                        }
                                        if (po.status == "RECEIVED" && unpaid > 0 && ctx.hasPermission(AppModule.INVENTORY, ActionType.POST)) {
                                            OutlinedButton(onClick = { payTarget = po }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Pay Supplier") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item { SectionHeader("Qatar Suppliers", null, "Add Supplier", "add_supplier_button") { showAddSupplier = true } }
                    if (data.suppliers.isEmpty()) {
                        item { EmptyStateCard("No Suppliers", "Register hardware, AC, electrical, and facility vendors.", Icons.Default.LocalShipping, "Add Supplier", "empty_suppliers", { showAddSupplier = true }) }
                    } else {
                        items(data.suppliers, key = { it.id }) { sup ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("${sup.supplierCode} • ${sup.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Contact: ${sup.contactPerson} • ${sup.phone} • CR: ${sup.crNumber.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSupplier) {
        var name by remember { mutableStateOf("") }
        var contact by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var cr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSupplier = false },
            title = { Text("Add Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Person") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cr, onValueChange = { cr = it }, label = { Text("Qatar CR No.") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.createSupplier(name, contact, phone, email, cr); showAddSupplier = false }) { Text(strings.save) } },
            dismissButton = { TextButton(onClick = { showAddSupplier = false }) { Text(strings.cancel) } }
        )
    }

    if (showAddItem) {
        var cat by remember { mutableStateOf("PLUMBING") }
        var name by remember { mutableStateOf("") }
        var sku by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("PCS") }
        var reorder by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showAddItem = false },
            title = { Text("Add Inventory Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU*") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (PCS/BOX/MTR)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reorder, onValueChange = { reorder = it }, label = { Text("Reorder Alert Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.createItem(cat, name, sku, unit, reorder.toLongOrNull() ?: 5L); showAddItem = false }) { Text(strings.save) } },
            dismissButton = { TextButton(onClick = { showAddItem = false }) { Text(strings.cancel) } }
        )
    }

    if (showCreatePo) {
        var sup by remember { mutableStateOf(data.suppliers.firstOrNull()) }
        var item by remember { mutableStateOf(data.items.firstOrNull()) }
        var qty by remember { mutableStateOf("10") }
        var unitCost by remember { mutableStateOf("25") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePo = false },
            title = { Text("Create Purchase Order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Supplier", data.suppliers, sup, { it.name }, { sup = it })
                    DropdownSelector("Item", data.items, item, { "${it.sku} - ${it.name}" }, { item = it })
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Order Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unitCost, onValueChange = { unitCost = it }, label = { Text("Unit Cost (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = sup
                    val itm = item
                    if (s != null && itm != null) {
                        vm.createPurchaseOrder(s.id, null, itm.id, qty.toLongOrNull() ?: 1L, QatarFormatters.parseQarToFils(unitCost) ?: 0L, notes)
                    }
                    showCreatePo = false
                }) { Text("Create PO") }
            },
            dismissButton = { TextButton(onClick = { showCreatePo = false }) { Text(strings.cancel) } }
        )
    }

    grnTarget?.let { po ->
        var loc by remember { mutableStateOf(data.stockLocations.firstOrNull()) }
        var inv by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { grnTarget = null },
            title = { Text("Receive Goods • ${po.purchaseNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Stock Location", data.stockLocations, loc, { it.name }, { loc = it })
                    OutlinedTextField(value = inv, onValueChange = { inv = it }, label = { Text("Supplier Invoice Number") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    loc?.let { vm.receiveGoods(po.id, it.id, inv) }
                    grnTarget = null
                }) { Text("Post Goods Receipt") }
            },
            dismissButton = { TextButton(onClick = { grnTarget = null }) { Text(strings.cancel) } }
        )
    }

    payTarget?.let { po ->
        var amt by remember { mutableStateOf("%.2f".format((po.totalAmountFils - po.paidAmountFils) / 100.0)) }
        var method by remember { mutableStateOf("BANK_TRANSFER") }
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("Pay Supplier • ${po.purchaseNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Payment (QAR)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Method (BANK_TRANSFER/CASH)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.paySupplier(po.id, QatarFormatters.parseQarToFils(amt) ?: 0L, method)
                    payTarget = null
                }) { Text("Post Payment") }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text(strings.cancel) } }
        )
    }

    countTarget?.let { item ->
        var loc by remember { mutableStateOf(data.stockLocations.firstOrNull()) }
        var counted by remember { mutableStateOf(item.currentStockQty.toString()) }
        AlertDialog(
            onDismissRequest = { countTarget = null },
            title = { Text("Physical Stock Count: ${item.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Location", data.stockLocations, loc, { it.name }, { loc = it })
                    OutlinedTextField(value = counted, onValueChange = { counted = it }, label = { Text("Counted Physical Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    loc?.let { vm.recordStockCount(item.id, it.id, counted.toLongOrNull() ?: item.currentStockQty) }
                    countTarget = null
                }) { Text("Post Count") }
            },
            dismissButton = { TextButton(onClick = { countTarget = null }) { Text(strings.cancel) } }
        )
    }
}
