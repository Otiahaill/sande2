# PROJECT STATUS — sande

**App:** sande (Native Android Sales & Inventory System)  
**Package:** com.example.sande (decision pending — see DECISIONS.md)  
**Status:** Sprint start. Target: working MVP offline-first inventory app within 24 hours.  
**Blueprint:** NATIVE_ANDROID_INVENTORY_BLUEPRINT.md (compressed to M0–M7 stub)

## What is realistically achievable in 24 hours

| In scope (MVP) | Deferred to post-launch |
|---|---|
| Room local DB, insert-only inventory ledger | Full Firestore Security Rules emulator test suite |
| Jetpack Compose UI, Material 3 light/dark | 1-week field trial (M10) |
| Firebase Auth sign-in (email/pw), PermissionPolicy | Backup/restore, R8 release smoke test |
| Catalogue: products, categories, stock view | Scheduled Firestore exports |
| Receiving & stock adjustments | FCM push notifications |
| Sales recording: cart, atomic sale, void, history, receipt numbers | Multi-location stock pool |
| Outbox stub + sync status chip (offline-first behavior) | Price override by salesperson |
| APK build + device install + smoke test | Signed release APK (keystore backup) |

**Build route:** GitHub Actions CI (Route B). Local Gradle build not realistic — probe showed ARM (aarch64), no JDK, ~73 MB RAM.

## Current state

- Workspace scaffold created: `sande/` with `docs/`, `firebase/rules-tests/`, `.github/workflows/`
- No Gradle files, no source, no APK yet

## Open decisions (blocking)

| ID | Decision | Options | Default | Owner |
|---|---|---|---|---|
| U2 | GitHub repo visibility | Public / Private | Your call | Brian |
| — | Package name for sande | e.g. com.example.sande | com.example.sande | Brian |
| U1 | Negative stock policy | WARN / BLOCK / ALLOW | WARN | Brian |
