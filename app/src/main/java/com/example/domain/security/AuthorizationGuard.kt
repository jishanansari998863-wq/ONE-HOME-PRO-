package com.example.domain.security

enum class AppModule {
    DASHBOARD,
    PROPERTIES,
    TENANTS,
    AGREEMENTS,
    PAYMENTS,
    CHEQUES,
    DEPOSITS,
    ACCOUNTING,
    EMPLOYEES,
    MAINTENANCE,
    INVENTORY,
    DOCUMENTS,
    REPORTS,
    NOTIFICATIONS,
    SETTINGS,
    SECURITY,
    BACKUPS,
    CUSTOMER_PORTAL
}

enum class ActionType {
    VIEW,
    CREATE,
    EDIT,
    ARCHIVE,
    APPROVE,
    POST,
    REVERSE,
    EXPORT,
    RESTORE
}

data class AuthContext(
    val userId: String,
    val email: String,
    val fullName: String,
    val userType: String, // OWNER, EMPLOYEE, CUSTOMER
    val activeBusinessId: String,
    val allowedBusinessIds: Set<String>,
    val allowedPropertyIds: Set<String>, // empty means all properties in business if Owner or unrestricted role
    val restrictToAssignedProperties: Boolean,
    val customerId: String?,
    val employeeId: String?,
    val permissionKeys: Set<String>, // "MODULE:ACTION"
    val sessionId: String,
    val deviceId: String
) {
    fun hasPermission(module: AppModule, action: ActionType): Boolean {
        if (userType == "OWNER") return true
        return permissionKeys.contains("${module.name}:${action.name}")
    }

    fun isCustomerPortalOnly(): Boolean = userType == "CUSTOMER"
}

class AuthorizationException(message: String) : SecurityException(message)

object AuthorizationGuard {
    fun requirePermission(
        ctx: AuthContext,
        module: AppModule,
        action: ActionType,
        businessId: String = ctx.activeBusinessId,
        propertyId: String? = null
    ) {
        if (!ctx.allowedBusinessIds.contains(businessId)) {
            throw AuthorizationException("Cross-business access blocked for business=$businessId")
        }
        if (ctx.isCustomerPortalOnly() && module != AppModule.CUSTOMER_PORTAL) {
            throw AuthorizationException("Customer portal users cannot access module=${module.name}")
        }
        if (!ctx.hasPermission(module, action)) {
            throw AuthorizationException("Permission denied: ${module.name}:${action.name}")
        }
        if (propertyId != null && ctx.restrictToAssignedProperties &&
            ctx.userType != "OWNER" && !ctx.allowedPropertyIds.contains(propertyId)
        ) {
            throw AuthorizationException("Property scope violation for property=$propertyId")
        }
    }

    fun requireOwner(ctx: AuthContext, businessId: String = ctx.activeBusinessId) {
        if (ctx.userType != "OWNER") {
            throw AuthorizationException("Owner-only operation blocked for userType=${ctx.userType}")
        }
        if (!ctx.allowedBusinessIds.contains(businessId)) {
            throw AuthorizationException("Cross-business access blocked for owner")
        }
    }

    fun requireCustomerSelfOrStaffPermission(
        ctx: AuthContext,
        targetCustomerId: String,
        staffModule: AppModule,
        staffAction: ActionType,
        businessId: String
    ) {
        if (!ctx.allowedBusinessIds.contains(businessId)) {
            throw AuthorizationException("Cross-business access blocked")
        }
        if (ctx.isCustomerPortalOnly()) {
            if (ctx.customerId != targetCustomerId) {
                throw AuthorizationException("Cross-customer access blocked: cannot view customer=$targetCustomerId")
            }
            return
        }
        requirePermission(ctx, staffModule, staffAction, businessId)
    }

    fun defaultPermissionCatalog(): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        for (m in AppModule.entries) {
            for (a in ActionType.entries) {
                list.add(Triple(m.name, a.name, "${a.name} access for ${m.name}"))
            }
        }
        return list
    }
}
