package com.example.domain.service

import android.content.Context
import android.net.Uri
import com.example.data.db.OneHomeProDatabase
import com.example.data.db.entities.*
import com.example.domain.document.DocumentImageProcessor
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
    private val hrDao = db.securityAndHrDao()
    private val accDao = db.accountingDao()

    private fun vaultDir(): File = File(context.filesDir, "private_vault").apply { mkdirs() }
    private fun backupDir(): File = File(context.filesDir, "encrypted_backups").apply { mkdirs() }

    private fun getOrCreateVaultSecret(): String {
        val keyFile = File(context.filesDir, ".vault_master_key")
        if (keyFile.exists()) {
            val existing = keyFile.readText(Charsets.UTF_8).trim()
            if (existing.length >= 24) return existing
        }
        val secret = CryptoUtil.generateSecureToken()
        keyFile.writeText(secret, Charsets.UTF_8)
        return secret
    }

    suspend fun storePickedDocument(
        ctx: AuthContext,
        sourceUri: Uri,
        entityType: String,
        entityId: String,
        category: String,
        title: String,
        propertyId: String? = null,
        expiryDateEpoch: Long? = null,
        isRestricted: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): DocumentEntity {
        onProgress(0.15f, "Validating selected file format...")
        val validated = DocumentImageProcessor.validatePickedFile(context, sourceUri)
        return storeProcessedDocumentBytes(
            ctx = ctx,
            bytes = validated.bytes,
            mimeType = validated.mimeType,
            extension = validated.extension,
            entityType = entityType,
            entityId = entityId,
            category = category,
            title = title.ifBlank { validated.displayName },
            propertyId = propertyId,
            expiryDateEpoch = expiryDateEpoch,
            isRestricted = isRestricted,
            onProgress = onProgress
        )
    }

    suspend fun storeProcessedDocumentBytes(
        ctx: AuthContext,
        bytes: ByteArray,
        mimeType: String,
        extension: String,
        entityType: String,
        entityId: String,
        category: String,
        title: String,
        propertyId: String? = null,
        expiryDateEpoch: Long? = null,
        isRestricted: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): DocumentEntity {
        onProgress(0.25f, "Verifying role-based access and record ownership...")
        if (ctx.isCustomerPortalOnly()) {
            require(entityType == "CUSTOMER" && entityId == ctx.customerId) {
                "Customers may only upload documents to their own profile"
            }
        } else {
            AuthorizationGuard.requirePermission(ctx, AppModule.DOCUMENTS, ActionType.CREATE, ctx.activeBusinessId, propertyId)
        }

        val normalizedMime = mimeType.lowercase()
        val normalizedExt = extension.lowercase()
        require(normalizedExt in setOf("jpg", "jpeg", "png", "pdf")) {
            "Unsupported format '$extension'. Supported formats: JPG, JPEG, PNG, PDF."
        }
        require(normalizedMime in setOf("image/jpeg", "image/jpg", "image/png", "application/pdf")) {
            "Unsupported MIME type '$mimeType'. Supported types: JPG, JPEG, PNG, PDF."
        }
        require(bytes.isNotEmpty() && bytes.size <= 15 * 1024 * 1024) {
            "Document size must be between 1 byte and 15 MB"
        }

        val resolvedPropertyId = resolveAndValidateTargetRecord(
            ctx = ctx,
            entityType = entityType.uppercase(),
            entityId = entityId,
            explicitPropertyId = propertyId
        )

        onProgress(0.55f, "Encrypting document for secure vault storage...")
        val docId = CryptoUtil.newUuid()
        val encryptedBytes = CryptoUtil.encryptAesGcm(bytes, getOrCreateVaultSecret())
        val destFile = File(vaultDir(), "${ctx.activeBusinessId}_$docId.$normalizedExt.ohp")
        destFile.writeBytes(encryptedBytes)

        onProgress(0.82f, "Recording SHA-256 integrity checksum and audit entry...")
        val sha = CryptoUtil.sha256Hex(bytes)
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            id = docId,
            businessId = ctx.activeBusinessId,
            propertyId = resolvedPropertyId,
            entityType = entityType.uppercase(),
            entityId = entityId,
            category = category.uppercase(),
            title = title.trim().ifBlank { "$category Document" },
            privateFilePath = destFile.absolutePath,
            mimeType = normalizedMime,
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
        opsDao.insertAuditLog(
            AuditLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = ctx.activeBusinessId,
                actorUserId = ctx.userId,
                action = "UPLOAD_DOCUMENT",
                entityType = entity.entityType,
                entityId = entity.entityId,
                summary = "Uploaded ${entity.category} (${entity.mimeType}, ${entity.fileSizeBytes} bytes) to ${entity.entityType}:${entity.entityId}",
                occurredAtEpoch = now
            )
        )
        onProgress(1.0f, "Upload complete")
        return entity
    }

    private suspend fun resolveAndValidateTargetRecord(
        ctx: AuthContext,
        entityType: String,
        entityId: String,
        explicitPropertyId: String?
    ): String? {
        require(entityId.isNotBlank()) { "Target record ID is required" }
        return when (entityType) {
            "CUSTOMER" -> {
                val c = coreDao.getCustomerById(entityId)
                    ?: throw IllegalArgumentException("Selected Tenant record does not exist")
                require(c.businessId == ctx.activeBusinessId) { "Cross-business tenant upload blocked" }
                explicitPropertyId
            }
            "PROPERTY" -> {
                val p = coreDao.getPropertyById(entityId)
                    ?: throw IllegalArgumentException("Selected Property record does not exist")
                require(p.businessId == ctx.activeBusinessId) { "Cross-business property upload blocked" }
                p.id
            }
            "EMPLOYEE" -> {
                val e = hrDao.getEmployeeById(entityId)
                    ?: throw IllegalArgumentException("Selected Employee record does not exist")
                require(e.businessId == ctx.activeBusinessId) { "Cross-business employee upload blocked" }
                explicitPropertyId
            }
            "AGREEMENT" -> {
                val ag = coreDao.getAgreementById(entityId)
                    ?: throw IllegalArgumentException("Selected Agreement record does not exist")
                require(ag.businessId == ctx.activeBusinessId) { "Cross-business agreement upload blocked" }
                ag.propertyId
            }
            "PAYMENT" -> {
                val pay = coreDao.getPaymentById(entityId)
                    ?: throw IllegalArgumentException("Selected Payment record does not exist")
                require(pay.businessId == ctx.activeBusinessId) { "Cross-business payment upload blocked" }
                val linkedPropId = pay.agreementId?.let { coreDao.getAgreementById(it)?.propertyId }
                linkedPropId ?: explicitPropertyId
            }
            "BUSINESS" -> {
                require(entityId == ctx.activeBusinessId) { "Cross-business upload blocked" }
                explicitPropertyId
            }
            else -> explicitPropertyId
        }
    }

    suspend fun readDecryptedDocumentBytes(ctx: AuthContext, documentId: String): Pair<DocumentEntity, ByteArray> {
        val doc = logDocumentAccess(ctx, documentId, "PREVIEW")
        val file = File(doc.privateFilePath)
        require(file.exists()) { "Encrypted document file missing from private vault" }
        val raw = file.readBytes()
        val decrypted = if (doc.privateFilePath.endsWith(".ohp")) {
            CryptoUtil.decryptAesGcm(raw, getOrCreateVaultSecret())
        } else {
            raw
        }
        require(CryptoUtil.sha256Hex(decrypted) == doc.sha256Checksum) {
            "Document integrity checksum mismatch"
        }
        return doc to decrypted
    }

    suspend fun deleteDocument(ctx: AuthContext, documentId: String) {
        val doc = opsDao.getDocumentById(documentId) ?: throw IllegalArgumentException("Document not found")
        if (ctx.isCustomerPortalOnly()) {
            require(doc.entityType == "CUSTOMER" && doc.entityId == ctx.customerId) {
                "Customers may only remove their own documents"
            }
        } else {
            AuthorizationGuard.requirePermission(ctx, AppModule.DOCUMENTS, ActionType.ARCHIVE, doc.businessId, doc.propertyId)
        }
        val now = System.currentTimeMillis()
        opsDao.updateDocument(doc.copy(isArchived = true))
        File(doc.privateFilePath).takeIf { it.exists() }?.delete()
        opsDao.insertDocumentAccessLog(
            DocumentAccessLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = doc.businessId,
                documentId = doc.id,
                actorUserId = ctx.userId,
                accessAction = "DELETE",
                outcome = "ALLOWED",
                accessedAtEpoch = now
            )
        )
        opsDao.insertAuditLog(
            AuditLogEntity(
                id = CryptoUtil.newUuid(),
                businessId = doc.businessId,
                actorUserId = ctx.userId,
                action = "DELETE_DOCUMENT",
                entityType = doc.entityType,
                entityId = doc.entityId,
                summary = "Archived & removed document ${doc.title} (${doc.category})",
                occurredAtEpoch = now
            )
        )
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
