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

class PropertyTenancyService(
    private val db: OneHomeProDatabase,
    private val accountingService: AccountingService,
    private val authService: AuthService
) {
    private val dao = db.coreAndTenancyDao()
    private val secDao = db.securityAndHrDao()
    private val opsDao = db.operationsDao()

    suspend fun createBusinessBranch(
        ctx: AuthContext,
        name: String,
        branchName: String,
        phone: String,
        email: String,
        address: String,
        crNumber: String
    ): BusinessEntity {
        AuthorizationGuard.requireOwner(ctx)
        require(name.isNotBlank()) { "Business name is required" }
        val now = System.currentTimeMillis()
        val id = CryptoUtil.newUuid()
        return db.withTransaction {
            val biz = BusinessEntity(
                id = id,
                name = name.trim(),
                code = "BIZ-${id.take(6).uppercase()}",
                branchName = branchName.trim().ifBlank { "Branch" },
                currency = "QAR",
                timezone = "Asia/Qatar",
                phone = phone.trim(),
                email = email.trim(),
                address = address.trim(),
                crNumber = crNumber.trim(),
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertBusiness(biz)
            accountingService.seedDefaultChartOfAccounts(id, now)
            authService.seedDefaultRolesForBusiness(id, now)
            val ownerRole = secDao.getRoleByName(id, "OWNER")!!
            secDao.insertUserRole(
                UserRoleEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = id,
                    userId = ctx.userId,
                    roleId = ownerRole.id,
                    assignedByUserId = ctx.userId,
                    assignedAtEpoch = now
                )
            )
            secDao.insertUserBusinessAccess(
                UserBusinessAccessEntity(
                    id = CryptoUtil.newUuid(),
                    userId = ctx.userId,
                    businessId = id,
                    grantedByUserId = ctx.userId,
                    grantedAtEpoch = now
                )
            )
            opsDao.insertStockLocation(
                StockLocationEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = id,
                    name = "Central Store (${biz.branchName})",
                    locationType = "WAREHOUSE",
                    createdAtEpoch = now
                )
            )
            biz
        }
    }

    suspend fun updateBusinessProfile(
        ctx: AuthContext,
        name: String,
        branchName: String,
        phone: String,
        email: String,
        address: String,
        crNumber: String,
        logoUri: String?
    ): BusinessEntity {
        AuthorizationGuard.requireOwner(ctx)
        val current = dao.getBusinessById(ctx.activeBusinessId) ?: error("Business missing")
        val updated = current.copy(
            name = name.trim().ifBlank { current.name },
            branchName = branchName.trim().ifBlank { current.branchName },
            phone = phone.trim(),
            email = email.trim(),
            address = address.trim(),
            crNumber = crNumber.trim(),
            logoUri = logoUri ?: current.logoUri,
            updatedAtEpoch = System.currentTimeMillis()
        )
        dao.updateBusiness(updated)
        return updated
    }

    suspend fun createProperty(
        ctx: AuthContext,
        name: String,
        code: String,
        propertyType: String,
        address: String,
        city: String,
        notes: String,
        photoUri: String? = null
    ): PropertyEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.CREATE, ctx.activeBusinessId)
        require(name.isNotBlank()) { "Property name is required" }
        val now = System.currentTimeMillis()
        val id = CryptoUtil.newUuid()
        val cleanCode = code.trim().ifBlank { "PROP-${id.take(4).uppercase()}" }
        val entity = PropertyEntity(
            id = id,
            businessId = ctx.activeBusinessId,
            code = cleanCode,
            name = name.trim(),
            propertyType = propertyType.ifBlank { "RESIDENTIAL" },
            address = address.trim(),
            city = city.trim().ifBlank { "Doha" },
            notes = notes.trim(),
            photoUri = photoUri,
            createdByUserId = ctx.userId,
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        dao.insertProperty(entity)
        return entity
    }

    suspend fun updateProperty(ctx: AuthContext, property: PropertyEntity): PropertyEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.EDIT, property.businessId, property.id)
        val updated = property.copy(updatedAtEpoch = System.currentTimeMillis())
        dao.updateProperty(updated)
        return updated
    }

    suspend fun addBuilding(
        ctx: AuthContext,
        propertyId: String,
        name: String,
        code: String,
        floorsCount: Int
    ): BuildingEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        val now = System.currentTimeMillis()
        val b = BuildingEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            propertyId = propertyId,
            name = name.trim(),
            buildingCode = code.trim().ifBlank { "BLD-${now % 1000}" },
            totalFloorsDeclared = floorsCount.coerceAtLeast(1),
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        dao.insertBuilding(b)
        return b
    }

    suspend fun addFloor(
        ctx: AuthContext,
        propertyId: String,
        buildingId: String,
        floorNumber: Int,
        label: String
    ): FloorEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        val now = System.currentTimeMillis()
        val f = FloorEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            propertyId = propertyId,
            buildingId = buildingId,
            floorNumber = floorNumber,
            label = label.trim().ifBlank { "Floor $floorNumber" },
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        dao.insertFloor(f)
        return f
    }

    suspend fun addRoom(
        ctx: AuthContext,
        propertyId: String,
        buildingId: String,
        floorId: String,
        roomNumber: String,
        roomType: String,
        capacity: Int,
        furnishedStatus: String,
        monthlyRateFils: Long,
        amenities: String,
        keyCount: Int,
        accessCardCode: String,
        furnitureList: String,
        photoUri: String? = null
    ): RoomEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        require(roomNumber.isNotBlank()) { "Room number is required" }
        require(monthlyRateFils > 0) { "Monthly rate must be > 0 QAR" }
        val now = System.currentTimeMillis()
        val room = RoomEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            propertyId = propertyId,
            buildingId = buildingId,
            floorId = floorId,
            roomNumber = roomNumber.trim(),
            roomType = roomType.ifBlank { "STUDIO" },
            capacity = capacity.coerceAtLeast(1),
            amenities = amenities.trim(),
            furnishedStatus = furnishedStatus,
            monthlyRateFils = monthlyRateFils,
            status = "VACANT",
            keyCount = keyCount.coerceAtLeast(0),
            accessCardCode = accessCardCode.trim(),
            furnitureList = furnitureList.trim(),
            photoUri = photoUri,
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        dao.insertRoom(room)
        return room
    }

    suspend fun updateRoomStatus(ctx: AuthContext, roomId: String, newStatus: String, repairNotes: String = "") {
        val room = dao.getRoomById(roomId) ?: throw IllegalArgumentException("Room not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.EDIT, room.businessId, room.propertyId)
        dao.updateRoom(
            room.copy(
                status = newStatus,
                repairNotes = repairNotes.ifBlank { room.repairNotes },
                updatedAtEpoch = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordInspection(
        ctx: AuthContext,
        propertyId: String,
        roomId: String?,
        inspectionType: String,
        conditionSummary: String,
        keysHandedOver: Int,
        accessCardsHandedOver: Int,
        repairRequired: Boolean
    ): PropertyInspectionEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        val entity = PropertyInspectionEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            propertyId = propertyId,
            roomId = roomId,
            inspectionType = inspectionType,
            conditionSummary = conditionSummary.trim(),
            keysHandedOver = keysHandedOver,
            accessCardsHandedOver = accessCardsHandedOver,
            repairRequired = repairRequired,
            inspectorUserId = ctx.userId,
            inspectedAtEpoch = System.currentTimeMillis()
        )
        dao.insertInspection(entity)
        return entity
    }

    suspend fun reserveRoom(
        ctx: AuthContext,
        roomId: String,
        customerId: String,
        depositFils: Long,
        daysValid: Int,
        notes: String
    ): RoomReservationEntity {
        val room = dao.getRoomById(roomId) ?: throw IllegalArgumentException("Room not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.EDIT, room.businessId, room.propertyId)
        require(room.status == "VACANT") { "Only VACANT rooms can be reserved" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val res = RoomReservationEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                roomId = roomId,
                customerId = customerId,
                reservationDepositFils = depositFils,
                startDateEpoch = now,
                expiresAtEpoch = now + daysValid * 86_400_000L,
                status = "ACTIVE",
                notes = notes.trim(),
                createdByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertReservation(res)
            dao.updateRoom(room.copy(status = "RESERVED", updatedAtEpoch = now))
            res
        }
    }

    suspend fun cancelReservation(ctx: AuthContext, reservation: RoomReservationEntity) {
        AuthorizationGuard.requirePermission(ctx, AppModule.PROPERTIES, ActionType.EDIT, reservation.businessId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.updateReservation(reservation.copy(status = "CANCELLED", updatedAtEpoch = now))
            val room = dao.getRoomById(reservation.roomId)
            if (room != null && room.status == "RESERVED") {
                dao.updateRoom(room.copy(status = "VACANT", updatedAtEpoch = now))
            }
        }
    }

    suspend fun createCustomer(
        ctx: AuthContext,
        fullName: String,
        phone: String,
        email: String,
        qidNumber: String,
        passportNumber: String,
        nationality: String,
        employerName: String,
        emergencyContact: String,
        notes: String
    ): CustomerEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.TENANTS, ActionType.CREATE, ctx.activeBusinessId)
        require(fullName.isNotBlank()) { "Customer name is required" }
        require(phone.isNotBlank()) { "Customer phone is required" }
        val now = System.currentTimeMillis()
        val id = CryptoUtil.newUuid()
        val customer = CustomerEntity(
            id = id,
            businessId = ctx.activeBusinessId,
            customerCode = "TEN-${id.take(6).uppercase()}",
            fullName = fullName.trim(),
            phone = phone.trim(),
            email = email.trim(),
            qidNumber = qidNumber.trim(),
            passportNumber = passportNumber.trim(),
            nationality = nationality.trim(),
            employerName = employerName.trim(),
            emergencyContact = emergencyContact.trim(),
            notes = notes.trim(),
            createdByUserId = ctx.userId,
            createdAtEpoch = now,
            updatedAtEpoch = now
        )
        dao.insertCustomer(customer)
        return customer
    }

    suspend fun updateCustomer(ctx: AuthContext, customer: CustomerEntity): CustomerEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.TENANTS, ActionType.EDIT, customer.businessId)
        val updated = customer.copy(updatedAtEpoch = System.currentTimeMillis())
        dao.updateCustomer(updated)
        return updated
    }

    suspend fun addFamilyMember(
        ctx: AuthContext,
        customerId: String,
        fullName: String,
        relationship: String,
        qidOrPassport: String,
        phone: String
    ): FamilyMemberEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.TENANTS, ActionType.EDIT, ctx.activeBusinessId)
        val fm = FamilyMemberEntity(
            id = CryptoUtil.newUuid(),
            businessId = ctx.activeBusinessId,
            customerId = customerId,
            fullName = fullName.trim(),
            relationship = relationship.trim(),
            qidOrPassport = qidOrPassport.trim(),
            phone = phone.trim(),
            createdAtEpoch = System.currentTimeMillis()
        )
        dao.insertFamilyMember(fm)
        return fm
    }

    suspend fun createAndActivateAgreement(
        ctx: AuthContext,
        roomId: String,
        customerId: String,
        billingCycle: String,
        rentAmountFils: Long,
        discountFils: Long,
        securityDepositFils: Long,
        startDateEpoch: Long,
        endDateEpoch: Long,
        noticePeriodDays: Int,
        templateNotes: String,
        allowOverlapOverride: Boolean = false
    ): AgreementEntity {
        val room = dao.getRoomById(roomId) ?: throw IllegalArgumentException("Room not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.AGREEMENTS, ActionType.CREATE, room.businessId, room.propertyId)
        val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")
        require(customer.businessId == room.businessId) { "Cross-business tenant assignment blocked" }
        require(endDateEpoch > startDateEpoch) { "Agreement end date must be after start date" }
        require(rentAmountFils > 0) { "Rent amount must be positive" }

        val now = System.currentTimeMillis()
        return db.withTransaction {
            val overlaps = dao.findOverlappingActiveAgreements(roomId, startDateEpoch, endDateEpoch)
            if (overlaps.isNotEmpty() && !allowOverlapOverride) {
                throw IllegalStateException(
                    "Room already has an active overlapping agreement (${overlaps.first().agreementNumber})."
                )
            }
            val agId = CryptoUtil.newUuid()
            val agNum = "AGR-${agId.take(6).uppercase()}"
            val agreement = AgreementEntity(
                id = agId,
                businessId = ctx.activeBusinessId,
                propertyId = room.propertyId,
                roomId = roomId,
                customerId = customerId,
                agreementNumber = agNum,
                status = "ACTIVE",
                billingCycle = billingCycle,
                rentAmountFils = rentAmountFils,
                discountFils = discountFils.coerceAtLeast(0L),
                securityDepositFils = securityDepositFils.coerceAtLeast(0L),
                startDateEpoch = startDateEpoch,
                endDateEpoch = endDateEpoch,
                noticePeriodDays = noticePeriodDays,
                templateNotes = templateNotes.trim(),
                moveInDateEpoch = now,
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertAgreement(agreement)
            dao.insertAgreementMember(
                AgreementMemberEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    agreementId = agId,
                    customerId = customerId,
                    createdAtEpoch = now
                )
            )
            dao.updateRoom(room.copy(status = "OCCUPIED", updatedAtEpoch = now))
            dao.insertTenancyHistory(
                TenancyHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    propertyId = room.propertyId,
                    roomId = roomId,
                    customerId = customerId,
                    agreementId = agId,
                    eventType = "MOVE_IN",
                    summary = "Agreement $agNum activated for ${customer.fullName} in Room ${room.roomNumber}",
                    actorUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
            if (securityDepositFils > 0) {
                dao.insertSecurityDeposit(
                    SecurityDepositEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        propertyId = room.propertyId,
                        agreementId = agId,
                        customerId = customerId,
                        expectedFils = securityDepositFils,
                        status = "OUTSTANDING",
                        updatedAtEpoch = now
                    )
                )
            }
            agreement
        }
    }

    suspend fun renewAgreement(
        ctx: AuthContext,
        agreementId: String,
        newEndDateEpoch: Long,
        newRentAmountFils: Long
    ): AgreementEntity {
        val old = dao.getAgreementById(agreementId) ?: throw IllegalArgumentException("Agreement not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.AGREEMENTS, ActionType.EDIT, old.businessId, old.propertyId)
        require(old.status == "ACTIVE" || old.status == "EXPIRED") { "Only active/expired agreements can be renewed" }
        require(newEndDateEpoch > old.endDateEpoch) { "Renewal end date must be after current end date" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            dao.updateAgreement(old.copy(status = "RENEWED", updatedAtEpoch = now))
            val newId = CryptoUtil.newUuid()
            val renewed = old.copy(
                id = newId,
                agreementNumber = "${old.agreementNumber}-R${old.versionNumber + 1}",
                status = "ACTIVE",
                rentAmountFils = newRentAmountFils,
                startDateEpoch = old.endDateEpoch + 86_400_000L,
                endDateEpoch = newEndDateEpoch,
                versionNumber = old.versionNumber + 1,
                parentAgreementId = old.id,
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertAgreement(renewed)
            dao.insertTenancyHistory(
                TenancyHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = old.businessId,
                    propertyId = old.propertyId,
                    roomId = old.roomId,
                    customerId = old.customerId,
                    agreementId = newId,
                    eventType = "RENEWAL",
                    summary = "Renewed ${old.agreementNumber} -> ${renewed.agreementNumber}",
                    actorUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
            renewed
        }
    }

    suspend fun terminateAndMoveOut(ctx: AuthContext, agreementId: String, reason: String) {
        val old = dao.getAgreementById(agreementId) ?: throw IllegalArgumentException("Agreement not found")
        AuthorizationGuard.requirePermission(ctx, AppModule.AGREEMENTS, ActionType.EDIT, old.businessId, old.propertyId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.updateAgreement(old.copy(status = "TERMINATED", moveOutDateEpoch = now, updatedAtEpoch = now))
            val room = dao.getRoomById(old.roomId)
            if (room != null) {
                dao.updateRoom(room.copy(status = "VACANT", updatedAtEpoch = now))
            }
            dao.insertTenancyHistory(
                TenancyHistoryEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = old.businessId,
                    propertyId = old.propertyId,
                    roomId = old.roomId,
                    customerId = old.customerId,
                    agreementId = old.id,
                    eventType = "MOVE_OUT",
                    summary = "Terminated & moved out: $reason",
                    actorUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )
        }
    }

    suspend fun generateRentForPeriod(ctx: AuthContext, periodEpoch: Long = System.currentTimeMillis()): Int {
        AuthorizationGuard.requirePermission(ctx, AppModule.AGREEMENTS, ActionType.POST, ctx.activeBusinessId)
        val periodKey = QatarFormatters.periodKey(periodEpoch)
        val periodLabel = QatarFormatters.periodLabel(periodEpoch)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val activeAgreements = dao.getAgreementsByBusiness(ctx.activeBusinessId)
                .filter { it.status == "ACTIVE" }
            var generated = 0
            for (ag in activeAgreements) {
                val existing = dao.getMonthlyRentByPeriod(ag.id, periodKey)
                if (existing != null) continue
                val netAmount = (ag.rentAmountFils - ag.discountFils).coerceAtLeast(0L)
                if (netAmount <= 0L) continue
                val rentId = CryptoUtil.newUuid()
                val journal = accountingService.postJournalInternal(
                    businessId = ctx.activeBusinessId,
                    propertyId = ag.propertyId,
                    entryDateEpoch = now,
                    description = "Rent billed $periodLabel (${ag.agreementNumber})",
                    sourceType = "RENT",
                    sourceId = rentId,
                    idempotencyKey = "RENT-${ag.id}-$periodKey",
                    createdByUserId = ctx.userId,
                    lines = listOf(
                        JournalLineInput(AccountingService.ACC_RENT_RECEIVABLE, netAmount, 0L, "$periodLabel rent due"),
                        JournalLineInput(AccountingService.ACC_RENTAL_INCOME, 0L, netAmount, "$periodLabel rental revenue")
                    )
                )
                dao.insertMonthlyRent(
                    MonthlyRentEntity(
                        id = rentId,
                        businessId = ctx.activeBusinessId,
                        propertyId = ag.propertyId,
                        roomId = ag.roomId,
                        agreementId = ag.id,
                        customerId = ag.customerId,
                        periodKey = periodKey,
                        periodLabel = periodLabel,
                        dueDateEpoch = periodEpoch,
                        amountDueFils = netAmount,
                        amountPaidFils = 0L,
                        status = "DUE",
                        journalEntryId = journal.id,
                        createdByUserId = ctx.userId,
                        createdAtEpoch = now,
                        updatedAtEpoch = now
                    )
                )
                generated++
            }
            generated
        }
    }
}
