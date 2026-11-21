# sande-2-online — Project Memory

## Project Identity
- **Name:** sande-2-online
- **Purpose:** Kotlin Android inventory/pharmacy management with online sync capabilities — Phase 7+ evolution of sande2/sande-fixed/sande-fixed2
- **Primary Entry Point:** com.example.sande.SplashScreen (launch activity)
- **Product Type:** Native Android application (Kotlin, AGP 8.5, compileSdk 34, online-first with offline cache)
- **Backend:** Cloudflare Workers (TypeScript), Cloudflare D1, Cloudflare R2, Cloudflare KV
- **Frontend SPA:** Control Centre — Vanilla TypeScript/DOM, Cloudflare Pages
- **Git Repository:** Otiahaill/sande2 (public), branch main
- **GitHub Remote:** https://github.com/Otiahaill/sande2

## Purpose
Inventory management system for retail/pharmacy setting with:
- Product catalog and categories
- Member management
- Sales and transaction tracking
- Stock adjustment and replenishment
- Member list and dashboard views
- **Online sync between devices** via Cloudflare Workers + D1 + R2
- Offline-first operation with local SQLite cache
- Multi-user support (owner + up to 4 staff, 5 devices per business)
- Business recovery codes and device management
- **SANDE Control Centre:** Platform admin SPA for multi-business management

## Technology Stack
### Android App
- **Language:** Kotlin 1.9.23
- **Framework:** Android Gradle Plugin 8.5, Jetpack Compose Material 3
- **compileSdk:** 34, **targetSdk:** 34
- **Build System:** Gradle with `gradlew` wrapper
- **Persistence:** Room database (23+ entities, 25+ DAOs, migrations v2→v6)
- **Network:** Ktor HTTP client + OkHttp engine
- **Auth:** Credential Manager (Google Sign-In), JWT access/refresh tokens, encrypted token storage
- **Dependency Injection:** SandeApplication.kt (DI container, 25+ repositories)
- **UI:** Jetpack Compose Material 3 (20+ screens)
- **Offline-first:** Local SQLite via Room; syncs to Cloudflare D1 when online
- **Navigation:** Drawer navigation with role-based filtering (owner sees admin options)

### Backend (Cloudflare Workers)
- **Runtime:** Cloudflare Workers (TypeScript)
- **Database:** Cloudflare D1 (SQLite-compatible, 29 tables, tenant-scoped by business_id)
- **Storage:** Cloudflare R2 (backup exports)
- **KV:** Cloudflare KV (sessions, rate limiting, connection manifests)
- **Workflows:** Scheduled cron triggers (nightly backup, exception cleanup, lease check)

### Control Centre (Frontend SPA)
- **Language:** TypeScript, vanilla DOM (no framework)
- **Build:** esbuild → Cloudflare Pages
- **Pages:** Dashboard, Businesses, BusinessDetail, SyncExceptions, AdminLogin
- **API:** All 15 backend endpoints typed

## Architecture
- **Package:** com.example.sande (Android), sande-2-online (backend/control-centre)
- **Data Layer:** Room entities + DAOs (25+ DAOs)
- **Repository Layer:** 25+ repository classes
- **Sync Layer:** OutboxProcessor, PullApplier, SyncWorker (WorkManager), exception handling
- **ViewModel Layer:** 15+ ViewModel classes
- **DI:** SandeApplication.kt provides application-level dependency injection
- **UI:** 20+ Compose screens + 5 Control Centre pages

## Key Data Models
| Entity | Table | Purpose |
|--------|-------|---------|
| StockMovement | stock_movements | Signed-delta ledger |
| SaleSession | sale_sessions | Complete sale record with receipt number |
| SaleItem | sale_items | Product snapshots at sale time |
| Receipt | receipts | Receipt records with counter, format, content hash |
| Counter | counters | Per-device sequences |
| AuditEvent | audit_events | Immutable audit trail |
| BusinessProfile | business_profiles | Business settings |
| ProductPriceHistory | product_price_history | Price change log |
| MembershipMirror | membership_mirrors | Read-only server membership copy |
| DeviceRegistration | device_registrations | Device registry |
| OutboxEntry | outbox_entries | Sync outbox |
| SyncCursor | sync_cursors | Pull cursor per device |
| SyncException | sync_exceptions | Conflict/exception queue |

## Build System
- **AGP:** 8.5
- **Kotlin:** 1.9.23
- **compileSdk:** 34
- **Build Tool:** Gradle with `gradlew` wrapper
- **CI:** GitHub Actions — builds APK, runs tests, deploys Worker + Control Centre

## Known Limitations
- Cloudflare Wrangler CLI cannot run on Android (no android-arm64 build) — connection scripts use Cloudflare REST API instead
- Local Gradle builds cannot download dependencies on this device — CI is the canonical build path
- Google OAuth credentials not yet connected (awaiting user API tokens)

## Current Strategic Direction
- Phases 0-7 complete: baseline, local data, receipts/reports, backend, auth/onboarding, sync, backup/recovery, Control Centre
- Phase 8 in progress: Hardening & multi-business launch

## Existing Internal Documentation
- docs/CONNECTION.md — Cloudflare/Google connection guide
- backend/docs/CONNECTION.md — Cloudflare resource setup script documentation
- backend/docs/DEPLOY.md — Deployment guide
- TASK_ESTIMATION.md — agent-hour estimates per phase

## Git Status
- **Branch:** main
- **Remote:** origin/main (GitHub: Otiahaill/sande2)
- **Working tree:** modified (uncommitted Phase 0-7 changes)
