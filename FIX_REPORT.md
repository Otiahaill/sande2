# sande-2-online — Fix Report (fourth pass)

**Verification model:** this project is built **only** by GitHub Actions (the device cannot
resolve Gradle dependencies). Nothing below was produced by a local compile. Every claim is
either (a) read directly from source, (b) proved by a static checker in `tools/`, or
(c) taken from a real GitHub Actions run log. Claims of the form "compiles" are only made
where a CI run actually proved it.

## What was actually wrong with the previous CI failure

Run #26 (`6c4e706`) failed in **71 seconds** with a single error:

```
app/build.gradle.kts line 11: val localProperties = java.util.Properties().apply {
                                                    ^ Unresolved reference: util
```

In a Kotlin DSL script the bare identifier `java` resolves to **Gradle's `java` extension**,
which shadows the `java` package. Kotlin and KSP never ran, so the previously reported
`MissingType` KSP error was never actually reached by that run.

Fixed with an explicit `import java.util.Properties`.

## Defects found and fixed in this pass

### Build / CI (4)
| Defect | Fix |
|---|---|
| `java.util.Properties` unresolved in `app/build.gradle.kts` | explicit `import java.util.Properties` |
| Secrets written into `local.properties` with `cat >`, destroying `sdk.dir` | secrets passed as Gradle properties (`-PSANDE_GOOGLE_CLIENT_ID=…`) |
| `configValue()` cast a non-String property with `as String?` (throws) | `as? String` + `takeIf { it.isNotBlank() }` |
| Backend CI workflow lived at `backend/.github/workflows/` — GitHub **ignores** workflows in subdirectories, so it never ran | moved to `.github/workflows/backend.yml`; deployment split into a manual `backend-deploy.yml` |

### Compile errors in app source (9 files)
| File | Defect |
|---|---|
| `LegacyAdoptionManager.kt` | imported the deleted `StockAdjustment` entity; called `businessDao()`, `stockAdjustmentDao()`, `countByBusiness()`, `getByBusiness()` — **none of which existed**; `Outbox.copy(businessId=…)` on an entity with no `businessId`; `Business.copy(deletedAt=…)` on an entity with no `deletedAt`; `saveTokens(…).await()` on a `Unit`-returning function |
| `SyncWorker.kt` | `import com.example.sande.data.api.ApiClient` — package is `data.remote` |
| `SessionManager.kt` | `GoogleAuthRequest` used without an import |
| `AppDatabase.kt` | `DeviceRegistrationDao` used without an import |
| `ReportsTest.kt` | `import com.example.sande.data.repository.ReportsViewModel` — it lives in `ui.viewmodel` |
| `FullMigrationTest.kt` | imported the deleted `StockAdjustment` entity |
| `DatabaseMigrationTest.kt`, `FullMigrationTest.kt` | used `InstrumentationRegistry` with no import, and lived in `src/test` where it can never run |

`LegacyAdoptionManager` was rewritten against the schema that actually exists. Supporting
DAO methods were added: `countByBusiness` / `getAllByBusiness` on `ProductDao`, `MovementDao`,
`MemberDao`, `OutboxEntryDao`; `update` on `MemberDao` and `SaleHeaderDao`; and a new
`BusinessDao` (the `Business` entity had no DAO at all).

### Room schema / migration (6 — all runtime crashes, invisible to `assembleDebug`)
| Defect | Effect |
|---|---|
| `MembershipMirror` declared `Index("syncedAt")` on a field named `lastSyncedAt`; `MIGRATION_4_5` created the index on the same non-existent column | KSP error **and** `no such column: syncedAt` at migration time |
| `MIGRATION_4_5` created `stock_movements`, `sale_items` and `product_price_history` **without the foreign keys** their entities declare | Room schema validation rejects the migrated database |
| `MIGRATION_4_5` copied `outbox` → `outbox_entries` selecting `businessId`, `payloadJson`, `attemptCount`, `lastError` — the legacy `outbox` table has `payload`, `attempts` and **no `businessId`** | `no such column: businessId` aborts the whole migration |
| `MIGRATION_4_5` inserted `sale_items.unitPriceCents` / `totalCents` from a nullable source and `categoryName` from a `LEFT JOIN` | `NOT NULL constraint failed` aborts the migration |
| `movements → stock_movements` copy had no `JOIN products`, so a dangling `productId` would violate the new FK | migration aborts |
| `stock_adjustments` table is now orphaned (entity removed in Phase 7) | harmless extra table; Room ignores unknown tables |

### Security / hygiene (4)
| Defect | Fix |
|---|---|
| Ktor client logged at `LogLevel.ALL`, writing OAuth tokens and business records to logcat | `LogLevel.INFO` in debug, `NONE` in release, plus `sanitizeHeader` for `Authorization` |
| `backend/node_modules` (5 120 files), `control-centre/node_modules` (139) and `control-centre/dist` (8) were committed | untracked; `node_modules/`, `dist/`, `.wrangler/` added to `.gitignore` |
| Cloudflare + Google credentials were committed in `study_session.md` | removed; only the non-secret project ID and the debug signing fingerprint remain |
| `API_BASE_URL` fallback still pointed at `your-subdomain.workers.dev` | now injected from `BuildConfig` / CI secrets; the placeholder remains only as a last-resort fallback |

## Migration path, established from git history

`git log -S "version = N"` over the whole history shows the schema versions that **actually
existed**: `2` (1921ab1), `3` (9c9296c, a5064ff, 9972c68) and `6` (28e22b1). Versions **1, 4
and 5 never existed** — so:

- `MIGRATION_1_2` is **not needed**; version 1 was never written.
- `MIGRATION_2_3` covers the v2 → v3 step.
- `MIGRATION_3_4` and `MIGRATION_5_6` are deliberate no-ops that exist so Room can chain
  v3 → v4 → v5 → v6. All schema work lives in `MIGRATION_4_5`.

A published user (the `sande2` v1.0.0 APK) has a **v3** database. `tools/compare_entities.py`
confirms every table that existed at v3 (`businesses`, `draft_sales`, `members`, `movements`,
`outbox`, `product_categories`, `products`, `sale_headers`) has **identical columns** in the
current v6 entities, and `MIGRATION_4_5` creates all 13 new tables. The upgrade path is
therefore v3 → v6 and nothing else.

## Static verification added

`tools/check_imports.py` — resolves every `com.example.sande.*` import against the project and
flags any project symbol used without an import.
`tools/check_dao_calls.py` — resolves every `database.xDao().method()` call against the DAO
interfaces.
`tools/compare_entities.py` — diffs Room entity column sets between two git revisions.

All three currently report **clean**. They exist because this project has no local compiler.

## Still outstanding (not fixed here)

- The Room schema JSON files (`app/schemas/6.json`) are generated by KSP during the CI build.
  They must be committed before `MigrationTestHelper` can run, because the helper loads the
  exported schemas from disk. The instrumented migration tests have therefore been moved to
  `app/src/androidTest/` and are **not yet wired into CI**.
- `wrangler.toml` still carries `PLACEHOLDER_*_ID` for the `staging` and `production`
  environments, so only the default (dev) worker can be deployed.
- `MembershipMirror`, `DeviceRegistration` and the sync cursor/exception tables are written by
  the app but no round-trip test proves the payloads match the backend contract.
- `backend/src/utils.test.ts` is the only backend test file; the route handlers have no tests.
