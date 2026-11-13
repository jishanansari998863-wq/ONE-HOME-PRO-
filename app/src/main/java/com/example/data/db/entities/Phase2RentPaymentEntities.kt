package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_rents",
    foreignKeys = [
        ForeignKey(
            entity = AgreementEntity::class,
            parentColumns = ["id"],
            childColumns = ["agreementId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("propertyId"),
        Index("agreementId"),
        Index("customerId"),
        Index(value = ["agreementId", "periodKey"], unique = true)
    ]
)
data class MonthlyRentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val roomId: String,
    val agreementId: String,
    val customerId: String,
    val periodKey: String, // e.g. 2026-11
    val periodLabel: String,
    val dueDateEpoch: Long,
    val amountDueFils: Long,
    val amountPaidFils: Long = 0L,
    val status: String = "DUE", // DUE, PARTIAL, PAID, OVERDUE, WAIVED
    val journalEntryId: String? = null,
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("customerId"),
        Index("agreementId"),
        Index(value = ["businessId", "referenceNumber"], unique = true),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val customerId: String,
    val agreementId: String? = null,
    val referenceNumber: String,
    val idempotencyKey: String,
    val amountFils: Long,
    val method: String, // CASH, BANK_TRANSFER, CARD, CHEQUE, OTHER
    val status: String = "CONFIRMED", // CONFIRMED, PENDING_CLEARANCE, REVERSED, REFUNDED
    val bankAccountId: String? = null,
    val chequeId: String? = null,
    val evidenceUri: String? = null,
    val journalEntryId: String? = null,
    val reversedPaymentId: String? = null,
    val notes: String = "",
    val receivedAtEpoch: Long,
    val receivedByUserId: String,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MonthlyRentEntity::class,
            parentColumns = ["id"],
            childColumns = ["monthlyRentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("paymentId"), Index("monthlyRentId")]
)
data class PaymentAllocationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val paymentId: String,
    val monthlyRentId: String,
    val allocatedAmountFils: Long,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("paymentId"),
        Index("customerId"),
        Index(value = ["businessId", "receiptNumber"], unique = true)
    ]
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val paymentId: String,
    val customerId: String,
    val receiptNumber: String,
    val amountFils: Long,
    val method: String,
    val issuedByUserId: String,
    val issuedAtEpoch: Long,
    val isVoided: Boolean = false
)

@Entity(
    tableName = "security_deposits",
    foreignKeys = [
        ForeignKey(
            entity = AgreementEntity::class,
            parentColumns = ["id"],
            childColumns = ["agreementId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("agreementId"), Index("customerId")]
)
data class SecurityDepositEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val agreementId: String,
    val customerId: String,
    val expectedFils: Long,
    val receivedFils: Long = 0L,
    val heldFils: Long = 0L,
    val deductedFils: Long = 0L,
    val refundedFils: Long = 0L,
    val status: String = "OUTSTANDING", // OUTSTANDING, HELD, PARTIALLY_SETTLED, SETTLED
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "deposit_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SecurityDepositEntity::class,
            parentColumns = ["id"],
            childColumns = ["securityDepositId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("securityDepositId")]
)
data class DepositTransactionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val securityDepositId: String,
    val transactionType: String, // RECEIVE, DEDUCTION, REFUND, ADJUSTMENT
    val amountFils: Long,
    val reason: String,
    val evidenceUri: String? = null,
    val journalEntryId: String? = null,
    val approvedByUserId: String? = null,
    val createdByUserId: String,
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "cheques",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("customerId"),
        Index("agreementId"),
        Index(value = ["businessId", "bankName", "chequeNumber"], unique = true)
    ]
)
data class ChequeEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val customerId: String,
    val agreementId: String? = null,
    val linkedRentId: String? = null,
    val chequeNumber: String,
    val bankName: String,
    val amountFils: Long,
    val chequeDateEpoch: Long,
    val payerName: String,
    val payeeName: String,
    val status: String = "RECEIVED", // RECEIVED, HELD, DUE_SOON, DEPOSITED, CLEARED, BOUNCED, CANCELLED, REPLACED, RETURNED
    val depositDateEpoch: Long? = null,
    val clearingDateEpoch: Long? = null,
    val bankReference: String = "",
    val bounceReason: String = "",
    val replacementChequeId: String? = null,
    val evidenceUri: String? = null,
    val journalEntryId: String? = null,
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "advance_rents",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("customerId"), Index("agreementId")]
)
data class AdvanceRentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val customerId: String,
    val agreementId: String,
    val paymentId: String,
    val originalAmountFils: Long,
    val remainingAmountFils: Long,
    val status: String = "HELD", // HELD, PARTIALLY_APPLIED, APPLIED, REFUNDED
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)
