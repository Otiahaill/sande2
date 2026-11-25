package com.example.sande.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.sande.ui.nav.AppNavGraph
import com.example.sande.ui.theme.SandeTheme

/**
 * The launcher activity.
 *
 * It is the entry point of the app, so everything it depends on is built
 * defensively: the token store falls back to plain preferences when the
 * keystore is unavailable, the database is recreated when the file on disk
 * cannot be opened, and the session restore reports failures instead of
 * throwing. Anything that still escapes is recorded by
 * [com.example.sande.ui.StartupCrashHandler] so it can be found in the
 * app's crash-reports folder.
 */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandeTheme {
                AppNavGraph()
            }
        }
    }
}
