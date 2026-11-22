package com.example.sande.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons

/**
 * Connection settings screen — where the user points the app at a backend
 * instance and supplies the Google OAuth client ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: ConnectionViewModel = viewModel(
        factory = viewModelFactoryOf { ConnectionViewModel(app.connectionSettings, app.apiClient) }
    )

    val scope = rememberCoroutineScope()

    var googleClientId by remember { mutableStateOf("") }
    var apiBaseUrl by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf("dev") }
    var deviceName by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Load current values
    LaunchedEffect(Unit) {
        val status = vm.load()
        googleClientId = status.googleClientId ?: ""
        apiBaseUrl = status.apiBaseUrl
        environment = status.environment
        deviceName = status.deviceName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open drawer")
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
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vm.isReady.value) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (vm.isReady.value) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (vm.isReady.value) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                    Column {
                        Text(
                            text = if (vm.isReady.value) "Connected" else "Setup required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (vm.isReady.value) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Text(
                            text = if (vm.isReady.value) {
                                "Backend and Google sign-in are configured"
                            } else {
                                "Fill in the fields below to connect"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vm.isReady.value) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }

            // Backend URL
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = { apiBaseUrl = it },
                label = { Text("Backend URL") },
                placeholder = { Text("https://sande-backend-dev….workers.dev") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("The Cloudflare Workers URL of your backend") }
            )

            // Environment picker
            Text("Environment", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dev" to "Development", "staging" to "Staging", "production" to "Production").forEach { (value, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = environment == value,
                        onClick = { environment = value },
                        label = { Text(label) }
                    )
                }
            }

            // Google client ID
            OutlinedTextField(
                value = googleClientId,
                onValueChange = { googleClientId = it },
                label = { Text("Google OAuth Client ID") },
                placeholder = { Text("1234567890-abc.apps.googleusercontent.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Web application client ID from Google Cloud Console") }
            )

            // Device name
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("How this device appears to the business owner") }
            )

            // Test button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isTesting = true
                        testResult = vm.testConnection(apiBaseUrl.trim())
                        isTesting = false
                    }
                },
                enabled = !isTesting && apiBaseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                }
                Text("Test connection")
            }

            testResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("OK")) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        saveMessage = vm.save(
                            googleClientId = googleClientId.trim(),
                            apiBaseUrl = apiBaseUrl.trim(),
                            environment = environment,
                            deviceName = deviceName.trim(),
                        )
                        isSaving = false
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                }
                Text("Save connection settings")
            }

            saveMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Fingerprint help
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android OAuth client fingerprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Add this SHA-1 to your Google Android OAuth client so sign-in works on debug builds:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "B6:A8:B3:F7:02:88:40:8B:F8:9D:CA:C7:E3:66:BC:37:E4:39:59:CE",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Package name: com.example.sande",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
