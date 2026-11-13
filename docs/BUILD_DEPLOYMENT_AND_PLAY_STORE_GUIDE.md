# ONE HOME PRO — Build, Release, Multi-Platform Scope & Operations Guide

## 1. Environment & Platform Scope Disclosure
- **Verified & Built Platform**: **Android** (Kotlin, Jetpack Compose, Material 3, Room SQLite, DataStore Preferences, Min SDK 24 / Target SDK 36) supporting phones (`NavigationBar`, compact grids) and tablets (`NavigationRail`, 4-column adaptive grids).
- **Android Debug APK**: Built locally via `gradle :app:assembleDebug` (`app/build/outputs/apk/debug/app-debug.apk`).
- **GitHub Actions CI & Release Workflow**: Configured in `/.github/workflows/android-apk-release.yml` to run `:app:testDebugUnitTest`, compile `:app:assembleDebug`, upload build/test artifacts, and publish `app-debug.apk` to **GitHub Releases** on `v*` tags or `workflow_dispatch`. *(Execution requires pushing this repository to GitHub; this container has no GitHub remote or push credentials.)*
- **Cloud Backend & Shared Database Status**: Firebase Firestore / Cloud Auth setup was **declined** by the user, and no hosted PostgreSQL/REST server credentials exist in this container. As a result, live cross-device Android ↔ iOS database synchronization is **Blocked** until a single shared backend (PostgreSQL + REST API per `/docs/POSTGRESQL_SCHEMA_AND_ER.md`, or Firebase Firestore/Auth if provisioned) is connected. Separate Android and iOS databases must **not** be created.
- **Apple iOS Support (iPhone / iPhone 11 / iOS Safari PWA / Xcode IPA)**: **Blocked** in this Linux Android build container because macOS, Xcode, iOS Simulator, Safari WebKit, and a hosted Web/PWA server are not available here. No `.ipa` has been built or claimed.

## 2. Roadmap for Future Shared Codebase + Native Xcode IPA / Web PWA
1. **Single Shared Backend & Database**: Deploy PostgreSQL using `/docs/POSTGRESQL_SCHEMA_AND_ER.md` behind a unified authenticated REST/GraphQL API (single source of truth for users, revocable sessions, RBAC permissions, accounting ledger, properties, leases, maintenance, and inventory across both Android and iOS).
2. **Shared Codebase (KMP + Compose Multiplatform)**: Extract `domain/` and `data/network/` into a shared Kotlin Multiplatform (`shared`) module targeting Android (`androidTarget`) and iOS (`iosArm64`, `iosSimulatorArm64`) so both apps share one codebase and one backend.
3. **macOS / Xcode IPA Build**: Open the generated iOS workspace in Xcode on macOS, configure Apple Developer signing/provisioning profiles, add `NSCameraUsageDescription` & `NSPhotoLibraryUsageDescription` in `Info.plist`, test on iPhone 11 (`6.1"`, `414×896pt` @2x) and tablets, then archive & export `.ipa`.
4. **iOS Safari PWA**: Only host a web/PWA build once connected to the **same** unified backend API (never an isolated local browser database), with `<meta name="apple-mobile-web-app-capable" content="yes">`, `viewport-fit=cover`, safe-area CSS insets, touch targets `>= 44pt`, and `<input type="file" accept="image/*" capture>` support.

## 3. Android Release Signing Instructions (Google Play Console)
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
5. Enable Play App Signing, upload Privacy Policy URL (covering Qatar Law No. 13 of 2016 + Photo Picker/Camera/SAF usage + Account Closure workflow), and submit for review.

## 4. Encrypted Backup, Restore Verification & Rollback Runbook
- **Creating a Backup**: Owner opens `More -> Owner Control Center -> Encrypted Backups -> Create Backup`, enters a strong passphrase (>= 6 chars; recommended 14+ chars), and stores the AES-256-GCM file outside the device.
- **Testing Integrity**: Tap `Decrypt & Verify Restore` on any backup record to confirm AES-GCM tag authentication, SHA-256 manifest match, record count, and Trial Balance (`Total Debits == Total Credits`).
- **Pre-Upgrade Rollback**: Before any major database schema version bump, export an encrypted backup and verify restore integrity first. Retain previous `.aab` release track in Google Play Console staged rollout (10% -> 50% -> 100%).
