package com.example.sande.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.SyncStatusViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.example.sande.di.SandeApplication
    val vm: SyncStatusViewModel = viewModel(
        factory = viewModelFactoryOf { SyncStatusViewModel(app.tokenManager, app.outboxEntryRepository, app.syncCursorRepository) }
    )

    val scope = rememberCoroutineScope()

    val isSyncing by vm.isSyncing.collectAsState()
    val unsyncedCount by vm.unsyncedCount.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    val scopeRef = remember { scope }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Status") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onOpenDrawer) {
                        androidx.compose.material.icons.Icons.Filled.Menu
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            scopeRef.launch {
                                vm.setIsSyncing(true)
                                vm.setStatusMessage("Syncing...")
                                // Trigger sync via WorkManager
                                com.example.sande.data.sync.SyncWorker.enqueueImmediate(context)
                                vm.setIsSyncing(false)
                                vm.setStatusMessage("Sync queued")
                                vm.load()
                            }
                        },
                        modifier = androidx.compose.ui.Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        androidx.compose.material.icons.Icons.Filled.Refresh
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sync status icon
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else {
                        androidx.compose.material.icons.Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = "Sync status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                    // Unsynced count
                    Text(
                        text = "$unsyncedCount pending",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "items waiting to sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                    // Last sync
                    if (lastSyncAt > 0) {
                        Text(
                            text = "Last synced: ${android.text.format.DateFormat.format("MMM dd, HH:mm", java.util.Date(lastSyncAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Never synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Sync cursor
                    if (cursor > 0) {
                        Text(
                            text = "Sync cursor: $cursor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status message
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            vm.setIsSyncing(true)
                            vm.setStatusMessage("Syncing...")
                            com.example.sande.data.sync.SyncWorker.enqueueImmediate(context)
                            vm.setIsSyncing(false)
                            vm.setStatusMessage("Sync queued")
                            vm.load()
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (isSyncing) "Syncing..." else "Sync Now", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        vm.load()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Refresh Status", fontSize = 16.sp)
                }
            }

            // Info text
            androidx.compose.material3.Text(
                text = "Sync runs automatically every 15 minutes when online. Changes are pushed to the cloud and pulled from other devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = 24.dp)
            )
        }
    }
}
