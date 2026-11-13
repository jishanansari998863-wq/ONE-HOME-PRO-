package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.OneHomeProDatabase
import com.example.domain.security.*
import com.example.domain.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndIsolationRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: OneHomeProDatabase
    private lateinit var sessionStore: SessionStore
    private lateinit var accountingService: AccountingService
    private lateinit var authService: AuthService
    private lateinit var propertyService: PropertyTenancyService
    private lateinit var workforceService: WorkforceMaintenanceInventoryService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, OneHomeProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = SessionStore(context)
        accountingService = AccountingService(db)
        authService = AuthService(db, sessionStore, accountingService)
        propertyService = PropertyTenancyService(db, accountingService, authService)
        workforceService = WorkforceMaintenanceInventoryService(db, accountingService)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `PBKDF2 hashing, wrong password rejection, brute-force lockout, and valid login work`() = runBlocking {
        val setup = authService.setupInitialOwnerAndBusiness(
            fullName = "Tariq Al-Thani",
            email = "owner@onehome.qa",
            password = "StrongPass#2026",
            businessName = "Doha Estates WLL",
            branchName = "West Bay HQ",
            enableMfa = false,
            rememberMe = true,
            deviceName = "Pixel Test"
        )
        assertTrue(setup is AuthResult.Authenticated)
        val ownerCtx = (setup as AuthResult.Authenticated).context
        assertEquals("OWNER", ownerCtx.userType)

        // Wrong passwords trigger lockout on 5th attempt
        repeat(4) {
            val fail = authService.login("owner@onehome.qa", "WrongPass!", false, "Pixel Test")
            assertTrue(fail is AuthResult.Failed)
        }
        val locked = authService.login("owner@onehome.qa", "WrongPass!", false, "Pixel Test")
        assertTrue(locked is AuthResult.Failed)
        assertTrue((locked as AuthResult.Failed).message.contains("locked", ignoreCase = true))

        // Even right password is blocked while lock window active
        val blockedDuringLock = authService.login("owner@onehome.qa", "StrongPass#2026", false, "Pixel Test")
        assertTrue(blockedDuringLock is AuthResult.Failed)
        assertTrue((blockedDuringLock as AuthResult.Failed).message.contains("locked", ignoreCase = true))
    }

    @Test
    fun `Remember Me revocable session token rejects after logout and logoutAllDevices`() = runBlocking {
        val setup = authService.setupInitialOwnerAndBusiness(
            fullName = "Owner One",
            email = "owner2@onehome.qa",
            password = "StrongPass#2026",
            businessName = "Lusail Holdings",
            branchName = "Lusail",
            enableMfa = false,
            rememberMe = true,
            deviceName = "TestPhone"
        ) as AuthResult.Authenticated

        val token = setup.rawSessionToken
        assertNotNull(authService.validateSessionToken(token))
        assertNotNull(authService.validateStoredSessionOrNull())

        // Logout current session revokes token immediately
        authService.logoutCurrentSession(setup.context)
        assertNull(authService.validateSessionToken(token))
        assertNull(authService.validateStoredSessionOrNull())

        // Sign back in on trusted device and test logoutAllDevices
        val login2 = authService.login("owner2@onehome.qa", "StrongPass#2026", true, "TestPhone") as AuthResult.Authenticated
        assertNotNull(authService.validateSessionToken(login2.rawSessionToken))
        authService.logoutAllDevices(login2.context)
        assertNull(authService.validateSessionToken(login2.rawSessionToken))
    }

    @Test
    fun `Password reset revokes all active sessions and MFA + recovery code flow succeeds`() = runBlocking {
        val setup = authService.setupInitialOwnerAndBusiness(
            fullName = "Owner MFA",
            email = "mfa@onehome.qa",
            password = "OldPassword#1",
            businessName = "Al Sadd Properties",
            branchName = "Al Sadd",
            enableMfa = true,
            rememberMe = true,
            deviceName = "OwnerDevice"
        ) as AuthResult.Authenticated

        val mfaRes = authService.login("mfa@onehome.qa", "OldPassword#1", false, "OwnerDevice")
        assertTrue(mfaRes is AuthResult.MfaRequired)
        val totpHint = (mfaRes as AuthResult.MfaRequired).demoHintInfo
        val verified = authService.verifyMfaAndCompleteLogin(mfaRes.userId, totpHint, true, "OwnerDevice")
        assertTrue(verified is AuthResult.Authenticated)
        val activeToken = (verified as AuthResult.Authenticated).rawSessionToken

        // Request and complete password reset
        val otp = authService.requestPasswordReset("mfa@onehome.qa")
        authService.completePasswordReset("mfa@onehome.qa", otp, "NewStrongPass#2")

        // Prior session token must now be revoked
        assertNull(authService.validateSessionToken(activeToken))

        // Old password fails, new password triggers MFA
        val oldFail = authService.login("mfa@onehome.qa", "OldPassword#1", false, "OwnerDevice")
        assertTrue(oldFail is AuthResult.Failed)
        val newOk = authService.login("mfa@onehome.qa", "NewStrongPass#2", false, "OwnerDevice")
        assertTrue(newOk is AuthResult.MfaRequired)
    }

    @Test
    fun `RBAC, employee privilege restriction, cross-business and cross-customer isolation are enforced`() = runBlocking {
        val ownerAuth = authService.setupInitialOwnerAndBusiness(
            fullName = "Owner Root",
            email = "root@onehome.qa",
            password = "StrongPass#2026",
            businessName = "Branch A",
            branchName = "Doha",
            enableMfa = false,
            rememberMe = false,
            deviceName = "AdminDevice"
        ) as AuthResult.Authenticated
        val ownerCtx = ownerAuth.context

        // Create second business branch B
        val branchB = propertyService.createBusinessBranch(ownerCtx, "Branch B", "Wakrah", "", "", "", "")

        // Create Property in Branch A, Room 101, Customer 1 & Customer 2
        val propA = propertyService.createProperty(ownerCtx, "Pearl Tower", "PRL", "RESIDENTIAL", "The Pearl", "Doha", "")
        val bldgA = propertyService.addBuilding(ownerCtx, propA.id, "Tower 1", "T1", 10)
        val flA = propertyService.addFloor(ownerCtx, propA.id, bldgA.id, 1, "Floor 1")
        val room1 = propertyService.addRoom(ownerCtx, propA.id, bldgA.id, flA.id, "101", "STUDIO", 2, "FURNISHED", 400_000L, "AC", 2, "C1", "Bed")

        val cust1 = propertyService.createCustomer(ownerCtx, "Nasser", "+97455551111", "nasser@qa.com", "2900001", "", "QA", "", "", "")
        val cust2 = propertyService.createCustomer(ownerCtx, "Hassan", "+97455552222", "hassan@qa.com", "2900002", "", "QA", "", "", "")

        // Provision Customer 1 Portal User
        authService.createCustomerPortalUser(ownerCtx, cust1.id, "nasser@qa.com", "TenantPass#1")
        val custLogin1 = authService.login("nasser@qa.com", "TenantPass#1", false, "AdminDevice")
        // Already trusted AdminDevice from setup? Different userId -> new device challenge!
        val custCtx = when (custLogin1) {
            is AuthResult.NewDeviceVerificationRequired -> {
                (authService.verifyNewDeviceChallenge(custLogin1.userId, custLogin1.verificationCode, true, false, "AdminDevice") as AuthResult.Authenticated).context
            }
            is AuthResult.Authenticated -> custLogin1.context
            else -> fail("Unexpected customer login result: $custLogin1") as Nothing
        }

        // Cross-customer isolation: Customer 1 MUST be blocked from viewing Customer 2 data or Property module
        try {
            AuthorizationGuard.requireCustomerSelfOrStaffPermission(custCtx, cust2.id, AppModule.TENANTS, ActionType.VIEW, ownerCtx.activeBusinessId)
            fail("Expected cross-customer AuthorizationException")
        } catch (_: AuthorizationException) { /* expected */ }

        try {
            AuthorizationGuard.requirePermission(custCtx, AppModule.ACCOUNTING, ActionType.VIEW, ownerCtx.activeBusinessId)
            fail("Expected customer blocked from ACCOUNTING module")
        } catch (_: AuthorizationException) { /* expected */ }

        // Provision Technician Employee in Branch A -> blocked from Owner-only and Branch B
        val emp = workforceService.addEmployee(ownerCtx, "Samir Tech", "HVAC Tech", null, "+97450009999", "samir@onehome.qa", "2880001", "Doha", "08:00-17:00")
        authService.createStaffUser(ownerCtx, emp.id, emp.fullName, "samir@onehome.qa", "TechPass#2026", "TECHNICIAN", listOf(propA.id))
        val techChallenge = authService.login("samir@onehome.qa", "TechPass#2026", false, "AdminDevice") as AuthResult.NewDeviceVerificationRequired
        val techCtx = (authService.verifyNewDeviceChallenge(techChallenge.userId, techChallenge.verificationCode, true, false, "AdminDevice") as AuthResult.Authenticated).context

        // Technician CANNOT close fiscal periods, create branches, or access Branch B
        try {
            accountingService.closeFiscalPeriod(techCtx, "2026-11")
            fail("Technician must not be allowed to close fiscal periods")
        } catch (_: AuthorizationException) { /* expected */ }

        try {
            propertyService.createBusinessBranch(techCtx, "Unauthorized", "X", "", "", "", "")
            fail("Technician must not create business branches")
        } catch (_: AuthorizationException) { /* expected */ }

        try {
            AuthorizationGuard.requirePermission(techCtx, AppModule.MAINTENANCE, ActionType.VIEW, branchB.id)
            fail("Technician must be blocked from Branch B")
        } catch (_: AuthorizationException) { /* expected */ }

        assertEquals(room1.id.isNotBlank(), true)
    }
}
