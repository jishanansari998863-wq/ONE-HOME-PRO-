package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "businesses",
    indices = [Index(value = ["code"], unique = true)]
)
data class BusinessEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val branchName: String,
    val currency: String = "QAR",
    val timezone: String = "Asia/Qatar",
    val logoUri: String? = null,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val crNumber: String = "",
    val isActive: Boolean = true,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "properties",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index(value = ["businessId", "code"], unique = true)]
)
data class PropertyEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val code: String,
    val name: String,
    val propertyType: String,
    val address: String,
    val city: String = "Doha",
    val notes: String = "",
    val photoUri: String? = null,
    val isArchived: Boolean = false,
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "buildings",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("propertyId")]
)
data class BuildingEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val name: String,
    val buildingCode: String,
    val totalFloorsDeclared: Int = 1,
    val notes: String = "",
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "floors",
    foreignKeys = [
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("propertyId"), Index("buildingId")]
)
data class FloorEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val buildingId: String,
    val floorNumber: Int,
    val label: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = FloorEntity::class,
            parentColumns = ["id"],
            childColumns = ["floorId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("propertyId"),
        Index("buildingId"),
        Index("floorId"),
        Index(value = ["buildingId", "roomNumber"], unique = true)
    ]
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val buildingId: String,
    val floorId: String,
    val roomNumber: String,
    val roomType: String,
    val capacity: Int = 1,
    val amenities: String = "",
    val furnishedStatus: String = "FURNISHED",
    val monthlyRateFils: Long,
    val status: String = "VACANT", // VACANT, OCCUPIED, MAINTENANCE, RESERVED, INACTIVE
    val keyCount: Int = 1,
    val accessCardCode: String = "",
    val furnitureList: String = "",
    val photoUri: String? = null,
    val notes: String = "",
    val repairNotes: String = "",
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "room_reservations",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("roomId"), Index("customerId")]
)
data class RoomReservationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val roomId: String,
    val customerId: String,
    val reservationDepositFils: Long = 0L,
    val startDateEpoch: Long,
    val expiresAtEpoch: Long,
    val status: String = "ACTIVE", // ACTIVE, CONVERTED, CANCELLED
    val notes: String = "",
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "property_inspections",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("propertyId"), Index("roomId")]
)
data class PropertyInspectionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val roomId: String? = null,
    val inspectionType: String, // MOVE_IN, MOVE_OUT, PREVENTIVE, CONDITION
    val conditionSummary: String,
    val keysHandedOver: Int = 0,
    val accessCardsHandedOver: Int = 0,
    val repairRequired: Boolean = false,
    val inspectorUserId: String,
    val inspectedAtEpoch: Long
)

@Entity(
    tableName = "customers",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index(value = ["businessId", "customerCode"], unique = true),
        Index("portalUserId")
    ]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val customerCode: String,
    val fullName: String,
    val phone: String,
    val email: String = "",
    val qidNumber: String = "",
    val passportNumber: String = "",
    val nationality: String = "",
    val employerName: String = "",
    val emergencyContact: String = "",
    val portalUserId: String? = null,
    val status: String = "ACTIVE", // ACTIVE, MOVED_OUT, ARCHIVED
    val notes: String = "",
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "family_members",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("customerId")]
)
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val customerId: String,
    val fullName: String,
    val relationship: String,
    val qidOrPassport: String = "",
    val phone: String = "",
    val createdAtEpoch: Long
)

@Entity(
    tableName = "agreements",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
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
        Index("roomId"),
        Index("customerId"),
        Index(value = ["businessId", "agreementNumber"], unique = true)
    ]
)
data class AgreementEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val roomId: String,
    val customerId: String,
    val agreementNumber: String,
    val status: String = "DRAFT", // DRAFT, ACTIVE, EXPIRED, TERMINATED, CANCELLED, RENEWED
    val billingCycle: String = "MONTHLY", // MONTHLY, QUARTERLY, ANNUAL
    val rentAmountFils: Long,
    val discountFils: Long = 0L,
    val securityDepositFils: Long = 0L,
    val startDateEpoch: Long,
    val endDateEpoch: Long,
    val noticePeriodDays: Int = 30,
    val versionNumber: Int = 1,
    val parentAgreementId: String? = null,
    val allowOverlapOverride: Boolean = false,
    val templateNotes: String = "",
    val moveInDateEpoch: Long? = null,
    val moveOutDateEpoch: Long? = null,
    val createdByUserId: String,
    val approvedByUserId: String? = null,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "agreement_members",
    foreignKeys = [
        ForeignKey(
            entity = AgreementEntity::class,
            parentColumns = ["id"],
            childColumns = ["agreementId"],
            onDelete = ForeignKey.CASCADE
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
data class AgreementMemberEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val agreementId: String,
    val customerId: String,
    val roleLabel: String = "PRIMARY_TENANT",
    val createdAtEpoch: Long
)

@Entity(
    tableName = "tenancy_history",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("roomId"), Index("customerId"), Index("agreementId")]
)
data class TenancyHistoryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val roomId: String,
    val customerId: String,
    val agreementId: String,
    val eventType: String, // MOVE_IN, RENEWAL, AMENDMENT, EARLY_TERMINATION, MOVE_OUT, CANCELLED
    val summary: String,
    val actorUserId: String,
    val occurredAtEpoch: Long
)
