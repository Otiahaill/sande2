# sande-2-online

**Kotlin Android inventory/pharmacy management project with online sync capabilities.**

Evolution of sande2 → sande-fixed → sande-fixed2 → sande-2-online. Adds multi-device synchronisation, Google Sign-In authentication, role-based access control, and Cloudflare Workers + D1 + R2 backend.

## Project Info
- **Package:** com.example.sande
- **Min SDK:** 26
- **Target SDK:** 34
- **Version:** 1.0.0 (development)
- **CI:** GitHub Actions — builds APK, runs tests, deploys Worker

## What It Does
Inventory management for retail/pharmacy setting with:
- Product catalog and categories management
- Member registration and list
- Sales and transaction tracking with receipt printing
- Stock adjustment and replenishment
- Dashboard and reports view (top products, hourly sales, by cashier)
- Stock movements ledger with audit trail
- **Online sync between devices** via Cloudflare Workers + D1 + R2
- Offline-first operation with local SQLite cache
- Multi-user support (owner + up to 4 staff, 5 devices per business)
- Business recovery codes and device management

## Screens
### Onboarding (7)
WelcomeScreen, GoogleSignInScreen, BusinessResolutionScreen, LegacyAdoptionScreen, RecoveryCodesScreen, DeviceApprovalScreen, InvitationAcceptScreen

### Main App (14+)
DashboardScreen, CatalogueScreen, StockScreen, StockMovementsScreen, SalesScreen, SaleHistoryScreen, ReceiptHistoryScreen, ReportsScreen, ReplenishmentAlertsScreen, MemberListScreen, AuditViewerScreen, AuditEventsScreen, BackupRestoreScreen, ConnectionSettingsScreen, SettingsScreen

## Technology Stack
- **Language:** Kotlin 1.9.23
- **Framework:** Android Gradle Plugin 8.5, Jetpack Compose Material 3
- **Database:** Room (23+ entities, 25+ DAOs)
- **Network:** Ktor + OkHttp
- **Auth:** Credential Manager (Google Sign-In), JWT tokens
- **Backend:** Cloudflare Workers (TypeScript), Cloudflare D1, Cloudflare R2, Cloudflare KV
- **Build System:** Gradle with gradlew wrapper

## Backend
Cloudflare Workers with:
- D1 database (29 tables, tenant-scoped by business_id)
- R2 bucket for backup exports
- KV namespaces for sessions and rate limiting
- REST API: auth, business, memberships, devices, sync, admin, backup, health

## How It Was Built

### Android App
```sh
# Build debug APK (requires Gradle + Android SDK)
cd sande-2-online
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease
```

**Note:** Local Gradle builds may fail on this device due to dependency download timeouts. CI (GitHub Actions) is the canonical build path.

### Backend
```sh
cd sande-2-online/backend

# Install dependencies
npm install

# Create Cloudflare resources
CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx \
  node scripts/cloudflare-setup.mjs --env=dev

# Apply D1 migrations
CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx \
  node scripts/d1-migrate.mjs --env=dev

# Set secrets
CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx \
  GOOGLE_CLIENT_ID=xxx GOOGLE_CLIENT_SECRET=xxx \
  node scripts/set-secrets.mjs --env=dev --generate

# Deploy Worker
CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx \
  node scripts/deploy-worker.mjs --env=dev

# Verify connection
node scripts/verify-connection.mjs --env=dev

# One-shot all steps
CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx \
  GOOGLE_CLIENT_ID=xxx GOOGLE_CLIENT_SECRET=xxx \
  GOOGLE_CLOUD_PROJECT_ID=xxx \
  node scripts/connect-all.mjs --env=dev
```

## API Keys
**Required for online operation:**
- `CLOUDFLARE_API_TOKEN` — Cloudflare API token with Workers/D1/KV/R2 edit permissions
- `CLOUDFLARE_ACCOUNT_ID` — Cloudflare account ID
- `GOOGLE_CLIENT_ID` — Google OAuth web application client ID
- `GOOGLE_CLIENT_SECRET` — Google OAuth web application client secret

These are set via Cloudflare Workers Secrets (not stored in code). See `backend/docs/CONNECTION.md` for the full connection guide.

**Offline operation:** No API keys required — app works fully offline with local Room database.

## License
Open source — Otiahaill (2026), public GitHub repository Otiahaill/sande2

## Known Limitations
- Cloudflare Wrangler CLI cannot run on Android (no android-arm64 build for workerd) — connection scripts use Cloudflare REST API instead
- Local Gradle builds cannot download dependencies on this device — CI is the canonical build path
- Google OAuth credentials not yet connected (awaiting user API tokens)
