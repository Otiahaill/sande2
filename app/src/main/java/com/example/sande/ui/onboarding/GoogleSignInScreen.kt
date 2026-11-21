package com.example.sande.ui.onboarding

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.data.auth.GoogleSignInClient
import com.example.sande.data.auth.GoogleSignInResult
import com.example.sande.di.SandeApplication
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignInScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val app = context.applicationContext as SandeApplication
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactoryOf { OnboardingViewModel(app.tokenManager, app.sessionManager) }
    )

    val googleSignInClient = remember { GoogleSignInClient(context, app.connectionResolver.googleClientId()) }
    val scope = rememberCoroutineScope()

    var isSigningIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Activity result launcher for Google Sign-In
    val signInLauncher = remember {
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.let { data ->
                    // Handle Google Sign-In result
                    scope.launch {
                        isSigningIn = true
                        error = null
                        try {
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data).getResult(com.google.android.gms.common.api.ApiException::class.java)
                            account.idToken?.let { idToken ->
                                vm.handleGoogleSignIn(
                                    idToken = idToken,
                                    deviceCode = app.connectionResolver.deviceCode(),
                                    deviceName = "Android Device",
                                    deviceType = "android",
                                )
                            }
                        } catch (e: Exception) {
                            error = "Sign in failed: ${e.message}"
                        } finally {
                            isSigningIn = false
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.sande.R.drawable.sandelogo_foreground),
            contentDescription = "SANDE Logo",
            modifier = Modifier.size(120.dp),
        )

        Text(
            text = "Sign in with Google",
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Secure, passwordless authentication",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

        // Google Sign-In Button
        Button(
            onClick = {
                if (isSigningIn) return@Button
                // Use Credential Manager or fallback
                scope.launch {
                    isSigningIn = true
                    error = null
                    try {
                        // Try Credential Manager first (modern)
                        val result = googleSignInClient.signInWithCredentialManager(activity)
                        result.onSuccess { googleResult: GoogleSignInResult ->
                            vm.handleGoogleSignIn(
                                idToken = googleResult.idToken,
                                deviceCode = app.connectionResolver.deviceCode(),
                                deviceName = "Android Device",
                                deviceType = "android",
                            )
                        }.onFailure { e ->
                            // Fallback to traditional Google Sign-In
                            val signInIntent = googleSignInClient.googleSignInClient.signInIntent
                            signInLauncher.launch(signInIntent)
                        }
                    } catch (e: Exception) {
                        error = "Sign in failed: ${e.message}"
                        isSigningIn = false
                    }
                }
            },
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.LightGray
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSigningIn) {
                    ProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                }
                // Google logo would go here - using text for now
                Text(text = "G", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF4285F4))
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Continue with Google", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }

        // Error message
        error?.let { err ->
            Text(
                text = err,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Back button
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text(text = "Back", fontSize = 16.sp)
        }
    }
}
