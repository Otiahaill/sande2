package com.example.sande.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sande.ui.theme.SandeShape

/** Money is stored as integer cents. 22000 -> "220.00". */
fun formatKes(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/** Builds a ViewModel from a plain lambda so screens can pass repositories in. */
fun <T : ViewModel> viewModelFactoryOf(build: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = build() as V
    }

@Composable
fun EmptyStateCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = SandeShape.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Epoch millis -> "14 NOV 16:05" in the device time zone. */
fun formatSaleDate(timestamp: Long): String {
    val dt = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestamp),
        java.time.ZoneId.systemDefault()
    )
    return "${dt.dayOfMonth} ${dt.month.name.take(3)} ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
}

/**
 * Page header with a drawer button on the left, used by screens that do not
 * have their own top app bar. Pass [onBack] to show a back arrow instead.
 */
@Composable
fun ScreenHeader(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (onBack != null) onBack() else onOpenDrawer() }) {
            Icon(
                imageVector = if (onBack != null) Icons.Default.ArrowBack else Icons.Default.Menu,
                contentDescription = if (onBack != null) "Back" else "Open navigation menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
