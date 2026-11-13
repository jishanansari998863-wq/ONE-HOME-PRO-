package com.example.ui

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.db.entities.*
import com.example.domain.security.AuthContext
import com.example.domain.service.AuthResult
import com.example.domain.service.JournalLineInput
import com.example.localization.AppLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OneHomeProViewModel(
    private val container: AppContainer,
    private val splashDelayMs: Long = 900L
) : ViewModel() {
    private val db = container.database
    private val coreDao = db.coreAndTenancyDao()
    private val accDao = db.accountingDao()
    private val hrDao = db.securityAndHrDao()
    private val opsDao = db.operationsDao()

    private val _uiState = MutableStateFlow(OneHomeUiState())
    val uiState: StateFlow<OneHomeUiState> = _uiState.asStateFlow()

    private var dataCollectionJob: Job? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            container.sessionStore.languageFlow.collectLatest { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            container.sessionStore.darkModeFlow.collectLatest { dark ->
                _uiState.update { it.copy(darkTheme = dark) }
            }
        }
        viewModelScope.launch {
            if (splashDelayMs > 0L) delay(splashDelayMs) // Brief branded splash display
            val hasUsers = container.authService.hasAnyUsers()
            val restoredCtx = if (hasUsers) container.authService.validateStoredSessionOrNull() else null
            if (restoredCtx != null) {
                onAuthenticated(restoredCtx)
            } else {
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        hasExistingUsers = hasUsers,
                        authStep = if (hasUsers) AuthScreenStep.LOGIN else AuthScreenStep.WELCOME_SETUP
                    )
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.sessionStore.setLanguage(language) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { container.sessionStore.setDarkMode(enabled) }
    }

    fun clearBanners() {
        _uiState.update { it.copy(bannerError = null, bannerSuccess = null) }
    }

    fun setAuthStep(step: AuthScreenStep) {
        _uiState.update { it.copy(authStep = step, bannerError = null, bannerSuccess = null) }
    }

    fun navigateTo(dest: AppDestination) {
        _uiState.update { state ->
            if (state.currentDestination == dest) state
            else state.copy(
                backStack = state.backStack + state.currentDestination,
                currentDestination = dest
            )
        }
    }

    fun navigateTab(dest: AppDestination) {
        _uiState.update { it.copy(backStack = emptyList(), currentDestination = dest) }
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.backStack
        return if (stack.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    currentDestination = stack.last(),
                    backStack = stack.dropLast(1)
                )
            }
            true
        } else {
            false
        }
    }

    fun setPropertyFilter(propertyId: String?) {
        _uiState.update { it.copy(selectedPropertyFilterId = propertyId) }
    }

    fun setGlobalSearchQuery(q: String) {
        _uiState.update { it.copy(globalSearchQuery = q) }
    }

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }

    // --- Authentication Actions ---
    fun setupOwnerAndBusiness(
        fullName: String,
        email: String,
        password: String,
        businessName: String,
        branchName: String,
        enableMfa: Boolean,
        rememberMe: Boolean
    ) {
        runAction("Owner & business initialized") {
            when (
                val res = container.authService.setupInitialOwnerAndBusiness(
                    fullName, email, password, businessName, branchName, enableMfa, rememberMe, deviceLabel()
                )
            ) {
                is AuthResult.Authenticated -> onAuthenticated(res.context)
                is AuthResult.Failed -> throw IllegalStateException(res.message)
                else -> {}
            }
        }
    }

    fun signIn(email: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null) }
            try {
                when (val res = container.authService.login(email, password, rememberMe, deviceLabel())) {
                    is AuthResult.Authenticated -> onAuthenticated(res.context)
                    is AuthResult.MfaRequired -> _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingMfaChallenge = res,
                            authStep = AuthScreenStep.MFA_CHALLENGE
                        )
                    }
                    is AuthResult.NewDeviceVerificationRequired -> _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingDeviceChallenge = res,
                            authStep = AuthScreenStep.DEVICE_CHALLENGE
                        )
                    }
                    is AuthResult.Failed -> _uiState.update {
                        it.copy(isBusy = false, bannerError = res.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, bannerError = e.message ?: "Authentication error") }
            }
        }
    }

    fun verifyNewDevice(code: String, trustDevice: Boolean) {
        val challenge = _uiState.value.pendingDeviceChallenge ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null) }
            when (
                val res = container.authService.verifyNewDeviceChallenge(
                    challenge.userId, code, trustDevice, challenge.rememberMe, deviceLabel()
                )
            ) {
                is AuthResult.Authenticated -> onAuthenticated(res.context)
                is AuthResult.MfaRequired -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingDeviceChallenge = null,
                        pendingMfaChallenge = res,
                        authStep = AuthScreenStep.MFA_CHALLENGE
                    )
                }
                is AuthResult.Failed -> _uiState.update { it.copy(isBusy = false, bannerError = res.message) }
                else -> {}
            }
        }
    }

    fun verifyMfa(codeOrRecovery: String) {
        val challenge = _uiState.value.pendingMfaChallenge ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null) }
            when (
                val res = container.authService.verifyMfaAndCompleteLogin(
                    challenge.userId, codeOrRecovery, challenge.rememberMe, deviceLabel()
                )
            ) {
                is AuthResult.Authenticated -> onAuthenticated(res.context)
                is AuthResult.Failed -> _uiState.update { it.copy(isBusy = false, bannerError = res.message) }
                else -> {}
            }
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null) }
            try {
                val otp = container.authService.requestPasswordReset(email)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingResetOtpNotice = "Verification code issued: $otp (valid 15 min)",
                        bannerSuccess = "Reset code generated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, bannerError = e.message) }
            }
        }
    }

    fun completePasswordReset(email: String, otp: String, newPassword: String) {
        runAction("Password reset complete. All prior sessions revoked.") {
            container.authService.completePasswordReset(email, otp, newPassword)
            _uiState.update { it.copy(authStep = AuthScreenStep.LOGIN, pendingResetOtpNotice = null) }
        }
    }

    fun enableMfa() {
        val ctx = _uiState.value.authContext ?: return
        viewModelScope.launch {
            try {
                val info = container.authService.enableMfaAndGenerateRecoveryCodes(ctx)
                _uiState.update {
                    it.copy(
                        mfaProvisionInfo = info,
                        bannerSuccess = "MFA enabled. Save your recovery codes securely."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(bannerError = e.message) }
            }
        }
    }

    fun disableMfa() {
        val ctx = _uiState.value.authContext ?: return
        runAction("MFA disabled") { container.authService.disableMfa(ctx) }
    }

    fun logout() {
        val ctx = _uiState.value.authContext ?: return
        viewModelScope.launch {
            container.authService.logoutCurrentSession(ctx)
            dataCollectionJob?.cancel()
            _uiState.update {
                it.copy(
                    authContext = null,
                    hasExistingUsers = true,
                    authStep = AuthScreenStep.LOGIN,
                    pendingMfaChallenge = null,
                    pendingDeviceChallenge = null,
                    data = BusinessSnapshotData(),
                    bannerSuccess = "Signed out securely"
                )
            }
        }
    }

    fun logoutAllDevices() {
        val ctx = _uiState.value.authContext ?: return
        viewModelScope.launch {
            container.authService.logoutAllDevices(ctx)
            dataCollectionJob?.cancel()
            _uiState.update {
                it.copy(
                    authContext = null,
                    hasExistingUsers = true,
                    authStep = AuthScreenStep.LOGIN,
                    data = BusinessSnapshotData(),
                    bannerSuccess = "All devices signed out and sessions revoked"
                )
            }
        }
    }

    fun revokeSession(sessionId: String) {
        val ctx = _uiState.value.authContext ?: return
        runAction("Session revoked") { container.authService.revokeSpecificSession(ctx, sessionId) }
    }

    fun switchBusiness(businessId: String) {
        val ctx = _uiState.value.authContext ?: return
        viewModelScope.launch {
            try {
                val updated = container.authService.switchBusiness(ctx, businessId)
                onAuthenticated(updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(bannerError = e.message) }
            }
        }
    }

    private fun onAuthenticated(ctx: AuthContext) {
        val defaultDest = if (ctx.isCustomerPortalOnly()) AppDestination.CUSTOMER_PORTAL else AppDestination.DASHBOARD
        _uiState.update {
            it.copy(
                isInitializing = false,
                isBusy = false,
                hasExistingUsers = true,
                authContext = ctx,
                pendingMfaChallenge = null,
                pendingDeviceChallenge = null,
                currentDestination = defaultDest,
                backStack = emptyList(),
                bannerError = null
            )
        }
        startObservingBusinessData(ctx)
        refreshFinancialReports()
    }

    private fun startObservingBusinessData(ctx: AuthContext) {
        dataCollectionJob?.cancel()
        val bizId = ctx.activeBusinessId
        dataCollectionJob = viewModelScope.launch {
            if (ctx.isCustomerPortalOnly()) {
                val custId = ctx.customerId ?: ""
                combine(
                    coreDao.observeBusinesses(),
                    coreDao.observeProperties(bizId),
                    coreDao.observeRooms(bizId),
                    coreDao.observeAgreementsForCustomer(custId),
                    coreDao.observeMonthlyRentsForCustomer(custId),
                    coreDao.observePaymentsForCustomer(custId),
                    coreDao.observeReceiptsForCustomer(custId),
                    coreDao.observeSecurityDepositsForCustomer(custId),
                    opsDao.observeComplaintsForCustomer(custId),
                    opsDao.observeDocumentsForCustomer(custId),
                    opsDao.observeNotificationsForUser(bizId, ctx.userId),
                    hrDao.observeSessionsForUser(ctx.userId),
                    hrDao.observeUserDevices(ctx.userId),
                    hrDao.observeLoginHistoryForUser(ctx.userId)
                ) { arr ->
                    @Suppress("UNCHECKED_CAST")
                    BusinessSnapshotData(
                        businesses = arr[0] as List<BusinessEntity>,
                        properties = arr[1] as List<PropertyEntity>,
                        rooms = arr[2] as List<RoomEntity>,
                        agreements = arr[3] as List<AgreementEntity>,
                        monthlyRents = arr[4] as List<MonthlyRentEntity>,
                        payments = arr[5] as List<PaymentEntity>,
                        receipts = arr[6] as List<ReceiptEntity>,
                        deposits = arr[7] as List<SecurityDepositEntity>,
                        complaints = arr[8] as List<ComplaintEntity>,
                        documents = arr[9] as List<DocumentEntity>,
                        notifications = arr[10] as List<NotificationEntity>,
                        activeSessions = arr[11] as List<LoginSessionEntity>,
                        userDevices = arr[12] as List<UserDeviceEntity>,
                        loginHistory = arr[13] as List<LoginHistoryEntity>
                    )
                }.collectLatest { snapshot ->
                    _uiState.update { it.copy(data = snapshot) }
                }
            } else {
                // Observe all staff/owner modules cleanly via separate collectors merged into state
                launch { coreDao.observeBusinesses().collectLatest { v -> updateData { it.copy(businesses = v) } } }
                launch { coreDao.observeProperties(bizId).collectLatest { v -> updateData { it.copy(properties = v) } } }
                launch { coreDao.observeBuildings(bizId).collectLatest { v -> updateData { it.copy(buildings = v) } } }
                launch { coreDao.observeFloors(bizId).collectLatest { v -> updateData { it.copy(floors = v) } } }
                launch { coreDao.observeRooms(bizId).collectLatest { v -> updateData { it.copy(rooms = v) } } }
                launch { coreDao.observeReservations(bizId).collectLatest { v -> updateData { it.copy(reservations = v) } } }
                launch { coreDao.observeInspections(bizId).collectLatest { v -> updateData { it.copy(inspections = v) } } }
                launch { coreDao.observeCustomers(bizId).collectLatest { v -> updateData { it.copy(customers = v) } } }
                launch { coreDao.observeAgreements(bizId).collectLatest { v -> updateData { it.copy(agreements = v) } } }
                launch { coreDao.observeTenancyHistory(bizId).collectLatest { v -> updateData { it.copy(tenancyHistory = v) } } }
                launch { coreDao.observeMonthlyRents(bizId).collectLatest { v -> updateData { it.copy(monthlyRents = v) } } }
                launch { coreDao.observePayments(bizId).collectLatest { v -> updateData { it.copy(payments = v) } } }
                launch { coreDao.observeReceipts(bizId).collectLatest { v -> updateData { it.copy(receipts = v) } } }
                launch { coreDao.observeSecurityDeposits(bizId).collectLatest { v -> updateData { it.copy(deposits = v) } } }
                launch { coreDao.observeDepositTransactions(bizId).collectLatest { v -> updateData { it.copy(depositTransactions = v) } } }
                launch { coreDao.observeCheques(bizId).collectLatest { v -> updateData { it.copy(cheques = v) } } }
                launch { accDao.observeChartOfAccounts(bizId).collectLatest { v -> updateData { it.copy(accounts = v) } } }
                launch { accDao.observeJournalEntries(bizId).collectLatest { v -> updateData { it.copy(journals = v) }; refreshFinancialReports() } }
                launch { accDao.observeJournalLines(bizId).collectLatest { v -> updateData { it.copy(journalLines = v) }; refreshFinancialReports() } }
                launch { accDao.observeIncomeRecords(bizId).collectLatest { v -> updateData { it.copy(incomeRecords = v) } } }
                launch { accDao.observeExpenseRecords(bizId).collectLatest { v -> updateData { it.copy(expenseRecords = v) } } }
                launch { accDao.observeBankAccounts(bizId).collectLatest { v -> updateData { it.copy(bankAccounts = v) } } }
                launch { accDao.observeCashbookEntries(bizId).collectLatest { v -> updateData { it.copy(cashbookEntries = v) } } }
                launch { accDao.observePettyCashEntries(bizId).collectLatest { v -> updateData { it.copy(pettyCashEntries = v) } } }
                launch { accDao.observeBankReconciliations(bizId).collectLatest { v -> updateData { it.copy(bankReconciliations = v) } } }
                launch { accDao.observeFiscalPeriods(bizId).collectLatest { v -> updateData { it.copy(fiscalPeriods = v) } } }
                launch { hrDao.observeEmployees(bizId).collectLatest { v -> updateData { it.copy(employees = v) } } }
                launch { hrDao.observeDepartments(bizId).collectLatest { v -> updateData { it.copy(departments = v) } } }
                launch { hrDao.observeTeams(bizId).collectLatest { v -> updateData { it.copy(teams = v) } } }
                launch { hrDao.observeAttendance(bizId).collectLatest { v -> updateData { it.copy(attendance = v) } } }
                launch { hrDao.observeWorkHistory(bizId).collectLatest { v -> updateData { it.copy(workHistory = v) } } }
                launch { hrDao.observeAllUsers().collectLatest { v -> updateData { it.copy(users = v) } } }
                launch { hrDao.observeRoles(bizId).collectLatest { v -> updateData { it.copy(roles = v) } } }
                launch { hrDao.observePermissions().collectLatest { v -> updateData { it.copy(permissions = v) } } }
                launch { hrDao.observeRolePermissions(bizId).collectLatest { v -> updateData { it.copy(rolePermissions = v) } } }
                launch { hrDao.observeUserRoles(bizId).collectLatest { v -> updateData { it.copy(userRoles = v) } } }
                launch { opsDao.observeComplaints(bizId).collectLatest { v -> updateData { it.copy(complaints = v) } } }
                launch { opsDao.observeJobAssignments(bizId).collectLatest { v -> updateData { it.copy(jobAssignments = v) } } }
                launch { opsDao.observeWorkReports(bizId).collectLatest { v -> updateData { it.copy(workReports = v) } } }
                launch { opsDao.observeWorkReportPhotos(bizId).collectLatest { v -> updateData { it.copy(workReportPhotos = v) } } }
                launch { opsDao.observeMaterialUsage(bizId).collectLatest { v -> updateData { it.copy(materialUsage = v) } } }
                launch { opsDao.observeSuppliers(bizId).collectLatest { v -> updateData { it.copy(suppliers = v) } } }
                launch { opsDao.observeItemCategories(bizId).collectLatest { v -> updateData { it.copy(itemCategories = v) } } }
                launch { opsDao.observeItems(bizId).collectLatest { v -> updateData { it.copy(items = v) } } }
                launch { opsDao.observeStockLocations(bizId).collectLatest { v -> updateData { it.copy(stockLocations = v) } } }
                launch { opsDao.observePurchases(bizId).collectLatest { v -> updateData { it.copy(purchases = v) } } }
                launch { opsDao.observePurchaseItems(bizId).collectLatest { v -> updateData { it.copy(purchaseItems = v) } } }
                launch { opsDao.observeStockMovements(bizId).collectLatest { v -> updateData { it.copy(stockMovements = v) } } }
                launch { opsDao.observeSupplierPayments(bizId).collectLatest { v -> updateData { it.copy(supplierPayments = v) } } }
                launch { opsDao.observeDocuments(bizId).collectLatest { v -> updateData { it.copy(documents = v) } } }
                launch { opsDao.observeDocumentAccessLogs(bizId).collectLatest { v -> updateData { it.copy(documentAccessLogs = v) } } }
                launch { opsDao.observeAllNotifications(bizId).collectLatest { v -> updateData { it.copy(notifications = v) } } }
                launch { opsDao.observeAuditLogs(bizId).collectLatest { v -> updateData { it.copy(auditLogs = v) } } }
                launch { opsDao.observeBackups(bizId).collectLatest { v -> updateData { it.copy(backups = v) } } }
                launch { opsDao.observeIntegrations(bizId).collectLatest { v -> updateData { it.copy(integrations = v) } } }
                launch { hrDao.observeSecurityEvents().collectLatest { v -> updateData { it.copy(securityEvents = v) } } }
                launch { hrDao.observeSessionsForUser(ctx.userId).collectLatest { v -> updateData { it.copy(activeSessions = v) } } }
                launch { hrDao.observeUserDevices(ctx.userId).collectLatest { v -> updateData { it.copy(userDevices = v) } } }
                launch { hrDao.observeLoginHistoryForUser(ctx.userId).collectLatest { v -> updateData { it.copy(loginHistory = v) } } }
            }
        }
    }

    private fun updateData(transform: (BusinessSnapshotData) -> BusinessSnapshotData) {
        _uiState.update { it.copy(data = transform(it.data)) }
    }

    fun refreshFinancialReports() {
        val bizId = _uiState.value.authContext?.activeBusinessId ?: return
        viewModelScope.launch {
            val tb = container.accountingService.computeTrialBalance(bizId)
            val pnl = container.accountingService.computeProfitAndLoss(bizId)
            val bs = container.accountingService.computeBalanceSheet(bizId)
            _uiState.update { it.copy(reports = FinancialReportsState(tb, pnl, bs)) }
        }
    }

    // --- Business & Property Actions ---
    fun createBusinessBranch(name: String, branch: String, phone: String, email: String, address: String, cr: String) =
        runAction("Branch created") {
            val ctx = requireCtx()
            container.propertyTenancyService.createBusinessBranch(ctx, name, branch, phone, email, address, cr)
            val refreshed = container.authService.switchBusiness(ctx, ctx.activeBusinessId)
            _uiState.update { it.copy(authContext = refreshed) }
        }

    fun updateBusinessProfile(name: String, branch: String, phone: String, email: String, address: String, cr: String, logoUri: String?) =
        runAction("Business profile updated") {
            container.propertyTenancyService.updateBusinessProfile(requireCtx(), name, branch, phone, email, address, cr, logoUri)
        }

    fun createProperty(name: String, code: String, type: String, address: String, city: String, notes: String, photoUri: String? = null) =
        runAction("Property created") {
            container.propertyTenancyService.createProperty(requireCtx(), name, code, type, address, city, notes, photoUri)
        }

    fun addBuilding(propertyId: String, name: String, code: String, floorsCount: Int) =
        runAction("Building added") {
            container.propertyTenancyService.addBuilding(requireCtx(), propertyId, name, code, floorsCount)
        }

    fun addFloor(propertyId: String, buildingId: String, floorNum: Int, label: String) =
        runAction("Floor added") {
            container.propertyTenancyService.addFloor(requireCtx(), propertyId, buildingId, floorNum, label)
        }

    fun addRoom(
        propertyId: String, buildingId: String, floorId: String, roomNumber: String,
        roomType: String, capacity: Int, furnished: String, rateFils: Long,
        amenities: String, keyCount: Int, accessCard: String, furniture: String
    ) = runAction("Room added") {
        container.propertyTenancyService.addRoom(
            requireCtx(), propertyId, buildingId, floorId, roomNumber,
            roomType, capacity, furnished, rateFils, amenities, keyCount, accessCard, furniture
        )
    }

    fun updateRoomStatus(roomId: String, status: String, notes: String = "") =
        runAction("Room status updated to $status") {
            container.propertyTenancyService.updateRoomStatus(requireCtx(), roomId, status, notes)
        }

    fun reserveRoom(roomId: String, customerId: String, depositFils: Long, daysValid: Int, notes: String) =
        runAction("Room reserved") {
            container.propertyTenancyService.reserveRoom(requireCtx(), roomId, customerId, depositFils, daysValid, notes)
        }

    fun recordInspection(propertyId: String, roomId: String?, type: String, summary: String, keys: Int, cards: Int, repair: Boolean) =
        runAction("Inspection recorded") {
            container.propertyTenancyService.recordInspection(requireCtx(), propertyId, roomId, type, summary, keys, cards, repair)
        }

    // --- Tenants & Agreements ---
    fun createCustomer(
        fullName: String, phone: String, email: String, qid: String,
        passport: String, nationality: String, employer: String, emergency: String, notes: String
    ) = runAction("Tenant registered") {
        container.propertyTenancyService.createCustomer(requireCtx(), fullName, phone, email, qid, passport, nationality, employer, emergency, notes)
    }

    fun addFamilyMember(customerId: String, name: String, relation: String, qid: String, phone: String) =
        runAction("Family member added") {
            container.propertyTenancyService.addFamilyMember(requireCtx(), customerId, name, relation, qid, phone)
        }

    fun provisionCustomerPortalUser(customerId: String, email: String, password: String) =
        runAction("Customer Portal login created") {
            container.authService.createCustomerPortalUser(requireCtx(), customerId, email, password)
        }

    fun createAgreement(
        roomId: String, customerId: String, billingCycle: String, rentFils: Long,
        discountFils: Long, depositFils: Long, startEpoch: Long, endEpoch: Long, noticeDays: Int, notes: String
    ) = runAction("Agreement activated") {
        container.propertyTenancyService.createAndActivateAgreement(
            requireCtx(), roomId, customerId, billingCycle, rentFils, discountFils, depositFils, startEpoch, endEpoch, noticeDays, notes
        )
    }

    fun renewAgreement(agreementId: String, newEndEpoch: Long, newRentFils: Long) =
        runAction("Agreement renewed") {
            container.propertyTenancyService.renewAgreement(requireCtx(), agreementId, newEndEpoch, newRentFils)
        }

    fun terminateAgreement(agreementId: String, reason: String) =
        runAction("Agreement terminated & room marked vacant") {
            container.propertyTenancyService.terminateAndMoveOut(requireCtx(), agreementId, reason)
        }

    fun generateMonthlyRent() = runAction("Monthly rent generated & posted to GL") {
        val count = container.propertyTenancyService.generateRentForPeriod(requireCtx())
        if (count == 0) throw IllegalStateException("No new rent due for current period (already billed or no active agreements).")
    }

    // --- Payments, Cheques & Deposits ---
    fun receivePayment(
        customerId: String, agreementId: String?, amountFils: Long, method: String,
        bankAccountId: String?, rentIds: List<String>, notes: String
    ) = runAction("Payment confirmed & receipt issued") {
        container.rentPaymentChequeService.receiveConfirmedPayment(
            requireCtx(), customerId, agreementId, amountFils, method, bankAccountId, rentIds, notes
        )
    }

    fun reversePayment(paymentId: String, reason: String) =
        runAction("Payment reversed with reversing journal") {
            container.rentPaymentChequeService.reverseOrRefundPayment(requireCtx(), paymentId, reason)
        }

    fun registerCheque(
        customerId: String, agreementId: String?, linkedRentId: String?, chequeNumber: String,
        bankName: String, amountFils: Long, chequeDateEpoch: Long, payerName: String, payeeName: String
    ) = runAction("Cheque registered (pending clearance)") {
        container.rentPaymentChequeService.registerCheque(
            requireCtx(), customerId, agreementId, linkedRentId, chequeNumber, bankName, amountFils, chequeDateEpoch, payerName, payeeName
        )
    }

    fun transitionCheque(chequeId: String, newStatus: String, bankRef: String = "", bounceReason: String = "") =
        runAction("Cheque status updated to $newStatus") {
            container.rentPaymentChequeService.transitionChequeStatus(requireCtx(), chequeId, newStatus, bankRef, bounceReason)
        }

    fun replaceCheque(bouncedChequeId: String, newNumber: String, newBank: String, newDateEpoch: Long) =
        runAction("Replacement cheque registered") {
            container.rentPaymentChequeService.replaceBouncedCheque(requireCtx(), bouncedChequeId, newNumber, newBank, newDateEpoch)
        }

    fun recordDepositTransaction(depositId: String, type: String, amountFils: Long, reason: String) =
        runAction("Security deposit $type posted") {
            container.rentPaymentChequeService.recordDepositTransaction(requireCtx(), depositId, type, amountFils, reason)
        }

    // --- Accounting ---
    fun postManualJournal(description: String, debitCode: String, creditCode: String, amountFils: Long) =
        runAction("Journal entry posted") {
            container.accountingService.postManualJournal(
                requireCtx(), description, null,
                listOf(
                    JournalLineInput(debitCode, amountFils, 0L, description),
                    JournalLineInput(creditCode, 0L, amountFils, description)
                )
            )
        }

    fun reverseJournal(journalId: String, reason: String) =
        runAction("Journal reversed") {
            container.accountingService.reverseJournalEntry(requireCtx(), journalId, reason)
        }

    fun createBankAccount(bankName: String, title: String, iban: String, openingFils: Long) =
        runAction("Bank account added") {
            container.accountingService.createBankAccount(requireCtx(), bankName, title, iban, openingFils)
        }

    fun reconcileBank(bankId: String, statementBalanceFils: Long, notes: String) =
        runAction("Bank reconciliation recorded") {
            container.accountingService.reconcileBankAccount(requireCtx(), bankId, statementBalanceFils, notes)
        }

    fun recordIncome(category: String, description: String, amountFils: Long, toBank: Boolean) =
        runAction("Income recorded") {
            container.accountingService.recordIncome(requireCtx(), null, category, description, amountFils, toBank)
        }

    fun recordExpense(category: String, vendor: String, description: String, amountFils: Long, method: String) =
        runAction("Expense recorded") {
            container.accountingService.recordExpense(requireCtx(), null, category, vendor, description, amountFils, method)
        }

    fun replenishPettyCash(amountFils: Long, notes: String) =
        runAction("Petty cash replenished") {
            container.accountingService.replenishPettyCash(requireCtx(), amountFils, notes)
        }

    fun closeFiscalPeriod(periodCode: String) =
        runAction("Fiscal period $periodCode closed") {
            container.accountingService.closeFiscalPeriod(requireCtx(), periodCode)
        }

    fun reopenFiscalPeriod(periodCode: String, reason: String) =
        runAction("Fiscal period $periodCode reopened") {
            container.accountingService.reopenFiscalPeriod(requireCtx(), periodCode, reason)
        }

    // --- Workforce, Maintenance & Inventory ---
    fun createDepartment(name: String, desc: String) =
        runAction("Department created") {
            container.workforceMaintenanceInventoryService.createDepartment(requireCtx(), name, desc)
        }

    fun addEmployee(name: String, title: String, deptId: String?, phone: String, email: String, qid: String, location: String) =
        runAction("Employee onboarded") {
            container.workforceMaintenanceInventoryService.addEmployee(requireCtx(), name, title, deptId, phone, email, qid, location, "08:00 - 17:00 AST")
        }

    fun provisionStaffLogin(employeeId: String, fullName: String, email: String, password: String, roleName: String) =
        runAction("Staff portal login provisioned") {
            container.authService.createStaffUser(requireCtx(), employeeId, fullName, email, password, roleName, emptyList())
        }

    fun deactivateEmployee(employeeId: String, reason: String) =
        runAction("Employee deactivated (work history preserved)") {
            container.workforceMaintenanceInventoryService.deactivateEmployee(requireCtx(), employeeId, reason)
        }

    fun recordAttendance(employeeId: String, status: String, overtimeMins: Int, notes: String) =
        runAction("Attendance saved") {
            container.workforceMaintenanceInventoryService.recordAttendance(requireCtx(), employeeId, status, overtimeMins, notes)
        }

    fun createComplaint(propertyId: String, roomId: String?, customerId: String?, title: String, desc: String, category: String, priority: String, preventive: Boolean) =
        runAction("Complaint / maintenance ticket created") {
            container.workforceMaintenanceInventoryService.createComplaint(requireCtx(), propertyId, roomId, customerId, title, desc, category, priority, preventive)
        }

    fun assignTechnician(complaintId: String, techEmployeeId: String) =
        runAction("Technician assigned") {
            container.workforceMaintenanceInventoryService.assignTechnician(requireCtx(), complaintId, techEmployeeId)
        }

    fun submitWorkReport(
        complaintId: String, techId: String, summary: String, labourMins: Int,
        labourFils: Long, beforeUri: String?, afterUri: String?, itemId: String?, locationId: String?, qty: Long
    ) = runAction("Work report & materials recorded") {
        container.workforceMaintenanceInventoryService.submitWorkReportAndMaterials(
            requireCtx(), complaintId, techId, summary, labourMins, labourFils, beforeUri, afterUri, itemId, locationId, qty
        )
    }

    fun approveJob(complaintId: String, decision: String, notes: String) =
        runAction("Job $decision") {
            container.workforceMaintenanceInventoryService.approveMaintenanceJob(requireCtx(), complaintId, decision, notes)
        }

    fun customerConfirmComplaint(complaintId: String) =
        runAction("Completion confirmed") {
            container.workforceMaintenanceInventoryService.customerConfirmComplaint(requireCtx(), complaintId)
        }

    fun createSupplier(name: String, contact: String, phone: String, email: String, cr: String) =
        runAction("Supplier added") {
            container.workforceMaintenanceInventoryService.createSupplier(requireCtx(), name, contact, phone, email, cr)
        }

    fun createItem(categoryName: String, itemName: String, sku: String, unit: String, reorderLevel: Long) =
        runAction("Inventory item added") {
            container.workforceMaintenanceInventoryService.createItemCategoryAndItem(requireCtx(), categoryName, itemName, sku, unit, reorderLevel)
        }

    fun createPurchaseOrder(supplierId: String, propertyId: String?, itemId: String, qty: Long, unitCostFils: Long, notes: String) =
        runAction("Purchase order created") {
            container.workforceMaintenanceInventoryService.createPurchaseOrder(requireCtx(), supplierId, propertyId, itemId, qty, unitCostFils, notes)
        }

    fun approvePurchase(purchaseId: String) =
        runAction("Purchase approved") {
            container.workforceMaintenanceInventoryService.approvePurchase(requireCtx(), purchaseId, "Owner approved")
        }

    fun receiveGoods(purchaseId: String, locationId: String, invoiceNum: String) =
        runAction("Goods received & stock valuation updated") {
            container.workforceMaintenanceInventoryService.receiveGoods(requireCtx(), purchaseId, locationId, invoiceNum)
        }

    fun paySupplier(purchaseId: String, amountFils: Long, method: String) =
        runAction("Supplier payment posted") {
            container.workforceMaintenanceInventoryService.paySupplier(requireCtx(), purchaseId, amountFils, method)
        }

    fun recordStockCount(itemId: String, locationId: String, countedQty: Long) =
        runAction("Physical stock count posted") {
            container.workforceMaintenanceInventoryService.recordStockCount(requireCtx(), itemId, locationId, countedQty)
        }

    // --- Documents, Notifications, Control & Backups ---
    fun uploadVaultDocument(uri: Uri, entityType: String, entityId: String, category: String, title: String) =
        runAction("Document stored in private encrypted vault") {
            container.governanceBackupService.storePickedDocument(requireCtx(), uri, entityType, entityId, category, title)
        }

    fun logDocumentPreview(docId: String) {
        val ctx = _uiState.value.authContext ?: return
        viewModelScope.launch {
            try {
                container.governanceBackupService.logDocumentAccess(ctx, docId, "PREVIEW")
            } catch (e: Exception) {
                _uiState.update { it.copy(bannerError = e.message) }
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { container.governanceBackupService.markNotificationRead(id) }
    }

    fun createBackup(passphrase: String) = runAction("Encrypted backup created") {
        container.governanceBackupService.createEncryptedBackup(requireCtx(), passphrase)
    }

    fun verifyRestoreBackup(backupId: String, passphrase: String) {
        val ctx = requireCtx()
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null) }
            try {
                val res = container.governanceBackupService.verifyAndTestRestoreBackup(ctx, backupId, passphrase)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastBackupVerification = res,
                        bannerSuccess = "Restore verification passed: ${res.verifiedRecordCount} records, GL balanced=${res.isBalanced}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, bannerError = "Restore verification failed: ${e.message}") }
            }
        }
    }

    fun setUserStatus(targetUserId: String, status: String) =
        runAction("User status updated to $status") {
            container.authService.setUserAccountStatus(requireCtx(), targetUserId, status)
        }

    fun configureIntegration(providerType: String, providerName: String, handle: String, enabled: Boolean) =
        runAction("Integration settings saved") {
            container.governanceBackupService.configureIntegration(requireCtx(), providerType, providerName, handle, enabled)
        }

    fun submitAccountClosureRequest(reason: String) =
        runAction("Account closure request submitted for Owner review") {
            container.authService.submitAccountClosureRequest(requireCtx(), reason)
        }

    private fun requireCtx(): AuthContext =
        _uiState.value.authContext ?: throw IllegalStateException("Session required")

    private fun runAction(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, bannerError = null, bannerSuccess = null) }
            try {
                block()
                _uiState.update { it.copy(isBusy = false, bannerSuccess = successMessage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, bannerError = e.message ?: "Operation failed") }
            }
        }
    }
}
