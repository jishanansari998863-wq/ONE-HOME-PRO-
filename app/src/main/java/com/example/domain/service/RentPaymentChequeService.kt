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

class RentPaymentChequeService(
    private val db: OneHomeProDatabase,
    private val accountingService: AccountingService
) {
    private val dao = db.coreAndTenancyDao()
    private val accDao = db.accountingDao()
    private val opsDao = db.operationsDao()

    suspend fun receiveConfirmedPayment(
        ctx: AuthContext,
        customerId: String,
        agreementId: String?,
        amountFils: Long,
        method: String, // CASH, BANK_TRANSFER, CARD, OTHER
        bankAccountId: String?,
        targetRentIdsInOrder: List<String>,
        notes: String,
        evidenceUri: String? = null,
        idempotencyKey: String = "PAY-${CryptoUtil.newUuid()}"
    ): PaymentEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.PAYMENTS, ActionType.POST, ctx.activeBusinessId)
        require(amountFils > 0) { "Payment amount must be positive" }
        require(method != "CHEQUE") { "Use Cheque Workflow to register cheques; received cheques are not cleared income." }

        return db.withTransaction {
            val dup = dao.getPaymentByIdempotencyKey(idempotencyKey)
            if (dup != null) return@withTransaction dup

            val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")
            require(customer.businessId == ctx.activeBusinessId) { "Cross-business payment blocked" }

            val ag = agreementId?.let { dao.getAgreementById(it) }
            val propertyId = ag?.propertyId ?: dao.getAgreementsByBusiness(ctx.activeBusinessId)
                .firstOrNull { it.customerId == customerId }?.propertyId ?: ""

            val now = System.currentTimeMillis()
            val payId = CryptoUtil.newUuid()
            val refNum = "PAY-${payId.take(6).uppercase()}"

            // Allocate across selected or oldest unpaid rent obligations
            val openRents = if (targetRentIdsInOrder.isNotEmpty()) {
                targetRentIdsInOrder.mapNotNull { dao.getMonthlyRentById(it) }
            } else {
                dao.getMonthlyRentsByBusiness(ctx.activeBusinessId)
                    .filter { it.customerId == customerId && it.amountPaidFils < it.amountDueFils && it.status != "WAIVED" }
                    .sortedBy { it.dueDateEpoch }
            }

            var remainingToAllocate = amountFils
            var allocatedToRentTotal = 0L
            val allocations = mutableListOf<Pair<MonthlyRentEntity, Long>>()

            for (rent in openRents) {
                if (remainingToAllocate <= 0) break
                val rentRemaining = (rent.amountDueFils - rent.amountPaidFils).coerceAtLeast(0L)
                if (rentRemaining <= 0) continue
                val alloc = minOf(remainingToAllocate, rentRemaining)
                allocations.add(rent to alloc)
                allocatedToRentTotal += alloc
                remainingToAllocate -= alloc
            }

            val advanceRentAmount = remainingToAllocate
            val assetAccount = if (method == "BANK_TRANSFER" || method == "CARD") {
                AccountingService.ACC_BANK
            } else {
                AccountingService.ACC_CASH
            }

            val journalLines = mutableListOf(
                JournalLineInput(assetAccount, amountFils, 0L, "$method receipt from ${customer.fullName}")
            )
            if (allocatedToRentTotal > 0L) {
                journalLines.add(
                    JournalLineInput(
                        AccountingService.ACC_RENT_RECEIVABLE,
                        0L,
                        allocatedToRentTotal,
                        "Allocated to rent obligations"
                    )
                )
            }
            if (advanceRentAmount > 0L) {
                journalLines.add(
                    JournalLineInput(
                        AccountingService.ACC_ADVANCE_RENT_UNEARNED,
                        0L,
                        advanceRentAmount,
                        "Advance rent held as unearned liability"
                    )
                )
            }

            val journal = accountingService.postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = propertyId.ifBlank { null },
                entryDateEpoch = now,
                description = "Payment $refNum (${customer.fullName})",
                sourceType = "PAYMENT",
                sourceId = payId,
                idempotencyKey = "JE-$idempotencyKey",
                createdByUserId = ctx.userId,
                lines = journalLines
            )

            val payment = PaymentEntity(
                id = payId,
                businessId = ctx.activeBusinessId,
                propertyId = propertyId,
                customerId = customerId,
                agreementId = agreementId,
                referenceNumber = refNum,
                idempotencyKey = idempotencyKey,
                amountFils = amountFils,
                method = method,
                status = "CONFIRMED",
                bankAccountId = bankAccountId,
                evidenceUri = evidenceUri,
                journalEntryId = journal.id,
                notes = notes.trim(),
                receivedAtEpoch = now,
                receivedByUserId = ctx.userId,
                createdAtEpoch = now
            )
            dao.insertPayment(payment)

            for ((rent, allocAmount) in allocations) {
                dao.insertPaymentAllocation(
                    PaymentAllocationEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        paymentId = payId,
                        monthlyRentId = rent.id,
                        allocatedAmountFils = allocAmount,
                        createdAtEpoch = now
                    )
                )
                val newPaid = rent.amountPaidFils + allocAmount
                val newStatus = if (newPaid >= rent.amountDueFils) "PAID" else "PARTIAL"
                dao.updateMonthlyRent(rent.copy(amountPaidFils = newPaid, status = newStatus, updatedAtEpoch = now))
            }

            if (advanceRentAmount > 0L && ag != null) {
                dao.insertAdvanceRent(
                    AdvanceRentEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        customerId = customerId,
                        agreementId = ag.id,
                        paymentId = payId,
                        originalAmountFils = advanceRentAmount,
                        remainingAmountFils = advanceRentAmount,
                        status = "HELD",
                        createdAtEpoch = now,
                        updatedAtEpoch = now
                    )
                )
            }

            val recNum = "RCP-${payId.take(6).uppercase()}"
            dao.insertReceipt(
                ReceiptEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = ctx.activeBusinessId,
                    paymentId = payId,
                    customerId = customerId,
                    receiptNumber = recNum,
                    amountFils = amountFils,
                    method = method,
                    issuedByUserId = ctx.userId,
                    issuedAtEpoch = now
                )
            )

            if (method == "CASH") {
                accDao.insertCashbookEntry(
                    CashbookEntryEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        direction = "INFLOW",
                        amountFils = amountFils,
                        description = "Rent Payment $recNum (${customer.fullName})",
                        reference = refNum,
                        journalEntryId = journal.id,
                        entryDateEpoch = now,
                        createdByUserId = ctx.userId
                    )
                )
            }

            if (customer.portalUserId != null) {
                opsDao.insertNotification(
                    NotificationEntity(
                        id = CryptoUtil.newUuid(),
                        businessId = ctx.activeBusinessId,
                        recipientUserId = customer.portalUserId,
                        recipientCustomerId = customer.id,
                        category = "PAYMENT",
                        title = "Payment Receipt $recNum",
                        message = "Confirmed payment of ${QatarFormatters.formatQar(amountFils)} ($method).",
                        channel = "IN_APP",
                        deliveryStatus = "DELIVERED_LOCAL",
                        createdAtEpoch = now
                    )
                )
            }
            payment
        }
    }

    suspend fun reverseOrRefundPayment(ctx: AuthContext, paymentId: String, reason: String) {
        AuthorizationGuard.requirePermission(ctx, AppModule.PAYMENTS, ActionType.REVERSE, ctx.activeBusinessId)
        require(reason.isNotBlank()) { "Reversal reason required" }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val pay = dao.getPaymentById(paymentId) ?: throw IllegalArgumentException("Payment not found")
            require(pay.businessId == ctx.activeBusinessId) { "Cross-business reversal blocked" }
            require(pay.status == "CONFIRMED") { "Only CONFIRMED payments can be reversed" }

            pay.journalEntryId?.let { accountingService.reverseJournalEntry(ctx, it, reason) }
            val allocs = dao.getAllocationsForPayment(pay.id)
            for (alloc in allocs) {
                val rent = dao.getMonthlyRentById(alloc.monthlyRentId) ?: continue
                val restoredPaid = (rent.amountPaidFils - alloc.allocatedAmountFils).coerceAtLeast(0L)
                val restoredStatus = when {
                    restoredPaid == 0L -> "DUE"
                    restoredPaid < rent.amountDueFils -> "PARTIAL"
                    else -> "PAID"
                }
                dao.updateMonthlyRent(rent.copy(amountPaidFils = restoredPaid, status = restoredStatus, updatedAtEpoch = now))
            }
            dao.updatePayment(pay.copy(status = "REVERSED"))
            dao.getReceiptByPaymentId(pay.id)?.let { dao.updateReceipt(it.copy(isVoided = true)) }
        }
    }

    // Integrated Cheque Lifecycle
    suspend fun registerCheque(
        ctx: AuthContext,
        customerId: String,
        agreementId: String?,
        linkedRentId: String?,
        chequeNumber: String,
        bankName: String,
        amountFils: Long,
        chequeDateEpoch: Long,
        payerName: String,
        payeeName: String,
        evidenceUri: String? = null
    ): ChequeEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.CHEQUES, ActionType.CREATE, ctx.activeBusinessId)
        require(chequeNumber.isNotBlank() && bankName.isNotBlank()) { "Cheque number & bank required" }
        require(amountFils > 0) { "Cheque amount must be positive" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")
            val ag = agreementId?.let { dao.getAgreementById(it) }
            val chId = CryptoUtil.newUuid()
            // Received cheque posts to Cheques Receivable (undeposited), NEVER cleared bank income!
            val journal = accountingService.postJournalInternal(
                businessId = ctx.activeBusinessId,
                propertyId = ag?.propertyId,
                entryDateEpoch = now,
                description = "Cheque received #$chequeNumber ($bankName) from ${customer.fullName}",
                sourceType = "CHEQUE",
                sourceId = chId,
                idempotencyKey = "CHQ-RCV-$chId",
                createdByUserId = ctx.userId,
                lines = listOf(
                    JournalLineInput(AccountingService.ACC_CHEQUES_RECEIVABLE, amountFils, 0L, "Cheque #$chequeNumber held"),
                    JournalLineInput(AccountingService.ACC_RENT_RECEIVABLE, 0L, amountFils, "Pending cheque clearance")
                )
            )
            val cheque = ChequeEntity(
                id = chId,
                businessId = ctx.activeBusinessId,
                propertyId = ag?.propertyId ?: "",
                customerId = customerId,
                agreementId = agreementId,
                linkedRentId = linkedRentId,
                chequeNumber = chequeNumber.trim(),
                bankName = bankName.trim(),
                amountFils = amountFils,
                chequeDateEpoch = chequeDateEpoch,
                payerName = payerName.trim().ifBlank { customer.fullName },
                payeeName = payeeName.trim().ifBlank { "ONE HOME PRO" },
                status = "RECEIVED",
                evidenceUri = evidenceUri,
                journalEntryId = journal.id,
                createdByUserId = ctx.userId,
                createdAtEpoch = now,
                updatedAtEpoch = now
            )
            dao.insertCheque(cheque)
            cheque
        }
    }

    suspend fun transitionChequeStatus(
        ctx: AuthContext,
        chequeId: String,
        newStatus: String,
        bankReference: String = "",
        bounceReason: String = ""
    ): ChequeEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.CHEQUES, ActionType.POST, ctx.activeBusinessId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val ch = dao.getChequeById(chequeId) ?: throw IllegalArgumentException("Cheque not found")
            require(ch.businessId == ctx.activeBusinessId) { "Cross-business cheque blocked" }

            var updated = ch.copy(
                status = newStatus,
                bankReference = bankReference.ifBlank { ch.bankReference },
                bounceReason = bounceReason.ifBlank { ch.bounceReason },
                depositDateEpoch = if (newStatus == "DEPOSITED") now else ch.depositDateEpoch,
                clearingDateEpoch = if (newStatus == "CLEARED") now else ch.clearingDateEpoch,
                updatedAtEpoch = now
            )

            when (newStatus) {
                "CLEARED" -> {
                    require(ch.status != "CLEARED" && ch.status != "BOUNCED") { "Invalid transition to CLEARED" }
                    accountingService.postJournalInternal(
                        businessId = ch.businessId,
                        propertyId = ch.propertyId.ifBlank { null },
                        entryDateEpoch = now,
                        description = "Cheque cleared #${ch.chequeNumber} (${ch.bankName})",
                        sourceType = "CHEQUE",
                        sourceId = ch.id,
                        idempotencyKey = "CHQ-CLR-${ch.id}",
                        createdByUserId = ctx.userId,
                        lines = listOf(
                            JournalLineInput(AccountingService.ACC_BANK, ch.amountFils, 0L, "Bank deposit cleared #${ch.chequeNumber}"),
                            JournalLineInput(AccountingService.ACC_CHEQUES_RECEIVABLE, 0L, ch.amountFils, "Cheque cleared")
                        )
                    )
                    // Now mark linked rent as paid & create confirmed payment + receipt!
                    val payId = CryptoUtil.newUuid()
                    val payment = PaymentEntity(
                        id = payId,
                        businessId = ch.businessId,
                        propertyId = ch.propertyId,
                        customerId = ch.customerId,
                        agreementId = ch.agreementId,
                        referenceNumber = "CHQCLR-${ch.chequeNumber}",
                        idempotencyKey = "PAY-CHQ-${ch.id}",
                        amountFils = ch.amountFils,
                        method = "CHEQUE",
                        status = "CONFIRMED",
                        chequeId = ch.id,
                        notes = "Cleared cheque #${ch.chequeNumber} (${ch.bankName})",
                        receivedAtEpoch = now,
                        receivedByUserId = ctx.userId,
                        createdAtEpoch = now
                    )
                    dao.insertPayment(payment)
                    if (ch.linkedRentId != null) {
                        val rent = dao.getMonthlyRentById(ch.linkedRentId)
                        if (rent != null) {
                            val alloc = minOf(ch.amountFils, (rent.amountDueFils - rent.amountPaidFils).coerceAtLeast(0L))
                            if (alloc > 0L) {
                                dao.insertPaymentAllocation(
                                    PaymentAllocationEntity(
                                        id = CryptoUtil.newUuid(),
                                        businessId = ch.businessId,
                                        paymentId = payId,
                                        monthlyRentId = rent.id,
                                        allocatedAmountFils = alloc,
                                        createdAtEpoch = now
                                    )
                                )
                                val newPaid = rent.amountPaidFils + alloc
                                dao.updateMonthlyRent(
                                    rent.copy(
                                        amountPaidFils = newPaid,
                                        status = if (newPaid >= rent.amountDueFils) "PAID" else "PARTIAL",
                                        updatedAtEpoch = now
                                    )
                                )
                            }
                        }
                    }
                    dao.insertReceipt(
                        ReceiptEntity(
                            id = CryptoUtil.newUuid(),
                            businessId = ch.businessId,
                            paymentId = payId,
                            customerId = ch.customerId,
                            receiptNumber = "RCP-CHQ-${ch.chequeNumber}",
                            amountFils = ch.amountFils,
                            method = "CHEQUE",
                            issuedByUserId = ctx.userId,
                            issuedAtEpoch = now
                        )
                    )
                }
                "BOUNCED" -> {
                    require(bounceReason.isNotBlank()) { "Bounce reason is required" }
                    accountingService.postJournalInternal(
                        businessId = ch.businessId,
                        propertyId = ch.propertyId.ifBlank { null },
                        entryDateEpoch = now,
                        description = "Cheque BOUNCED #${ch.chequeNumber}: $bounceReason",
                        sourceType = "CHEQUE",
                        sourceId = ch.id,
                        idempotencyKey = "CHQ-BNC-${ch.id}",
                        createdByUserId = ctx.userId,
                        lines = listOf(
                            JournalLineInput(AccountingService.ACC_RENT_RECEIVABLE, ch.amountFils, 0L, "Bounced cheque re-opened receivable"),
                            JournalLineInput(AccountingService.ACC_CHEQUES_RECEIVABLE, 0L, ch.amountFils, "Cheque bounced #${ch.chequeNumber}")
                        )
                    )
                }
            }
            dao.updateCheque(updated)
            updated
        }
    }

    suspend fun replaceBouncedCheque(
        ctx: AuthContext,
        bouncedChequeId: String,
        newChequeNumber: String,
        newBankName: String,
        newChequeDateEpoch: Long
    ): ChequeEntity {
        val old = dao.getChequeById(bouncedChequeId) ?: throw IllegalArgumentException("Cheque not found")
        require(old.status == "BOUNCED") { "Only BOUNCED cheques can be replaced" }
        return db.withTransaction {
            val replacement = registerCheque(
                ctx = ctx,
                customerId = old.customerId,
                agreementId = old.agreementId,
                linkedRentId = old.linkedRentId,
                chequeNumber = newChequeNumber,
                bankName = newBankName,
                amountFils = old.amountFils,
                chequeDateEpoch = newChequeDateEpoch,
                payerName = old.payerName,
                payeeName = old.payeeName
            )
            dao.updateCheque(
                old.copy(
                    status = "REPLACED",
                    replacementChequeId = replacement.id,
                    updatedAtEpoch = System.currentTimeMillis()
                )
            )
            replacement
        }
    }

    // Security Deposits (Strictly Separate Liability Accounting!)
    suspend fun recordDepositTransaction(
        ctx: AuthContext,
        securityDepositId: String,
        transactionType: String, // RECEIVE, DEDUCTION, REFUND
        amountFils: Long,
        reason: String,
        evidenceUri: String? = null
    ): SecurityDepositEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.DEPOSITS, ActionType.POST, ctx.activeBusinessId)
        require(amountFils > 0) { "Amount must be positive" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val dep = dao.getSecurityDepositById(securityDepositId)
                ?: throw IllegalArgumentException("Security deposit record not found")
            require(dep.businessId == ctx.activeBusinessId) { "Cross-business deposit access blocked" }

            val lines = when (transactionType) {
                "RECEIVE" -> listOf(
                    JournalLineInput(AccountingService.ACC_CASH, amountFils, 0L, "Security deposit received"),
                    JournalLineInput(AccountingService.ACC_SECURITY_DEPOSITS_HELD, 0L, amountFils, "Deposit liability held")
                )
                "DEDUCTION" -> {
                    require(amountFils <= dep.heldFils) { "Deduction exceeds held deposit" }
                    listOf(
                        JournalLineInput(AccountingService.ACC_SECURITY_DEPOSITS_HELD, amountFils, 0L, "Damage/cleaning deduction"),
                        JournalLineInput(AccountingService.ACC_OTHER_INCOME, 0L, amountFils, "Deposit deduction recognized")
                    )
                }
                "REFUND" -> {
                    require(amountFils <= dep.heldFils) { "Refund exceeds held deposit" }
                    listOf(
                        JournalLineInput(AccountingService.ACC_SECURITY_DEPOSITS_HELD, amountFils, 0L, "Deposit refunded to tenant"),
                        JournalLineInput(AccountingService.ACC_BANK, 0L, amountFils, "Deposit refund payout")
                    )
                }
                else -> throw IllegalArgumentException("Unknown deposit transaction type: $transactionType")
            }

            val je = accountingService.postJournalInternal(
                businessId = dep.businessId,
                propertyId = dep.propertyId,
                entryDateEpoch = now,
                description = "Deposit $transactionType: $reason",
                sourceType = "DEPOSIT",
                sourceId = dep.id,
                idempotencyKey = "DEP-$transactionType-${CryptoUtil.newUuid()}",
                createdByUserId = ctx.userId,
                approvedByUserId = ctx.userId,
                lines = lines
            )

            dao.insertDepositTransaction(
                DepositTransactionEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = dep.businessId,
                    securityDepositId = dep.id,
                    transactionType = transactionType,
                    amountFils = amountFils,
                    reason = reason.trim(),
                    evidenceUri = evidenceUri,
                    journalEntryId = je.id,
                    approvedByUserId = ctx.userId,
                    createdByUserId = ctx.userId,
                    occurredAtEpoch = now
                )
            )

            val newReceived = if (transactionType == "RECEIVE") dep.receivedFils + amountFils else dep.receivedFils
            val newDeducted = if (transactionType == "DEDUCTION") dep.deductedFils + amountFils else dep.deductedFils
            val newRefunded = if (transactionType == "REFUND") dep.refundedFils + amountFils else dep.refundedFils
            val newHeld = (newReceived - newDeducted - newRefunded).coerceAtLeast(0L)
            val newStatus = when {
                newReceived == 0L -> "OUTSTANDING"
                newHeld == 0L -> "SETTLED"
                newDeducted > 0L || newRefunded > 0L -> "PARTIALLY_SETTLED"
                else -> "HELD"
            }

            val updated = dep.copy(
                receivedFils = newReceived,
                heldFils = newHeld,
                deductedFils = newDeducted,
                refundedFils = newRefunded,
                status = newStatus,
                updatedAtEpoch = now
            )
            dao.updateSecurityDeposit(updated)
            updated
        }
    }
}
