package com.example.sande.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
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
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sande.ui.screens.AuditViewerScreen
import com.example.sande.ui.screens.BackupRestoreScreen
import com.example.sande.ui.screens.CatalogueScreen
import com.example.sande.ui.screens.DashboardScreen
import com.example.sande.ui.screens.MemberListScreen
import com.example.sande.ui.screens.ProductFormScreen
import com.example.sande.ui.screens.ReceiveStockScreen
import com.example.sande.ui.screens.ReplenishmentAlertsScreen
import com.example.sande.ui.screens.ReportsScreen
import com.example.sande.ui.screens.SaleHistoryScreen
import com.example.sande.ui.screens.SalesScreen
import com.example.sande.ui.screens.SettingsScreen
import com.example.sande.ui.screens.SignInScreen
import com.example.sande.ui.screens.SplashScreen
import com.example.sande.ui.screens.StockScreen
import kotlinx.coroutines.launch

/** Every screen in the app has a route so the drawer can reach all of them. */
object Routes {
    const val SPLASH = "splash"
    const val SIGN_IN = "sign_in"
    const val DASHBOARD = "dashboard"
    const val CATALOGUE = "catalogue"
    const val STOCK = "stock"
    const val SALES = "sales"
    const val SALE_HISTORY = "sale_history"
    const val REPORTS = "reports"
    const val ALERTS = "alerts"
    const val MEMBERS = "members"
    const val AUDIT = "audit"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
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
    val children: List<DrawerEntry> = emptyList()
)

private data class DrawerSection(val title: String, val entries: List<DrawerEntry>)

/**
 * The drawer is grouped by domain, and pages that are models of other pages
 * (Catalogue, Sales) own the views derived from them as nested children.
 */
private val drawerSections = listOf(
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
                        Routes.STOCK, "Stock", Icons.Default.Storage,
                        caption = "Levels per catalogue product"
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
                Routes.SALES, "Sales", Icons.Default.ShoppingCart,
                caption = "Record a sale",
                children = listOf(
                    DrawerEntry(
                        Routes.SALE_HISTORY, "Sale History", Icons.Default.ReceiptLong,
                        caption = "Sales already recorded"
                    ),
                    DrawerEntry(
                        Routes.AUDIT, "Audit Viewer", Icons.Default.History,
                        caption = "Every stock movement trail"
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
                caption = "Who can use the app"
            ),
            DrawerEntry(
                Routes.BACKUP, "Backup & Restore", Icons.Default.SettingsBackupRestore,
                caption = "Export and import the database"
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
    val drawerAvailable = currentRoute != null &&
        currentRoute != Routes.SPLASH &&
        currentRoute != Routes.SIGN_IN

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerAvailable,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "sande",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp)
                )
                HorizontalDivider()
                drawerSections.forEach { section ->
                    Text(
                        text = section.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)
                    )
                    section.entries.forEach { entry ->
                        DrawerItem(
                            entry = entry,
                            currentRoute = currentRoute,
                            onSelect = { route ->
                                scope.launch { drawerState.close() }
                                if (currentRoute != route) {
                                    navController.navigate(route) { launchSingleTop = true }
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                DrawerItem(
                    entry = DrawerEntry(
                        Routes.SETTINGS, "Settings", Icons.Default.Settings,
                        caption = "Account and app preferences"
                    ),
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        scope.launch { drawerState.close() }
                        if (currentRoute != route) {
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("Sign out") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.SIGN_IN) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp)
                )
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
    onSelect: (String) -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(entry.icon, contentDescription = null) },
        label = {
            Column {
                Text(entry.label)
                if (entry.caption != null) {
                    Text(
                        text = entry.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        selected = currentRoute == entry.route,
        onClick = { onSelect(entry.route) },
        modifier = Modifier.padding(start = 16.dp, end = 12.dp)
    )
    entry.children.forEach { child ->
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = child.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = {
                Column {
                    Text(child.label)
                    if (child.caption != null) {
                        Text(
                            text = child.caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            selected = currentRoute == child.route,
            onClick = { onSelect(child.route) },
            modifier = Modifier.padding(start = 52.dp, end = 12.dp)
        )
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, onOpenDrawer: () -> Unit) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
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
            SalesScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.SALE_HISTORY) {
            SaleHistoryScreen(onOpenDrawer = onOpenDrawer)
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
        composable(Routes.BACKUP) {
            BackupRestoreScreen(onOpenDrawer = onOpenDrawer)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenDrawer = onOpenDrawer,
                onSignedOut = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
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
