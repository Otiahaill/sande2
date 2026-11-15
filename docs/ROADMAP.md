# SANDE — INTEGRATED DEVELOPMENT ROADMAP

Project: sande — Native Android Sales & Inventory System
Repository: Otiahaill/sande (GitHub Actions CI verified building)
Authors: AndroidHarness Agent / Brian

---

## STATUS SUMMARY (as of 2026-11-14)

### What EXISTS in the repo (verified via CI build #26 passing)

| Layer | Files | Status |
|-------|-------|--------|
| Build config | `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.jar`, `gradle.properties` | **Complete**. AGP 8.5.0, Kotlin 1.9.23, Compose BOM 2024.06.00, Compose compiler 1.5.13, compileSdk 34, Java 17. Gradle 8.9 wrapper with valid `gradle-wrapper.jar`. |
| Data entities | `Product`, `Movement`, `MovementType`, `Outbox`, `Member`, `Business`, `SaleHeader` | **Complete**. Room entities with `@Entity`, `@PrimaryKey`, `@ForeignKey`, `@Index`. |
| DAOs | `ProductDao`, `MovementDao`, `OutboxDao`, `MemberDao`, `SaleHeaderDao` | **Complete**. Room `@Dao` with `@Insert`, `@Update`, `@Query`, `@Delete`. |
| Repositories | `ProductRepository`, `MovementRepository`, `OutboxRepository`, `MemberRepository`, `SaleHeaderRepository`, `AuthRepository` | **Complete**. Room access + business methods. |
| Domain | `PermissionPolicy` | **Complete**. Role-based checks (OWNER/MANAGER/SALESPERSON). |
| DI | `SandeApplication` | **Complete**. Lazy-initialized AppDatabase + all repositories. |
| UI (Compose M3) | SplashActivity, SplashScreen, SignInScreen, SplashScreen, CatalogueScreen, StockScreen, SalesScreen, SettingsScreen, DashboardScreen, SaleHistoryScreen, ProductFormScreen, MemberListScreen, ReportsScreen, BackupRestoreScreen, AuditViewerScreen, ReplenishmentAlertsScreen | **Complete**. Material 3 Compose screens (some are placeholders). |
| UI Theme | `Color.kt` (iOS light/dark), `Type.kt`, `Shape.kt`, `Theme.kt`, `colors.xml`, `themes.xml` | **Complete**. iOS design language (#0A84FF brand). |
| Nav | `AppNavGraph.kt` — Splash → SignIn → Shell (bottom nav: Dashboard/Catalogue/Stock/Sales/Settings) | **Complete**. Navigation Compose. |
| CI | `.github/workflows/ci.yml` | **Complete**. JDK 17, AGP 8.5, Gradle 8.9 wrapper, SDK license, assembleDebug, lintDebug. |
| CI Status | **PASS** (run #26) | Build + lint both pass (0 errors; 36 warnings, all cosmetic: obsolete AGP version, unused resources, missing data-extraction-rules). |

### What is MISSING (blueprint M6–M10 + directive requirements)

| Item | Status | Priority |
|------|--------|----------|
| Firebase project / `google-services.json` | NOT present | Critical |
| Firebase Auth implementation | Mock `AuthRepository` only | Critical (final stage per directive) |
| Firebase Security Rules | NOT present | Critical |
| Firebase Emulator Suite | NOT configured | Critical |
| Multi-tenancy (business/membership scoping) | `businessId` field exists on Member but NOT enforced | Critical |
| Branch model | NOT present | High |
| Granular permissions (beyond owner/manager/salesperson) | `PermissionPolicy` has basic roles only | High |
| Fractional quantities / flexible units | NOT present | High |
| Price override per salesperson | `canEditProducts` exists but no override UI/logic | Medium |
| Atomic sale recording | `SalesViewModel.recordSale()` calls `MovementRepository.recordSaleAtomic()` but sale data still per-line, not grouped under SaleHeader consistently | Medium |
| Sync/outbox push-pull | `OutboxRepository` exists, no push/pull implementation | Medium |
| Backup/restore | Not present | Medium |
| Reports/CSV export | `ReportsScreen` placeholder only | Medium |
| Audit viewer | `AuditViewerScreen` placeholder only | Medium |
| Replenishment alerts | `ReplenishmentAlertsViewModel` exists, no alerts logic | Medium |
| Signed release APK / keystore | Not present | Medium |
| Field trial | Not done | Low |

---

## INTEGRATED ROADMAP

### PHASE 0 — Requirements validation & environment setup (DONE)
- [x] Confirm existing project compiles (CI run #26 green)
- [x] Verify all 60+ source files exist
- [x] Confirm Lint passes (0 errors, 36 cosmetic warnings)
- [x] Confirm Gradle wrapper + AGP 8.5 + Kotlin 1.9.23 + Compose BOM 2024.06.00
- [x] Write this ROADMAP.md as single source of truth

### PHASE 1 — Domain model extension (implement)
Create/extend entities for multi-tenancy:
- `Business` — already exists (id, name, createdAt)
- `Branch` — NEW: id, businessId, name, address, isActive, createdAt
- `Member` — already exists (has businessId)
- `Membership` — NEW: uid, businessId, role, status, createdAt
- `ProductCategory` — NEW: id, businessId, name, isActive
- `Product` — extend: add `unitOfMeasure`, `conversionRatio`, `priceCents`, `categoryId`, `businessId`
- `Movement` — already exists (has productId, type, quantity, referenceId)
- `SaleHeader` — already exists (receiptNumber, totalCents, deviceId, synced) — ensure businessId + receiptNumber
- `Outbox` — already exists

### PHASE 2 — Permission architecture
- `PermissionPolicy` granular permissions:
  - `canViewProducts`, `canCreateProduct`, `canEditProduct`, `canDeleteProduct`
  - `canViewStock`, `canAdjustStock`
  - `canViewSales`, `canRecordSale`, `canVoidSale`, `canOverridePrice`
  - `canReadReports`
  - `canManageMembers`, `canManageBusiness`
- Business-scoped: all queries filtered by `businessId`
- Permission cache: cache resolved permissions per business context

### PHASE 3 — Firebase setup & Firestore model
- Create Firebase project (developer-owned account)
- Register Android app (package `com.example.sande`)
- Download `google-services.json`
- Add `firebase-bom`, `firebase-auth-ktx`, `firebase-firestore-ktx` to `libs.versions.toml`
- Firestore documents:
  - `businesses/{id}` {name, createdAt, ownerUid, settings}
  - `businesses/{id}/branches/{id}` {name, address, isActive}
  - `businesses/{id}/members/{uid}` {role, status, createdAt}
  - `businesses/{id}/products/{id}` {name, sku, category, priceCents, unitOfMeasure, conversionRatio, isActive}
  - `businesses/{id}/movements/{id}` {productId, type, quantity, timestamp, deviceId, referenceId}
  - `businesses/{id}/sales/{id}` {receiptNumber, totalCents, deviceId, deviceTime, serverTime, synced, lineItems:[{productId, quantity, unitPriceCents, lineTotalCents}]}
  - `businesses/{id}/outbox/{id}` {entityType, entityId, operation, payload, createdAt, synced}
- Firestore Security Rules: per-business read/write by member uid + role
- Firebase Emulator Suite config (`firebase.json`)

### PHASE 4 — Offline sync engine
- `OutboxRepository` — push/pull sync with conflict detection
- `syncState` per outbox item: LOCAL_PENDING, UPLOADING, SERVER_ACCEPTED, SYNC_FAILED, REQUIRES_REVIEW
- Deterministic client IDs (UUID) prevent duplicates
- `Movement.syncState` + `SaleHeader.syncState`
- SyncWorker (WorkManager) — push on reconnect
- Idempotent server processing (idempotency key = document id)
- Conflict detection: last-write-wins with conflict flag + alert

### PHASE 5 — Cloud Functions (trusted server-side)
- `recordSale` — validate sale, check stock, write ledger atomically
- `recordVoid` — validate sale, create reversal movement
- `recordAdjustment` — validate manager/owner, create adjustment
- `syncStock` — reconcile balances after push
- `auditLog` — record privileged operations
- Negative stock policy enforcement per business settings

### PHASE 6 — Android UI (Material 3 iOS theme)
- Role-based UI: hide/show actions per PermissionPolicy
- Business/branch switcher in shell header
- Catalogue with fractional quantities
- Sales recording with cart, subtotal, receipt number
- Stock view with low-stock alerts
- Member management (owner only)
- Audit viewer
- Backup/restore
- Settings: negative stock policy (WARN/BLOCK/ALLOW), price override toggle

### PHASE 7 — Firebase Auth (FINAL STAGE per directive)
- Email/password sign-in
- Session persistence (Room cache for offline)
- Forgot password
- Member membership resolution (businesses user belongs to)
- Business switcher
- Deactivate/reactivate accounts

### PHASE 8 — Release & verification
- Signed release APK + keystore backup
- 1-week field trial plan
- Backup/restore procedure
- Replenishment alerts
- Reports/CSV export

### PHASE 9 — Testing & verification
- Unit tests for repositories & ViewModels
- Firestore Security Rules emulator tests
- Sync convergence tests (two devices)
- Negative stock policy tests
- Multi-tenant isolation tests
- Release build + keystore backup
- 1-week field trial plan

## EXECUTION ORDER

PHASE 0 (done) → PHASE 1 → PHASE 2 → PHASE 3 → PHASE 4 → PHASE 5 → PHASE 6 → PHASE 7 (FINAL) → PHASE 8 → PHASE 9

Authentication (Firebase Auth) is intentionally deferred to PHASE 7 per directive. All offline-capable data layers and UI are built first so that Auth integration is purely additive at the end.

## DECISION LOG

| ID | Decision | Value |
|----|----------|-------|
| D1 | Build route | GitHub Actions CI (Route B) — probe showed ARM aarch64, no JDK, ~73 MB RAM |
| D2 | Build config | AGP 8.5.0, Kotlin 1.9.23, Compose BOM 2024.06.00, Compose compiler 1.5.13, compileSdk 34 |
| D3 | UI | Jetpack Compose Material 3, iOS-inspired light/dark theme |
| D4 | Build system | Gradle 8.9 wrapper |
| D5 | Data layer | Room 2.6.1 (SQLite), insert-only movement ledger |
| D6 | Sync | Outbox model + WorkManager + client-generated UUIDs |
| D7 | Firebase | Spark plan, email/password auth, Firestore Native mode, Security Rules |
| D8 | Negative stock policy | Default WARN (configurable per business) |
| D9 | Revenue model | Zero cash spent on Firebase Spark, GitHub Actions free tier |
| D10 | Auth implementation order | FINAL stage (per directive directive #14) |

## VERIFICATION CHECKLIST

- [ ] CI `assembleDebug` passes (done — run #26)
- [ ] CI `lintDebug` passes (done — 0 errors)
- [ ] `google-services.json` present (NOT committed; developer creates)
- [ ] Firebase Security Rules written + emulator tests pass
- [ ] Offline sale survives app kill + restart
- [ ] Two devices sync to identical balances
- [ ] Multi-business isolation verified
- [ ] Negative stock WARN alert works
- [ ] Signed release APK built + keystore backed up ×2
- [ ] 1-week field trial plan documented

---

**Next immediate action: PHASE 1 — Create `sande-firebase.json` placeholder (do NOT commit real credentials), add Firebase dependencies to `libs.versions.toml`, create `Business`, `Branch`, `ProductCategory` entities, extend `Product` with `unitOfMeasure`/`conversionRatio`/`categoryId`/`businessId`, extend `SaleHeader` with `businessId`. Execute now.**
