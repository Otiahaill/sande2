# sande-2-online — Architecture

## Project Overview
Kotlin Android inventory/pharmacy management application with online sync capabilities. Evolution of sande2 → sande-fixed → sande-fixed2 → sande-2-online. Supports offline-first operation with Cloudflare Workers + D1 + R2 backend for multi-device synchronisation.

## Package Structure
- **Package:** com.example.sande
- **Data Layer:** Room database (23+ entities, 25+ DAOs, migrations v2→v6)
- **Repository Layer:** 25+ repository classes
- **Sync Layer:** OutboxProcessor, PullApplier, SyncWorker, exception handling
- **ViewModel Layer:** 15+ ViewModel classes
- **DI:** SandeApplication.kt provides application-level dependency injection
- **UI:** 20+ Compose screens with drawer navigation
- **Backend:** Cloudflare Workers (TypeScript), D1, R2, KV

## Data Layer (Room)
### Entities (23+)
**Legacy (kept for migration compatibility):**
1. Business — business entity/configuration
2. Member — member/customer registration
3. Product — product catalog item
4. ProductCategory — category classification
5. Movement — inventory movement (legacy)
6. SaleHeader — sales header (legacy)
7. DraftSale — draft sales transaction
8. StockAdjustment — stock adjustment entries (legacy, folded into StockMovement)
9. Outbox — outbox entries (legacy)

**New (online-capable):**
10. StockMovement — signed-delta ledger (replaces Movement + StockAdjustment)
11. SaleSession — complete sale record with receipt number
12. SaleItem — product snapshots at sale time
13. Receipt — receipt records with counter, format, content hash
14. Counter — per-device sequences
15. AuditEvent — immutable audit trail
16. BusinessProfile — business settings
17. ProductPriceHistory — price change log
18. MembershipMirror — read-only server membership copy
19. DeviceRegistration — device registry
20. OutboxEntry — sync outbox
21. SyncCursor — pull cursor per device
22. SyncException — conflict/exception queue

### DAOs (25+)
- ProductDao, ProductCategoryDao, ProductPriceHistoryDao
- MovementDao (legacy), StockMovementDao
- OutboxDao (legacy), OutboxEntryDao
- MemberDao, MembershipMirrorDao
- SaleHeaderDao (legacy), SaleSessionDao, SaleItemDao
- DraftSaleDao
- ReceiptDao, CounterDao
- AuditEventDao
- DeviceRegistrationDao
- BusinessProfileDao
- SyncCursorDao, SyncExceptionDao

## Repository Layer (25+)
| Repository | Purpose |
|------------|---------|
| ProductRepository | Product CRUD, price history, audit |
| StockMovementRepository | Stock movement queries, balance calculation |
| CheckoutRepository | Atomic sale/receive/adjust/void transactions |
| SaleSessionRepository | Sale session CRUD |
| SaleItemRepository | Sale item queries |
| ReceiptRepository | Complete receipt data loading |
| CounterRepository | Per-device counter management |
| AuditEventRepository | Audit trail queries |
| TokenManager | Encrypted token storage |
| SessionManager | Token lifecycle, refresh, lease monitoring |
| ApiClient | Ktor HTTP client for backend |
| ConnectionSettings | Runtime connection configuration |
| ConnectionResolver | Resolves effective connection config |
| LegacyAdoptionManager | Migrates default-business data |
| MembershipMirrorRepository | Server membership copy |
| DeviceRegistrationRepository | Device registry |
| OutboxEntryRepository | Sync outbox management |
| SyncCursorRepository | Pull cursor management |
| SyncExceptionRepository | Conflict/exception management |

## Sync Layer
- **OutboxProcessor:** Pushes pending outbox entries to `/api/sync/push` in batches
- **PullApplier:** Pulls changes since cursor from `/api/sync/pull` and applies locally
- **SyncWorker:** WorkManager scheduled push/pull
- **SyncException:** Handles STOCK_NEGATIVE, PRICE_MISMATCH, CLOCK_SKEW, REVOKED_DEVICE, CONFLICT

## ViewModel Layer (15+)
| ViewModel | Purpose |
|-----------|---------|
| StockViewModel | Stock levels with real-time balances |
| SalesViewModel | Cart management, atomic checkout |
| ReportsViewModel | Sales reports (totals, top products, hourly, by cashier) |
| ReceiptHistoryViewModel | Receipt listing, reprint, share |
| StockMovementsViewModel | Movement ledger with filters |
| AuditEventsViewModel | Audit trail with severity filters |
| SessionManager | Auth state, lease monitoring |
| OnboardingViewModel | Onboarding flow orchestration |
| ConnectionViewModel | Connection settings management |

## UI Navigation (20+ Routes)
**Onboarding:**
- WELCOME → SIGN_IN → BUSINESS_RESOLUTION → LEGACY_ADOPTION → RECOVERY_CODES → DEVICE_APPROVAL → DASHBOARD
- INVITATION_ACCEPT (staff join flow)

**Main App:**
- DASHBOARD, CATALOGUE, STOCK, STOCK_MOVEMENTS, SALES, SALE_HISTORY, RECEIPT_HISTORY, REPORTS, ALERTS, MEMBERS, AUDIT, AUDIT_EVENTS, BACKUP, CONNECTION, SETTINGS
- PRODUCT_NEW, PRODUCT_EDIT, RECEIVE_STOCK

**Drawer sections:** Overview, Inventory, Sales, Administration (owner-only entries filtered by role)

## Backend Architecture (Cloudflare Workers)
### TenantDB (business-scoped)
All business-scoped queries auto-inject `business_id` — impossible to cross-tenant access.

### PlatformDB (admin)
Platform-level operations: business CRUD, account management, device admin, sync monitoring.

### Routes
- **Auth:** Google sign-in, business registration, staff invitation, device registration, token refresh, logout
- **Business:** Get/update business profile
- **Memberships:** List, invite, accept, update, revoke (owner only)
- **Devices:** List, register, update, approve, revoke
- **Sync:** Push (outbox), pull (changes since cursor), status, exceptions
- **Admin:** Business list/details, device management, sync health, platform stats/monitoring, audit log
- **Backup:** Create, list, restore, status (NDJSON export to R2)
- **Health:** D1/R2/KV connectivity check

### Migrations
- 0001_platform_tables.sql: businesses, accounts, memberships, invitations, devices, refresh_tokens, recovery_codes, sync_batches, change_log, backups, app_versions, platform_admins, support_access_grants
- 0002_business_tables.sql: products, product_categories, product_price_history, stock_movements, sale_sessions, sale_items, receipts, counters, audit_events, membership_mirrors, device_registrations, outbox_entries, sync_cursors, sync_exceptions, business_profiles

## Security
- EncryptedSharedPreferences for token storage (AES256-GCM)
- JWT access tokens (15 min expiry) + refresh tokens (14-day lease, rotated on use)
- Google ID token verification via JWKS
- Device approval workflow (owner approves staff devices; staff devices start as pending_approval)
- Rate limiting: per-IP, per-device, per-business
- Tenant-scoped D1 access (business_id auto-injected on every query)
- Admin API key for platform routes
- Support access grants (time-limited, scoped, audited)

## Build System
- **AGP:** 8.5
- **Kotlin:** 1.9.23
- **compileSdk:** 34
- **Build Tool:** Gradle with `gradlew` wrapper
- **CI:** GitHub Actions — Android build + tests + backend typecheck/lint/test + Worker deploy

## Known Limitations
- Wrangler CLI unsupported on android-arm64 — connection scripts use Cloudflare REST API
- Local Gradle builds cannot download dependencies — CI is canonical build path
- Cloudflare/Google credentials not yet connected

## Architecture Decision Records (ADRs)
- **ADR 001:** Room over SQLite raw — type safety, compile-time schema verification, migration tooling
- **ADR 002:** Repository pattern over direct DAO access — separates data source concerns, enables future network layer
- **ADR 003:** Signed-delta ledger (StockMovement) over absolute balances — enables conflict resolution and audit
- **ADR 004:** Offline-first with cloud sync — local Room database primary, D1 secondary; sync when online
- **ADR 005:** Tenant-scoped database access — business_id auto-injected prevents cross-tenant data leaks
- **ADR 006:** JWT access + refresh tokens — short-lived access tokens, long-lived refresh tokens with device binding
- **ADR 007:** Cloudflare Workers + D1 + R2 — serverless, pay-per-use, global edge distribution
