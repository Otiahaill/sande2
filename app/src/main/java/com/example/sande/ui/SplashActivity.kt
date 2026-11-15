package com.example.sande.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.sande.ui.nav.AppNavGraph
import com.example.sande.ui.theme.SandeTheme

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
