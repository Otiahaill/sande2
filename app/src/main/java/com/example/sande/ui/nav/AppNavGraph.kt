package com.example.sande.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sande.data.repository.AuthRepository
import com.example.sande.di.SandeApplication
import com.example.sande.domain.PermissionPolicy
import com.example.sande.ui.screens.AuditEventsScreen
import com.example.sande.ui.screens.AuditViewerScreen
import com.example.sande.ui.screens.BackupRestoreScreen
import com.example.sande.ui.screens.CatalogueScreen
import com.example.sande.ui.screens.DashboardScreen
import com.example.sande.ui.screens.MemberListScreen
import com.example.sande.ui.screens.ProductFormScreen
import com.example.sande.ui.screens.ReceiveStockScreen
import com.example.sande.ui.screens.ReceiptHistoryScreen
import com.example.sande.ui.screens.ReplenishmentAlertsScreen
import com.example.sande.ui.screens.ReportsScreen
import com.example.sande.ui.screens.SaleHistoryScreen
import com.example.sande.ui.screens.SalesScreen
import com.example.sande.ui.screens.SettingsScreen
import com.example.sande.ui.screens.SignInScreen
import com.example.sande.ui.screens.SplashScreen
import com.example.sande.ui.screens.StockMovementsScreen
import com.example.sande.ui.screens.StockScreen
import com.example.sande.ui.onboarding.BusinessResolutionScreen
import com.example.sande.ui.onboarding.DeviceApprovalScreen
import com.example.sande.ui.onboarding.InvitationAcceptScreen
import com.example.sande.ui.onboarding.LegacyAdoptionScreen
import com.example.sande.ui.onboarding.RecoveryCodesScreen
import com.example.sande.ui.onboarding.WelcomeScreen
import com.example.sande.ui.onboarding.GoogleSignInScreen
import com.example.sande.ui.settings.ConnectionSettingsScreen
import com.example.sande.ui.screens.SyncStatusScreen
import com.example.sande.ui.theme.SandeTheme
import kotlinx.coroutines.launch

/** Every screen in the app has a route so the drawer can reach all of them. */
object Routes {
    // Onboarding
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"
    const val BUSINESS_RESOLUTION = "business_resolution"
    const val LEGACY_ADOPTION = "legacy_adoption"
    const val RECOVERY_CODES = "recovery_codes"
    const val DEVICE_APPROVAL = "device_approval"
    const val INVITATION_ACCEPT = "invitation_accept"

    // Main app
    const val DASHBOARD = "dashboard"
    const val CATALOGUE = "catalogue"
    const val STOCK = "stock"
    const val STOCK_MOVEMENTS = "stock/movements"
    const val SALES = "sales"
    const val SALE_HISTORY = "sale_history"
    const val RECEIPT_HISTORY = "receipt_history"
    const val REPORTS = "reports"
    const val ALERTS = "alerts"
    const val MEMBERS = "members"
    const val AUDIT = "audit"
    const val AUDIT_EVENTS = "audit/events"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val CONNECTION = "connection"
    const val SYNC_STATUS = "sync_status"
    const val PRODUCT_NEW = "product/new"
    const val PRODUCT_EDIT = "product/edit/{productId}"
    const val RECEIVE_STOCK = "stock/receive"

    fun productEdit(productId: String): String = "product/edit/$productId"
}

private data class DrawerEntry(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val caption: String? = null,
    val children: List<DrawerEntry> = emptyList(),
    val requiresOwner: Boolean = false
)

private data class DrawerSection(val title: String, val entries: List<DrawerEntry>)

/**
 * The drawer is grouped by domain. Pages that are models of other pages
 * (Catalogue, Sales) own the views derived from them as nested children.
 */
private val ownerDrawerSections = listOf(
    DrawerSection(
        title = "Overview",
        entries = listOf(
            DrawerEntry(
                Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard,
                caption = "Totals and recent activity"
            ),
            DrawerEntry(
                Routes.REPORTS, "Reports", Icons.Default.BarChart,
                caption = "Sales and stock summaries"
            )
        )
    ),
    DrawerSection(
        title = "Inventory",
        entries = listOf(
            DrawerEntry(
                Routes.CATALOGUE, "Catalogue", Icons.Default.Inventory2,
                caption = "Product master data",
                children = listOf(
                    DrawerEntry(
                        Routes.STOCK, "Stock Levels", Icons.Default.Storage,
                        caption = "Levels per catalogue product"
                    ),
                    DrawerEntry(
                        Routes.STOCK_MOVEMENTS, "Stock Movements", Icons.Default.History,
                        caption = "Detailed movement ledger"
                    ),
                    DrawerEntry(
                        Routes.ALERTS, "Low Stock Alerts", Icons.Default.Warning,
                        caption = "Replenishment for catalogue items"
                    )
                )
            )
        )
    ),
    DrawerSection(
        title = "Sales",
        entries = listOf(
            DrawerEntry(
                Routes.SALES, "New Sale", Icons.Default.ShoppingCart,
                caption = "Record a sale",
                children = listOf(
                    DrawerEntry(
                        Routes.SALE_HISTORY, "Sale History", Icons.Default.ReceiptLong,
                        caption = "Sales already recorded"
                    ),
                    DrawerEntry(
                        Routes.RECEIPT_HISTORY, "Receipt History", Icons.Default.ReceiptLong,
                        caption = "Print & share receipts"
                    ),
                    DrawerEntry(
                        Routes.AUDIT, "Audit Viewer (Legacy)", Icons.Default.History,
                        caption = "Every stock movement trail"
                    ),
                    DrawerEntry(
                        Routes.AUDIT_EVENTS, "Audit Events", Icons.Default.History,
                        caption = "Detailed audit trail with filters"
                    )
                )
            )
        )
    ),
    DrawerSection(
        title = "Administration",
        entries = listOf(
            DrawerEntry(
                Routes.MEMBERS, "Members", Icons.Default.People,
                caption = "Manage team members",
                requiresOwner = true
            ),
            DrawerEntry(
                Routes.BACKUP, "Backup & Restore", Icons.Default.SettingsBackupRestore,
                caption = "Export and import the database",
                requiresOwner = true
            ),
            DrawerEntry(
                Routes.CONNECTION, "Connection", Icons.Default.Cloud,
                caption = "Backend URL and Google sign-in"
            ),
            DrawerEntry(
                Routes.SYNC_STATUS, "Sync Status", Icons.Default.CloudDone,
                caption = "Cloud synchronisation status"
            ),
            DrawerEntry(
                Routes.SETTINGS, "Settings", Icons.Default.Settings,
                caption = "Account and preferences"
            )
        )
    )
)

private val salespersonDrawerSections = listOf(
    DrawerSection(
        title = "Sales",
        entries = listOf(
            DrawerEntry(
                Routes.SALES, "New Sale", Icons.Default.ShoppingCart,
                caption = "Record a sale"
            ),
            DrawerEntry(
                Routes.SALE_HISTORY, "My Sales", Icons.Default.ReceiptLong,
                caption = "Your sales history"
            )
        )
    ),
    DrawerSection(
        title = "Inventory",
        entries = listOf(
            DrawerEntry(
                Routes.CATALOGUE, "Product Catalogue", Icons.Default.Inventory2,
                caption = "Browse products"
            ),
            DrawerEntry(
                Routes.STOCK, "Stock Levels", Icons.Default.Storage,
                caption = "View stock levels"
            )
        )
    ),
    DrawerSection(
        title = "Account",
        entries = listOf(
            DrawerEntry(
                Routes.SETTINGS, "Settings", Icons.Default.Settings,
                caption = "Account and preferences"
            )
        )
    )
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerAvailable = currentRoute != null && currentRoute != Routes.SPLASH && currentRoute != Routes.SIGN_IN

    val app = LocalContext.current.applicationContext as SandeApplication
    val session by app.authRepository.session.collectAsState()
    // Sign-in is not wired to AuthRepository yet, so a missing session shows the owner menu.
    val sections = if (session?.role == "SALESPERSON") salespersonDrawerSections else ownerDrawerSections

    val goTo: (String) -> Unit = { route ->
        scope.launch { drawerState.close() }
        if (route != currentRoute) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerAvailable,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "sande",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp)
                    )
                    HorizontalDivider()
                    sections.forEach { section ->
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)
                        )
                        section.entries.forEach { entry ->
                            DrawerItem(entry, currentRoute, indent = 0.dp, onClick = goTo)
                            entry.children.forEach { child ->
                                DrawerItem(child, currentRoute, indent = 16.dp, onClick = goTo)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    ) {
        AppNavHost(navController = navController, onOpenDrawer = { scope.launch { drawerState.open() } })
    }
}

@Composable
private fun DrawerItem(
    entry: DrawerEntry,
    currentRoute: String?,
    indent: androidx.compose.ui.unit.Dp,
    onClick: (String) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(entry.label) },
        icon = { Icon(imageVector = entry.icon, contentDescription = null) },
        selected = currentRoute == entry.route,
        onClick = { onClick(entry.route) },
        modifier = Modifier.padding(start = indent, end = 12.dp)
    )
}

@Composable
private fun AppNavHost(navController: NavHostController, onOpenDrawer: () -> Unit) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onNavigateToSignIn = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SIGN_IN) {
            GoogleSignInScreen(
                onBack = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BUSINESS_RESOLUTION) {
            BusinessResolutionScreen(
                onBack = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.BUSINESS_RESOLUTION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LEGACY_ADOPTION) {
            LegacyAdoptionScreen(
                onBack = {
                    navController.navigate(Routes.BUSINESS_RESOLUTION) {
                        popUpTo(Routes.LEGACY_ADOPTION) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LEGACY_ADOPTION) { inclusive = true }
                    }
                },
                onComplete = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LEGACY_ADOPTION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.RECOVERY_CODES) {
            RecoveryCodesScreen(
                onContinue = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.RECOVERY_CODES) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DEVICE_APPROVAL) {
            DeviceApprovalScreen(
                onApproved = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.DEVICE_APPROVAL) { inclusive = true }
                    }
                },
                onRetry = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.DEVICE_APPROVAL) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.INVITATION_ACCEPT) {
            InvitationAcceptScreen(
                onBack = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.INVITATION_ACCEPT) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.CATALOGUE) {
            CatalogueScreen(
                onOpenDrawer = onOpenDrawer,
                onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) },
                onEditProduct = { id -> navController.navigate(Routes.productEdit(id)) }
            )
        }
        composable(Routes.STOCK) {
            StockScreen(
                onOpenDrawer = onOpenDrawer,
                onReceiveStock = { navController.navigate(Routes.RECEIVE_STOCK) }
            )
        }
        composable(Routes.SALES) {
            SalesScreen(onOpenDrawer = onOpenDrawer, onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) })
        }
        composable(Routes.SALE_HISTORY) {
            SaleHistoryScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.RECEIPT_HISTORY) {
            ReceiptHistoryScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.REPORTS) {
            ReportsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.ALERTS) {
            ReplenishmentAlertsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.MEMBERS) {
            MemberListScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.AUDIT) {
            AuditViewerScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.AUDIT_EVENTS) {
            AuditEventsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.STOCK_MOVEMENTS) {
            StockMovementsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.BACKUP) {
            BackupRestoreScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.CONNECTION) {
            ConnectionSettingsScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.SYNC_STATUS) {
            SyncStatusScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.PRODUCT_NEW) {
            ProductFormScreen(
                productId = null,
                onOpenDrawer = onOpenDrawer,
                onProductSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PRODUCT_EDIT,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { entry ->
            ProductFormScreen(
                productId = entry.arguments?.getString("productId"),
                onOpenDrawer = onOpenDrawer,
                onProductSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.RECEIVE_STOCK) {
            ReceiveStockScreen(
                onOpenDrawer = onOpenDrawer,
                onReceived = { navController.popBackStack() }
            )
        }
    }
}
