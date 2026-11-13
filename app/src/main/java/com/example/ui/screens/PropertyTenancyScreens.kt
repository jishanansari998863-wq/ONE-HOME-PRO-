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
import com.example.data.db.entities.*
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.util.QatarFormatters
import com.example.localization.LocalAppStrings
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.components.*

@Composable
fun PropertiesScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.PROPERTIES, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    val props = remember(data.properties, ctx) {
        if (ctx.restrictToAssignedProperties && ctx.userType != "OWNER") {
            data.properties.filter { ctx.allowedPropertyIds.contains(it.id) }
        } else data.properties
    }
    var selectedPropertyId by remember(props) { mutableStateOf(props.firstOrNull()?.id) }
    var showAddProperty by remember { mutableStateOf(false) }
    var showAddBuilding by remember { mutableStateOf(false) }
    var showAddFloor by remember { mutableStateOf(false) }
    var showAddRoom by remember { mutableStateOf(false) }
    var reserveRoomTarget by remember { mutableStateOf<RoomEntity?>(null) }
    var inspectionProperty by remember { mutableStateOf<PropertyEntity?>(null) }

    val propBuildings = data.buildings.filter { it.propertyId == selectedPropertyId }
    val propFloors = data.floors.filter { it.propertyId == selectedPropertyId }
    val propRooms = data.rooms.filter { it.propertyId == selectedPropertyId }
    val propInspections = data.inspections.filter { it.propertyId == selectedPropertyId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("properties_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.navProperties,
                subtitle = "${props.size} properties • ${data.rooms.size} total rooms",
                actionLabel = if (ctx.hasPermission(AppModule.PROPERTIES, ActionType.CREATE)) "Add Property" else null,
                actionTag = "add_property_button",
                onAction = { showAddProperty = true }
            )
        }

        if (props.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Properties Yet",
                    description = "Add your first Qatar residential building, villa compound, or commercial block.",
                    icon = Icons.Default.Apartment,
                    actionLabel = "Add Property",
                    testTag = "empty_properties_card",
                    onAction = { showAddProperty = true }
                )
            }
        } else {
            items(props, key = { it.id }) { prop ->
                val isSelected = prop.id == selectedPropertyId
                val pRooms = data.rooms.filter { it.propertyId == prop.id }
                val vacant = pRooms.count { it.status == "VACANT" }
                val occupied = pRooms.count { it.status == "OCCUPIED" }
                ElevatedCard(
                    onClick = { selectedPropertyId = prop.id },
                    modifier = Modifier.fillMaxWidth().testTag("property_card_${prop.code}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${prop.code} • ${prop.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${prop.propertyType} • ${prop.city} • ${prop.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(if (isSelected) "SELECTED" else "ACTIVE")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Rooms: ${pRooms.size}", style = MaterialTheme.typography.labelMedium)
                            Text("Occupied: $occupied", style = MaterialTheme.typography.labelMedium)
                            Text("Vacant: $vacant", style = MaterialTheme.typography.labelMedium)
                        }
                        if (isSelected && ctx.hasPermission(AppModule.PROPERTIES, ActionType.CREATE)) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showAddBuilding = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_building_button")) {
                                    Text("+ Building")
                                }
                                OutlinedButton(
                                    onClick = { showAddFloor = true },
                                    enabled = propBuildings.isNotEmpty(),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_floor_button")
                                ) {
                                    Text("+ Floor")
                                }
                                Button(
                                    onClick = { showAddRoom = true },
                                    enabled = propFloors.isNotEmpty(),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_room_button")
                                ) {
                                    Text("+ Room")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = { inspectionProperty = prop },
                                modifier = Modifier.testTag("record_inspection_button")
                            ) {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Record Condition / Handover Inspection (${propInspections.size})")
                            }
                        }
                    }
                }
            }

            if (selectedPropertyId != null) {
                item {
                    Text(
                        text = "Buildings (${propBuildings.size}) • Floors (${propFloors.size}) • Rooms (${propRooms.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(propRooms, key = { it.id }) { room ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("room_card_${room.roomNumber}")) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Room ${room.roomNumber} (${room.roomType})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${room.furnishedStatus} • Cap ${room.capacity} • Keys: ${room.keyCount} • Card: ${room.accessCardCode.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusBadge(room.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            QarAmountText(fils = room.monthlyRateFils)
                            if (ctx.hasPermission(AppModule.PROPERTIES, ActionType.EDIT)) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (room.status == "VACANT") {
                                        OutlinedButton(onClick = { reserveRoomTarget = room }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Reserve")
                                        }
                                        OutlinedButton(onClick = { vm.updateRoomStatus(room.id, "MAINTENANCE", "Scheduled maintenance") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Maintenance")
                                        }
                                    } else if (room.status == "MAINTENANCE" || room.status == "RESERVED") {
                                        OutlinedButton(onClick = { vm.updateRoomStatus(room.id, "VACANT", "Ready for lease") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                            Text("Mark Vacant")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProperty) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("RESIDENTIAL_BUILDING") }
        var address by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("Doha") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddProperty = false },
            title = { Text("Add Property") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Property Name") }, modifier = Modifier.fillMaxWidth().testTag("prop_name_input"))
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Property Code (e.g. DOHA-01)") }, modifier = Modifier.fillMaxWidth().testTag("prop_code_input"))
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (RESIDENTIAL_BUILDING/VILLA/COMMERCIAL)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Street / Zone Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City / Municipality") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createProperty(name, code, type, address, city, notes)
                    showAddProperty = false
                }, modifier = Modifier.testTag("save_property_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddProperty = false }) { Text(strings.cancel) } }
        )
    }

    if (showAddBuilding && selectedPropertyId != null) {
        var bName by remember { mutableStateOf("Main Building") }
        var bCode by remember { mutableStateOf("B1") }
        var floors by remember { mutableStateOf("3") }
        AlertDialog(
            onDismissRequest = { showAddBuilding = false },
            title = { Text("Add Building") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bName, onValueChange = { bName = it }, label = { Text("Building Name") }, modifier = Modifier.fillMaxWidth().testTag("bldg_name_input"))
                    OutlinedTextField(value = bCode, onValueChange = { bCode = it }, label = { Text("Building Code") }, modifier = Modifier.fillMaxWidth().testTag("bldg_code_input"))
                    OutlinedTextField(value = floors, onValueChange = { floors = it }, label = { Text("Total Floors") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addBuilding(selectedPropertyId!!, bName, bCode, floors.toIntOrNull() ?: 1)
                    showAddBuilding = false
                }, modifier = Modifier.testTag("save_building_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddBuilding = false }) { Text(strings.cancel) } }
        )
    }

    if (showAddFloor && selectedPropertyId != null) {
        var selectedBldg by remember { mutableStateOf(propBuildings.firstOrNull()) }
        var floorNum by remember { mutableStateOf("1") }
        var label by remember { mutableStateOf("Floor 1") }
        AlertDialog(
            onDismissRequest = { showAddFloor = false },
            title = { Text("Add Floor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Building", propBuildings, selectedBldg, { "${it.buildingCode} - ${it.name}" }, { selectedBldg = it })
                    OutlinedTextField(value = floorNum, onValueChange = { floorNum = it }, label = { Text("Floor Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. Ground / First)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedBldg?.let { vm.addFloor(selectedPropertyId!!, it.id, floorNum.toIntOrNull() ?: 1, label) }
                    showAddFloor = false
                }, modifier = Modifier.testTag("save_floor_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddFloor = false }) { Text(strings.cancel) } }
        )
    }

    if (showAddRoom && selectedPropertyId != null) {
        var selectedFloor by remember { mutableStateOf(propFloors.firstOrNull()) }
        var roomNumber by remember { mutableStateOf("") }
        var roomType by remember { mutableStateOf("STUDIO") }
        var capacity by remember { mutableStateOf("2") }
        var rateQar by remember { mutableStateOf("") }
        var amenities by remember { mutableStateOf("AC, Wi-Fi, Private Bath") }
        var keys by remember { mutableStateOf("2") }
        var card by remember { mutableStateOf("") }
        var furniture by remember { mutableStateOf("Bed, Wardrobe, Fridge") }
        AlertDialog(
            onDismissRequest = { showAddRoom = false },
            title = { Text("Add Room / Unit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Floor", propFloors, selectedFloor, { it.label }, { selectedFloor = it })
                    OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("Room / Flat Number (e.g. 101)") }, modifier = Modifier.fillMaxWidth().testTag("room_number_input"))
                    OutlinedTextField(value = roomType, onValueChange = { roomType = it }, label = { Text("Room Type (STUDIO/1BHK/PARTITION/BEDSPACE)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rateQar, onValueChange = { rateQar = it }, label = { Text("Monthly Rent Rate (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("room_rate_input"))
                    OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("Occupant Capacity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = keys, onValueChange = { keys = it }, label = { Text("Key Count") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = card, onValueChange = { card = it }, label = { Text("Access Card Code") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amenities, onValueChange = { amenities = it }, label = { Text("Amenities") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = furniture, onValueChange = { furniture = it }, label = { Text("Furniture Inventory") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fl = selectedFloor
                    val rateFils = QatarFormatters.parseQarToFils(rateQar) ?: 0L
                    if (fl != null) {
                        vm.addRoom(
                            selectedPropertyId!!, fl.buildingId, fl.id, roomNumber,
                            roomType, capacity.toIntOrNull() ?: 1, "FURNISHED", rateFils,
                            amenities, keys.toIntOrNull() ?: 1, card, furniture
                        )
                    }
                    showAddRoom = false
                }, modifier = Modifier.testTag("save_room_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddRoom = false }) { Text(strings.cancel) } }
        )
    }

    reserveRoomTarget?.let { room ->
        var selectedCust by remember { mutableStateOf(data.customers.firstOrNull()) }
        var depQar by remember { mutableStateOf("500") }
        var days by remember { mutableStateOf("7") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reserveRoomTarget = null },
            title = { Text("Reserve Room ${room.roomNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Tenant", data.customers, selectedCust, { "${it.customerCode} - ${it.fullName}" }, { selectedCust = it })
                    OutlinedTextField(value = depQar, onValueChange = { depQar = it }, label = { Text("Hold Deposit (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Validity (Days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedCust?.let {
                        vm.reserveRoom(room.id, it.id, QatarFormatters.parseQarToFils(depQar) ?: 0L, days.toIntOrNull() ?: 7, notes)
                    }
                    reserveRoomTarget = null
                }) { Text("Reserve") }
            },
            dismissButton = { TextButton(onClick = { reserveRoomTarget = null }) { Text(strings.cancel) } }
        )
    }

    inspectionProperty?.let { prop ->
        var type by remember { mutableStateOf("CONDITION") }
        var summary by remember { mutableStateOf("") }
        var keys by remember { mutableStateOf("2") }
        var repair by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { inspectionProperty = null },
            title = { Text("Inspection: ${prop.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (MOVE_IN/MOVE_OUT/CONDITION)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Condition Summary") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = keys, onValueChange = { keys = it }, label = { Text("Keys Verified") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = repair, onCheckedChange = { repair = it })
                        Text("Repair Required?")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.recordInspection(prop.id, null, type, summary, keys.toIntOrNull() ?: 0, 0, repair)
                    inspectionProperty = null
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { inspectionProperty = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
fun TenantsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.TENANTS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val customers = uiState.data.customers
    var showAddTenant by remember { mutableStateOf(false) }
    var portalTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var familyTarget by remember { mutableStateOf<CustomerEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("tenants_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.navTenants,
                subtitle = "${customers.size} registered customers / tenants",
                actionLabel = if (ctx.hasPermission(AppModule.TENANTS, ActionType.CREATE)) "Add Tenant" else null,
                actionTag = "add_tenant_button",
                onAction = { showAddTenant = true }
            )
        }
        if (customers.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Tenants Registered",
                    description = "Add real tenants with Qatar QID / Passport, phone, and emergency details.",
                    icon = Icons.Default.People,
                    actionLabel = "Register Tenant",
                    testTag = "empty_tenants_card",
                    onAction = { showAddTenant = true }
                )
            }
        } else {
            items(customers, key = { it.id }) { cust ->
                val activeAgreement = uiState.data.agreements.firstOrNull { it.customerId == cust.id && it.status == "ACTIVE" }
                val custDueFils = uiState.data.monthlyRents
                    .filter { it.customerId == cust.id && it.status != "PAID" && it.status != "WAIVED" }
                    .sumOf { (it.amountDueFils - it.amountPaidFils).coerceAtLeast(0L) }
                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("tenant_card_${cust.customerCode}")) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cust.customerCode} • ${cust.fullName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Tel: ${cust.phone} • QID: ${cust.qidNumber.ifBlank { "-" }} • ${cust.nationality.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (cust.employerName.isNotBlank()) {
                                    Text("Employer: ${cust.employerName}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            StatusBadge(cust.status)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (activeAgreement != null) "Lease: ${activeAgreement.agreementNumber}" else "No active lease",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text("Due: ${QatarFormatters.formatQar(custDueFils)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { familyTarget = cust }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                Text("+ Family Member")
                            }
                            if (cust.portalUserId == null && ctx.userType == "OWNER") {
                                Button(
                                    onClick = { portalTarget = cust },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("provision_portal_${cust.customerCode}")
                                ) {
                                    Text("Enable Portal Login")
                                }
                            } else if (cust.portalUserId != null) {
                                StatusBadge("PORTAL ACTIVE")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTenant) {
        var fullName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var qid by remember { mutableStateOf("") }
        var passport by remember { mutableStateOf("") }
        var nationality by remember { mutableStateOf("") }
        var employer by remember { mutableStateOf("") }
        var emergency by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTenant = false },
            title = { Text("Register Tenant") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name*") }, modifier = Modifier.fillMaxWidth().testTag("tenant_name_input"))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Qatar Mobile (+974...)*") }, modifier = Modifier.fillMaxWidth().testTag("tenant_phone_input"))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = qid, onValueChange = { qid = it }, label = { Text("Qatar ID (QID)") }, modifier = Modifier.fillMaxWidth().testTag("tenant_qid_input"))
                    OutlinedTextField(value = passport, onValueChange = { passport = it }, label = { Text("Passport Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nationality, onValueChange = { nationality = it }, label = { Text("Nationality") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("Employer") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = emergency, onValueChange = { emergency = it }, label = { Text("Emergency Contact") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createCustomer(fullName, phone, email, qid, passport, nationality, employer, emergency, notes)
                    showAddTenant = false
                }, modifier = Modifier.testTag("save_tenant_button")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showAddTenant = false }) { Text(strings.cancel) } }
        )
    }

    portalTarget?.let { cust ->
        var email by remember { mutableStateOf(cust.email) }
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { portalTarget = null },
            title = { Text("Enable Customer Portal: ${cust.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Strict isolation: Customer will only see their own agreements, rent, receipts, and complaints.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Portal Email") }, modifier = Modifier.fillMaxWidth().testTag("portal_email_input"))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Initial Password (min 8 chars)") }, modifier = Modifier.fillMaxWidth().testTag("portal_password_input"))
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.provisionCustomerPortalUser(cust.id, email, password)
                    portalTarget = null
                }, modifier = Modifier.testTag("save_portal_user_button")) { Text("Create Login") }
            },
            dismissButton = { TextButton(onClick = { portalTarget = null }) { Text(strings.cancel) } }
        )
    }

    familyTarget?.let { cust ->
        var name by remember { mutableStateOf("") }
        var relation by remember { mutableStateOf("SPOUSE") }
        var qid by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { familyTarget = null },
            title = { Text("Add Family / Co-Occupant") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = relation, onValueChange = { relation = it }, label = { Text("Relationship") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = qid, onValueChange = { qid = it }, label = { Text("QID / Passport") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addFamilyMember(cust.id, name, relation, qid, phone)
                    familyTarget = null
                }) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { familyTarget = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
fun AgreementsScreen(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel
) {
    val ctx = uiState.authContext ?: return
    if (!ctx.hasPermission(AppModule.AGREEMENTS, ActionType.VIEW)) {
        PermissionDeniedCard()
        return
    }
    val strings = LocalAppStrings.current
    val data = uiState.data
    var showCreate by remember { mutableStateOf(false) }
    var renewTarget by remember { mutableStateOf<AgreementEntity?>(null) }
    var terminateTarget by remember { mutableStateOf<AgreementEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("agreements_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.agreements,
                subtitle = "${data.agreements.size} agreements",
                actionLabel = if (ctx.hasPermission(AppModule.AGREEMENTS, ActionType.CREATE)) "New Lease" else null,
                actionTag = "new_agreement_button",
                onAction = { showCreate = true }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.generateMonthlyRent() },
                    enabled = ctx.hasPermission(AppModule.PAYMENTS, ActionType.POST),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("generate_monthly_rent_button")
                ) {
                    Icon(Icons.Default.EventRepeat, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Generate Current Month Rent & GL")
                }
            }
        }
        if (data.agreements.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Rental Agreements",
                    description = "Activate a lease linking a vacant room and tenant with QAR monthly rent and security deposit.",
                    icon = Icons.Default.Description,
                    actionLabel = "Create Agreement",
                    testTag = "empty_agreements_card",
                    onAction = { showCreate = true }
                )
            }
        } else {
            items(data.agreements, key = { it.id }) { ag ->
                val custName = data.customers.firstOrNull { it.id == ag.customerId }?.fullName ?: "Tenant"
                val roomNo = data.rooms.firstOrNull { it.id == ag.roomId }?.roomNumber ?: "Room"
                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("agreement_card_${ag.agreementNumber}")) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${ag.agreementNumber} (v${ag.versionNumber})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$custName • Room $roomNo • ${ag.billingCycle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(ag.status)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Rent: ${QatarFormatters.formatQar(ag.rentAmountFils)} | Deposit: ${QatarFormatters.formatQar(ag.securityDepositFils)} | ${QatarFormatters.formatDate(ag.startDateEpoch)} -> ${QatarFormatters.formatDate(ag.endDateEpoch)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (ag.status == "ACTIVE" && ctx.hasPermission(AppModule.AGREEMENTS, ActionType.EDIT)) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { renewTarget = ag }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text("Renew")
                                }
                                OutlinedButton(onClick = { terminateTarget = ag }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text("Terminate / Move Out")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        val vacantRooms = data.rooms.filter { it.status == "VACANT" || it.status == "RESERVED" }
        var selectedRoom by remember { mutableStateOf(vacantRooms.firstOrNull()) }
        var selectedCustomer by remember { mutableStateOf(data.customers.firstOrNull()) }
        var rentQar by remember { mutableStateOf(selectedRoom?.let { "%.2f".format(it.monthlyRateFils / 100.0) } ?: "") }
        var depositQar by remember { mutableStateOf(rentQar) }
        var months by remember { mutableStateOf("12") }
        var cycle by remember { mutableStateOf("MONTHLY") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Activate Rental Agreement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Vacant Room", vacantRooms, selectedRoom, { "Room ${it.roomNumber} (${QatarFormatters.formatQar(it.monthlyRateFils)})" }, {
                        selectedRoom = it
                        rentQar = "%.2f".format(it.monthlyRateFils / 100.0)
                    }, testTag = "agreement_room_dropdown")
                    DropdownSelector("Tenant", data.customers, selectedCustomer, { "${it.customerCode} - ${it.fullName}" }, { selectedCustomer = it }, testTag = "agreement_customer_dropdown")
                    OutlinedTextField(value = rentQar, onValueChange = { rentQar = it }, label = { Text("Monthly Rent (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("agreement_rent_input"))
                    OutlinedTextField(value = depositQar, onValueChange = { depositQar = it }, label = { Text("Security Deposit Expected (QAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("agreement_deposit_input"))
                    OutlinedTextField(value = months, onValueChange = { months = it }, label = { Text("Term Length (Months)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cycle, onValueChange = { cycle = it }, label = { Text("Billing Cycle (MONTHLY/QUARTERLY/ANNUAL)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Special Terms") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val rm = selectedRoom
                    val cu = selectedCustomer
                    if (rm != null && cu != null) {
                        val now = System.currentTimeMillis()
                        val termMonths = (months.toLongOrNull() ?: 12L).coerceAtLeast(1L)
                        val endEpoch = now + termMonths * 30L * 24L * 3600_000L
                        vm.createAgreement(
                            rm.id, cu.id, cycle,
                            QatarFormatters.parseQarToFils(rentQar) ?: rm.monthlyRateFils,
                            0L,
                            QatarFormatters.parseQarToFils(depositQar) ?: 0L,
                            now, endEpoch, 30, notes
                        )
                    }
                    showCreate = false
                }, modifier = Modifier.testTag("save_agreement_button")) { Text("Activate Lease") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(strings.cancel) } }
        )
    }

    renewTarget?.let { ag ->
        var newRentQar by remember { mutableStateOf("%.2f".format(ag.rentAmountFils / 100.0)) }
        var extraMonths by remember { mutableStateOf("12") }
        AlertDialog(
            onDismissRequest = { renewTarget = null },
            title = { Text("Renew ${ag.agreementNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newRentQar, onValueChange = { newRentQar = it }, label = { Text("New Monthly Rent (QAR)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = extraMonths, onValueChange = { extraMonths = it }, label = { Text("Extend From End (Months)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val monthsAdd = (extraMonths.toLongOrNull() ?: 12L).coerceAtLeast(1L)
                    val newEnd = ag.endDateEpoch + monthsAdd * 30L * 24L * 3600_000L
                    vm.renewAgreement(ag.id, newEnd, QatarFormatters.parseQarToFils(newRentQar) ?: ag.rentAmountFils)
                    renewTarget = null
                }) { Text("Confirm Renewal") }
            },
            dismissButton = { TextButton(onClick = { renewTarget = null }) { Text(strings.cancel) } }
        )
    }

    terminateTarget?.let { ag ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { terminateTarget = null },
            title = { Text("Terminate Lease & Move Out") },
            text = {
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Move-out / Termination Reason") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    vm.terminateAgreement(ag.id, reason)
                    terminateTarget = null
                }) { Text("Terminate") }
            },
            dismissButton = { TextButton(onClick = { terminateTarget = null }) { Text(strings.cancel) } }
        )
    }
}
