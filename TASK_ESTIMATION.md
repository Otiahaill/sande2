# SANDE 2 Online — Task Estimation & Agent Timing

**Source plan:** `.harness/attachments/1121-101945-3c90fdc9-SANDE_Architecture_Plan.md`  
**Base project:** `sande2` (offline, working, ~5,000 lines Kotlin, 60 files)  
**Target:** `sande-2-online` — same app + online capabilities per the architecture plan

---

## Estimation Method

| Unit | Meaning |
|------|---------|
| **Agent-hour (AH)** | One focused agent work session (read → edit → verify). Includes exploration, implementation, and a smoke test. |
| **Risk multiplier** | ×1.0 (clear), ×1.3 (some unknowns), ×1.5 (new domain), ×2.0 (legacy data migration) |
| **Parallelisable** | Tasks marked `P` can run concurrently by separate subagents; total wall-clock is the longest chain. |

> All estimates assume the agent has the full architecture plan in context and the current `sande2` source available. They do **not** include human review cycles or physical device testing (Bluetooth printers, real Google sign-in, etc.).

---

## Phase 0 — Stabilise Baseline (S)

| # | Task | AH (base) | Risk | Est. AH | P? | Notes |
|---|------|-----------|------|---------|----|-------|
| 0.1 | Clone repo, verify CI green on current HEAD | 0.5 | 1.0 | 0.5 | | Already done via copy |
| 0.2 | Remove `fallbackToDestructiveMigration()` from `AppDatabase`; add explicit migration 3→4 (empty, tested) | 1.5 | 1.3 | 2.0 | | Enable `exportSchema = true` |
| 0.3 | Add JUnit + Room test dependencies; write ledger balance invariant test (sum of deltas = balance) | 2.0 | 1.3 | 2.6 | P | In-memory Room |
| 0.4 | Write checkout atomicity test (forced failure at each step → no partial write) | 2.0 | 1.3 | 2.6 | P | Inject fault in transaction |
| 0.5 | Fix dual receive write: stop writing `stock_adjustments` in `ReceiveStockScreen` | 0.8 | 1.0 | 0.8 | | One-line repo change |
| 0.6 | Fix receipt-number bug (business ID passed where device code expected) | 0.5 | 1.0 | 0.5 | | `SaleHeaderRepository` |
| 0.7 | Snapshot real Sunday Hardware DB; add as test fixture; verify it opens in new build | 1.5 | 2.0 | 3.0 | | Critical for Phase 1 migration rehearsal |

**Phase 0 total: ~12 AH** (wall-clock ~7 AH with parallel tests)

---

## Phase 1 — Local Data Foundation (L) — **No Cloud**

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 1.1 | New entities: `SaleItem`, `Receipt`, `AuditEvent`, `Counter`, `BusinessProfile`, `ProductPriceHistory` | 3.0 | 1.3 | 3.9 | P | Data classes + Room annotations |
| 1.2 | New DAOs + repositories for above | 3.5 | 1.3 | 4.6 | P | Follow existing patterns |
| 1.3 | Migration 4→5: `movements` → `stock_movements` (signed delta, actor, reason, reversal link, reference) | 4.0 | 2.0 | 8.0 | | Run on real-data fixture; verify balances |
| 1.4 | Migration 5→6: `sale_headers` → `sale_sessions` + create `sale_items` (snapshots) | 3.5 | 2.0 | 7.0 | | Preserve all historical sales |
| 1.5 | Retire `StockAdjustment` entity/DAO/repo; fold into ledger | 1.5 | 1.3 | 2.0 | | Delete files, remove from `SandeApplication` |
| 1.6 | Replace `MovementDao` balance logic with single `observeBalances(businessId)` query | 1.5 | 1.3 | 2.0 | P | All screens (Stock, Dashboard, Replenishment) switch |
| 1.7 | Extend `MovementRepository.recordSaleAtomic`: write session + items + ledger + receipt row + audit + outbox bundle in one transaction | 4.0 | 1.5 | 6.0 | | Core checkout change |
| 1.8 | Receipt numbering: per-device `Counter` (deviceCode + sequence) allocated inside transaction | 1.5 | 1.3 | 2.0 | | Replaces count-based bug |
| 1.9 | `Member` → `MembershipMirror` (server-synced, read-only locally) | 1.5 | 1.3 | 2.0 | P | New entity, DAO, repo |
| 1.10 | Connect `DraftSaleRepository` to `SalesViewModel` (cart persistence across app kill) | 1.0 | 1.0 | 1.0 | | Existing code, just wire it |
| 1.11 | Update `ProductRepository`: owner-only writes, price history on change, audit event | 2.0 | 1.3 | 2.6 | | |
| 1.12 | Unit tests: migration v3→6 on real fixture; balances equal before/after; transaction atomicity; report totals match old totals | 3.0 | 1.5 | 4.5 | P | |

**Phase 1 total: ~45.6 AH** (wall-clock ~25 AH with parallel entity/DAO work)

---

## Phase 2 — Receipts, Owner Visibility, Reports (M)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 2.1 | `ReceiptRenderer` + `ReceiptRepository`: load receipt + session + items + header snapshot | 2.5 | 1.3 | 3.3 | | |
| 2.2 | Thermal formatter (ESC/POS): 58 mm & 80 mm profiles, logo dithering, line wrap, currency | 3.5 | 1.5 | 5.3 | P | Library choice per Appendix A#9 |
| 2.3 | PDF renderer (A4/A5) using same layout model | 2.0 | 1.3 | 2.6 | P | |
| 2.4 | Share sheet (PDF/image) + WhatsApp deep link with text summary | 1.5 | 1.0 | 1.5 | | |
| 2.5 | Bluetooth thermal print (paired printer in settings, Android print framework fallback) | 2.5 | 1.5 | 3.8 | | Permissions + pairing flow |
| 2.6 | Receipt History screen: search by number/date/salesperson, reprint, share | 2.0 | 1.3 | 2.6 | | |
| 2.7 | Stock Movements screen (owner): filter product/type/account/date, tap sale → session | 2.0 | 1.3 | 2.6 | | Replaces AuditViewer's movement list |
| 2.8 | Audit Events screen: timeline with links to movements/sessions, severity | 2.0 | 1.3 | 2.6 | | New screen |
| 2.9 | Reports: time-zone-aware (business TZ), drill-down (period → sessions → items), working Export (CSV) | 3.0 | 1.3 | 3.9 | | Fix "top product shows ID" |
| 2.10 | Golden-file tests for receipt rendering (58/80 mm, PDF); print on real printer | 2.0 | 1.5 | 3.0 | P | Snapshot tests |
| 2.11 | Report vs raw-data verification tests | 1.5 | 1.3 | 2.0 | P | |

**Phase 2 total: ~33.2 AH** (wall-clock ~18 AH with parallel renderer work)

---

## Phase 3 — Backend Foundation (L) — Cloudflare Workers + D1 + R2

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 3.1 | Create `backend/` folder, `wrangler.jsonc` (dev/staging/prod), TypeScript setup | 1.5 | 1.0 | 1.5 | | |
| 3.2 | D1 migrations: platform tables (businesses, accounts, memberships, invitations, devices, refresh_tokens, recovery_codes, sync_batches, sync_exceptions, change_log, backups, app_versions) | 3.0 | 1.5 | 4.5 | | Composite FKs, triggers, indexes |
| 3.3 | D1 migrations: business tables (products, categories, price_history, stock_movements, sale_sessions, sale_items, receipts, audit_events, counters, memberships_mirror) | 3.5 | 1.5 | 5.3 | | Same shapes as local Room |
| 3.4 | Tenant-scoped DB access layer (only path to D1): injects `business_id` from token | 2.5 | 1.5 | 3.8 | | Critical for isolation |
| 3.5 | Auth routes: `POST /auth/google` (verify ID token, find/create account, return access+refresh tokens) | 2.5 | 1.3 | 3.3 | | Google keys JWKS caching |
| 3.6 | Business registration: create business + owner membership + primary device (code A) + recovery codes in one atomic batch | 2.0 | 1.3 | 2.6 | | |
| 3.7 | Staff invitation: email → code → accept (checks: no other membership, staff < 4) | 2.0 | 1.3 | 2.6 | | |
| 3.8 | Device registration: check-and-insert active ≤ 5; owner immediate, staff pending approval | 2.0 | 1.3 | 2.6 | | Returns device code |
| 3.9 | Token refresh: rotate refresh token, issue new access token (15 min), enforce lease | 2.0 | 1.3 | 2.6 | | |
| 3.10 | Admin routes (separate middleware, separate token): platform dashboard, business directory, device admin, sync monitoring | 3.0 | 1.5 | 4.5 | | No financial data by default |
| 3.11 | Cross-tenant isolation tests on every endpoint (automated sweep in CI) | 3.0 | 1.5 | 4.5 | P | |
| 3.12 | Constraint tests: 5th staff, 6th device, concurrent invite accepts, concurrent device regs | 2.5 | 1.5 | 3.8 | P | |
| 3.13 | Migration tests (D1) on real-shaped data | 2.0 | 1.5 | 3.0 | P | |
| 3.14 | CI pipeline for backend (lint, test, deploy to staging) | 1.5 | 1.0 | 1.5 | | `.github/workflows/backend-ci.yml` |

**Phase 3 total: ~46.8 AH** (wall-clock ~25 AH with parallel test suites)

---

## Phase 4 — Android Auth & Onboarding (M)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 4.1 | Add deps: Credential Manager, Google ID token, WorkManager, HTTP client (Ktor/OkHttp), EncryptedSharedPreferences | 1.0 | 1.0 | 1.0 | | `libs.versions.toml` + `build.gradle.kts` |
| 4.2 | `data/auth/`: `GoogleSignInClient`, `SessionManager` (refresh token encrypted, access token in memory, lease timer) | 3.0 | 1.5 | 4.5 | | Replaces mock `AuthRepository` |
| 4.3 | `data/remote/`: API client, DTOs, token interceptor, error mapping | 2.5 | 1.3 | 3.3 | | |
| 4.4 | Onboarding flow screens: Welcome → Google Sign-In → Business Resolution (none/owner/staff/invited) | 3.5 | 1.5 | 5.3 | | Multi-step Compose flow |
| 4.5 | Register Business screen (name, category, phone, country, currency, TZ, optional address/logo) | 2.0 | 1.3 | 2.6 | | Locale defaults, editable |
| 4.6 | Settings screen: negative-stock policy, receipt footer, printer, offline lease length | 1.5 | 1.0 | 1.5 | | |
| 4.7 | Owner recovery codes: generate, show once, confirm saved, store hashed on server | 1.5 | 1.3 | 2.0 | | |
| 4.8 | Staff/Device management screens (invite, approve, revoke, replace) | 2.5 | 1.3 | 3.3 | | Owner-only |
| 4.9 | **Legacy data adoption**: on first launch, if local data under `default-business` exists → offer "Register & keep data" → rewrite business IDs + placeholder user IDs across all tables, safety copy, mark for upload | 4.0 | 2.0 | 8.0 | | Critical; rehearse on real DB copy |
| 4.10 | Nav graph: onboarding destinations, conditional splash routing | 1.5 | 1.0 | 1.5 | | |
| 4.11 | `PermissionPolicy` now fed by server role; device/staff permissions added | 1.0 | 1.0 | 1.0 | | |
| 4.12 | Tests: fresh onboarding, legacy adoption, revoked device, expired lease, sign-in failure on poor network | 3.0 | 1.5 | 4.5 | P | |

**Phase 4 total: ~38.5 AH** (wall-clock ~22 AH with parallel screen work)

---

## Phase 5 — Synchronisation (L)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 5.1 | `data/sync/`: `SyncWorker` (WorkManager), `OutboxProcessor` (push), `PullApplier` (pull), `SyncState` repo | 4.0 | 1.5 | 6.0 | | |
| 5.2 | Push: batch outbox entries (sale bundles first), send with batch ID, per-record ack, retry with back-off | 3.5 | 1.5 | 5.3 | | D1 batch size limits |
| 5.3 | Backend ingestion: atomic D1 batch per bundle, verify totals & tenant ownership, assign server_seq + running balance, write change_log | 3.0 | 1.5 | 4.5 | | |
| 5.4 | Pull: `GET /sync/pull?cursor=N` → apply in one local transaction, store new cursor | 2.5 | 1.3 | 3.3 | | |
| 5.5 | Exception handling: `STOCK_NEGATIVE`, `PRICE_MISMATCH`, `CLOCK_SKEW`, `REVOKED_DEVICE`, `VALIDATION` → owner-visible Discrepancies screen | 3.0 | 1.5 | 4.5 | | |
| 5.6 | Sync Status screen: chip (Up to date / N pending / Offline / Needs attention), last sync, lease days, rejected bundles list | 2.0 | 1.3 | 2.6 | | |
| 5.7 | Owner sees other devices' last sync times | 1.0 | 1.0 | 1.0 | | |
| 5.8 | Integration tests: kill mid-push, duplicate push, out-of-order, airplane-mode soak, two devices selling last unit, large backlog | 4.0 | 1.5 | 6.0 | P | Simulation harness |
| 5.9 | Two-week pilot at Sunday Hardware (owner + 1 staff device) — **human time, not agent AH** | — | — | — | | Zero lost/duplicated records target |

**Phase 5 total: ~33.2 AH** (wall-clock ~20 AH with parallel test harness)

---

## Phase 6 — Backup & Recovery (M)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 6.1 | Local file backup: single archive (DB snapshot + manifest: businessId, schemaVer, appVer, rowCounts, SHA-256, createdAt) | 2.5 | 1.3 | 3.3 | | |
| 6.2 | Share via system picker (Google Drive, etc.) — no Drive API | 1.0 | 1.0 | 1.0 | | |
| 6.3 | Restore flow: import into empty DB or merge insert-if-absent by ID; verify manifest (businessId, hash, counts, schemaVer) | 3.0 | 1.5 | 4.5 | | New device code after restore |
| 6.4 | Backend: nightly cron → logical export (NDJSON per table + manifest) → R2 `backups/{businessId}/{date}`; rolling window (30 daily + 12 monthly) | 2.5 | 1.3 | 3.3 | | |
| 6.5 | Backup status screen: last backup, last cloud sync, failures, row counts | 1.5 | 1.0 | 1.5 | | |
| 6.6 | Restore tests: fresh device from cloud, fresh device from file, restore twice (no dupes), corrupted file rejection, ledger check after restore | 3.0 | 1.5 | 4.5 | P | |

**Phase 6 total: ~18.1 AH** (wall-clock ~11 AH)

---

## Phase 7 — SANDE Control Centre (M)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 7.1 | `control-centre/` SPA (TypeScript, framework per Appendix A#10), Cloudflare Pages deploy | 3.0 | 1.3 | 3.9 | | |
| 7.2 | Admin auth: Cloudflare Access + Worker middleware, `platform_admins` table, roles | 2.0 | 1.3 | 2.6 | | Separate identity pool |
| 7.3 | Platform dashboard: businesses (active/suspended), accounts, devices, sync health, app versions | 2.5 | 1.3 | 3.3 | | Aggregates only |
| 7.4 | Business directory: details, status, owner/staff, devices, last sync, activate/suspend | 2.5 | 1.3 | 3.3 | | |
| 7.5 | Device & account admin: list, last seen, revoke, investigate, controlled recovery | 2.0 | 1.3 | 2.6 | | |
| 7.6 | Sync monitoring: pending/failed counts, error kinds, oldest unsynced, affected record IDs (not contents) | 2.0 | 1.3 | 2.6 | | |
| 7.7 | Platform monitoring: row counts, storage, D1/R2 usage, Worker errors, backup success | 1.5 | 1.3 | 2.0 | | |
| 7.8 | Admin audit log: every admin action with reason + ticket ref | 1.0 | 1.0 | 1.0 | | |
| 7.9 | Support access: owner-granted, scoped, time-limited, audited, visible in business audit | 2.5 | 1.5 | 3.8 | | `support_access_grants` |
| 7.10 | Tests: admin cannot read financials without grant; grant scope/expiry respected; all actions logged; business token rejected on admin routes | 2.5 | 1.5 | 3.8 | P | |

**Phase 7 total: ~28.9 AH** (wall-clock ~17 AH)

---

## Phase 8 — Hardening & Multi-Business Launch (M)

| # | Task | AH | Risk | Est. AH | P? | Notes |
|---|------|----|------|---------|----|-------|
| 8.1 | Security review, rate limiting, monitoring/alerts, cost alerts | 2.0 | 1.3 | 2.6 | | |
| 8.2 | Move to Workers Paid ($5/mo), configure custom domain | 0.5 | 1.0 | 0.5 | | |
| 8.3 | Release channel + minimum-version enforcement (app_versions table) | 1.0 | 1.0 | 1.0 | | |
| 8.4 | Runbooks (incident, recovery, onboarding new business, support access) | 2.0 | 1.0 | 2.0 | | |
| 8.5 | Privacy policy + data-protection review (Kenya DPA 2019) — legal, not agent | — | — | — | | |
| 8.6 | Load test: synthetic multi-business traffic; penetration-style tests; restore drill | 3.0 | 1.5 | 4.5 | P | |
| 8.7 | Second business onboards with zero manual intervention | 1.0 | 1.3 | 1.3 | | Verification |

**Phase 8 total: ~11.9 AH** (wall-clock ~7 AH)

---

## Summary

| Phase | Est. Agent-Hours | Wall-Clock (with parallelism) | Key Dependency |
|-------|------------------|-------------------------------|----------------|
| 0. Stabilise Baseline | 12.0 | 7 | — |
| 1. Local Data Foundation | 45.6 | 25 | Phase 0 |
| 2. Receipts & Owner Visibility | 33.2 | 18 | Phase 1 |
| 3. Backend Foundation | 46.8 | 25 | Phase 1 (schema shapes) |
| 4. Android Auth & Onboarding | 38.5 | 22 | Phases 1 + 3 |
| 5. Synchronisation | 33.2 | 20 | Phases 3 + 4 |
| 6. Backup & Recovery | 18.1 | 11 | Phase 5 |
| 7. Control Centre | 28.9 | 17 | Phases 3 + 5 |
| 8. Hardening & Launch | 11.9 | 7 | All prior |
| **Total** | **268.2 AH** | **~152 AH** (≈19 agent-days) | |

> **Critical path:** Phase 0 → Phase 1 → Phase 3 → Phase 4 → Phase 5 → Phase 6/7/8  
> Phases 3 and 1 can overlap after Phase 1 schema shapes are agreed (Phase 1.1–1.3).  
> Phase 2 can start once Phase 1 checkout transaction is solid.

---

## Recommended Agent Workflow

1. **Parallelise within phases** — spin up subagents for independent files (entities, DAOs, screens, tests).
2. **Checkpoint after each phase** — run full test suite, verify on device/emulator, commit.
3. **Phase 1 migration rehearsal** — run on a copy of the real Sunday Hardware DB *before* committing the migration; this is the highest-risk single task.
4. **Backend contract first** — `packages/contracts` fixtures should be written alongside Phase 3 D1 migrations so Android sync (Phase 5) has a stable target.
5. **Pilot early** — Phase 5's two-week pilot at Sunday Hardware is the real validation; budget calendar time, not agent AH.

---

## Appendix: Appendix A Decisions (from plan) — Confirm Before Starting

| # | Decision | Plan Default | Your Choice |
|---|----------|--------------|-------------|
| 1 | Offline lease length | 14 days (owner-adjustable to 30) | |
| 2 | Devices per account | 1 active (owner may hold 2) | |
| 3 | Staff receive/adjust stock? | No (owner only) | |
| 4 | Suspended business behaviour | Offline selling continues | |
| 5 | Staff discounts/overrides | Off by default | |
| 6 | Currency/country lock after first sale | Yes | |
| 7 | Voids/returns | Out of scope (schema ready) | |
| 8 | Email OTP provider | Decide later; Google first | |
| 9 | Thermal printer models / ESC/POS library | Confirm at Sunday Hardware | |
| 10 | Control Centre framework | Mainstream TS SPA (your familiarity) | |
| 11 | Backend language | TypeScript on Workers | |
| 12 | Customer records for WhatsApp | Don't store; enter at send time | |
| 13 | Payment integration | Out of scope | |
| 14 | Data protection / privacy policy | Legal review before 2nd business | |

---

**Next step:** Confirm the Appendix A decisions, then begin Phase 0. The first concrete agent task is removing `fallbackToDestructiveMigration()` and adding the explicit migration test harness.
