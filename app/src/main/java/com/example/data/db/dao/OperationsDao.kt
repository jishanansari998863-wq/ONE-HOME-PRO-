package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationsDao {
    // Phase 5: Maintenance & Complaints
    @Query("SELECT * FROM complaints WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeComplaints(businessId: String): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints WHERE customerId = :customerId ORDER BY createdAtEpoch DESC")
    fun observeComplaintsForCustomer(customerId: String): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints WHERE id = :id")
    suspend fun getComplaintById(id: String): ComplaintEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComplaint(entity: ComplaintEntity)

    @Update
    suspend fun updateComplaint(entity: ComplaintEntity)

    @Query("SELECT * FROM complaint_attachments WHERE businessId = :businessId")
    fun observeComplaintAttachments(businessId: String): Flow<List<ComplaintAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComplaintAttachment(entity: ComplaintAttachmentEntity)

    @Query("SELECT * FROM job_assignments WHERE businessId = :businessId ORDER BY assignedAtEpoch DESC")
    fun observeJobAssignments(businessId: String): Flow<List<JobAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJobAssignment(entity: JobAssignmentEntity)

    @Query("SELECT * FROM work_reports WHERE businessId = :businessId ORDER BY completedAtEpoch DESC")
    fun observeWorkReports(businessId: String): Flow<List<WorkReportEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkReport(entity: WorkReportEntity)

    @Query("SELECT * FROM work_report_photos WHERE businessId = :businessId")
    fun observeWorkReportPhotos(businessId: String): Flow<List<WorkReportPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkReportPhoto(entity: WorkReportPhotoEntity)

    @Query("SELECT * FROM material_usage WHERE businessId = :businessId ORDER BY recordedAtEpoch DESC")
    fun observeMaterialUsage(businessId: String): Flow<List<MaterialUsageEntity>>

    @Query("SELECT * FROM material_usage WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getMaterialUsageByIdempotencyKey(key: String): MaterialUsageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterialUsage(entity: MaterialUsageEntity)

    @Query("SELECT * FROM job_approvals WHERE businessId = :businessId ORDER BY decidedAtEpoch DESC")
    fun observeJobApprovals(businessId: String): Flow<List<JobApprovalEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJobApproval(entity: JobApprovalEntity)

    @Query("SELECT * FROM complaint_status_history WHERE complaintId = :complaintId ORDER BY changedAtEpoch ASC")
    fun observeComplaintStatusHistory(complaintId: String): Flow<List<ComplaintStatusHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComplaintStatusHistory(entity: ComplaintStatusHistoryEntity)

    // Phase 6: Suppliers, Purchases & Inventory
    @Query("SELECT * FROM suppliers WHERE businessId = :businessId ORDER BY name ASC")
    fun observeSuppliers(businessId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(entity: SupplierEntity)

    @Query("SELECT * FROM item_categories WHERE businessId = :businessId ORDER BY name ASC")
    fun observeItemCategories(businessId: String): Flow<List<ItemCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItemCategory(entity: ItemCategoryEntity)

    @Query("SELECT * FROM items WHERE businessId = :businessId ORDER BY name ASC")
    fun observeItems(businessId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE businessId = :businessId")
    suspend fun getItemsByBusiness(businessId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(entity: ItemEntity)

    @Update
    suspend fun updateItem(entity: ItemEntity)

    @Query("SELECT * FROM stock_locations WHERE businessId = :businessId ORDER BY name ASC")
    fun observeStockLocations(businessId: String): Flow<List<StockLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockLocation(entity: StockLocationEntity)

    @Query("SELECT * FROM purchases WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observePurchases(businessId: String): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: String): PurchaseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(entity: PurchaseEntity)

    @Update
    suspend fun updatePurchase(entity: PurchaseEntity)

    @Query("SELECT * FROM purchase_items WHERE businessId = :businessId")
    fun observePurchaseItems(businessId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItemsForPurchase(purchaseId: String): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items WHERE id = :id")
    suspend fun getPurchaseItemById(id: String): PurchaseItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseItem(entity: PurchaseItemEntity)

    @Update
    suspend fun updatePurchaseItem(entity: PurchaseItemEntity)

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId ORDER BY occurredAtEpoch DESC")
    fun observeStockMovements(businessId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getStockMovementByIdempotencyKey(key: String): StockMovementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(entity: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockTransfer(entity: StockTransferEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockTransferLine(entity: StockTransferLineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockAdjustment(entity: StockAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockCount(entity: StockCountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockCountLine(entity: StockCountLineEntity)

    @Query("SELECT * FROM supplier_payments WHERE businessId = :businessId ORDER BY paidAtEpoch DESC")
    fun observeSupplierPayments(businessId: String): Flow<List<SupplierPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayment(entity: SupplierPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPaymentAllocation(entity: SupplierPaymentAllocationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseApproval(entity: PurchaseApprovalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoodsReceiptDetail(entity: GoodsReceiptDetailEntity)

    // Governance: Documents, Notifications, Audit Logs, Backups, Integrations
    @Query("SELECT * FROM documents WHERE businessId = :businessId AND isArchived = 0 ORDER BY uploadedAtEpoch DESC")
    fun observeDocuments(businessId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE entityId = :customerId AND isArchived = 0 ORDER BY uploadedAtEpoch DESC")
    fun observeDocumentsForCustomer(customerId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(entity: DocumentEntity)

    @Update
    suspend fun updateDocument(entity: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocumentAccessLog(entity: DocumentAccessLogEntity)

    @Query("SELECT * FROM document_access_logs WHERE businessId = :businessId ORDER BY accessedAtEpoch DESC LIMIT 100")
    fun observeDocumentAccessLogs(businessId: String): Flow<List<DocumentAccessLogEntity>>

    @Query("SELECT * FROM notifications WHERE businessId = :businessId AND recipientUserId = :userId ORDER BY createdAtEpoch DESC")
    fun observeNotificationsForUser(businessId: String, userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeAllNotifications(businessId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotification(entity: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markNotificationRead(notificationId: String)

    @Query("SELECT * FROM audit_logs WHERE businessId = :businessId ORDER BY occurredAtEpoch DESC LIMIT 200")
    fun observeAuditLogs(businessId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(entity: AuditLogEntity)

    @Query("SELECT * FROM backup_records WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeBackups(businessId: String): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records WHERE id = :id")
    suspend fun getBackupById(id: String): BackupRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackupRecord(entity: BackupRecordEntity)

    @Update
    suspend fun updateBackupRecord(entity: BackupRecordEntity)

    @Query("SELECT * FROM integration_configs WHERE businessId = :businessId ORDER BY providerType ASC")
    fun observeIntegrations(businessId: String): Flow<List<IntegrationConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntegration(entity: IntegrationConfigEntity)
}
