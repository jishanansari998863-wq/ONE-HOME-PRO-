package com.example.domain.service

import androidx.room.withTransaction
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.security.AuthContext
import com.example.domain.security.AuthorizationGuard
import com.example.domain.security.CryptoUtil
import com.example.domain.util.QatarFormatters

class WorkforceMaintenanceInventoryService(
    private val db: OneHomeProDatabase,
    private val accountingService: AccountingService
) {
    private val hrDao = db.securityAndHrDao()
    private val opsDao = db.operationsDao()

    // --- HR & Workforce ---
    suspend fun createDepartment(ctx: AuthContext, name: String, description: String): DepartmentEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.EMPLOYEES, ActionType.CREATE, ctx.activeBusinessId)
        val dep = DepartmentEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            name = name.trim(),
            description = description.trim(),
            createdAtEpoch = System.currentTimeMillis()
        )
        hrDao.insertDepartment(dep)
        return dep
    }

    suspend fun createTeam(ctx: AuthContext, departmentId: String, name: String): TeamEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.EMPLOYEES, ActionType.CREATE, ctx.activeBusinessId)
        val team = TeamEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            departmentId = departmentId,
            name = name.trim(),
            createdAtEpoch = System.currentTimeMillis()
        )
        hrDao.insertTeam(team)
        return team
    }

    suspend fun addEmployee(
        ctx: AuthContext,
        fullName: String,
        jobTitle: String,
        departmentId: String?,
        phone: String,
        email: String,
        qidNumber: String,
        workLocation: String,
        shiftSchedule: String
    ): EmployeeEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.EMPLOYEES, ActionType.CREATE, ctx.activeBusinessId)
        require(fullName.isNotBlank()) { "Employee name required" }
        val now = System.currentTimeMillis()
        val empId = CryptoUtil.newUuid()
        return db.withTransaction {
            val emp = EmployeeEntity(
                id = empId,
                businessId = ctx.activeBusinessId,
                employeeCode = "EMP-${empId.take(6).uppercase()}",
                fullName = fullName.trim(),
                jobTitle = jobTitle.trim().ifBlank { "Staff" },
                departmentId = departmentId,
                phone = phone.trim(),
                email = email.trim(),
                qidNumber = qidNumber.trim(),
                workLocation = workLocation.trim(),
                shiftSchedule = shiftSchedule.ifBlank { "08:00 - 17:00 AST" },
                hiredAtEpoch = now,
                createdByUserId = ctx.userId,
                updatedAtEpoch = now
            )
            hrDao.insertEmployee(emp)
            hrDao.insertEmployeeWorkHistory(
                EmployeeWorkHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    employeeId = empId,
                    eventType = "ONBOARDED",
                    summary = "Employee ${emp.employeeCode} (${emp.jobTitle}) onboarded",
                    actorUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
            emp
        }
    }

    suspend fun deactivateEmployee(ctx: AuthContext, employeeId: String, reason: String) {
        AuthorizationGuard.requirePermission(ctx, AppModule.EMPLOYEES, ActionType.ARCHIVE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val emp = hrDao.getEmployeeById(employeeId) ?: throw IllegalArgumentException("Employee not found")
            require(emp.businessId == ctx.activeBusinessId) { "Cross-business access blocked" }
            hrDao.updateEmployee(emp.copy(status = "DEACTIVATED", deactivatedAtEpoch = now, updatedAtEpoch = now))
            hrDao.insertEmployeeWorkHistory(
                EmployeeWorkHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    employeeId = emp.id,
                    eventType = "DEACTIVATED",
                    summary = "Deactivated (history preserved): $reason",
                    actorUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
        }
    }

    suspend fun recordAttendance(
        ctx: AuthContext,
        employeeId: String,
        status: String,
        overtimeMinutes: Int,
        notes: String
    ): AttendanceEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.EMPLOYEES, ActionType.CREATE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        val att = AttendanceEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            employeeId = employeeId,
            dateKey = QatarFormatters.dateKey(now),
            status = status,
            checkInTime = if (status == "PRESENT" || status == "OVERTIME") "08:00" else "",
            overtimeMinutes = overtimeMinutes.coerceAtLeast(0),
            notes = notes.trim(),
            recordedByUserId = ctx.userId,
            recordedAtEpoch = now
        )
        hrDao.insertAttendance(att)
        return att
    }

    // --- Maintenance & Complaints ---
    suspend fun createComplaint(
        ctx: AuthContext,
        propertyId: String,
        roomId: String?,
        customerId: String?,
        title: String,
        description: String,
        category: String,
        priority: String,
        isPreventive: Boolean = false,
        attachmentUri: String? = null
    ): ComplaintEntity {
        if (ctx.isCustomerPortalOnly()) {
            require(customerId == ctx.customerId) { "Customers can only file complaints for themselves" }
        } else {
            AuthorizationGuard.requirePermission(ctx, AppModule.MAINTENANCE, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        }
        require(title.isNotBlank()) { "Complaint title is required" }
        val now = System.currentTimeMillis()
        val compId = CryptoUtil.newUuid()
        return db.withTransaction {
            val comp = ComplaintEntity(
                id = compId,
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                roomId = roomId,
                customerId = customerId,
                complaintNumber = "CMP-${compId.take(6).uppercase()}",
                title = title.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                status = "OPEN",
                isPreventive = isPreventive,
                createdByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            opsDao.insertComplaint(comp)
            opsDao.insertComplaintStatusHistory(
                ComplaintStatusHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    complaintId = compId,
                    fromStatus = "NEW",
                    toStatus = "OPEN",
                    note = "Complaint created",
                    changedByUserId = ctx.userId,
                    changedAtEpoch = now
                )
            )
            if (!attachmentUri.isNullOrBlank()) {
                opsDao.insertComplaintAttachment(
                    ComplaintAttachmentEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        complaintId = compId,
                        fileUri = attachmentUri,
                        mediaType = "PHOTO",
                        uploadedByUserId = ctx.userId,
                        uploadedAtEpoch = now
                    )
                )
            }
            comp
        }
    }

    suspend fun assignTechnician(
        ctx: AuthContext,
        complaintId: String,
        technicianEmployeeId: String,
        deadlineEpoch: Long? = null
    ) {
        val comp = opsDao.getComplaintById(complaintId) ?: throw IllegalArgumentException("Complaint not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.MAINTENANCE, ActionType.EDIT, comp.businessId, comp.propertyId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            opsDao.insertJobAssignment(
                JobAssignmentEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = comp.businessId,
                    complaintId = comp.id,
                    technicianEmployeeId = technicianEmployeeId,
                    assignedByUserId = ctx.userId,
                    assignedAtEpoch = now,
                    deadlineEpoch = deadlineEpoch
                )
            )
            opsDao.updateComplaint(comp.copy(status = "ASSIGNED", updatedAtEpoch = now))
            opsDao.insertComplaintStatusHistory(
                ComplaintStatusHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = comp.businessId,
                    complaintId = comp.id,
                    fromStatus = comp.status,
                    toStatus = "ASSIGNED",
                    note = "Assigned technician $technicianEmployeeId",
                    changedByUserId = ctx.userId,
                    changedAtEpoch = now
                )
            )
        }
    }

    suspend fun submitWorkReportAndMaterials(
        ctx: AuthContext,
        complaintId: String,
        technicianEmployeeId: String,
        summary: String,
        labourMinutes: Int,
        labourCostFils: Long,
        beforePhotoUri: String?,
        afterPhotoUri: String?,
        usedItemId: String?,
        usedStockLocationId: String?,
        usedQuantity: Long
    ): WorkReportEntity {
        val comp = opsDao.getComplaintById(complaintId) ?: throw IllegalArgumentException("Complaint not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.MAINTENANCE, ActionType.EDIT, comp.businessId, comp.propertyId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val reportId = CryptoUtil.newUuid()
            val report = WorkReportEntity(
                id = reportId,
                businessId = comp.businessId,
                complaintId = comp.id,
                technicianEmployeeId = technicianEmployeeId,
                summary = summary.trim(),
                labourMinutes = labourMinutes.coerceAtLeast(0),
                labourCostFils = labourCostFils.coerceAtLeast(0L),
                completedAtEpoch = now
            )
            opsDao.insertWorkReport(report)
            if (!beforePhotoUri.isNullOrBlank()) {
                opsDao.insertWorkReportPhoto(
                    WorkReportPhotoEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = comp.businessId,
                        workReportId = reportId,
                        phase = "BEFORE",
                        photoUri = beforePhotoUri,
                        capturedAtEpoch = now
                    )
                )
            }
            if (!afterPhotoUri.isNullOrBlank()) {
                opsDao.insertWorkReportPhoto(
                    WorkReportPhotoEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = comp.businessId,
                        workReportId = reportId,
                        phase = "AFTER",
                        photoUri = afterPhotoUri,
                        capturedAtEpoch = now
                    )
                )
            }
            if (usedItemId != null && usedStockLocationId != null && usedQuantity > 0L) {
                consumeMaterialOnceInternal(
                    ctx = ctx,
                    comp = comp,
                    workReportId = reportId,
                    itemId = usedItemId,
                    stockLocationId = usedStockLocationId,
                    quantity = usedQuantity,
                    idempotencyKey = "MAT-$reportId-$usedItemId"
                )
            }
            opsDao.updateComplaint(comp.copy(status = "AWAITING_APPROVAL", updatedAtEpoch = now))
            opsDao.insertComplaintStatusHistory(
                ComplaintStatusHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = comp.businessId,
                    complaintId = comp.id,
                    fromStatus = comp.status,
                    toStatus = "AWAITING_APPROVAL",
                    note = "Work report submitted",
                    changedByUserId = ctx.userId,
                    changedAtEpoch = now
                )
            )
            report
        }
    }

    suspend fun approveMaintenanceJob(ctx: AuthContext, complaintId: String, decision: String, notes: String) {
        val comp = opsDao.getComplaintById(complaintId) ?: throw IllegalArgumentException("Complaint not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.MAINTENANCE, ActionType.APPROVE, comp.businessId, comp.propertyId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            opsDao.insertJobApproval(
                JobApprovalEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = comp.businessId,
                    complaintId = comp.id,
                    status = decision,
                    approvedByUserId = ctx.userId,
                    notes = notes.trim(),
                    decidedAtEpoch = now
                )
            )
            val nextStatus = if (decision == "APPROVED") "COMPLETED" else "IN_PROGRESS"
            opsDao.updateComplaint(comp.copy(status = nextStatus, updatedAtEpoch = now))
            opsDao.insertComplaintStatusHistory(
                ComplaintStatusHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = comp.businessId,
                    complaintId = comp.id,
                    fromStatus = comp.status,
                    toStatus = nextStatus,
                    note = "$decision: $notes",
                    changedByUserId = ctx.userId,
                    changedAtEpoch = now
                )
            )
        }
    }

    suspend fun customerConfirmComplaint(ctx: AuthContext, complaintId: String) {
        val comp = opsDao.getComplaintById(complaintId) ?: throw IllegalArgumentException("Complaint not found")
        require(ctx.customerId == comp.customerId || ctx.userType == "OWNER") { "Only the tenant can confirm completion" }
        val now = System.currentTimeMillis()
        opsDao.updateComplaint(comp.copy(customerConfirmed = true, status = "CLOSED", updatedAtEpoch = now))
    }

    private suspend fun consumeMaterialOnceInternal(
        ctx: AuthContext,
        comp: ComplaintEntity,
        workReportId: String,
        itemId: String,
        stockLocationId: String,
        quantity: Long,
        idempotencyKey: String
    ): MaterialUsageEntity {
        val existing = opsDao.getMaterialUsageByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing
        val item = opsDao.getItemById(itemId) ?: throw IllegalArgumentException("Inventory item not found")
        require(item.businessId == comp.businessId) { "Cross-business inventory blocked" }
        require(item.currentStockQty >= quantity) {
            "Insufficient stock for ${item.name} (available=${item.currentStockQty}, requested=$quantity). Negative stock forbidden."
        }
        val now = System.currentTimeMillis()
        val unitCost = item.weightedAvgCostFils.coerceAtLeast(0L)
        val totalCost = unitCost * quantity
        val moveId = CryptoUtil.newUuid()
        opsDao.insertStockMovement(
            StockMovementEntity(
                id = moveId,
                businessId = comp.businessId,
                itemId = item.id,
                stockLocationId = stockLocationId,
                movementType = "OUT",
                qtyDelta = -quantity,
                unitCostFils = unitCost,
                referenceType = "MAINTENANCE",
                referenceId = comp.id,
                idempotencyKey = "SM-$idempotencyKey",
                notes = "Used on ${comp.complaintNumber}",
                createdByUserId = ctx.userId,
                occurredAtEpoch = now
            )
        )
        opsDao.updateItem(item.copy(currentStockQty = item.currentStockQty - quantity, updatedAtEpoch = now))

        val jeId = if (totalCost > 0L) {
            accountingService.postJournalInternal(
                businessId = comp.businessId,
                propertyId = comp.propertyId,
                entryDateEpoch = now,
                description = "Maintenance material usage: ${item.name} x$quantity (${comp.complaintNumber})",
                sourceType = "MAINTENANCE",
                sourceId = comp.id,
                idempotencyKey = "JE-$idempotencyKey",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(AccountingService.ACC_MAINTENANCE_EXPENSE, totalCost, 0L, "${item.name} consumed"),
                    JournalLineInput(AccountingService.ACC_INVENTORY, 0L, totalCost, "Inventory reduced")
                )
            ).id
        } else {
            ""
        }

        val usage = MaterialUsageEntity(
            id = CryptoUtil.newUuid(),
            businessId = comp.businessId,
            complaintId = comp.id,
            workReportId = workReportId,
            itemId = item.id,
            stockLocationId = stockLocationId,
            quantity = quantity,
            unitCostFils = unitCost,
            totalCostFils = totalCost,
            idempotencyKey = idempotencyKey,
            stockMovementId = moveId,
            journalEntryId = jeId,
            recordedByUserId = ctx.userId,
            recordedAtEpoch = now
        )
        opsDao.insertMaterialUsage(usage)
        return usage
    }

    // --- Purchase & Inventory ---
    suspend fun createSupplier(
        ctx: AuthContext,
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        crNumber: String
    ): SupplierEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.CREATE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        val id = CryptoUtil.newUuid()
        val sup = SupplierEntity(
            id = id,
            businessId = ctx.activeBusinessId,
            supplierCode = "SUP-${id.take(5).uppercase()}",
            name = name.trim(),
            contactPerson = contactPerson.trim(),
            phone = phone.trim(),
            email = email.trim(),
            crNumber = crNumber.trim(),
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        opsDao.insertSupplier(sup)
        return sup
    }

    suspend fun createItemCategoryAndItem(
        ctx: AuthContext,
        categoryName: String,
        itemName: String,
        sku: String,
        unit: String,
        reorderLevel: Long
    ): ItemEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.CREATE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val catId = CryptoUtil.newUuid()
            opsDao.insertItemCategory(
                ItemCategoryEntity(
                    id = catId,
                    businessId = ctx.activeBusinessId,
                    name = categoryName.trim().ifBlank { "General Maintenance" },
                    createdAtEpoch = now
                )
            )
            val itemId = CryptoUtil.newUuid()
            val item = ItemEntity(
                id = itemId,
                businessId = ctx.activeBusinessId,
                categoryId = catId,
                sku = sku.trim().ifBlank { "SKU-${itemId.take(5).uppercase()}" },
                name = itemName.trim(),
                unit = unit.trim().ifBlank { "PCS" },
                reorderLevel = reorderLevel.coerceAtLeast(0L),
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            opsDao.insertItem(item)
            item
        }
    }

    suspend fun createPurchaseOrder(
        ctx: AuthContext,
        supplierId: String,
        propertyId: String?,
        itemId: String,
        orderedQty: Long,
        unitCostFils: Long,
        notes: String
    ): PurchaseEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        require(orderedQty > 0 && unitCostFils > 0) { "Ordered quantity and unit cost must be positive" }
        val now = System.currentTimeMillis()
        val purId = CryptoUtil.newUuid()
        val total = orderedQty * unitCostFils
        return db.withTransaction {
            val purchase = PurchaseEntity(
                id = purId,
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                supplierId = supplierId,
                purchaseNumber = "PO-${purId.take(6).uppercase()}",
                status = "REQUESTED",
                totalAmountFils = total,
                notes = notes.trim(),
                createdByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            opsDao.insertPurchase(purchase)
            opsDao.insertPurchaseItem(
                PurchaseItemEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    purchaseId = purId,
                    itemId = itemId,
                    orderedQty = orderedQty,
                    unitCostFils = unitCostFils
                )
            )
            purchase
        }
    }

    suspend fun approvePurchase(ctx: AuthContext, purchaseId: String, notes: String) {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.APPROVE, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val po = opsDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase not found")
            opsDao.insertPurchaseApproval(
                PurchaseApprovalEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = po.businessId,
                    purchaseId = po.id,
                    decision = "APPROVED",
                    notes = notes,
                    decidedByUserId = ctx.userId,
                    decidedAtEpoch = now
                )
            )
            opsDao.updatePurchase(po.copy(status = "APPROVED", approvedByUserId = ctx.userId, updatedAtEpoch = now))
        }
    }

    suspend fun receiveGoods(
        ctx: AuthContext,
        purchaseId: String,
        stockLocationId: String,
        supplierInvoiceNumber: String
    ) {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.POST, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val po = opsDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase not found")
            require(po.status == "APPROVED" || po.status == "ORDERED" || po.status == "PARTIALLY_RECEIVED") {
                "Purchase must be APPROVED before receiving goods"
            }
            val lines = opsDao.getPurchaseItemsForPurchase(po.id)
            var totalReceivedCost = 0L
            for (line in lines) {
                val qtyToReceive = (line.orderedQty - line.receivedQty).coerceAtLeast(0L)
                if (qtyToReceive <= 0L) continue
                val item = opsDao.getItemById(line.itemId)!!
                val oldValue = item.currentStockQty * item.weightedAvgCostFils
                val addValue = qtyToReceive * line.unitCostFils
                val newQty = item.currentStockQty + qtyToReceive
                val newAvg = if (newQty > 0L) (oldValue + addValue) / newQty else line.unitCostFils
                totalReceivedCost += addValue

                val moveId = CryptoUtil.newUuid()
                val grKey = "GR-${po.id}-${line.id}-${line.receivedQty + qtyToReceive}"
                opsDao.insertStockMovement(
                    StockMovementEntity(
                        id = moveId,
                        businessId = po.businessId,
                        itemId = item.id,
                        stockLocationId = stockLocationId,
                        movementType = "IN",
                        qtyDelta = qtyToReceive,
                        unitCostFils = line.unitCostFils,
                        referenceType = "PURCHASE_GR",
                        referenceId = po.id,
                        idempotencyKey = grKey,
                        notes = "Goods received for ${po.purchaseNumber}",
                        createdByUserId = ctx.userId,
                        occurredAtEpoch = now
                    )
                )
                opsDao.updateItem(item.copy(currentStockQty = newQty, weightedAvgCostFils = newAvg, updatedAtEpoch = now))
                opsDao.updatePurchaseItem(line.copy(receivedQty = line.receivedQty + qtyToReceive))

                val je = accountingService.postJournalInternal(
                    businessId = po.businessId,
                    propertyId = po.propertyId,
                    entryDateEpoch = now,
                    description = "Goods receipt ${po.purchaseNumber} (${item.name} x$qtyToReceive)",
                    sourceType = "PURCHASE",
                    sourceId = po.id,
                    idempotencyKey = "JE-$grKey",
                    createdByUserId = ctx.userId,
                    lines = listOf(
                        JournalLineInput(AccountingService.ACC_INVENTORY, addValue, 0L, "${item.name} received"),
                        JournalLineInput(AccountingService.ACC_ACCOUNTS_PAYABLE, 0L, addValue, "Supplier payable ${po.purchaseNumber}")
                    )
                )
                opsDao.insertGoodsReceiptDetail(
                    GoodsReceiptDetailEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = po.businessId,
                        purchaseId = po.id,
                        purchaseItemId = line.id,
                        stockLocationId = stockLocationId,
                        receivedQty = qtyToReceive,
                        unitCostFils = line.unitCostFils,
                        stockMovementId = moveId,
                        journalEntryId = je.id,
                        receivedByUserId = ctx.userId,
                        receivedAtEpoch = now
                    )
                )
            }
            require(totalReceivedCost > 0L) { "Nothing left to receive on ${po.purchaseNumber}" }
            opsDao.updatePurchase(
                po.copy(
                    status = "RECEIVED",
                    supplierInvoiceNumber = supplierInvoiceNumber.ifBlank { po.supplierInvoiceNumber },
                    updatedAtEpoch = now
                )
            )
        }
    }

    suspend fun paySupplier(ctx: AuthContext, purchaseId: String, amountFils: Long, method: String) {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.POST, ctx.activeBusinessId)
        require(amountFils > 0)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val po = opsDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase not found")
            val remaining = (po.totalAmountFils - po.paidAmountFils).coerceAtLeast(0L)
            require(amountFils <= remaining) { "Payment exceeds remaining payable balance" }
            val payId = CryptoUtil.newUuid()
            val ref = "SPAY-${payId.take(6).uppercase()}"
            val creditCode = if (method == "CASH") AccountingService.ACC_CASH else AccountingService.ACC_BANK
            val je = accountingService.postJournalInternal(
                businessId = po.businessId,
                propertyId = po.propertyId,
                entryDateEpoch = now,
                description = "Supplier payment $ref for ${po.purchaseNumber}",
                sourceType = "PURCHASE",
                sourceId = po.id,
                idempotencyKey = "JE-$payId",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(AccountingService.ACC_ACCOUNTS_PAYABLE, amountFils, 0L, "Settle payable"),
                    JournalLineInput(creditCode, 0L, amountFils, method)
                )
            )
            opsDao.insertSupplierPayment(
                SupplierPaymentEntity(
                    id = payId,
                    businessId = po.businessId,
                    supplierId = po.supplierId,
                    referenceNumber = ref,
                    amountFils = amountFils,
                    method = method,
                    journalEntryId = je.id,
                    paidByUserId = ctx.userId,
                    paidAtEpoch = now
                )
            )
            opsDao.insertSupplierPaymentAllocation(
                SupplierPaymentAllocationEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = po.businessId,
                    supplierPaymentId = payId,
                    purchaseId = po.id,
                    allocatedFils = amountFils,
                    createdAtEpoch = now
                )
            )
            opsDao.updatePurchase(po.copy(paidAmountFils = po.paidAmountFils + amountFils, updatedAtEpoch = now))
        }
    }

    suspend fun adjustStock(
        ctx: AuthContext,
        itemId: String,
        stockLocationId: String,
        qtyDelta: Long,
        reason: String
    ) {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.APPROVE, ctx.activeBusinessId)
        require(qtyDelta != 0L)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val item = opsDao.getItemById(itemId) ?: throw IllegalArgumentException("Item not found")
            val newQty = item.currentStockQty + qtyDelta
            require(newQty >= 0L) { "Negative stock is prohibited without authorized override" }
            val moveId = CryptoUtil.newUuid()
            opsDao.insertStockMovement(
                StockMovementEntity(
                    id = moveId,
                    businessId = ctx.activeBusinessId,
                    itemId = item.id,
                    stockLocationId = stockLocationId,
                    movementType = "ADJUSTMENT",
                    qtyDelta = qtyDelta,
                    unitCostFils = item.weightedAvgCostFils,
                    referenceType = "ADJUSTMENT",
                    referenceId = moveId,
                    idempotencyKey = "ADJ-$moveId",
                    notes = reason,
                    createdByUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
            opsDao.insertStockAdjustment(
                StockAdjustmentEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    itemId = item.id,
                    stockLocationId = stockLocationId,
                    qtyDelta = qtyDelta,
                    reason = reason,
                    stockMovementId = moveId,
                    approvedByUserId = ctx.userId,
                    createdByUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
            opsDao.updateItem(item.copy(currentStockQty = newQty, updatedAtEpoch = now))
        }
    }

    suspend fun recordStockCount(ctx: AuthContext, itemId: String, stockLocationId: String, countedQty: Long) {
        AuthorizationGuard.requirePermission(ctx, AppModule.INVENTORY, ActionType.POST, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val item = opsDao.getItemById(itemId) ?: throw IllegalArgumentException("Item not found")
            val countId = CryptoUtil.newUuid()
            val variance = countedQty - item.currentStockQty
            opsDao.insertStockCount(
                StockCountEntity(
                    id = countId,
                    businessId = ctx.activeBusinessId,
                    stockLocationId = stockLocationId,
                    countReference = "CNT-${countId.take(6).uppercase()}",
                    countedByUserId = ctx.userId,
                    countedAtEpoch = now
                )
            )
            opsDao.insertStockCountLine(
                StockCountLineEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    stockCountId = countId,
                    itemId = item.id,
                    expectedQty = item.currentStockQty,
                    countedQty = countedQty,
                    varianceQty = variance
                )
            )
            if (variance != 0L) {
                adjustStock(ctx, itemId, stockLocationId, variance, "Physical stock count variance")
            }
        }
    }
}
