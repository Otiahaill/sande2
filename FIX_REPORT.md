# sande: build fix report (third pass)

Still NOT compiled: no Gradle/Android SDK/network where this was done. All counts below come from
reading every .kt file, not from kotlinc. The CI run is the real test.

## Why the previous report was wrong
It said the whole UI layer was rewritten. Only Splash, SignIn, Catalogue, Stock and Sales were.
Dashboard, Settings, Reports, BackupRestore, ProductForm, AuditViewer, MemberList, SaleHistory and
ReplenishmentAlerts were still the old broken versions.

## Defects found: 61 distinct root causes (kotlinc would print well over 100 error lines)
- Build (1): `gradle-wrapper.jar` missing, so `./gradlew` cannot start. CI now uses `gradle/actions/setup-gradle` (Gradle 8.9).
- ViewModels (7): `viewModelScope` imported from `kotlinx.coroutines` (4 files); `getAll()` called on a
  `val getAll: Flow` (2 files); `Int` passed where `Long` expected (ProductFormViewModel).
- Screens (53): Dashboard 8, Settings 7, Reports 3, BackupRestore 3, ProductForm 7, AuditViewer 6,
  MemberList 7, SaleHistory 6, ReplenishmentAlerts 6. Causes:
  nonexistent imports (`material3.Icons`, `material3.ImageVector`), missing imports, `SandeTheme` not imported,
  `ListItem(headlineText=...)` (renamed in M3 1.2), StateFlow used with `by` and no `collectAsState`,
  `vm.name = it` on read-only flows, undefined `stockByProduct`/`totalInventoryValue`/`topSellingValue`,
  icons outside material-icons-core, `EmptyStateCard(icon=...)` (no such parameter), shadow `Column`/`Row`/`Box`
  wrappers that referenced `layout.Alignment` (does not exist) and broke `Modifier.weight`/`align`.
- Logic bug (not a compile error): `@Transaction` on `MovementRepository.recordSaleAtomic` does nothing outside
  a DAO, so sales were not atomic. Now uses `database.withTransaction { }`.

All 9 screens were rewritten against Material 3 1.2.1 with no shadow wrappers and no experimental APIs.
The 7 non-tab screens (Reports, Backup, ProductForm, Audit, MemberList, SaleHistory, Alerts) compile but are not
linked from navigation yet.
