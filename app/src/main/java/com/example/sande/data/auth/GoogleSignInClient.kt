package com.example.sande.data.auth

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.google.GetGoogleIdOption
import androidx.credentials.google.GoogleIdTokenCredential
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles Google Sign-In using Credential Manager (modern) with fallback to GoogleSignInClient.
 */
class GoogleSignInClient(
    private val context: Context,
    private val serverClientId: String,
) {

    private val credentialManager = CredentialManager.create(context)
    private val googleSignInClient: GoogleSignInClient = createGoogleSignInClient()

    private fun createGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Starts Google Sign-In flow using Credential Manager (preferred on Android 14+).
     * Returns the Google ID token.
     */
    suspend fun signInWithCredentialManager(activity: Activity): Result<GoogleSignInResult> = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Result<GoogleSignInResult>>()

        val getCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .build())
            .build()

        credentialManager.getCredentialAsync(
            context,
            getCredentialRequest,
            object : CredentialManagerCallback<GetCredentialResponse> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential is GoogleIdTokenCredential) {
                        val idToken = credential.idToken
                        if (idToken == null) {
                            return@onResult deferred.complete(Result.failure(Exception("No ID token")))
                        }
                        deferred.complete(Result.success(GoogleSignInResult(
                            idToken = idToken,
                            email = credential.id ?: "",
                            displayName = credential.name,
                            photoUrl = credential.pictureUri?.toString(),
                        )))
                    } else {
                        deferred.complete(Result.failure(Exception("Unexpected credential type")))
                    }
                }

                override fun onError(e: GetCredentialException) {
                    deferred.complete(Result.failure(e))
                }
            }
        )

        deferred.await()
    }

    /**
     * Fallback to traditional GoogleSignInClient for older devices or when Credential Manager fails.
     */
    suspend fun signInWithGoogleSignInClient(activity: Activity): Result<GoogleSignInResult> = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Result<GoogleSignInResult>>()

        val signInIntent = googleSignInClient.signInIntent
        val requestCode = 9001 // Unique request code

        // We need to handle the activity result - this is simplified
        // In practice, you'd use ActivityResultContracts.StartActivityForResult
        // For now, we'll use the blocking approach with a custom callback
        
        // This is a simplified version - real implementation needs ActivityResult API
        try {
            val task: Task<GoogleSignInAccount> = googleSignInClient.signInIntent
                .let { intent -> 
                    // This won't work directly - need proper activity result handling
                    // Placeholder for actual implementation
                    throw UnsupportedOperationException("Use ActivityResult API")
                }
            
            deferred.complete(Result.failure(Exception("Use ActivityResult API for Google Sign-In")))
        } catch (e: Exception) {
            deferred.complete(Result.failure(e))
        }

        deferred.await()
    }

    /**
     * Signs out from Google.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        googleSignInClient.signOut()
        // Also clear credential manager if needed
    }

    /**
     * Revokes access (disconnects account).
     */
    suspend fun revokeAccess() = withContext(Dispatchers.IO) {
        googleSignInClient.revokeAccess()
    }

    /**
     * Checks if user is already signed in.
     */
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    /**
     * Gets the last signed-in account (if any).
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}

/**
 * Wrapper for Google Sign-In result.
 */
data class GoogleSignInResult(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)
