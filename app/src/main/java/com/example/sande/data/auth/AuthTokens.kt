package com.example.sande.data.auth

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Tokens returned from the backend auth endpoints.
 */
@Serializable
@Keep
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int, // seconds
    val membership: MembershipInfo,
)

@Serializable
@Keep
data class MembershipInfo(
    val id: String,
    val businessId: String,
    val businessName: String,
    val role: String, // "owner" | "staff"
    val deviceCode: String,
    val deviceStatus: String, // "active" | "pending_approval"
)

/**
 * Stored token data (with expiry timestamps).
 */
@Serializable
@Keep
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiry: Long, // epoch millis
    val refreshTokenExpiry: Long, // epoch millis
    val membership: MembershipInfo,
    val accountId: String,
) {
    fun isAccessTokenValid(): Boolean = System.currentTimeMillis() < accessTokenExpiry
    fun isRefreshTokenValid(): Boolean = System.currentTimeMillis() < refreshTokenExpiry
}

/**
 * Google Sign-In result.
 */
@Serializable
@Keep
data class GoogleSignInResult(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

/**
 * Device registration request.
 *
 * The request/response DTOs used by the API layer live in
 * `com.example.sande.data.remote` — they are the wire format. The ones here
 * were duplicated and are kept only because the auth package still references
 * them; see the `remote` package for the canonical definitions.
 */
@Deprecated(
    "Use com.example.sande.data.remote.DeviceRegistrationRequest",
    ReplaceWith("com.example.sande.data.remote.DeviceRegistrationRequest")
)
@Serializable
@Keep
data class DeviceRegistrationRequest(
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String, // "android" | "ios" | "web"
    val pushToken: String? = null,
)

/**
 * Business registration request.
 */
@Deprecated(
    "Use com.example.sande.data.remote.BusinessRegistrationRequest",
    ReplaceWith("com.example.sande.data.remote.BusinessRegistrationRequest")
)
@Serializable
@Keep
data class BusinessRegistrationRequest(
    val name: String,
    val category: String,
    val phone: String?,
    val countryCode: String,
    val currencyCode: String,
    val timezone: String,
    val address: String?,
)

/**
 * Staff invitation request.
 */
@Deprecated(
    "Use com.example.sande.data.remote.InviteStaffRequest",
    ReplaceWith("com.example.sande.data.remote.InviteStaffRequest")
)
@Serializable
@Keep
data class InviteStaffRequest(
    val email: String,
    val role: String = "staff",
)

/**
 * Invitation acceptance request.
 */
@Deprecated(
    "Use com.example.sande.data.remote.AcceptInvitationRequest",
    ReplaceWith("com.example.sande.data.remote.AcceptInvitationRequest")
)
@Serializable
@Keep
data class AcceptInvitationRequest(
    val code: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
)

/**
 * Token refresh request.
 */
@Serializable
@Keep
data class RefreshTokenRequest(
    val refreshToken: String,
)

/**
 * Sync push request.
 *
 * The wire-format DTOs live in `com.example.sande.data.remote`. These copies are
 * deprecated; see the `remote` package for the canonical definitions.
 */
@Deprecated(
    "Use com.example.sande.data.remote.SyncPushRequest",
    ReplaceWith("com.example.sande.data.remote.SyncPushRequest")
)
@Serializable
@Keep
data class SyncPushRequest(
    val batchId: String,
    val entries: List<OutboxEntry>,
)

/**
 * Wire format of an outbox entry.
 *
 * @see com.example.sande.data.remote.OutboxEntry
 */
@Deprecated(
    "Use com.example.sande.data.remote.OutboxEntry",
    ReplaceWith("com.example.sande.data.remote.OutboxEntry")
)
@Serializable
@Keep
data class OutboxEntry(
    val id: String,
    val businessId: String,
    val entityType: String,
    val entityId: String,
    val operation: String, // "create" | "update" | "delete"
    val payloadJson: String,
    val batchId: String? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
    val syncedAt: Long = 0,
)
