package com.example.domain.security

object SuperAdminConfig {
    const val SUPER_ADMIN_EMAIL = "jishanansari998863@gmail.com"
    const val ROLE_SUPER_ADMIN = "SUPER_ADMIN"

    // Only these roles may ever be requested by or assigned to standard users
    val PUBLIC_REQUESTABLE_ROLES = listOf("OWNER", "EMPLOYEE", "CUSTOMER")

    const val GRANT_ACTIVE = "ACTIVE"
    const val GRANT_PENDING = "PENDING_APPROVAL"
    const val GRANT_REJECTED = "REJECTED"
    const val GRANT_SUSPENDED = "SUSPENDED"
    const val GRANT_REVOKED = "REVOKED"

    const val DEVICE_ACTIVE = "ACTIVE"
    const val DEVICE_REVOKED_LOST = "REVOKED_LOST"

    fun isSuperAdminEmail(email: String?): Boolean =
        email?.trim()?.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) == true
}
