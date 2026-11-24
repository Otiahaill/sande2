package com.example.sande.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.data.auth.LegacyAdoptionManager
import com.example.sande.di.SandeApplication
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyAdoptionScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactoryOf { OnboardingViewModel(app.tokenManager, app.sessionManager, app.legacyAdoptionManager) }
    )

    val legacyDataCounts = vm.legacyDataCounts.collectAsState()
    val adoptionProgress = vm.adoptionProgress.collectAsState()
    val adoptionMessage = vm.adoptionMessage.collectAsState()
    val adoptionError = vm.adoptionError.collectAsState()
    val adoptionComplete = vm.adoptionComplete.collectAsState()

    val scope = rememberCoroutineScope()

    // Auto-start adoption if counts are available and not already adopting/complete
    LaunchedEffect(legacyDataCounts.value) {
        if (legacyDataCounts.value != null && !adoptionComplete.value && adoptionProgress.value == 0f) {
            vm.adoptLegacyData()
        }
    }

    // Navigate when complete
    LaunchedEffect(adoptionComplete.value) {
        if (adoptionComplete.value) {
            onComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = "Data migration",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        // Title
        Text(
            text = "Migrate Your Existing Data",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Message
        Text(
            text = "We found existing business data on this device. Would you like to migrate it to your new online account?",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        // Data summary
        legacyDataCounts.value?.let { c ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data to Migrate",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    DataRow(label = "Products", count = c.products)
                    DataRow(label = "Stock Movements", count = c.movements)
                    DataRow(label = "Sales", count = c.sales)
                    DataRow(label = "Members", count = c.members)
                    DataRow(label = "Stock Adjustments", count = c.stockAdjustments)
                    DataRow(label = "Pending Sync Items", count = c.outboxEntries)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    DataRow(
                        label = "Total Records",
                        count = c.total,
                        isTotal = true
                    )
                }
            }
        }

        // Progress bar during adoption
        if (adoptionProgress.value > 0f && adoptionProgress.value < 1f) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = adoptionMessage.value,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = adoptionProgress.value,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        adoptionError.value?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(text = err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
        }

        // Buttons
        if (!adoptionComplete.value) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch { vm.adoptLegacyData() }
                    },
                    enabled = adoptionProgress.value == 0f || adoptionProgress.value == 1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    if (adoptionProgress.value > 0f && adoptionProgress.value < 1f) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (adoptionProgress.value > 0f && adoptionProgress.value < 1f) "Migrating..." else "Migrate Data", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = {
                        onSkip()
                    },
                    enabled = adoptionProgress.value == 0f || adoptionProgress.value == 1f,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Skip Migration (Start Fresh)", fontSize = 16.sp)
                }
            }
        } else {
            Button(
                onClick = { onComplete() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(text = "Continue to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Back button
        TextButton(onClick = { if (adoptionProgress.value == 0f || adoptionProgress.value == 1f) onBack() }) {
            Text(text = "Back", fontSize = 16.sp)
        }
    }

    @Composable
    fun DataRow(label: String, count: Int, isTotal: Boolean = false) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = if (isTotal) 16.sp else 14.sp,
                fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = count.toString(),
                fontSize = if (isTotal) 16.sp else 14.sp,
                fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
                color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
