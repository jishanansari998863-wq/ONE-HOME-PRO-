# Security Summary

ONE HOME PRO (Qatar Business Edition) underwent a complete security, privacy, performance, and reliability audit with strict preservation of the existing Super Admin authority (`ansarisahabaj980@gmail.com`), Multi-Factor Authentication (MFA), Account Recovery security, and business records.

## Key Protections Verified

- **Server-Side Zero-Trust Authorization & Firestore Rules**: `firestore.rules` enforces default-deny across all collections, verified Google email checks (`email_verified == true`), immutable Super Admin ownership (`platform_config/global`), non-self-approving `access_requests`, Super Admin–only `access_grants` and `businesses`, revoked-device blocking on session creation, immutable audit logs, and strict business isolation on `cloud_snapshots`.
- **Encrypted Cloud Sync (AES-256-GCM)**: Full business snapshots (`CloudDataSyncService`) are compressed and encrypted client-side with AES-256-GCM (`OHPENC1:` envelope) using business-derived key material from Firestore before upload, with SHA-256 checksum validation on restore.
- **Hardware-Backed Vault Protection**: Local private documents (`private_vault/*.ohp`) and backups (`encrypted_backups/*.ohp`) are AES-256-GCM encrypted, and `.vault_master_key` is wrapped using `AndroidKeyStore` AES-256-GCM (`one_home_pro_vault_kek`, `KSGCM1:` prefix) with restricted owner-only file permissions (`0600`) and transparent migration for legacy plaintext keys.
- **Super Admin Brute-Force Lockout & Clean MFA Flow**: Consecutive failed Super Admin master password, MFA, initial kit reissue, or recovery verification attempts lock out progressively at 5 failures (starting at 5 minutes) and log a `SUPER_ADMIN_BRUTE_FORCE_LOCK` audit event. Live TOTP codes and reset OTPs are never surfaced in UI hints or logs, and initial Base32 TOTP QR/secret setup reissues safely only before first MFA completion.
- **Spreadsheet Formula Injection Protection**: CSV exports (`ReportExporter`) prefix leading `=`, `+`, `-`, `@`, tab, and carriage return characters (including after leading whitespace) with a single quote so spreadsheet applications treat exported cells strictly as literal text.
- **Screen Privacy & Zero Auto-Backup Leakage**: `MainActivity` enables `WindowManager.LayoutParams.FLAG_SECURE` on physical/emulator Android devices to block unauthorized screenshots and recent-apps thumbnails. `AndroidManifest.xml` disables cloud/device backups (`android:allowBackup="false"`) and excludes databases, files, and preferences in `data_extraction_rules.xml`.
- **UI Concurrency & Performance Hardening**: Financial report recalculations (Trial Balance, P&L, Balance Sheet) are conflated with a 60ms coalesce window to prevent redundant Room queries during rapid ledger bursts, primary actions guard against double-submission via `uiState.isBusy`, and R8 shrinking/obfuscation (`isMinifyEnabled = true`, `isShrinkResources = true`) is enabled for Release builds alongside `Log.v`/`Log.d` stripping.

## Remaining External / Hardware Items

- **Google Play Release Signing**: Release APK/AAB signing requires configuring CI secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) with the Play Upload Keystore (absent locally).
- **Standalone Node Rules Suite (`firestore.test.js`)**: Runs in GitHub Actions where `@firebase/rules-unit-testing` is installed; locally, Firestore security rules are verified through the 5 JUnit Emulator tests in `SuperAdminRulesTest`.
- **Physical Device Profiling**: CameraX preview under low light, sustained memory/battery profiling, and Android 14/15/16 OEM hardware validation remain pending real device QA.

---

## PoC Verification

All verification tests passed locally (`45/45` tests across `10` test suites, `0` failures, `0` errors, `0` skipped) via `firebase emulators:exec --project=demo-no-project --only auth,firestore "gradle :app:testDebugUnitTest --max-workers=2 --console=plain"`:

### 1. CSV Formula Injection Neutralization (`SecurityAuditPocRobolectricTest` — PoC 1)
- **Attack Blocked**: Exporting malicious tenant, property, or report strings starting with `=CMD|...`, `   =SUM(...)`, `+1+1`, `-2+3`, `@SUM(1,2)`, or `\t=IMPORTXML(...)`.
- **Proof**: Verified that `ReportExporter.buildCsvReport` prefixes every dangerous payload with `'` inside quoted CSV cells so Excel/Sheets cannot evaluate formulas.

### 2. Cloud Snapshot Plaintext Exposure Prevention (`SecurityAuditPocRobolectricTest` — PoC 2 & `SuperAdminRulesTest`)
- **Attack Blocked**: Reading raw gzipped business JSON (`H4sI...`) from Firestore cloud snapshots or decrypting a snapshot with the wrong business key; cross-business read/overwrite of `cloud_snapshots`.
- **Proof**: Verified `CloudDataSyncService.syncBusinessToCloud` produces an `OHPENC1:` AES-256-GCM ciphertext containing zero plaintext property names, customer names, QIDs, or passport numbers; wrong-key decryption throws; clean-database restore reconstructs exact records and fails closed on checksum mismatch; Firestore Emulator confirms `PERMISSION_DENIED` on cross-business snapshot reads/writes.

### 3. MFA Secret Integrity & Zero OTP UI Leakage (`SecurityAuditPocRobolectricTest` — PoC 3 & `SuperAdminSecurityRobolectricTest`)
- **Attack Blocked**: Returning a static 6-digit OTP instead of a Base32 TOTP authenticator secret or exposing live TOTP/reset OTP codes in `AuthResult.MfaRequired.demoHintInfo` or `pendingResetOtpNotice`.
- **Proof**: Verified `AuthService.enableMfaAndGenerateRecoveryCodes` returns a valid RFC 4648 Base32 TOTP secret + 6 recovery codes, authenticator verification passes, `demoHintInfo` is empty, `pendingResetOtpNotice` contains no 6-digit code, ZXing QR encoding/decoding matches exact `otpauth://totp/...` URI, and pending initial MFA kit reissue self-locks permanently after first successful verification (`SuperAdminRulesTest` & `SuperAdminSecurityRobolectricTest`).

### 4. Super Admin Brute-Force Lockout (`SecurityAuditPocRobolectricTest` — PoC 4)
- **Attack Blocked**: Unlimited automated guessing of Super Admin Master Password or MFA challenge codes.
- **Proof**: Verified 4 bad attempts fail safely, the 5th bad attempt triggers progressive lockout (`AuthorizationException`) and records `SUPER_ADMIN_BRUTE_FORCE_LOCK`, and even valid credentials are rejected while the lockout window is active.

### 5. End-to-End 20-Module & Double-Submit Guard Smoke (`SecurityAuditPocRobolectricTest` — PoC 5)
- **Attack Blocked**: Duplicate entity creation on rapid double-tap (`createProperty` while `isBusy` is active) and cross-module data corruption across Property, Building, Floor, Room, Inspection, Tenant, Family, Reservation, Agreement, Monthly Rent, Payment, Balanced Trial Balance, HR/Department/Team/Attendance, Maintenance, Procurement/Inventory, AES-GCM Local Vault, Operational Alerts, PDF Export, and EN/AR/HI/Hinglish localization.
- **Proof**: Verified rapid duplicate submission creates exactly 1 property record, Trial Balance debits equal credits, disk vault files do not expose raw `%PDF` bytes, and decrypted bytes match SHA-256 checksums cleanly.

### Suite Summary (45 / 45 Passing)
- `AccountingAndOperationsRobolectricTest`: 6 passed (17.01s)
- `AdaptiveLayoutAndCameraRobolectricTest`: 3 passed (5.72s)
- `ComposeUiFlowRobolectricTest`: 5 passed (6.38s)
- `DocumentScannerAndVaultRobolectricTest`: 5 passed (4.65s)
- `ExampleRobolectricTest`: 1 passed (0.04s)
- `ExampleUnitTest`: 1 passed (0.00s)
- `SecurityAndIsolationRobolectricTest`: 5 passed (4.17s)
- `SecurityAuditPocRobolectricTest`: 5 passed (4.47s)
- `SuperAdminRulesTest` (Live Firebase Auth + Firestore Emulators): 5 passed (16.49s)
- `SuperAdminSecurityRobolectricTest`: 9 passed (14.63s)
