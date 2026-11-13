# ONE HOME PRO — Build, Release, Multi-Platform Scope & Operations Guide

## 1. Environment & Platform Scope Disclosure
- **Verified & Built Platform**: **Android** (Kotlin, Jetpack Compose, Material 3, Room SQLite, DataStore Preferences, Min SDK 24 / Target SDK 36).
- **Cloud Backend Status**: Firebase Firestore / Cloud Auth integration is **Blocked** because Firebase setup was declined during interactive provisioning. The application runs as a complete offline-first local database engine with PBKDF2-HMAC-SHA256 authentication, RBAC, double-entry GL, and AES-256-GCM encrypted exportable backups.
- **Unbuilt Desktop / Apple / Web Targets**: Native **iOS**, **macOS**, **Windows**, and **Web** runtimes are **Blocked** in this Android-only Linux Gradle container (no Xcode, macOS, Windows SDK, or React/Flutter web bundler is installed). Sharing business logic via Kotlin Multiplatform (KMP) or a PostgreSQL + REST API backend is recommended when provisioning those targets in a multi-OS CI runner.

## 2. Android Debug & Release Signing Instructions
1. Export or clone the repository to a workstation with Android Studio / JDK 17+.
2. Generate your Google Play Upload Keystore:
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
3. Configure signing environment variables in CI/CD (never commit secrets):
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
4. Build signed App Bundle (`.aab`) for Google Play Console:
   ```bash
   gradle :app:bundleRelease
   ```
5. Enable Play App Signing, upload Privacy Policy URL (covering Qatar Law No. 13 of 2016 + Photo Picker usage + Account Closure workflow), and submit for review.

## 3. Encrypted Backup, Restore Verification & Rollback Runbook
- **Creating a Backup**: Owner opens `More -> Owner Control Center -> Encrypted Backups -> Create Backup`, enters a strong passphrase (>= 6 chars; recommended 14+ chars), and stores the AES-256-GCM file outside the device.
- **Testing Integrity**: Tap `Decrypt & Verify Restore` on any backup record to confirm AES-GCM tag authentication, SHA-256 manifest match, record count, and Trial Balance (`Total Debits == Total Credits`).
- **Pre-Upgrade Rollback**: Before any major database schema version bump, export an encrypted backup and verify restore integrity first. Retain previous `.aab` release track in Google Play Console staged rollout (10% -> 50% -> 100%).

## 4. Role Quick-Start Guide
- **Owner**: Complete initial `Welcome Setup` (sets Owner credentials + initial Company/Branch in `QAR` / `Asia/Qatar`), add real Properties/Buildings/Floors/Rooms, onboard Staff (`Workforce & HR`), assign RBAC roles, and close monthly Fiscal Periods.
- **Staff (Property Manager / Accountant / Technician)**: Sign in with Owner-provisioned credentials, verify unrecognized device challenge (if new device), complete MFA if enabled, and operate strictly within assigned modules/properties.
- **Customer / Tenant**: Sign in via Customer Portal credentials provisioned by Owner to view only personal agreements, room info, rent schedule, receipts, complaints, and personal document uploads.
