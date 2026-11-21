# sande-2-online — Testing

## Test Framework
- **Unit tests:** JUnit 4 + Truth + AndroidX Test + Room Testing
- **Instrumentation tests:** Espresso (androidTest)
- **Backend tests:** Vitest (Node.js)

## Available Test Commands (Android)
| Command | Description |
|---------|-------------|
| `./gradlew :app:testDebugUnitTest` | Run unit tests |
| `./gradlew :app:assembleDebug` | Build debug APK (verifies Kotlin compilation) |
| `./gradlew :app:assembleRelease` | Build release APK |
| `./gradlew connectedAndroidTest` | Run instrumentation tests |

**Note:** Local Gradle builds may fail on this device due to dependency download timeouts. CI (GitHub Actions) is the canonical build path.

## Available Test Commands (Backend)
| Command | Description |
|---------|-------------|
| `npm run test` | Run Vitest unit tests |
| `npm run typecheck` | TypeScript type check |
| `npm run lint` | ESLint |

## Unit Tests (Android)
| Test File | Coverage |
|-----------|----------|
| DatabaseMigrationTest.kt | Room migrations v2→3, 3→4, full v2→v6 path |
| LedgerBalanceTest.kt | Sum of signed deltas = product balance |
| CheckoutAtomicityTest.kt | Atomic sale (all-or-nothing) |
| CheckoutRepositoryTest.kt | All 7 tables written correctly in transaction |
| FullMigrationTest.kt | Full migration 2→6 with real data, balance verification |
| ProductPriceHistoryTest.kt | Price updates → history + audit events |
| DraftSalePersistenceTest.kt | Cart survives app kill |
| ReceiptRendererTest.kt | Text/HTML/thermal output, currency formatting |
| ReportsTest.kt | Totals, voided exclusion, top products, hourly breakdown |

## Backend Unit Tests
| Test File | Coverage |
|-----------|----------|
| utils.test.ts | hashToken, generateSecureToken, generateInvitationCode, generateRecoveryCode |

## Critical User Workflows (manual)
1. **Build APK:** `./gradlew :app:assembleDebug` produces debug APK
2. **Install APK:** `pm install -r app-debug.apk` succeeds
3. **Launch app:** SplashScreen → Welcome → Google Sign-In → Business Resolution
4. **Onboard business:** Register new business → Recovery Codes → Dashboard
5. **Record sale:** Add items to cart → Checkout → Receipt printed
6. **Receive stock:** Select product → Enter quantity → Record In
7. **View reports:** Reports screen → Period selector → Totals/Top products/Hourly
8. **Test connection:** Settings → Connection → Enter backend URL → Test connection

## Offline Verification
- App works without network (Room database local)
- Sync only when online (WorkManager scheduled)
- No Firebase/Google service imports in source (except Google Sign-In OAuth)

## Regression Tests
- No automated regression test suite yet
- Manual verification recommended after each build

## Known Test Failures
- None — tests not yet executed due to Gradle dependency issues on this device

## Manual Testing Checklist
- [ ] `./gradlew :app:testDebugUnitTest` succeeds
- [ ] Debug APK installs via `pm install -r`
- [ ] SplashScreen launches; onboarding flow
- [ ] Google Sign-In works (requires credentials)
- [ ] Business registration works (requires backend)
- [ ] Sale recording creates receipt
- [ ] Stock movements track correctly
- [ ] Reports show correct totals
- [ ] Receipt printing/sharing works
- [ ] Release APK verifiable with `apksigner verify`

## Build Verification
- Success criteria: APK path printed by gradle, no compile errors
- CI builds Android APK + runs backend tests + deploys Worker

## Notes
- Testing cannot fully execute without Gradle/android-sdk setup
- Backend tests require Node.js + vitest (available in this environment)
- Cloudflare/Google credentials needed for end-to-end sync testing
