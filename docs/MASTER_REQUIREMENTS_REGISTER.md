# ONE HOME PRO — Master Requirements Register & Traceability Matrix

- **Product Name**: ONE HOME PRO (Qatar Business Edition)
- **Primary Currency / Timezone**: `QAR` (stored internally as integer `Long` fils = 1/100 QAR) / `Asia/Qatar`
- **Languages**: English (`EN`), Arabic (`AR` with true RTL layout), Hindi/Hinglish (`HI`)

## Requirement Traceability Summary

| ID | Category | Requirement | Implementation Target | Honest Status |
|---|---|---|---|---|
| REQ-001 | Branding & Identity | Preserve exact name `ONE HOME PRO`, official logo as adaptive app icon + mipmap PNGs + splash screen emblem | `strings.xml`, `metadata.json`, `mipmap-*`, `SplashAndAuthScreens.kt` | Implemented & Compiled |
| REQ-002 | Clean Data Policy | Zero fake customers, tenants, rooms, payments, employees, or purchases; honest empty states | `OneHomeProViewModel`, `CommonComponents.kt`, `WelcomeSetupScreen` | Implemented & Tested |
| REQ-003 | Multi-Platform Target | Android app (Kotlin + Compose + Material 3); native iOS / Windows / macOS / Web parity | Android implemented; iOS/macOS/Windows/Web SDKs unavailable in Linux Android container | Android Implemented; Other Platforms Blocked |
| REQ-004 | Relational Schema | Full 6-phase 83-table relational schema + governance tables + PostgreSQL DDL equivalent | `Phase1*..Phase6*Entities.kt`, `GovernanceEntities.kt`, `POSTGRESQL_SCHEMA_AND_ER.md` | Implemented & KSP Compiled |
| REQ-005 | Authentication & Crypto | PBKDF2-HMAC-SHA256 (120k iterations), brute-force lockout (5 attempts), MFA TOTP + recovery codes, new device verification, forgot/reset password | `CryptoUtil.kt`, `AuthService.kt`, `SplashAndAuthScreens.kt` | Implemented & Tested |
| REQ-006 | Session & Token Lifecycle | Revocable hashed `Remember Me` device session tokens, session list, single revoke, logout all devices | `SessionStore.kt`, `AuthService.kt`, `AccountSecurityScreen` | Implemented & Tested |
| REQ-007 | Multi-Tenant RBAC & Isolation | Business branch isolation, property scope restriction, RBAC roles (`OWNER`, `PROPERTY_MANAGER`, `ACCOUNTANT`, `TECHNICIAN`, `CUSTOMER_PORTAL`), zero cross-customer leaks | `AuthorizationGuard.kt`, `OneHomeProViewModel.kt`, `CustomerPortalScreens.kt` | Implemented & Tested |
| REQ-008 | Property & Tenancy | Properties -> Buildings -> Floors -> Rooms, availability, reservations, inspections, overlap-protected agreements, renewals, move-outs, rent generation | `PropertyTenancyService.kt`, `PropertyTenancyScreens.kt` | Implemented & Tested |
| REQ-009 | Payments, Cheques & Deposits | Idempotent confirmed receipts, payment allocation & reversals, cheque lifecycle (`RECEIVED` -> `DEPOSITED` -> `CLEARED`/`BOUNCED`/replacement without inflating bank before clearing), GL 2100 security deposit liability | `RentPaymentChequeService.kt`, `RentPaymentScreens.kt` | Implemented & Tested |
| REQ-010 | Double-Entry Accounting | Chart of Accounts, Journal Entries (`DR == CR` in `withTransaction`), Trial Balance, P&L, Balance Sheet, Bank Accounts & Reconciliation, Cashbook, Petty Cash, Fiscal Period Close/Reopen | `AccountingService.kt`, `AccountingScreens.kt` | Implemented & Tested |
| REQ-011 | Workforce, Maintenance & Inventory | Departments, employee onboarding & non-destructive deactivation, attendance, complaints, job assignments, work reports + Photo Picker before/after photos, single-execution material usage, weighted-avg inventory, negative stock block | `WorkforceMaintenanceInventoryService.kt`, `WorkforceMaintenanceInventoryScreens.kt` | Implemented & Tested |
| REQ-012 | Private Vault & Notifications | Zero-permission Photo Picker & SAF PDF vault with SHA-256 checksum & access audit logs; local notification inbox | `GovernanceBackupService.kt`, `DocumentsAndReportsScreens.kt` | Implemented & Compiled |
| REQ-013 | 17-Report Hub | 17 operational & financial reports computed directly from SQLite/GL tables | `ReportsScreen` in `DocumentsAndReportsScreens.kt` | Implemented & Compiled |
| REQ-014 | Encrypted Backup & Restore | AES-256-GCM passphrase-encrypted JSON/SQLite snapshots + verified restore dry-run validating checksum & GL Trial Balance | `GovernanceBackupService.kt`, `OwnerControlScreen.kt` | Implemented & Tested |
| REQ-015 | Cloud Sync (Firebase) | Firestore real-time cloud sync / auth | User declined Firebase setup in AI Studio UI consent flow | Blocked (User Declined) |
| REQ-016 | External Gateways | Live SMS, SMTP, WhatsApp Business, Qatar bank open-banking feeds, online card PSPs | Configurable endpoints stored honestly as `PROVIDER_NOT_CONFIGURED` / `LOCAL_SANDBOX`; requires company API credentials & compliance review | Blocked (No Live Credentials) |
