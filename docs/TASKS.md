# TASKS — sande 24-hour sprint

## Phase 1 — Hours 0–4: Foundation & spec (M0/M1 compressed)
- [ ] Feasibility probe (ARCH aarch64, no JDK → Route B CI)
- [ ] Decisions U1–U9 recorded in DECISIONS.md
- [ ] Package name confirmed
- [ ] PROJECT_STATUS.md baseline (this file)

## Phase 2 — Hours 4–8: Project skeleton & UI shell (M2/M3 compressed)
- [ ] Gradle root + app module build files
- [ ] libs.versions.toml (Kotlin, Compose BOM, Room, Hilt, WorkManager, Firebase BoM)
- [ ] settings.gradle.kts, build.gradle.kts (root + app)
- [ ] AndroidManifest.xml (minSdk 24, targetSdk 36, network, fingerprint)
- [ ] google-services.json placeholder (owner provides real one)
- [ ] Material 3 theme (light/dark), 48dp touch targets, Compose
- [ ] Navigation Compose shell with placeholder screens per role
- [ ] GitHub Actions workflow: testDebugUnitTest + assembleDebug → artifact

## Phase 3 — Hours 8–14: Database & catalogue (M4/M5 compressed)
- [ ] Room entities: business, member, product, movement, outbox, audit
- [ ] Room DAOs + insert-only movement triggers
- [ ] stock_balance view (sum of movements)
- [ ] Firebase Auth sign-in / sign-out / session persistence
- [ ] PermissionPolicy class (owner / manager / salesperson)
- [ ] Catalogue screens: products, categories, stock view
- [ ] Receiving & adjustments screens

## Phase 4 — Hours 14–20: Sales recording (M6 compressed)
- [ ] Cart UI (add product, qty, subtotal)
- [ ] Atomic sale transaction (ledger movements written in one Room transaction)
- [ ] Void sale
- [ ] Sale history screen
- [ ] Receipt numbers: {prefix}-{deviceShort}-{counter}
- [ ] Stock policy WARN on negative stock (alert, not block)

## Phase 5 — Hours 20–24: Sync stub, build, smoke test (M7 stub + build)
- [ ] Outbox model + push/pull interface (stubbed for MVP)
- [ ] Sync status chip on shell
- [ ] Debug APK via CI (or local if probe improves)
- [ ] Install on device, record a sale in airplane mode, verify it survives app kill/restart
- [ ] Runbook + known-issues + deferred-scope list
