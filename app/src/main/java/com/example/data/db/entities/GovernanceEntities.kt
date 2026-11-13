package com.example.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index("businessId"),
        Index("entityType"),
        Index("entityId"),
        Index("category")
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val propertyId: String? = null,
    val entityType: String, // PROPERTY, CUSTOMER, AGREEMENT, EMPLOYEE, SUPPLIER, COMPLAINT, RECEIPT, BUSINESS
    val entityId: String,
    val category: String, // QID, PASSPORT, AGREEMENT, RECEIPT, INVOICE, WORK_EVIDENCE, PROPERTY_RECORD, LOGO, OTHER
    val title: String,
    val privateFilePath: String,
    val mimeType: String,
    val sha256Checksum: String,
    val fileSizeBytes: Long,
    val version: Int = 1,
    val expiryDateEpoch: Long? = null,
    val isRestricted: Boolean = true,
    val isArchived: Boolean = false,
    val uploadedByUserId: String,
    val uploadedAtEpoch: Long
)

@Entity(
    tableName = "document_access_logs",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("businessId"), Index("documentId"), Index("actorUserId")]
)
data class DocumentAccessLogEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val documentId: String,
    val actorUserId: String,
    val accessAction: String, // PREVIEW, EXPORT, DOWNLOAD, VERIFY
    val outcome: String, // ALLOWED, DENIED
    val accessedAtEpoch: Long
)

@Entity(
    tableName = "notifications",
    indices = [Index("businessId"), Index("recipientUserId"), Index("isRead")]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val recipientUserId: String,
    val recipientCustomerId: String? = null,
    val category: String, // RENT_DUE, PAYMENT, AGREEMENT_EXPIRY, CHEQUE, DEPOSIT, MAINTENANCE, APPROVAL, STOCK, DOCUMENT, BACKUP, SECURITY
    val title: String,
    val message: String,
    val channel: String = "IN_APP", // IN_APP, EMAIL, SMS, WHATSAPP
    val deliveryStatus: String = "DELIVERED_LOCAL", // DELIVERED_LOCAL, PROVIDER_NOT_CONFIGURED, QUEUED, FAILED
    val isRead: Boolean = false,
    val createdAtEpoch: Long
)

@Entity(
    tableName = "audit_logs",
    indices = [Index("businessId"), Index("actorUserId"), Index("entityType")]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val actorUserId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val occurredAtEpoch: Long
)

@Entity(
    tableName = "backup_records",
    indices = [Index("businessId")]
)
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val filePath: String,
    val manifestSha256: String,
    val isEncrypted: Boolean = true,
    val recordCountTotal: Int,
    val trialBalanceDifferenceFils: Long,
    val status: String = "CREATED", // CREATED, VERIFIED_RESTORE, FAILED_VERIFICATION
    val createdByUserId: String,
    val createdAtEpoch: Long,
    val lastVerifiedRestoreAtEpoch: Long? = null
)

@Entity(
    tableName = "integration_configs",
    indices = [Index("businessId"), Index(value = ["businessId", "providerType"], unique = true)]
)
data class IntegrationConfigEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val providerType: String, // PAYMENT_GATEWAY, BANK_FEED, EMAIL_SMTP, SMS_GATEWAY, WHATSAPP_API, CLOUD_BACKUP
    val providerName: String,
    val isEnabled: Boolean = false,
    val endpointOrHandle: String = "",
    val lastTestStatus: String = "NOT_TESTED",
    val lastTestedAtEpoch: Long? = null,
    val updatedByUserId: String,
    val updatedAtEpoch: Long
)
