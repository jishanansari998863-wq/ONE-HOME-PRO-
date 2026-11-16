package com.example

import android.app.Application
import com.example.data.db.OneHomeProDatabase
import com.example.data.remote.FirebaseCentralAuthorityGateway
import com.example.domain.security.CentralAuthorityGateway
import com.example.domain.security.SessionStore
import com.example.domain.service.*

class AppContainer(
    app: Application,
    val database: OneHomeProDatabase = OneHomeProDatabase.getInstance(app),
    val centralAuthority: CentralAuthorityGateway = FirebaseCentralAuthorityGateway(app),
    val sessionStore: SessionStore = SessionStore(app)
) {
    val accountingService: AccountingService = AccountingService(database)
    val authService: AuthService = AuthService(database, sessionStore, accountingService, centralAuthority)
    val propertyTenancyService: PropertyTenancyService =
        PropertyTenancyService(database, accountingService, authService)
    val rentPaymentChequeService: RentPaymentChequeService =
        RentPaymentChequeService(database, accountingService)
    val workforceMaintenanceInventoryService: WorkforceMaintenanceInventoryService =
        WorkforceMaintenanceInventoryService(database, accountingService)
    val governanceBackupService: GovernanceBackupService =
        GovernanceBackupService(app.applicationContext, database, accountingService)
    val cloudSyncService: CloudDataSyncService =
        CloudDataSyncService(database, centralAuthority, sessionStore)
}

class OneHomeProApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
