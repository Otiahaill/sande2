package com.example.sande.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.sande.data.remote.Result
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Signs the user in with Google.
 *
 * Credential Manager is the only supported flow. The old
 * `com.google.android.gms.auth.api.signin.GoogleSignInClient` facade was
 * removed in play-services-auth 21.x, so the legacy path could no longer be
 * used at all.
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

    /**
     * Signs in with Google using Credential Manager.
     *
     * [serverClientId] must be the **Web** OAuth client ID — the ID token returned
     * by Google is intended for that client, and the backend exchanges it.
     */
    suspend fun signInWithCredentialManager(activity: Activity): Result<GoogleSignInResult> =
        withContext(Dispatchers.IO) {
            try {
                if (serverClientId.isBlank()) {
                    // Without a server client id the credential provider has
                    // nothing to match, and Google answers with the very
                    // unhelpful "No credentials available". Say what is
                    // actually missing instead.
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Google Sign-In is not configured: no OAuth client ID. " +
                                "Set it in Settings → Connection."
                        )
                    )
                }

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
                if (credential is GoogleIdTokenCredential) {
                    Result.success(
                        GoogleSignInResult(
                            idToken = credential.idToken,
                            // GoogleIdTokenCredential does not expose the
                            // email, so it is read from the ID token claims.
                            email = emailFromIdToken(credential.idToken),
                            displayName = credential.displayName,
                            photoUrl = credential.profilePictureUri?.toString(),
                        )
                    )
                } else {
                    Result.failure(
                        Exception("Unexpected credential type: ${credential::class.java.simpleName}")
                    )
                }
            } catch (e: GetCredentialException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Reads the `email` claim out of a Google ID token.
     *
     * The token is a JWT, so the claims are the base64url encoded payload in
     * the middle segment. Returns an empty string when it cannot be read —
     * the backend verifies the token anyway and gets the email from it.
     */
    private fun emailFromIdToken(idToken: String): String {
        return try {
            val payload = idToken.split(".")[1]
            val decoded = String(
                android.util.Base64.decode(
                    payload,
                    android.util.Base64.URL_SAFE or
                        android.util.Base64.NO_WRAP or
                        android.util.Base64.NO_PADDING
                )
            )
            val claims = kotlinx.serialization.json.Json.parseToJsonElement(decoded)
                .jsonObject
            claims["email"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Signs out.
     *
     * Credential Manager does not keep a session of its own, so there is
     * nothing to clear on the Google side; the app's own tokens are cleared by
     * the session manager.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        // Nothing to do — the token store is cleared by SessionManager.
    }

    /**
     * Revokes access (disconnects account).
     */
    suspend fun revokeAccess() = withContext(Dispatchers.IO) {
        // Access is revoked on the backend when the membership is removed.
    }

    /**
     * Checks if the user is already signed in.
     *
     * Credential Manager is stateless, so the app's own token store is the
     * only reliable source of truth — see `SessionManager.isSignedIn`.
     */
    fun isSignedIn(): Boolean = false
}
