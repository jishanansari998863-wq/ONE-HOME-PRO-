package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suppliers",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index(value = ["businessId", "supplierCode"], unique = true)]
)
data class SupplierEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val supplierCode: String,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val crNumber: String = "",
    val isActive: Boolean = true,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "item_categories",
    indices = [Index("businessId"), Index(value = ["businessId", "name"], unique = true)]
)
data class ItemCategoryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val description: String = "",
    val createdAtEpoch: Long
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ItemCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("categoryId"),
        Index(value = ["businessId", "sku"], unique = true)
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val categoryId: String,
    val sku: String,
    val name: String,
    val unit: String = "PCS",
    val reorderLevel: Long = 5L,
    val currentStockQty: Long = 0L,
    val weightedAvgCostFils: Long = 0L,
    val isActive: Boolean = true,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "stock_locations",
    indices = [Index("businessId"), Index("propertyId")]
)
data class StockLocationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val name: String,
    val locationType: String = "WAREHOUSE", // WAREHOUSE, PROPERTY_STORE, VEHICLE
    val isActive: Boolean = true,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("supplierId"),
        Index(value = ["businessId", "purchaseNumber"], unique = true)
    ]
)
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val supplierId: String,
    val purchaseNumber: String,
    val quotationRef: String = "",
    val supplierInvoiceNumber: String = "",
    val status: String = "REQUESTED", // REQUESTED, APPROVED, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    val totalAmountFils: Long = 0L,
    val paidAmountFils: Long = 0L,
    val notes: String = "",
    val createdByUserId: String,
    val approvedByUserId: String? = null,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("purchaseId"), Index("itemId")]
)
data class PurchaseItemEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val purchaseId: String,
    val itemId: String,
    val orderedQty: Long,
    val receivedQty: Long = 0L,
    val unitCostFils: Long
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StockLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockLocationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("itemId"),
        Index("stockLocationId"),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val itemId: String,
    val stockLocationId: String,
    val movementType: String, // IN, OUT, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT, RETURN, DAMAGED
    val qtyDelta: Long,
    val unitCostFils: Long,
    val referenceType: String,
    val referenceId: String,
    val idempotencyKey: String,
    val notes: String = "",
    val createdByUserId: String,
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "stock_transfers",
    indices = [Index("businessId"), Index("fromLocationId"), Index("toLocationId")]
)
data class StockTransferEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val fromLocationId: String,
    val toLocationId: String,
    val status: String = "COMPLETED",
    val notes: String = "",
    val createdByUserId: String,
    val transferredAtEpoch: Long
)

@Entity(
    tableName = "stock_transfer_lines",
    foreignKeys = [
        ForeignKey(
            entity = StockTransferEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockTransferId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("stockTransferId"), Index("itemId")]
)
data class StockTransferLineEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val stockTransferId: String,
    val itemId: String,
    val quantity: Long
)

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("itemId"), Index("stockLocationId")]
)
data class StockAdjustmentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val itemId: String,
    val stockLocationId: String,
    val qtyDelta: Long,
    val reason: String, // DAMAGE, EXPIRY, SHRINKAGE, FOUND, AUDIT_CORRECTION
    val stockMovementId: String,
    val approvedByUserId: String?,
    val createdByUserId: String,
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "stock_counts",
    indices = [Index("businessId"), Index("stockLocationId")]
)
data class StockCountEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val stockLocationId: String,
    val countReference: String,
    val status: String = "POSTED",
    val countedByUserId: String,
    val countedAtEpoch: Long
)

@Entity(
    tableName = "stock_count_lines",
    foreignKeys = [
        ForeignKey(
            entity = StockCountEntity::class,
            parentColumns = ["id"],
            childColumns = ["stockCountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("stockCountId"), Index("itemId")]
)
data class StockCountLineEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val stockCountId: String,
    val itemId: String,
    val expectedQty: Long,
    val countedQty: Long,
    val varianceQty: Long
)

@Entity(
    tableName = "supplier_payments",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("supplierId")]
)
data class SupplierPaymentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val supplierId: String,
    val referenceNumber: String,
    val amountFils: Long,
    val method: String,
    val journalEntryId: String,
    val paidByUserId: String,
    val paidAtEpoch: Long
)

@Entity(
    tableName = "supplier_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = SupplierPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierPaymentId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("supplierPaymentId"), Index("purchaseId")]
)
data class SupplierPaymentAllocationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val supplierPaymentId: String,
    val purchaseId: String,
    val allocatedFils: Long,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "purchase_approvals",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("purchaseId")]
)
data class PurchaseApprovalEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val purchaseId: String,
    val decision: String, // APPROVED, REJECTED
    val notes: String = "",
    val decidedByUserId: String,
    val decidedAtEpoch: Long
)

@Entity(
    tableName = "goods_receipt_details",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseItemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("purchaseId"), Index("purchaseItemId")]
)
data class GoodsReceiptDetailEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val purchaseId: String,
    val purchaseItemId: String,
    val stockLocationId: String,
    val receivedQty: Long,
    val unitCostFils: Long,
    val stockMovementId: String,
    val journalEntryId: String,
    val receivedByUserId: String,
    val receivedAtEpoch: Long
)
