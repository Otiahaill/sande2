package com.example.sande.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sande.data.repository.AuthRepository
import com.example.sande.domain.PermissionPolicy
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
import com.example.sande.ui.screens.StockAdjustmentScreen
import com.example.sande.ui.theme.SandeTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectAsState

object Routes {
    const val SPLASH = "splash"
    const val SIGN_IN = "sign_in"
    const val DASHBOARD = "dashboard"
    const val CATALOGUE = "catalogue"
    const val STOCK = "stock"
    const val STOCK_ADJUSTMENT = "stock/adjust"
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
    val title: String,
    val icon: ImageVector,
    val route: String,
    val caption: String? = null,
    val children: List<DrawerEntry> = emptyList(),
    val requiresOwner: Boolean = false
)

private data class DrawerSection(
    val title: String,
    val entries: List<DrawerEntry>
)

private val ownerDrawerSections = listOf(
    DrawerSection(
        title = "Overview",
        entries = listOf(
            DrawerEntry("Dashboard", Icons.Default.Dashboard, Routes.DASHBOARD, "Totals and recent activity"),
            DrawerEntry("Reports", Icons.Default.BarChart, Routes.REPORTS, "Sales and stock summaries")
        )
    ),
    DrawerSection(
        title = "Inventory",
        entries = listOf(
            DrawerEntry(
                "Catalogue", Icons.Default.Inventory2, Routes.CATALOGUE,
                "Product master data",
                children = listOf(
                    DrawerEntry("Stock", Icons.Default.Storage, Routes.STOCK, "Levels per product"),
                    DrawerEntry("Receive Stock", Icons.Default.ShoppingCart, Routes.RECEIVE_STOCK, "Receive items"),
                    DrawerEntry("Adjust Stock", Icons.Default.Storage, Routes.STOCK_ADJUSTMENT, "Adjust stock"),
                    DrawerEntry("Low Stock Alerts", Icons.Default.Warning, Routes.ALERTS, "Replenishment alerts")
                )
            )
        )
    ),
    DrawerSection(
        title = "Sales",
        entries = listOf(
            DrawerEntry(
                "Sales", Icons.Default.ShoppingCart, Routes.SALES,
                "Record a sale",
                children = listOf(
                    DrawerEntry("Sale History", Icons.Default.ReceiptLong, Routes.SALE_HISTORY, "Sales already recorded"),
                    DrawerEntry("Audit Viewer", Icons.Default.History, Routes.AUDIT, "Stock movement trail")
                )
            )
        )
    ),
    DrawerSection(
        title = "Administration",
        entries = listOf(
            DrawerEntry("Members", Icons.Default.People, Routes.MEMBERS, "Manage team members", requiresOwner = true),
            DrawerEntry("Backup & Restore", Icons.Default.SettingsBackupRestore, Routes.BACKUP, "Export/import DB", requiresOwner = true),
            DrawerEntry("Settings", Icons.Default.Settings, Routes.SETTINGS, "App settings")
        )
    )
)

private val salespersonDrawerSections = listOf(
    DrawerSection(
        title = "Sales",
        entries = listOf(
            DrawerEntry("New Sale", Icons.Default.ShoppingCart, Routes.SALES, "Record a sale"),
            DrawerEntry("My Sales", Icons.Default.ReceiptLong, Routes.SALE_HISTORY, "Your sales history")
        )
    ),
    DrawerSection(
        title = "Inventory",
        entries = listOf(
            DrawerEntry("Product Catalogue", Icons.Default.Inventory2, Routes.CATALOGUE, "Browse products"),
            DrawerEntry("Stock Levels", Icons.Default.Storage, Routes.STOCK, "View stock levels")
        )
    )
)

@Composable
fun AppNavGraph(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val session by authRepository.session.collectAsState()
    val isOwner = session?.role == "OWNER"
    val isSignedIn = session != null

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentRouteStr = currentRoute?.destination?.route
    val drawerAvailable = currentRouteStr != null &&
        currentRouteStr != Routes.SPLASH &&
        currentRouteStr != Routes.SIGN_IN

    // Choose which drawer sections based on role
    val sections = when {
        isOwner -> ownerDrawerSections
        isSignedIn && !isOwner -> salespersonDrawerSections
        else -> emptyList()
    }

    SandeTheme {
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
                    sections.forEach { section ->
                        Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            section.entries.forEach { entry ->
                                DrawerSectionItem(entry, navController, onOpenDrawer = {
                                    scope.launch { drawerState.open() }
                                })
                            }
                        }
                    }
                }
            }
        ) {
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
                        authRepository = authRepository,
                        onSignedIn = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onNavigateTo = { route -> navController.navigate(route) }
                    )
                }
                composable(Routes.CATALOGUE) {
                    CatalogueScreen(
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) },
                        onEditProduct = { id -> navController.navigate(Routes.productEdit(id)) }
                    )
                }
                composable(Routes.STOCK) {
                    StockScreen(
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onReceiveStock = { navController.navigate(Routes.RECEIVE_STOCK) }
                    )
                }
                composable(Routes.STOCK_ADJUSTMENT) {
                    StockAdjustmentScreen(
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onStockAdjusted = { navController.popBackStack() }
                    )
                }
                composable(Routes.SALES) {
                    SalesScreen(
                        authRepository = authRepository,
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) }
                    )
                }
                composable(Routes.SALE_HISTORY) {
                    SaleHistoryScreen(
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        authRepository = authRepository
                    )
                }
                composable(Routes.REPORTS) {
                    ReportsScreen(onOpenDrawer = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.ALERTS) {
                    ReplenishmentAlertsScreen(onOpenDrawer = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.MEMBERS) {
                    MemberListScreen(onOpenDrawer = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.AUDIT) {
                    AuditViewerScreen(onOpenDrawer = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.BACKUP) {
                    BackupRestoreScreen(onOpenDrawer = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenDrawer = { navController.popBackStack() },
                        authRepository = authRepository
                    )
                }
                composable(Routes.PRODUCT_NEW) {
                    ProductFormScreen(
                        productId = null,
                        authRepository = authRepository,
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
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
                        authRepository = authRepository,
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onProductSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(Routes.RECEIVE_STOCK) {
                    ReceiveStockScreen(
                        authRepository = authRepository,
                        onOpenDrawer = { navController.navigate(Routes.SETTINGS) },
                        onReceived = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionItem(
    entry: DrawerEntry,
    navController: NavHostController,
    onOpenDrawer: () -> Unit
) {
    var expanded by remember { mutableStateOf(entry.children.isNotEmpty()) }
    
    if (entry.children.isEmpty()) {
        NavigationDrawerItem(
            icon = { Icon(entry.icon, contentDescription = entry.title) },
            label = {
                Column {
                    Text(entry.title)
                    entry.caption?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            selected = false,
            onClick = {
                navController.navigate(entry.route) {
                    popUpTo(Routes.DASHBOARD) { saveState = true }
                    launchSingleTop = true
                }
                onOpenDrawer()
            },
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    } else {
        NavigationDrawerItem(
            icon = { Icon(entry.icon, contentDescription = entry.title) },
            label = {
                Column {
                    Text(entry.title)
                    entry.caption?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            selected = false,
            onClick = {
                expanded = !expanded
            },
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (expanded) {
            entry.children.forEach { child ->
                NavigationDrawerItem(
                    icon = { Icon(child.icon, contentDescription = child.title) },
                    label = {
                        Column {
                            Text(child.title)
                            child.caption?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    selected = false,
                    onClick = {
                        navController.navigate(child.route) {
                            popUpTo(Routes.DASHBOARD) { saveState = true }
                            launchSingleTop = true
                        }
                        onOpenDrawer()
                    },
                    modifier = Modifier
                        .padding(start = 24.dp, horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
