# Sande Project - Current and Main Goals

## Main Goal
Upgrade the existing inventory application (sande) into a fully functional, local-first inventory management and point-of-sale system.

## Current Goal
Fix all compilation errors in the GitHub Actions build so the project compiles successfully. The last CI build (Run #49) failed with:
- ReportsScreen.kt: SummaryCard modifier weight usage and unresolved `weight` reference
- SalesScreen.kt: `weight` import conflict (it's internal to RowScope/ColumnScope)

Immediate fixes needed:
1. Remove `import androidx.compose.foundation.layout.weight` from SalesScreen.kt (it's a RowScope/ColumnScope member, not importable)
2. Remove `Modifier.weight(1f)` from SummaryCard in ReportsScreen.kt — SummaryCard is a top-level composable with no Row/Column scope. Pass `modifier` parameter instead.
3. Fix `Spacer(modifier = Modifier.weight(1f))` in SalesScreen.kt — it's inside a Column scope but `weight` import was incorrectly added at file level

## Technology Stack
- Kotlin + Jetpack Compose + Material 3
- Room (SQLite) with KSP for offline-first local database
- AGP 8.5 / Kotlin 1.9.23 / compileSdk 34
- Build: Gradle via GitHub Actions CI
- Package: com.example.sande

## Key Directories
- sande/app/src/main/java/com/example/sande/ui/screens/ — All Compose screens
- sande/app/src/main/java/com/example/sande/ui/viewmodel/ — ViewModels
- sande/app/src/main/java/com/example/sande/data/ — Room entities, DAOs, repositories
- sande/app/src/main/java/com/example/sande/ui/nav/AppNavGraph.kt — Navigation graph
- sande/app/src/main/java/com/example/sande/di/SandeApplication.kt — DI / repositories
