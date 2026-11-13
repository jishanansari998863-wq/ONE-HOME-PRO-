package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreAndTenancyDao {
    // Businesses
    @Query("SELECT * FROM businesses ORDER BY createdAtEpoch ASC")
    fun observeBusinesses(): Flow<List<BusinessEntity>>

    @Query("SELECT * FROM businesses ORDER BY createdAtEpoch ASC")
    suspend fun getAllBusinesses(): List<BusinessEntity>

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun getBusinessById(id: String): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBusiness(entity: BusinessEntity)

    @Update
    suspend fun updateBusiness(entity: BusinessEntity)

    // Properties, Buildings, Floors, Rooms
    @Query("SELECT * FROM properties WHERE businessId = :businessId AND isArchived = 0 ORDER BY createdAtEpoch DESC")
    fun observeProperties(businessId: String): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE businessId = :businessId")
    suspend fun getPropertiesByBusiness(businessId: String): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun getPropertyById(id: String): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProperty(entity: PropertyEntity)

    @Update
    suspend fun updateProperty(entity: PropertyEntity)

    @Query("SELECT * FROM buildings WHERE businessId = :businessId ORDER BY createdAtEpoch ASC")
    fun observeBuildings(businessId: String): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings WHERE businessId = :businessId")
    suspend fun getBuildingsByBusiness(businessId: String): List<BuildingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBuilding(entity: BuildingEntity)

    @Query("SELECT * FROM floors WHERE businessId = :businessId ORDER BY floorNumber ASC")
    fun observeFloors(businessId: String): Flow<List<FloorEntity>>

    @Query("SELECT * FROM floors WHERE businessId = :businessId")
    suspend fun getFloorsByBusiness(businessId: String): List<FloorEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFloor(entity: FloorEntity)

    @Query("SELECT * FROM rooms WHERE businessId = :businessId ORDER BY roomNumber ASC")
    fun observeRooms(businessId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE businessId = :businessId")
    suspend fun getRoomsByBusiness(businessId: String): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getRoomById(id: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoom(entity: RoomEntity)

    @Update
    suspend fun updateRoom(entity: RoomEntity)

    @Query("SELECT * FROM room_reservations WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeReservations(businessId: String): Flow<List<RoomReservationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReservation(entity: RoomReservationEntity)

    @Update
    suspend fun updateReservation(entity: RoomReservationEntity)

    @Query("SELECT * FROM property_inspections WHERE businessId = :businessId ORDER BY inspectedAtEpoch DESC")
    fun observeInspections(businessId: String): Flow<List<PropertyInspectionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInspection(entity: PropertyInspectionEntity)

    // Customers & Family Members
    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeCustomers(businessId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE businessId = :businessId")
    suspend fun getCustomersByBusiness(businessId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE portalUserId = :userId LIMIT 1")
    suspend fun getCustomerByPortalUserId(userId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(entity: CustomerEntity)

    @Update
    suspend fun updateCustomer(entity: CustomerEntity)

    @Query("SELECT * FROM family_members WHERE customerId = :customerId ORDER BY createdAtEpoch ASC")
    fun observeFamilyMembers(customerId: String): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE businessId = :businessId")
    suspend fun getFamilyMembersByBusiness(businessId: String): List<FamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFamilyMember(entity: FamilyMemberEntity)

    // Agreements & Tenancy History
    @Query("SELECT * FROM agreements WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeAgreements(businessId: String): Flow<List<AgreementEntity>>

    @Query("SELECT * FROM agreements WHERE customerId = :customerId ORDER BY createdAtEpoch DESC")
    fun observeAgreementsForCustomer(customerId: String): Flow<List<AgreementEntity>>

    @Query("SELECT * FROM agreements WHERE businessId = :businessId")
    suspend fun getAgreementsByBusiness(businessId: String): List<AgreementEntity>

    @Query("SELECT * FROM agreements WHERE id = :id")
    suspend fun getAgreementById(id: String): AgreementEntity?

    @Query(
        "SELECT * FROM agreements WHERE roomId = :roomId AND status = 'ACTIVE' " +
            "AND NOT (endDateEpoch < :startDateEpoch OR startDateEpoch > :endDateEpoch)"
    )
    suspend fun findOverlappingActiveAgreements(
        roomId: String,
        startDateEpoch: Long,
        endDateEpoch: Long
    ): List<AgreementEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAgreement(entity: AgreementEntity)

    @Update
    suspend fun updateAgreement(entity: AgreementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAgreementMember(entity: AgreementMemberEntity)

    @Query("SELECT * FROM tenancy_history WHERE businessId = :businessId ORDER BY occurredAtEpoch DESC")
    fun observeTenancyHistory(businessId: String): Flow<List<TenancyHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTenancyHistory(entity: TenancyHistoryEntity)

    // Phase 2: Rents, Payments, Allocations, Receipts, Deposits, Cheques, Advance Rent
    @Query("SELECT * FROM monthly_rents WHERE businessId = :businessId ORDER BY dueDateEpoch DESC")
    fun observeMonthlyRents(businessId: String): Flow<List<MonthlyRentEntity>>

    @Query("SELECT * FROM monthly_rents WHERE customerId = :customerId ORDER BY dueDateEpoch DESC")
    fun observeMonthlyRentsForCustomer(customerId: String): Flow<List<MonthlyRentEntity>>

    @Query("SELECT * FROM monthly_rents WHERE businessId = :businessId")
    suspend fun getMonthlyRentsByBusiness(businessId: String): List<MonthlyRentEntity>

    @Query("SELECT * FROM monthly_rents WHERE id = :id")
    suspend fun getMonthlyRentById(id: String): MonthlyRentEntity?

    @Query("SELECT * FROM monthly_rents WHERE agreementId = :agreementId AND periodKey = :periodKey LIMIT 1")
    suspend fun getMonthlyRentByPeriod(agreementId: String, periodKey: String): MonthlyRentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMonthlyRent(entity: MonthlyRentEntity)

    @Update
    suspend fun updateMonthlyRent(entity: MonthlyRentEntity)

    @Query("SELECT * FROM payments WHERE businessId = :businessId ORDER BY receivedAtEpoch DESC")
    fun observePayments(businessId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY receivedAtEpoch DESC")
    fun observePaymentsForCustomer(customerId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE businessId = :businessId")
    suspend fun getPaymentsByBusiness(businessId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getPaymentByIdempotencyKey(key: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(entity: PaymentEntity)

    @Update
    suspend fun updatePayment(entity: PaymentEntity)

    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun getAllocationsForPayment(paymentId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations WHERE businessId = :businessId")
    fun observePaymentAllocations(businessId: String): Flow<List<PaymentAllocationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPaymentAllocation(entity: PaymentAllocationEntity)

    @Query("SELECT * FROM receipts WHERE businessId = :businessId ORDER BY issuedAtEpoch DESC")
    fun observeReceipts(businessId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE customerId = :customerId ORDER BY issuedAtEpoch DESC")
    fun observeReceiptsForCustomer(customerId: String): Flow<List<ReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(entity: ReceiptEntity)

    @Update
    suspend fun updateReceipt(entity: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE paymentId = :paymentId LIMIT 1")
    suspend fun getReceiptByPaymentId(paymentId: String): ReceiptEntity?

    @Query("SELECT * FROM security_deposits WHERE businessId = :businessId ORDER BY updatedAtEpoch DESC")
    fun observeSecurityDeposits(businessId: String): Flow<List<SecurityDepositEntity>>

    @Query("SELECT * FROM security_deposits WHERE customerId = :customerId")
    fun observeSecurityDepositsForCustomer(customerId: String): Flow<List<SecurityDepositEntity>>

    @Query("SELECT * FROM security_deposits WHERE id = :id")
    suspend fun getSecurityDepositById(id: String): SecurityDepositEntity?

    @Query("SELECT * FROM security_deposits WHERE agreementId = :agreementId LIMIT 1")
    suspend fun getSecurityDepositByAgreementId(agreementId: String): SecurityDepositEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecurityDeposit(entity: SecurityDepositEntity)

    @Update
    suspend fun updateSecurityDeposit(entity: SecurityDepositEntity)

    @Query("SELECT * FROM deposit_transactions WHERE businessId = :businessId ORDER BY occurredAtEpoch DESC")
    fun observeDepositTransactions(businessId: String): Flow<List<DepositTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepositTransaction(entity: DepositTransactionEntity)

    @Query("SELECT * FROM cheques WHERE businessId = :businessId ORDER BY chequeDateEpoch ASC")
    fun observeCheques(businessId: String): Flow<List<ChequeEntity>>

    @Query("SELECT * FROM cheques WHERE businessId = :businessId")
    suspend fun getChequesByBusiness(businessId: String): List<ChequeEntity>

    @Query("SELECT * FROM cheques WHERE id = :id")
    suspend fun getChequeById(id: String): ChequeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheque(entity: ChequeEntity)

    @Update
    suspend fun updateCheque(entity: ChequeEntity)

    @Query("SELECT * FROM advance_rents WHERE businessId = :businessId ORDER BY createdAtEpoch DESC")
    fun observeAdvanceRents(businessId: String): Flow<List<AdvanceRentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdvanceRent(entity: AdvanceRentEntity)

    @Update
    suspend fun updateAdvanceRent(entity: AdvanceRentEntity)
}
