package com.example.sande.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles Google Sign-In using Credential Manager (modern), with a fallback to the
 * legacy [GoogleSignInClient].
 *
 * The Google ID credential classes live in the
 * `com.google.android.libraries.identity.googleid:googleid` artifact — they are NOT
 * part of `androidx.credentials`. `androidx.credentials:credentials-play-services-auth`
 * only ships the Play Services credential *providers*.
 */
class GoogleAuthManager(
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
        return GoogleSignInClient.getClient(context, gso)
    }

    /**
     * Signs in with Google using Credential Manager.
     *
     * [serverClientId] must be the **Web** OAuth client ID — the ID token returned
     * by Google is intended for that client, and the backend exchanges it.
     */
    suspend fun signInWithCredentialManager(activity: Activity): Result<GoogleSignInResult> =
        withContext(Dispatchers.IO) {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(
                    context = context,
                    request = request,
                )

                val credential = response.credential
                if (credential !is GoogleIdTokenCredential) {
                    return@withContext Result.failure(
                        Exception("Unexpected credential type: ${credential::class.java.simpleName}")
                    )
                }

                Result.success(
                    GoogleSignInResult(
                        idToken = credential.idToken,
                        // googleid 1.1.0 does not expose the email, so it is read
                        // from the ID token itself.
                        email = IdTokenClaims.decode(credential.idToken)?.email ?: "",
                        displayName = credential.displayName,
                        photoUrl = credential.profilePictureUri?.toString(),
                    )
                )
            } catch (e: GetCredentialException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fallback to traditional GoogleSignInClient for older devices or when
     * Credential Manager fails.
     *
     * The legacy flow needs an Activity result, which this class cannot receive on
     * its own; callers should use the ActivityResult API and drive
     * [handleSignInResult] instead.
     */
    suspend fun signInWithGoogleSignInClient(activity: Activity): Result<GoogleSignInResult> =
        withContext(Dispatchers.IO) {
            Result.failure(
                UnsupportedOperationException(
                    "Use the ActivityResult API with GoogleSignInClient and call handleSignInResult()"
                )
            )
        }

    /**
     * Converts the result of a legacy Google Sign-In intent into the app's own
     * result type.
     */
    fun handleSignInResult(resultCode: Int, data: android.content.Intent?): Result<GoogleSignInResult> {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(
                com.google.android.gms.common.api.ApiException::class.java
            )
            Result.success(
                GoogleSignInResult(
                    idToken = account.idToken
                        ?: return Result.failure(Exception("No ID token in Google Sign-In result")),
                    email = account.email ?: "",
                    displayName = account.displayName,
                    photoUrl = account.photoUrl?.toString(),
                )
            )
        } catch (e: com.google.android.gms.common.api.ApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Intent for the legacy Google Sign-In flow.
     *
     * Exposed so the sign-in screen can launch it as a fallback when Credential
     * Manager fails, and receive the result through the Activity Result API.
     */
    fun getLegacySignInIntent(): android.content.Intent = googleSignInClient.signInIntent

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
