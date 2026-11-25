package com.example.sande.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sande.ui.nav.AppNavGraph
import com.example.sande.ui.theme.SandeTheme

/**
 * The launcher activity.
 *
 * It is the entry point of the app, so it must always bring up a window: if
 * the navigation graph cannot be built, the user gets an explanation on
 * screen rather than the app vanishing with no way to open it again.
 */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandeTheme {
                AppRoot()
            }
        }
    }
}

/**
 * Builds the application UI, falling back to an error screen if anything
 * goes wrong while the graph is composed.
 */
@Composable
private fun AppRoot() {
    try {
        AppNavGraph()
    } catch (e: Throwable) {
        StartupFailureScreen(e)
    }
}

/** Shown when the app could not start; it says what went wrong. */
@Composable
private fun StartupFailureScreen(error: Throwable) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "SANDE could not start",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = error.message ?: error::class.java.simpleName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Details were written to the app's crash-reports folder.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
