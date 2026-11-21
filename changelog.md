# sande-2-online — Changelog

All entries are chronological. Status descriptions are accurate.

## 2026-11-21 — Phase 8: Hardening & Multi-Business Launch (Planning)
- **Status:** In planning

## 2026-11-21 — Project Memory Initialisation & Phase 0-4 Complete
- **Feature:** Project memory files rewritten to describe sande-2-online (online-capable, not offline-only sande2 copy)
- **Files Created:** project.md, study_session.md, changelog.md, README.md, ARCHITECTURE.md, TASKS.md, TESTING.md updated
- **Status:** Completed

## 2026-11-21 — Phase 0: Stabilise Baseline
- **Feature:** Removed `fallbackToDestructiveMigration()` from AppDatabase; added explicit `MIGRATION_3_4` no-op migration
- **Feature:** Bumped Room DB version to 4, enabled `exportSchema = true`
- **Feature:** Migration tests (DatabaseMigrationTest.kt), ledger balance test (LedgerBalanceTest.kt), checkout atomicity test (CheckoutAtomicityTest.kt)
- **Feature:** Fixed dual receive write (removed StockAdjustmentRepository from StockViewModel)
- **Feature:** Fixed receipt-number bug (generateReceiptNumber takes businessId, uses countByBusiness)
- **Feature:** Test dependencies added: Truth, roomTesting, androidx-test, espresso
- **Status:** Completed

## 2026-11-21 — Phase 1: Local Data Foundation
- **Feature:** 13 new entities created: SaleItem, SaleSession, Receipt, AuditEvent, Counter, BusinessProfile, ProductPriceHistory, StockMovement, MembershipMirror, DeviceRegistration, OutboxEntry, SyncCursor, SyncException
- **Feature:** 13 new DAOs created for all new entities
- **Feature:** 13 new repositories created for all new entities
- **Feature:** CheckoutRepository — atomic Room transaction creating SaleSession, SaleItems, StockMovements, Receipt, Counter, AuditEvent, OutboxEntry
- **Feature:** RecordReceiveAtomic, RecordAdjustmentAtomic, VoidSaleAtomic transaction methods
- **Feature:** Retired StockAdjustment entity/DAO/repository (folded into StockMovement ledger)
- **Feature:** MovementRepository write methods deprecated; reads from stock_movements
- **Feature:** StockViewModel uses StockMovementRepository.observeBalances
- **Feature:** SalesViewModel uses CheckoutRepository + DraftSaleRepository cart persistence
- **Feature:** ProductRepository.updatePrice() records price history + audit event
- **Feature:** Migration 4→5 created all new tables + legacy data migration; Migration 5→6 cleanup marker
- **Tests:** FullMigrationTest.kt (v2→v6), ProductPriceHistoryTest.kt, DraftSalePersistenceTest.kt
- **Status:** Completed

## 2026-11-21 — Phase 2: Receipts, Owner Visibility, Reports
- **Feature:** ReceiptRenderer — text, HTML, thermal (ESC/POS 58mm/80mm) formats
- **Feature:** ThermalPrinterFormatter — ESC/POS byte generation
- **Feature:** PdfReceiptRenderer — Android PdfDocument API + system Print dialog
- **Feature:** ReceiptRepository — loads complete receipt data
- **Screens:** ReceiptHistoryScreen, StockMovementsScreen, AuditEventsScreen, ReportsScreen
- **ViewModels:** ReceiptHistoryViewModel, StockMovementsViewModel, AuditEventsViewModel, ReportsViewModel
- **Tests:** ReceiptRendererTest.kt, ReportsTest.kt
- **Navigation:** Routes.RECEIPT_HISTORY, Routes.STOCK_MOVEMENTS, Routes.AUDIT_EVENTS
- **Status:** Completed

## 2026-11-21 — Phase 3: Backend Foundation (Cloudflare Workers + D1 + R2)
- **Feature:** wrangler.jsonc, wrangler.toml (dev/staging/prod environments)
- **Feature:** package.json, tsconfig.json, vitest.config.ts
- **Feature:** Migration 0001_platform_tables.sql (29 platform tables)
- **Feature:** Migration 0002_business_tables.sql (business-scoped tables)
- **Feature:** db/tenant.ts — TenantDB (business-scoped) + PlatformDB (admin)
- **Feature:** Middleware auth.ts (JWT, Google ID token, role/device checks) + rateLimit.ts
- **Feature:** Routes auth, business, memberships, devices, sync, admin, backup, health
- **Feature:** CI/CD backend-ci.yml + updated root ci.yml (Android + backend pipeline)
- **Feature:** Connection scripts: cloudflare-setup.mjs, d1-migrate.mjs, set-secrets.mjs, deploy-worker.mjs, store-config.mjs, verify-connection.mjs, connect-all.mjs
- **Feature:** docs/CONNECTION.md — full connection guide
- **Feature:** utils.test.ts — auth utility tests
- **Status:** Completed

## 2026-11-21 — Phase 4: Android Auth & Onboarding
- **Feature:** TokenManager — EncryptedSharedPreferences token storage
- **Feature:** GoogleSignInClient — Credential Manager + GoogleSignInClient fallback
- **Feature:** SessionManager — token lifecycle, auto-refresh, offline lease monitoring
- **Feature:** ApiClient — Ktor HTTP client with auth interceptor
- **Feature:** ConnectionSettings/ConnectionResolver — runtime-configurable client ID, API URL, device code
- **Feature:** LegacyAdoptionManager — migrates default-business data to new business ID
- **Screens:** WelcomeScreen, GoogleSignInScreen, BusinessResolutionScreen, LegacyAdoptionScreen, RecoveryCodesScreen, DeviceApprovalScreen, InvitationAcceptScreen, ConnectionSettingsScreen
- **ViewModels:** OnboardingViewModel, ConnectionViewModel
- **Navigation:** 8 new routes (WELCOME, SIGN_IN, BUSINESS_RESOLUTION, LEGACY_ADOPTION, RECOVERY_CODES, DEVICE_APPROVAL, INVITATION_ACCEPT, CONNECTION)
- **Status:** Completed

## Security Changes
- EncryptedSharedPreferences for token storage (AES256-GCM)
- JWT access tokens (15 min) + refresh tokens (14-day lease)
- Device approval workflow (owner approves staff devices)
- Rate limiting per IP/device/business
- Tenant-scoped D1 access (business_id auto-injected)

## Dependency Updates
- androidx-credential-manager, google-play-services-auth
- ktor-client-core/android/json, ktor-serialization-kotlinx
- okhttp
- androidx-security-crypto, androidx-biometric
- coil-compose
- androidx-work-runtime

## Architecture Changes
- Room DB version 3→6 with migrations v2→v6
- 13 new tables added; legacy tables kept for migration compatibility
- 25+ repositories wired in SandeApplication
- Ktor + OkHttp networking layer added
- 20+ Compose screens (14 legacy + 6 new onboarding/connection)

## Defect Fixes
- Removed `fallbackToDestructiveMigration()` (data loss risk)
- Fixed dual receive write (double-counting stock)
- Fixed receipt-number bug (global count → per-business count)
- Deprecated MovementRepository write methods (use CheckoutRepository instead)

## Known Issue Documentation
- Wrangler CLI unsupported on android-arm64 — connection scripts use Cloudflare REST API
- Local Gradle builds cannot download dependencies — CI is canonical build path
- Cloudflare/Google credentials not yet connected (awaiting user API tokens)
