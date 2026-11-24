package com.example.sande.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.BorderStroke
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessResolutionScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRegisterComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactoryOf { OnboardingViewModel(app.tokenManager, app.sessionManager, app.legacyAdoptionManager) }
    )

    val authState = vm.authState.collectAsState()
    val pendingRegistration = vm.pendingRegistration.collectAsState()
    val scope = rememberCoroutineScope()

    // Navigate when registration completes
    androidx.compose.runtime.LaunchedEffect(authState.value) {
        if (authState.value is com.example.sande.data.auth.SessionManager.AuthState.AUTHENTICATED) {
            // Check if there's legacy data to adopt
            scope.launch {
                vm.checkLegacyData()
                val counts = vm.legacyDataCounts.first()
                if (counts != null && counts.total > 0) {
                    onRegisterComplete() // Navigate to legacy adoption screen
                } else {
                    onRegisterComplete() // Navigate to dashboard (will go through recovery codes)
                }
            }
        }
    }

    val selection = when (val state = authState.value) {
        is com.example.sande.data.auth.SessionManager.AuthState.NEEDS_BUSINESS_SELECTION -> {
            Triple(true, state.account, state.memberships)
        }
        else -> Triple(
            false,
            null,
            emptyList<com.example.sande.data.auth.SessionManager.MembershipInfo>()
        )
    }
    val needsBusinessSelection = selection.first
    val account = selection.second
    val memberships = selection.third

    var selectedBusinessId by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var businessName by remember { mutableStateOf("") }
    var businessCategory by remember { mutableStateOf("retail") }
    var businessPhone by remember { mutableStateOf("") }
    var businessCountry by remember { mutableStateOf("KE") }
    var businessCurrency by remember { mutableStateOf("KES") }
    var businessTimezone by remember { mutableStateOf("Africa/Nairobi") }
    var businessAddress by remember { mutableStateOf("") }
    var registerError by remember { mutableStateOf<String?>(null) }

    if (!needsBusinessSelection && pendingRegistration.value == null) {
        // Not in business selection state and no pending registration, show loading
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return@BusinessResolutionScreen
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Choose Your Business",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Signed in as ${account?.email}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Existing memberships
        if (memberships.isNotEmpty()) {
            Text(
                text = "Your Businesses",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memberships) { membership ->
                    BusinessCard(
                        membership = membership,
                        isSelected = selectedBusinessId == membership.businessId,
                        onClick = { selectedBusinessId = membership.businessId },
                    )
                }
            }

            // Select button
            Button(
                onClick = {
                    selectedBusinessId?.let { businessId ->
                        val membership = memberships.find { it.businessId == businessId }
                        membership?.let {
                            scope.launch {
                                vm.selectBusiness(
                                    businessId = businessId,
                                    deviceCode = app.connectionResolver.deviceCode(),
                                    deviceName = "Android Device",
                                    deviceType = "android",
                                )
                            }
                        }
                    }
                },
                enabled = selectedBusinessId != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(text = "Select Business", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Divider()
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        // Register new business
        Text(
            text = "Or Register a New Business",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = businessName,
                onValueChange = { businessName = it },
                label = { Text("Business Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = businessCategory,
                onValueChange = { businessCategory = it },
                label = { Text("Category *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = businessPhone,
                onValueChange = { businessPhone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = businessCountry,
                    onValueChange = { businessCountry = it },
                    label = { Text("Country *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                OutlinedTextField(
                    value = businessCurrency,
                    onValueChange = { businessCurrency = it },
                    label = { Text("Currency *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            OutlinedTextField(
                value = businessTimezone,
                onValueChange = { businessTimezone = it },
                label = { Text("Timezone *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = businessAddress,
                onValueChange = { businessAddress = it },
                label = { Text("Address (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            registerError?.let { err ->
                Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (businessName.isBlank() || businessCategory.isBlank() || businessCountry.isBlank() || businessCurrency.isBlank() || businessTimezone.isBlank()) {
                        registerError = "Please fill all required fields"
                        return@Button
                    }
                    scope.launch {
                        isRegistering = true
                        registerError = null
                        vm.registerBusiness(
                            com.example.sande.data.remote.BusinessRegistrationRequest(
                                name = businessName,
                                category = businessCategory,
                                phone = businessPhone.takeIf { it.isNotBlank() },
                                countryCode = businessCountry,
                                currencyCode = businessCurrency,
                                timezone = businessTimezone,
                                address = businessAddress.takeIf { it.isNotBlank() },
                            )
                        )
                    }
                },
                enabled = !isRegistering,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = if (isRegistering) "Registering..." else "Register Business", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Back button
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text(text = "Back", fontSize = 16.sp)
        }
    }
}

@Composable
fun BusinessCard(
    membership: com.example.sande.data.auth.SessionManager.MembershipInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column {
                Text(text = membership.businessName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "${membership.role.capitalize()} • ${membership.status}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
