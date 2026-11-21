# sande-2-online — Study Session

**Last Updated:** 2026-11-21 (Phase 7 complete — Control Centre)

## Current Development Objective
Phases 0-7 complete. Phase 7 (SANDE Control Centre admin SPA) code written and CI/CD configured. Ready for Phase 8 (Hardening & multi-business launch).

## What Has Been Completed

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
