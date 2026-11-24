package com.example.sande.ui.onboarding

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.sande.data.remote.AcceptInvitationRequest
import com.example.sande.di.SandeApplication
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationAcceptScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactoryOf { OnboardingViewModel(app.tokenManager, app.sessionManager, app.legacyAdoptionManager) }
    )

    val scope = rememberCoroutineScope()

    var invitationCode by remember { mutableStateOf("") }
    var isAccepting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            imageVector = Icons.Filled.Mail,
            contentDescription = "Invitation",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        // Title
        Text(
            text = "Accept Invitation",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Message
        Text(
            text = "Enter the 6-digit invitation code you received to join the business.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        // Code input
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            OutlinedTextField(
                value = invitationCode,
                onValueChange = { invitationCode = it.uppercase().replace(" ", "") },
                label = { Text("Invitation Code") },
                placeholder = { Text("123456") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                visualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
                    // Auto-format as groups of 3.
                    // Compose UI 1.6.8 still uses the TransformedText shape.
                    val formatted = text.text.replace(" ", "").chunked(3).joinToString(" ")
                    androidx.compose.ui.text.input.TransformedText(
                        androidx.compose.ui.text.AnnotatedString(formatted),
                        androidx.compose.ui.text.input.OffsetMapping.Identity
                    )
                },
                onKeyboardAction = { _ ->
                    if (invitationCode.replace(" ", "").length == 6) {
                        acceptInvitation()
                    }
                }
            )
        }

        error?.let { err ->
            Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        }

        // Accept button
        Button(
            onClick = { acceptInvitation() },
            enabled = !isAccepting && invitationCode.replace(" ", "").length == 6,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            if (isAccepting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (isAccepting) "Accepting..." else "Accept Invitation", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // Back button
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text(text = "Back", fontSize = 16.sp)
        }
    }

    fun acceptInvitation() {
        scope.launch {
            isAccepting = true
            error = null
            try {
                val deviceCode = app.connectionResolver.deviceCode()
                vm.acceptInvitation(AcceptInvitationRequest(
                    code = invitationCode.replace(" ", ""),
                    deviceCode = deviceCode,
                    deviceName = "Android Device",
                    deviceType = "android",
                ))
            } catch (e: Exception) {
                error = "Failed to accept invitation: ${e.message}"
            } finally {
                isAccepting = false
            }
        }
    }
}
