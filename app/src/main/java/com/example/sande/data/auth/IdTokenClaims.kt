package com.example.sande.data.auth

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Claims carried in a Google ID token.
 *
 * The `com.google.android.libraries.identity.googleid:googleid` artifact does not
 * expose the email address on [GoogleIdTokenCredential], so it is read straight
 * out of the token instead. The token is a JWT: `header.payload.signature`, with
 * the payload base64url-encoded.
 *
 * Nothing here is a security decision — the backend verifies the token signature
 * and the `email_verified` claim before trusting any of these values.
 */
@Serializable
data class GoogleIdTokenClaims(
    val email: String? = null,
    val email_verified: Boolean? = null,
    val sub: String? = null,
    val name: String? = null,
    val picture: String? = null,
)

object IdTokenClaims {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes the claims of a Google ID token, or returns null if the token is
     * malformed.
     */
    fun decode(idToken: String): GoogleIdTokenClaims? = try {
        val payload = idToken.split(".")[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        json.decodeFromString<GoogleIdTokenClaims>(String(decoded, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}
