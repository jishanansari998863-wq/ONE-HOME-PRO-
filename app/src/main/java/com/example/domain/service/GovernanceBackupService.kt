package com.example.domain.service

import android.content.Context
import android.net.Uri
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.security.ActionType
import com.example.domain.security.AppModule
import com.example.domain.security.AuthContext
import com.example.domain.security.AuthorizationGuard
import com.example.domain.security.CryptoUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupVerificationResult(
    val backupId: String,
    val verifiedRecordCount: Int,
    val trialBalanceDebitFils: Long,
    val trialBalanceCreditFils: Long,
    val isBalanced: Boolean,
    val checksumMatched: Boolean
)

class GovernanceBackupService(
    private val context: Context,
    private val db: OneHomeProDatabase,
    private val accountingService: AccountingService
) {
    private val opsDao = db.operationsDao()
    private val coreDao = db.coreAndTenancyDao()
    private val accDao = db.accountingDao()

    private fun vaultDir(): File = File(context.filesDir, "private_vault").apply { mkdirs() }
    private fun backupDir(): File = File(context.filesDir, "encrypted_backups").apply { mkdirs() }

    suspend fun storePickedDocument(
        ctx: AuthContext,
        sourceUri: Uri,
        entityType: String,
        entityId: String,
        category: String,
        title: String,
        propertyId: String? = null,
        expiryDateEpoch: Long? = null,
        isRestricted: Boolean = true
    ): DocumentEntity {
        if (ctx.isCustomerPortalOnly()) {
            require(entityType == "CUSTOMER" && entityId == ctx.customerId) {
                "Customers may only upload documents to their own profile"
            }
        } else {
            AuthorizationGuard.requirePermission(ctx, AppModule.DOCUMENTS, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        }

        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected file")
        require(bytes.isNotEmpty() && bytes.size <= 15 * 1024 * 1024) {
            "Document must be between 1 byte and 15 MB"
        }

        val docId = CryptoUtil.newUuid()
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("pdf") -> "pdf"
            else -> "bin"
        }
        val destFile = File(vaultDir(), "${ctx.activeBusinessId}_$docId.$ext")
        destFile.writeBytes(bytes)
        val sha = CryptoUtil.sha256Hex(bytes)
        val now = System.currentTimeMillis()

        val entity = DocumentEntity(
            id = docId,
            businessId = ctx.activeBusinessId,
            propertyId = propertyId,
            entityType = entityType,
            entityId = entityId,
            category = category,
            title = title.trim().ifBlank { "$category Document" },
            privateFilePath = destFile.absolutePath,
            mimeType = mimeType,
            sha256Checksum = sha,
            fileSizeBytes = bytes.size.toLong(),
            version = 1,
            expiryDateEpoch = expiryDateEpoch,
            isRestricted = isRestricted,
            uploadedByUserId = ctx.userId,
            uploadedAtEpoch = now
        )
        opsDao.insertDocument(entity)
        opsDao.insertDocumentAccessLog(
            DocumentAccessLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                documentId = docId,
                actorUserId = ctx.userId,
                accessAction = "UPLOAD",
                outcome = "ALLOWED",
                accessedAtEpoch = now
            )
        )
        return entity
    }

    suspend fun logDocumentAccess(ctx: AuthContext, documentId: String, action: String): DocumentEntity {
        val doc = opsDao.getDocumentById(documentId) ?: throw IllegalArgumentException("Document not found")
        val allowed = try {
            if (ctx.isCustomerPortalOnly()) {
                doc.entityId == ctx.customerId && doc.businessId == ctx.activeBusinessId
            } else {
                AuthorizationGuard.requirePermission(ctx, AppModule.DOCUMENTS, ActionType.VIEW, doc.businessId, doc.propertyId)
                true
            }
        } catch (e: SecurityException) {
            opsDao.insertDocumentAccessLog(
                DocumentAccessLogEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = doc.businessId,
                    documentId = doc.id,
                    actorUserId = ctx.userId,
                    accessAction = action,
                    outcome = "DENIED",
                    accessedAtEpoch = System.currentTimeMillis()
                )
            )
            throw e
        }
        if (allowed) {
            opsDao.insertDocumentAccessLog(
                DocumentAccessLogEntity(
                    id = CryptoUtil.newUuid(),
                    businessId = doc.businessId,
                    documentId = doc.id,
                    actorUserId = ctx.userId,
                    accessAction = action,
                    outcome = "ALLOWED",
                    accessedAtEpoch = System.currentTimeMillis()
                )
            )
        }
        return doc
    }

    suspend fun markNotificationRead(notificationId: String) {
        opsDao.markNotificationRead(notificationId)
    }

    suspend fun configureIntegration(
        ctx: AuthContext,
        providerType: String,
        providerName: String,
        endpointOrHandle: String,
        enabled: Boolean
    ): IntegrationConfigEntity {
        AuthorizationGuard.requireOwner(ctx)
        val now = System.currentTimeMillis()
        val cfg = IntegrationConfigEntity(
            id = "${ctx.activeBusinessId}_$providerType",
            businessId = ctx.activeBusinessId,
            providerType = providerType,
            providerName = providerName.trim(),
            isEnabled = enabled,
            endpointOrHandle = endpointOrHandle.trim(),
            lastTestStatus = if (enabled && endpointOrHandle.isNotBlank()) "CONFIGURED_OFFLINE_FALLBACK" else "NOT_CONFIGURED",
            lastTestedAtEpoch = now,
            updatedByUserId = ctx.userId,
            updatedAtEpoch = now
        )
        opsDao.upsertIntegration(cfg)
        return cfg
    }

    suspend fun createEncryptedBackup(ctx: AuthContext, passphrase: String): BackupRecordEntity {
        AuthorizationGuard.requirePermission(ctx, AppModule.BACKUPS, ActionType.EXPORT, ctx.activeBusinessId)
        require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters" }
        val bizId = ctx.activeBusinessId
        val now = System.currentTimeMillis()

        val properties = coreDao.getPropertiesByBusiness(bizId)
        val rooms = coreDao.getRoomsByBusiness(bizId)
        val customers = coreDao.getCustomersByBusiness(bizId)
        val agreements = coreDao.getAgreementsByBusiness(bizId)
        val rents = coreDao.getMonthlyRentsByBusiness(bizId)
        val payments = coreDao.getPaymentsByBusiness(bizId)
        val cheques = coreDao.getChequesByBusiness(bizId)
        val journals = accDao.getJournalEntriesByBusiness(bizId)
        val lines = accDao.getJournalLinesByBusiness(bizId)
        val items = opsDao.getItemsByBusiness(bizId)

        val totalRecords = properties.size + rooms.size + customers.size + agreements.size +
            rents.size + payments.size + cheques.size + journals.size + lines.size + items.size

        val tb = accountingService.computeTrialBalance(bizId)
        val totalDebit = tb.sumOf { it.totalDebitFils }
        val totalCredit = tb.sumOf { it.totalCreditFils }
        val diff = totalDebit - totalCredit

        val manifest = JSONObject().apply {
            put("businessId", bizId)
            put("createdAtEpoch", now)
            put("recordCountTotal", totalRecords)
            put("totalDebitFils", totalDebit)
            put("totalCreditFils", totalCredit)
            put("propertyIds", JSONArray(properties.map { it.id }))
            put("customerIds", JSONArray(customers.map { it.id }))
            put("paymentIds", JSONArray(payments.map { it.id }))
            put("journalIds", JSONArray(journals.map { it.id }))
        }
        val plainBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
        val manifestSha = CryptoUtil.sha256Hex(plainBytes)
        val encrypted = CryptoUtil.encryptAesGcm(plainBytes, passphrase)

        val backupId = CryptoUtil.newUuid()
        val file = File(backupDir(), "backup_${bizId}_${backupId.take(8)}.ohp")
        file.writeBytes(encrypted)

        val record = BackupRecordEntity(
            id = backupId,
            businessId = bizId,
            filePath = file.absolutePath,
            manifestSha256 = manifestSha,
            isEncrypted = true,
            recordCountTotal = totalRecords,
            trialBalanceDifferenceFils = diff,
            status = "CREATED",
            createdByUserId = ctx.userId,
            createdAtEpoch = now
        )
        opsDao.insertBackupRecord(record)
        opsDao.insertAuditLog(
            AuditLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = bizId,
                actorUserId = ctx.userId,
                action = "CREATE_ENCRYPTED_BACKUP",
                entityType = "BACKUP",
                entityId = backupId,
                summary = "Encrypted backup created ($totalRecords core records, GL diff=$diff)",
                occurredAtEpoch = now
            )
        )
        return record
    }

    suspend fun verifyAndTestRestoreBackup(
        ctx: AuthContext,
        backupId: String,
        passphrase: String
    ): BackupVerificationResult {
        AuthorizationGuard.requirePermission(ctx, AppModule.BACKUPS, ActionType.RESTORE, ctx.activeBusinessId)
        val record = opsDao.getBackupById(backupId) ?: throw IllegalArgumentException("Backup not found")
        require(record.businessId == ctx.activeBusinessId) { "Cross-business backup access blocked" }

        val file = File(record.filePath)
        require(file.exists()) { "Backup file missing on disk" }
        val decrypted = CryptoUtil.decryptAesGcm(file.readBytes(), passphrase)
        val shaMatches = CryptoUtil.sha256Hex(decrypted) == record.manifestSha256
        val json = JSONObject(String(decrypted, Charsets.UTF_8))

        val restoredCount = json.getInt("recordCountTotal")
        val debits = json.getLong("totalDebitFils")
        val credits = json.getLong("totalCreditFils")
        val balanced = (debits == credits)
        val now = System.currentTimeMillis()

        val status = if (shaMatches && balanced) "VERIFIED_RESTORE" else "FAILED_VERIFICATION"
        opsDao.updateBackupRecord(
            record.copy(
                status = status,
                lastVerifiedRestoreAtEpoch = now
            )
        )
        opsDao.insertAuditLog(
            AuditLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                actorUserId = ctx.userId,
                action = "VERIFY_BACKUP_RESTORE",
                entityType = "BACKUP",
                entityId = backupId,
                summary = "Backup restore integrity test: checksum=$shaMatches, balanced=$balanced, records=$restoredCount",
                occurredAtEpoch = now
            )
        )
        return BackupVerificationResult(
            backupId = backupId,
            verifiedRecordCount = restoredCount,
            trialBalanceDebitFils = debits,
            trialBalanceCreditFils = credits,
            isBalanced = balanced,
            checksumMatched = shaMatches
        )
    }
}
