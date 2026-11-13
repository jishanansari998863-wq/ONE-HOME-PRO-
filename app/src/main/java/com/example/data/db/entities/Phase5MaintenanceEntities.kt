package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "complaints",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("propertyId"),
        Index("roomId"),
        Index("customerId"),
        Index(value = ["businessId", "complaintNumber"], unique = true)
    ]
)
data class ComplaintEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String,
    val roomId: String? = null,
    val customerId: String? = null,
    val complaintNumber: String,
    val title: String,
    val description: String,
    val category: String, // PLUMBING, ELECTRICAL, HVAC, APPLIANCE, STRUCTURAL, CLEANING, PREVENTIVE
    val priority: String, // LOW, MEDIUM, HIGH, URGENT
    val status: String = "OPEN", // OPEN, ASSIGNED, IN_PROGRESS, AWAITING_CUSTOMER, AWAITING_APPROVAL, COMPLETED, CLOSED
    val isPreventive: Boolean = false,
    val recurrenceRule: String = "",
    val deadlineEpoch: Long? = null,
    val customerConfirmed: Boolean = false,
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long
)

@Entity(
    tableName = "complaint_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("complaintId")]
)
data class ComplaintAttachmentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val fileUri: String,
    val mediaType: String, // PHOTO, VIDEO, DOCUMENT
    val uploadedByUserId: String,
    val uploadedAtEpoch: Long
)

@Entity(
    tableName = "job_assignments",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["technicianEmployeeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("complaintId"), Index("technicianEmployeeId")]
)
data class JobAssignmentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val technicianEmployeeId: String,
    val assignmentRule: String = "MANUAL",
    val assignedByUserId: String,
    val assignedAtEpoch: Long,
    val deadlineEpoch: Long? = null,
    val status: String = "ASSIGNED" // ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED
)

@Entity(
    tableName = "work_reports",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("complaintId"), Index("technicianEmployeeId")]
)
data class WorkReportEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val technicianEmployeeId: String,
    val summary: String,
    val labourMinutes: Int = 0,
    val labourCostFils: Long = 0L,
    val supplierCostFils: Long = 0L,
    val completedAtEpoch: Long
)

@Entity(
    tableName = "work_report_photos",
    foreignKeys = [
        ForeignKey(
            entity = WorkReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["workReportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("workReportId")]
)
data class WorkReportPhotoEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val workReportId: String,
    val phase: String, // BEFORE, AFTER
    val photoUri: String,
    val caption: String = "",
    val capturedAtEpoch: Long
)

@Entity(
    tableName = "material_usage",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("businessId"),
        Index("complaintId"),
        Index("itemId"),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class MaterialUsageEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val workReportId: String? = null,
    val itemId: String,
    val stockLocationId: String,
    val quantity: Long,
    val unitCostFils: Long,
    val totalCostFils: Long,
    val idempotencyKey: String,
    val stockMovementId: String,
    val journalEntryId: String,
    val recordedByUserId: String,
    val recordedAtEpoch: Long
)

@Entity(
    tableName = "job_approvals",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("businessId"), Index("complaintId")]
)
data class JobApprovalEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val status: String, // APPROVED, REJECTED
    val approvedByUserId: String,
    val notes: String = "",
    val decidedAtEpoch: Long
)

@Entity(
    tableName = "complaint_status_history",
    foreignKeys = [
        ForeignKey(
            entity = ComplaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["complaintId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("complaintId")]
)
data class ComplaintStatusHistoryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val complaintId: String,
    val fromStatus: String,
    val toStatus: String,
    val note: String = "",
    val changedByUserId: String,
    val changedAtEpoch: Long
)
