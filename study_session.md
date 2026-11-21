# sande-2-online — Study Session

**Last Updated:** 2026-11-21 15:21 (CI build debugging session — Phases 0-8 complete, APK build failing)

## Current Development Objective
**Phases 0-8 code COMPLETE and pushed to `main`.** Current blocker: GitHub Actions APK build fails with KSP/Room errors. Next task: fix the Room schema/type-converter issue so the APK builds, then download the artifact.

## Live Infrastructure (already provisioned)
| Resource | Name | ID / Status |
|----------|------|-------------|
| D1 database | `sande-platform-dev` | `06f5fadf-8b62-4ddb-9e95-421515291928` — 29 tables migrated |
| KV namespace | `sande-session-dev` | `32df0f0153674c059656882dd8eeb9c2` |
| KV namespace | `sande-ratelimit-dev` | `c7bf7ad612d64de5aafc09269023fd1b` |
| R2 bucket | `sande-backups-dev` | created (WEUR, Standard) |
| KV namespace | `sande-config` | **not yet created** — needed for connection manifest |

## Credentials
All required credentials are received and stored in **GitHub repo secrets** only
(`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `GOOGLE_CLIENT_ID`,
`GOOGLE_CLIENT_SECRET`). Never write the literal values into tracked files —
GitHub push protection blocks the push (already hit twice).

- Google Cloud Project ID: `even-turbine-515410-j7` (not a secret, safe to record)
- Android OAuth client: package `com.example.sande`, debug SHA-1
  `B6:A8:B3:F7:02:88:40:8B:F8:9D:CA:C7:E3:66:BC:37:E4:39:59:CE`
  (release keystore SHA-1 must be added to the same OAuth client later)

## Android debug signing fingerprint (for Google OAuth Android client)
```
SHA-1:   B6:A8:B3:F7:02:88:40:8B:F8:9D:CA:C7:E3:66:BC:37:E4:39:59:CE
SHA-256: F0:CC:35:51:79:82:ED:C1:81:1C:28:BF:49:06:0D:0C:B1:89:C9:AC:4E:EC:69:35:12:A1:35:B3:FC:F0:EB:C3
Package:  com.example.sande
```

## CI Debugging History (2026-11-21)
The `ci.yml` workflow originally had 5 jobs and failed with **0 jobs created** (invalid YAML — a stray ``` fence at EOF plus `defaults.run` + `working-directory` conflicts). A minimal test workflow proved GitHub Actions works fine. The workflow was rewritten to a single `build` job.

| Run | Commit | Error | Fix |
|-----|--------|-------|-----|
| #16-18 | various | Workflow YAML invalid → 0 jobs, immediate failure | Rewrote `ci.yml` to single `build` job (verified with minimal test workflow ✅) |
| #21 | `befdb00` | `GoogleSignInClient.kt:72` — `??` is not Kotlin (TS operator) | `??` → `?:` |
| #23 | `a025595` | `SessionManager.kt:52-53` — `VALID(expiresAt: Long)` invalid enum syntax | Changed to plain enum constants `VALID, EXPIRING_SOON, EXPIRED, UNKNOWN` |
| #24 | `512932f` | KSP: `AppDatabase` references a type that is not present | Pushed all files + `control-centre/package-lock.json` |
| #25 | `ee8808a` | KSP error (log URL expired before read) | `credential.idToken` explicit null check instead of `?:` |

### How to read CI logs (URLs expire in ~10 minutes)
```bash
# 1. Get the job id
curl -s "https://api.github.com/repos/Otiahaill/sande2/actions/runs/<RUN_ID>/jobs" | grep '"id"'

# 2. Immediately fetch the log redirect (get the 302 Location)
curl -sI "https://api.github.com/repos/Otiahaill/sande2/actions/jobs/<JOB_ID>/logs"

# 3. Follow the redirect and grep for errors — do this in the SAME command
curl -sL "<LOCATION_URL>" | grep -i "e: \|error:"
```

## The Remaining Blocker (exact error)
```
e: [ksp] [MissingType]: Element 'com.example.sande.data.db.AppDatabase' references a type that is not present
e: Error occurred in KSP, check log for detail
   > Compilation error. See log for more details
```
This means a type referenced by `AppDatabase` does not compile. Prime suspects, in order:

1. **`MovementType` enum needs a Room `@TypeConverter`** — `Movement.kt` has `val type: MovementType`. The original `sande2` project compiled, so a converter may have been lost or never registered. Check for a `Converters.kt` in `data/db/` and whether `AppDatabase` has a `@TypeConverters(...)` annotation.
2. **`exportSchema = true` without `room.schemaLocation`** — `app/build.gradle.kts` needs `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
3. Any entity/DAO that references a type that itself fails to compile.

### Next 5 concrete actions
1. `ls app/src/main/java/com/example/sande/data/db/` — check for `Converters.kt`
2. `grep -n "TypeConverters" app/src/main/java/com/example/sande/data/db/AppDatabase.kt`
3. `grep -n "schemaLocation\|ksp {" app/build.gradle.kts`
4. If no converter exists: create `data/db/Converters.kt` with a `MovementType` ↔ `String` converter and annotate `AppDatabase` with `@TypeConverters(Converters::class)`
5. Add `room.schemaLocation` to `app/build.gradle.kts`, commit, push, then immediately read CI logs with the 3-step command above

## What Has Been Completed

### Phase 0 — Stabilise Baseline ✅
- Removed `fallbackToDestructiveMigration()`; added explicit `MIGRATION_3_4` no-op migration
- Bumped Room DB version to 4, enabled `exportSchema = true`
- Migration tests, ledger balance test, checkout atomicity test
- Fixed dual receive write and receipt-number bug

### Phase 1 — Local Data Foundation ✅
- **13 entities:** SaleItem, SaleSession, Receipt, AuditEvent, Counter, BusinessProfile, ProductPriceHistory, StockMovement, MembershipMirror, DeviceRegistration, OutboxEntry, SyncCursor, SyncException
- **13 DAOs** + **13 repositories** matching them
- **CheckoutRepository:** atomic Room transaction (SaleSession + SaleItems + StockMovements + Receipt + Counter + AuditEvent + OutboxEntry); also recordReceiveAtomic / recordAdjustmentAtomic / voidSaleAtomic
- Retired StockAdjustment entity/DAO/repository into the StockMovement ledger
- Migrations 4→5 and 5→6

### Phase 2 — Receipts, Owner Visibility, Reports ✅
- ReceiptRenderer (text/HTML/thermal), ThermalPrinterFormatter (ESC/POS 58/80mm), PdfReceiptRenderer (A4/A5 + print dialog)
- Screens: ReceiptHistory, StockMovements, AuditEvents, Reports

### Phase 3 — Backend Foundation ✅
- Cloudflare Workers + D1 (29 tables across 3 migration files) + R2 + KV
- TenantDB (auto-injects `business_id`) + PlatformDB; auth middleware (JWT, Google ID token, role/device checks); rate limiting
- Routes: auth, business, memberships, devices, sync, admin, backup, health, scheduled
- Connection scripts: `cloudflare-setup.mjs`, `d1-migrate.mjs`, `set-secrets.mjs`, `deploy-worker.mjs`, `store-config.mjs`, `verify-connection.mjs`, `connect-all.mjs`

### Phase 4 — Android Auth & Onboarding ✅
- TokenManager (EncryptedSharedPreferences), GoogleSignInClient (Credential Manager), SessionManager (token refresh + offline lease)
- ApiClient (Ktor), ConnectionSettings + ConnectionResolver (runtime-configurable URL/client ID/device code)
- LegacyAdoptionManager (migrates `default-business` data)
- Screens: Welcome, GoogleSignIn, BusinessResolution, LegacyAdoption, RecoveryCodes, DeviceApproval, InvitationAccept, ConnectionSettings

### Phase 5 — Synchronisation ✅
- OutboxProcessor (push), PullApplier (pull), SyncWorker (WorkManager 15-min periodic), SyncStatusViewModel + SyncStatusScreen

### Phase 6 — Backup & Recovery ✅
- BackupManager: NDJSON payload + ZIP archive + manifest + SHA-256 checksum; BackupViewModel + BackupRestoreScreen; FileProvider configured
- Backend nightly cron backup → R2 with 30 daily + 12 monthly retention

### Phase 7 — SANDE Control Centre ✅
- Vanilla TypeScript SPA for Cloudflare Pages: Dashboard, Businesses, BusinessDetail, SyncExceptions, Login
- API client typing all 15 backend endpoints; UI component library
- TypeScript compiles clean (`tsc --noEmit` → 0 errors)

### Phase 8 — Hardening ✅
- securityHeadersMiddleware (CSP, X-Content-Type-Options, X-Frame-Options, X-XSS-Protection)
- minVersionMiddleware (426 Upgrade Required), tenantIsolationMiddleware
- Migration 0003 seeds `app_versions`

## Files Recently Modified
- `.github/workflows/ci.yml` — simplified to single `build` job (APK only)
- `app/src/main/java/com/example/sande/data/auth/GoogleSignInClient.kt` — `??`→`?:`, null check on idToken
- `app/src/main/java/com/example/sande/data/auth/SessionManager.kt` — enum constructor syntax
- `control-centre/src/*.ts` — import paths and type assertions fixed
- `backend/docs/DEPLOY.md` — secrets replaced with placeholders

## Important Discoveries
- Wrangler CLI cannot run on Android (workerd has no android-arm64 build) — connection scripts use Cloudflare REST API instead
- esbuild native binary cannot execute from Android shared storage — Worker bundling must happen in CI (Linux)
- Local Gradle builds on this device cannot download dependencies — **GitHub Actions is the canonical build path**
- GitHub push protection blocks commits containing real secrets — never write credentials to files
- GitHub Actions log URLs expire ~10 minutes after fetch — capture and grep in one command

## Current Blockers
1. **APK does not build in CI** — KSP `MissingType` on `AppDatabase` (see "Remaining Blocker" above for the exact fix sequence)
2. `sande-config` KV namespace not created yet (needed before `store-config.mjs`)
3. Backend Worker not deployed (blocked until CI is green)

## Exact Commands to Resume Work
```sh
cd /storage/emulated/0/Android/data/com.androidharness.app/files/workspace/sande-2-online

# 1. Inspect the Room setup
ls app/src/main/java/com/example/sande/data/db/
grep -n "TypeConverters" app/src/main/java/com/example/sande/data/db/AppDatabase.kt
grep -n "schemaLocation\|ksp {" app/build.gradle.kts

# 2. Fix, then push and watch CI
git add -A && git commit -m "fix: Room type converter and schema location" && git push origin main

# 3. Watch CI + grab logs immediately (URLs expire fast)
RUN_ID=$(curl -s "https://api.github.com/repos/Otiahaill/sande2/actions/runs?per_page=1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
JOB_ID=$(curl -s "https://api.github.com/repos/Otiahaill/sande2/actions/runs/$RUN_ID/jobs" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
curl -sIL "https://api.github.com/repos/Otiahaill/sande2/actions/jobs/$JOB_ID/logs" 2>/dev/null | grep -i "^location:" | awk '{print $2}' | tr -d '\r' | xargs -I{} curl -sL "{}" | grep -i "e: \|error:" | head -30

# 4. Download the APK artifact once green
curl -sL "https://api.github.com/repos/Otiahaill/sande2/actions/runs/$RUN_ID/artifacts" | grep -o '"archive_download_url":"[^"]*"'
```

## Decisions Awaiting Confirmation
- None blocking. All architectural decisions from `SANDE_Architecture_Plan.md` Appendix A are implemented as planned (14-day lease, 1 active device for owner with 2 allowed, staff cannot adjust stock, currency locked after first sale, etc.).

### Phase 0 — Stabilise Baseline (COMPLETED)
- Removed `fallbackToDestructiveMigration()` from AppDatabase; added explicit `MIGRATION_3_4` no-op migration
- Bumped Room DB version to 4, enabled `exportSchema = true`
- Migration tests (DatabaseMigrationTest.kt), ledger balance test (LedgerBalanceTest.kt), checkout atomicity test (CheckoutAtomicityTest.kt)
- Fixed dual receive write (removed StockAdjustmentRepository from StockViewModel)
- Fixed receipt-number bug (generateReceiptNumber takes businessId, uses countByBusiness)
- Test dependencies added: Truth, roomTesting, androidx-test, espresso

### Phase 1 — Local Data Foundation (COMPLETED)
- **Entities (13 new):** SaleItem, SaleSession, Receipt, AuditEvent, Counter, BusinessProfile, ProductPriceHistory, StockMovement, MembershipMirror, DeviceRegistration, OutboxEntry, SyncCursor, SyncException
- **DAOs (13 new):** SaleItemDao, SaleSessionDao, ReceiptDao, AuditEventDao, CounterDao, BusinessProfileDao, ProductPriceHistoryDao, StockMovementDao, MembershipMirrorDao, DeviceRegistrationDao, OutboxEntryDao, SyncCursorDao, SyncExceptionDao
- **Repositories (13 new):** SaleSessionRepository, SaleItemRepository, ReceiptRepository, AuditEventRepository, CounterRepository, BusinessProfileRepository, ProductPriceHistoryRepository, StockMovementRepository, MembershipMirrorRepository, DeviceRegistrationRepository, OutboxEntryRepository, SyncCursorRepository, SyncExceptionRepository
- **CheckoutRepository:** Atomic transaction creating SaleSession, SaleItems, StockMovements, Receipt, Counter, AuditEvent, OutboxEntry in one Room transaction. Also recordReceiveAtomic, recordAdjustmentAtomic, voidSaleAtomic.
- **Retired:** StockAdjustment entity/DAO/repository folded into StockMovement ledger
- **MovementRepository:** Write methods deprecated; reads now from stock_movements
- **StockViewModel:** Uses StockMovementRepository.observeBalances (real-time)
- **SalesViewModel:** Uses CheckoutRepository; persists cart via DraftSaleRepository
- **ProductRepository:** updatePrice() records price history + audit event
- **Tests:** DatabaseMigrationTest.kt, LedgerBalanceTest.kt, CheckoutAtomicityTest.kt, CheckoutRepositoryTest.kt, FullMigrationTest.kt, ProductPriceHistoryTest.kt, DraftSalePersistenceTest.kt

### Phase 2 — Receipts, Owner Visibility, Reports (COMPLETED)
- **ReceiptRenderer:** Text, HTML, thermal (ESC/POS 58mm/80mm) formats
- **ThermalPrinterFormatter:** ESC/POS byte generation with alignment, bold, underline, barcode-ready
- **PdfReceiptRenderer:** Android PdfDocument API for A4/A5/thermal PDF + system Print dialog
- **ReceiptRepository:** Loads complete receipt data (session + items + business + receipt)
- **Screens:** ReceiptHistoryScreen, StockMovementsScreen, AuditEventsScreen, ReportsScreen
- **ViewModels:** ReceiptHistoryViewModel, StockMovementsViewModel, AuditEventsViewModel, ReportsViewModel
- **Tests:** ReceiptRendererTest.kt, ReportsTest.kt
- **Navigation:** Routes.RECEIPT_HISTORY, Routes.STOCK_MOVEMENTS, Routes.AUDIT_EVENTS

### Phase 3 — Backend Foundation (COMPLETED)
- **Infrastructure:** wrangler.jsonc, wrangler.toml (dev/staging/prod), package.json, tsconfig.json
- **Migrations (2):** 0001_platform_tables.sql (29 tables), 0002_business_tables.sql (business-scoped tables)
- **db/tenant.ts:** TenantDB (business-scoped, auto-injects business_id) + PlatformDB (admin)
- **Middleware:** auth.ts (JWT verification, Google ID token, role/device checks), rateLimit.ts
- **Routes:** auth, business, memberships, devices, sync, admin, backup, health
- **CI/CD:** backend-ci.yml, updated root ci.yml with Android + backend pipeline + Control Centre Pages deploy
- **Tests:** utils.test.ts (hashing, token/code generation)
- **Connection scripts:** cloudflare-setup.mjs, d1-migrate.mjs, set-secrets.mjs, deploy-worker.mjs, store-config.mjs, verify-connection.mjs, connect-all.mjs
- **docs/CONNECTION.md:** Full connection guide; backend/docs/DEPLOY.md: deployment guide

### Phase 4 — Android Auth & Onboarding (COMPLETED)
- **Auth:** TokenManager (EncryptedSharedPreferences), GoogleSignInClient (Credential Manager + fallback), SessionManager (token lifecycle, auto-refresh, offline lease)
- **ApiClient:** Ktor HTTP client with auth interceptor, all backend endpoints
- **ConnectionSettings/ConnectionResolver:** Runtime-configurable Google client ID, API base URL, device code
- **LegacyAdoptionManager:** Migrates default-business data to new business ID with outbox entries
- **Screens:** WelcomeScreen, GoogleSignInScreen, BusinessResolutionScreen, LegacyAdoptionScreen, RecoveryCodesScreen, DeviceApprovalScreen, InvitationAcceptScreen, ConnectionSettingsScreen
- **ViewModel:** OnboardingViewModel, ConnectionViewModel
- **Navigation:** 8 new routes added

### Phase 5 — Synchronisation (COMPLETED)
- **OutboxProcessor:** Pushes pending outbox entries to `/api/sync/push` in batches
- **PullApplier:** Pulls changes since cursor from `/api/sync/pull`
- **SyncWorker:** WorkManager periodic (15 min) + on-demand push/pull
- **SyncStatusViewModel + SyncStatusScreen:** Push/pull state, pending count, lease expiry
- **SyncExceptionRepository:** Stores and retrieves sync exceptions

### Phase 6 — Backup & Recovery (COMPLETED)
- **BackupManager:** NDJSON archive with manifest, ZIP packaging, SHA-256 content hash
- **BackupViewModel + BackupRestoreScreen:** Create, share, inspect, restore backups
- **FileProvider:** Configured for secure file sharing
- **Backend:** Nightly cron backup → NDJSON export → R2 with retention window (30 daily + 12 monthly)
- **Migration 4→5/5→6:** Backup tables added to schema

### Phase 7 — SANDE Control Centre (COMPLETED — code written)
- **Backend:** Admin routes (businesses, memberships, devices, sync, exceptions, audit, stats, monitoring) + scheduled cron jobs (nightly backup, cleanup, lease check)
- **Frontend SPA:** Vanilla TypeScript/DOM, Cloudflare Pages deployment
- **Pages:** Dashboard (stats + monitoring + audit), Businesses (directory + status filter), BusinessDetail (members/devices/sync), SyncExceptions (filter + resolve)
- **API client:** Full TypeScript types for all 15 backend endpoints
- **UI components:** Stat cards, data tables, status badges, currency/date formatting
- **CI/CD:** GitHub Actions deploys backend to Workers + Control Centre to Pages on push to main

## What Is Currently Being Implemented
- Phase 8 — Hardening & multi-business launch (planning)

## Files Recently Modified
- `sande-2-online/TASKS.md` — Phase 7 marked complete, Phase 8 pending
- `sande-2-online/.github/workflows/ci.yml` — Added Control Centre Pages deployment
- `sande-2-online/control-centre/` — New directory: package.json, tsconfig.json, wrangler.toml, src/api/client.ts, src/components/index.ts, src/pages/{Dashboard,Businesses,BusinessDetail,SyncExceptions,Login}.ts, src/main.ts, index.html

## Important Discoveries
- Wrangler CLI cannot run on Android (workerd has no android-arm64 build) — connection scripts use Cloudflare REST API instead
- CI is the canonical build path (local Gradle cannot download dependencies)
- esbuild native binary cannot execute from Android shared storage — Worker bundling done in CI
- Control Centre is a vanilla TypeScript SPA (no framework) deployed to Cloudflare Pages

## Current Blockers
- Cloudflare/Google credentials not yet provided — cannot deploy Worker or test sync end-to-end
- User needs to supply: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET

## Errors Encountered
- Gradle builds fail on this device due to dependency download timeouts (CI is canonical build path)
- Wrangler CLI unsupported on android-arm64 — connection scripts use REST API

## Tests Performed
- All unit tests written (not yet run due to Gradle dependency issues)
- Code changes follow existing patterns; CI will validate compilation

## Decisions Awaiting Confirmation
- Cloudflare credentials from user (API token, account ID)
- Google OAuth web client ID/secret from user

## Immediate Next Action
1. Add GitHub secrets (CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)
2. Push to main — CI builds Android APK, runs backend tests, deploys Worker + Control Centre
3. End-to-end test: Admin logs into Control Centre → suspends a business → Android app device shows "Business suspended" on next sync

## Exact Commands to Resume Work
```sh
# Add GitHub secrets via repo Settings → Secrets and variables → Actions
# Then push to main to trigger CI/CD
cd sande-2-online
git add .
git commit -m "Phase 7: SANDE Control Centre admin SPA"
git push origin main
```
