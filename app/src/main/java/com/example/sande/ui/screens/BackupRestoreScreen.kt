package com.example.sande.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.data.backup.RestoreMode
import com.example.sande.di.SandeApplication
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.BackupViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: BackupViewModel = viewModel(
        factory = viewModelFactoryOf { BackupViewModel(context, app.backupManager, app.tokenManager) }
    )

    val isBusy by vm.isBusy.collectAsState()
    val lastBackupFile by vm.lastBackupFile.collectAsState()
    val pendingRestore by vm.pendingRestore.collectAsState()
    val message by vm.message.collectAsState()
    val restoreResult by vm.restoreResult.collectAsState()

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.inspectForRestore(it) }
    }

    // Share launcher
    var shareUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ===== Local Backup Section =====
            Text(
                text = "Local Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Create a portable archive of your business data. Save it to Drive, email it, or keep a copy — it can restore your data on any device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { vm.createBackup() },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text(if (isBusy) "Working..." else "Create Backup", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            lastBackupFile?.let { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${file.length() / 1024} KB · ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(java.util.Date(file.lastModified()))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                        OutlinedButton(
                            onClick = {
                                vm.shareBackup { uri, mime ->
                                    shareUri = uri
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "SANDE Backup")
                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    shareLauncher.launch(android.content.Intent.createChooser(intent, "Share backup via"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                            Text("Share Backup")
                        }
                    }
                }
            }

            // ===== Restore Section =====
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = "Restore From File",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Import a backup archive from Drive, Downloads, or anywhere else. Existing records with the same ID are replaced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text("Choose Backup File", fontSize = 16.sp)
            }

            // ===== Cloud Backup Section =====
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = "Cloud Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "The owner can trigger an automatic cloud backup. Backups are stored encrypted in secure cloud storage and can restore a new device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ===== Message =====
            message?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.contains("failed", true) || msg.contains("Invalid", true)) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // ===== Restore confirmation dialog =====
    pendingRestore?.let { (file, validation) ->
        AlertDialog(
            onDismissRequest = { vm.cancelRestore() },
            title = { Text("Restore Backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val m = validation.manifest
                    if (m != null) {
                        Text("Business: ${m.businessName}")
                        Text("Created: ${m.createdAtIso.take(10)}")
                        Text("Records: ${m.totalRows}")
                        Text("App version: ${m.appVersionName}")
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "Existing records with the same ID will be replaced. This cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.confirmRestore(RestoreMode.REPLACE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelRestore() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
