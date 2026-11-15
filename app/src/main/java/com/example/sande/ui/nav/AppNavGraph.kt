package com.example.sande.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sande.ui.screens.CatalogueScreen
import com.example.sande.ui.screens.DashboardScreen
import com.example.sande.ui.screens.SalesScreen
import com.example.sande.ui.screens.SettingsScreen
import com.example.sande.ui.screens.SignInScreen
import com.example.sande.ui.screens.SplashScreen
import com.example.sande.ui.screens.StockScreen

private enum class Stage { SPLASH, SIGN_IN, SHELL }

private class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Dashboard", Icons.Default.Home),
    Tab("Catalogue", Icons.Default.Menu),
    Tab("Stock", Icons.Default.Info),
    Tab("Sales", Icons.Default.ShoppingCart),
    Tab("Settings", Icons.Default.Settings)
)

@Composable
fun AppNavGraph() {
    var stage by rememberSaveable { mutableStateOf(Stage.SPLASH) }
    when (stage) {
        Stage.SPLASH -> SplashScreen(onFinished = { stage = Stage.SIGN_IN })
        Stage.SIGN_IN -> SignInScreen(onSignedIn = { stage = Stage.SHELL })
        Stage.SHELL -> Shell(onSignedOut = { stage = Stage.SIGN_IN })
    }
}

@Composable
private fun Shell(onSignedOut: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val m = Modifier.padding(innerPadding)
        when (selected) {
            0 -> DashboardScreen(modifier = m)
            1 -> CatalogueScreen(modifier = m)
            2 -> StockScreen(modifier = m)
            3 -> SalesScreen(modifier = m)
            else -> SettingsScreen(onSignedOut = onSignedOut, modifier = m)
        }
    }
}
