package com.example.sande.data.remote

import android.content.Context
import com.example.sande.data.auth.AuthTokens
import com.example.sande.data.prefs.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Result type used by the API layer.
 *
 * `kotlin.Result` cannot be used as a return type of a suspend function that is
 * called from a non-inline context in older Kotlin versions, and it cannot be
 * returned from a function with a nullable generic argument, so the API layer
 * uses its own small wrapper.
 */
sealed class Result<out T> {
    data class Success<out T>(val value: T) : Result<T>()
    data class Failure(val error: Throwable) : Result<Nothing>()

    companion object {
        fun <T> success(value: T): Result<T> = Success(value)
        fun <T> failure(error: Throwable): Result<T> = Failure(error)
    }

    fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(value)
        return this
    }

    fun onFailure(action: (Throwable) -> Unit): Result<T> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }

    fun map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Result.success(transform(value))
        is Failure -> Result.failure(error)
    }
}
/**
 * Ktor HTTP client for communicating with the SANDE backend.
 */
class ApiClient(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val baseUrl: String = "https://sande-backend.your-domain.workers.dev", // Configure via BuildConfig
) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            // Never log request/response bodies in a release build: they contain
            // OAuth tokens, membership data and business records.
            level = if (com.example.sande.BuildConfig.DEBUG) {
                LogLevel.INFO
            } else {
                LogLevel.NONE
            }
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        // Default request timeout
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }

        // Response validation
        expectSuccess = false
    }

    init {
        // Attach the bearer token to every request.
        //
        // Ktor 2.x removed `HttpClientConfig.intercept`; the request pipeline is
        // configured on the client instance instead. The interceptor block is
        // suspend, so the token can be read straight from the token manager.
        client.requestPipeline.intercept(HttpRequestPipeline.Render) {
            val tokens = tokenManager.getTokens()
            if (tokens != null && tokens.isAccessTokenValid()) {
                headers.append(HttpHeaders.Authorization, "[redacted]")
            }
        }
    }

    // ===== Health Check =====

    /**
     * GET /health
     * Checks that a backend is reachable and healthy. Used by the Connection
     * settings screen to validate a URL before saving it.
     */
    suspend fun healthCheck(baseUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/')
            val response = client.get<HealthResponse> {
                this.url("$url/health")
            }
            if (response.status == "healthy") {
                "OK — backend is healthy (${response.latencyMs}ms)"
            } else {
                "Reachable but degraded — D1: ${response.checks?.d1}, R2: ${response.checks?.r2}, KV: ${response.checks?.kv}"
            }
        } catch (e: Exception) {
            throw Exception("Could not reach $baseUrl — ${e.message}")
        }
    }

    // ===== Auth Endpoints =====

    /**
     * POST /auth/google
     * Authenticate with Google ID token.
     */
    suspend fun googleAuth(request: GoogleAuthRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<AuthResponse> {
                url("$baseUrl/auth/google")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /api/business/register
     * Register a new business (owner).
     */
    suspend fun registerBusiness(request: BusinessRegistrationRequest): Result<BusinessRegistrationResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<BusinessRegistrationResponse> {
                url("$baseUrl/api/business/register")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /auth/refresh
     * Refresh access token.
     */
    suspend fun refreshToken(request: RefreshTokenRequest): Result<RefreshTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<RefreshTokenResponse> {
                url("$baseUrl/auth/refresh")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /auth/logout
     * Revoke refresh token.
     */
    suspend fun logout(request: RefreshTokenRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<String> {
                url("$baseUrl/auth/logout")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response).map { Unit }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /memberships/accept
     * Accept a staff invitation.
     */
    suspend fun acceptInvitation(request: AcceptInvitationRequest): Result<InvitationAcceptResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<InvitationAcceptResponse> {
                url("$baseUrl/api/memberships/accept")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /devices/register
     * Register a new device.
     */
    suspend fun registerDevice(request: DeviceRegistrationRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<String> {
                url("$baseUrl/api/devices/register")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response).map { Unit }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Sync Endpoints =====

    /**
     * POST /api/sync/push
     * Push outbox entries.
     */
    suspend fun pushSync(request: SyncPushRequest): Result<SyncPushResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<SyncPushResponse> {
                url("$baseUrl/api/sync/push")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * GET /api/sync/pull
     * Pull changes since cursor.
     */
    suspend fun pullSync(cursor: Long, limit: Int): Result<SyncPullResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.get<SyncPullResponse> {
                url("$baseUrl/api/sync/pull")
                parameter("cursor", cursor.toString())
                parameter("limit", limit.toString())
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * GET /api/sync/status
     * Get sync status.
     */
    suspend fun getSyncStatus(): Result<SyncStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.get<SyncStatusResponse> {
                url("$baseUrl/api/sync/status")
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Business Endpoints =====

    /**
     * GET /api/business/me
     * Get current business info.
     */
    suspend fun getMyBusiness(): Result<BusinessInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.get<BusinessInfoResponse> {
                url("$baseUrl/api/business/me")
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * PUT /api/business/me/profile
     * Update business profile.
     */
    suspend fun updateProfile(request: ProfileUpdateRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<String> {
                url("$baseUrl/api/business/me/profile")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response).map { Unit }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Membership Endpoints =====

    /**
     * GET /api/memberships
     * List memberships (owner only).
     */
    suspend fun listMemberships(): Result<List<MembershipInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = client.get<MembershipsResponse> {
                url("$baseUrl/api/memberships")
            }
            handleResponse(response).map { it.memberships }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /api/memberships/invite
     * Invite a staff member (owner only).
     */
    suspend fun inviteStaff(request: InviteStaffRequest): Result<InviteStaffResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<InviteStaffResponse> {
                url("$baseUrl/api/memberships/invite")
                contentType(io.ktor.http.ContentType.Application.Json)
                body = request
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Device Endpoints =====

    /**
     * GET /api/devices
     * List devices.
     */
    suspend fun listDevices(): Result<List<DeviceInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = client.get<DevicesResponse> {
                url("$baseUrl/api/devices")
            }
            handleResponse(response).map { it.devices }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    /**
     * POST /api/devices/:deviceCode/approve
     * Approve a device (owner only).
     */
    suspend fun approveDevice(deviceCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<String> {
                url("$baseUrl/api/devices/$deviceCode/approve")
                contentType(io.ktor.http.ContentType.Application.Json)
            }
            handleResponse(response).map { Unit }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Backup Endpoints =====

    /**
     * POST /api/backup/create
     * Create a backup (owner only).
     */
    suspend fun createBackup(): Result<BackupCreateResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.post<BackupCreateResponse> {
                url("$baseUrl/api/backup/create")
                contentType(io.ktor.http.ContentType.Application.Json)
            }
            handleResponse(response)
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", e))
        }
    }

    // ===== Generic Response Handler =====

    private suspend fun <T : Any> handleResponse(response: HttpResponse): Result<T> {
        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> {
                val body = response.bodyAsText()
                try {
                    Result.success(Json.decodeFromString<T>(body))
                } catch (e: Exception) {
                    Result.failure(ApiException("Failed to parse response: ${e.message}", e))
                }
            }
            HttpStatusCode.Unauthorized -> {
                Result.failure(ApiException("Unauthorized", null, 401))
            }
            HttpStatusCode.Forbidden -> {
                Result.failure(ApiException("Forbidden", null, 403))
            }
            HttpStatusCode.NotFound -> {
                Result.failure(ApiException("Not found", null, 404))
            }
            HttpStatusCode.TooManyRequests -> {
                Result.failure(ApiException("Rate limited", null, 429))
            }
            else -> {
                val body = response.bodyAsText()
                Result.failure(ApiException("Server error: ${response.status} - $body", null, response.status.value))
            }
        }
    }
}

// ===== Request/Response DTOs =====

@Serializable
data class HealthResponse(
    val status: String,
    val latencyMs: Long,
    val checks: HealthChecks? = null,
)
@Serializable
data class HealthChecks(
    val d1: String? = null,
    val r2: String? = null,
    val kv: String? = null,
)

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val data: AuthResponseData? = null,
    val error: ApiError? = null,
)

@Serializable
data class AuthResponseData(
    val tokens: AuthTokens? = null,
    val account: AccountInfo? = null,
    val memberships: List<MembershipInfo>? = null,
    val needsBusinessSelection: Boolean = false,
)

@Serializable
data class AccountInfo(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("photo_url") val photoUrl: String?,
)

@Serializable
data class MembershipInfo(
    val id: String,
    @SerialName("business_id") val businessId: String,
    @SerialName("business_name") val businessName: String,
    val role: String,
    val status: String,
    @SerialName("device_code") val deviceCode: String?,
)

@Serializable
data class BusinessRegistrationRequest(
    val name: String,
    val category: String,
    val phone: String?,
    val countryCode: String,
    val currencyCode: String,
    val timezone: String,
    val address: String?,
)

@Serializable
data class BusinessRegistrationResponse(
    val success: Boolean,
    val data: BusinessRegistrationData? = null,
    val error: ApiError? = null,
)

@Serializable
data class BusinessRegistrationData(
    val tokens: AuthTokens,
    val accountId: String,
    val recoveryCodes: List<String>,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshTokenResponse(
    val success: Boolean,
    val data: RefreshTokenData? = null,
    val error: ApiError? = null,
)

@Serializable
data class RefreshTokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

@Serializable
data class AcceptInvitationRequest(
    val code: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
)

@Serializable
data class InvitationAcceptResponse(
    val success: Boolean,
    val data: InvitationAcceptData? = null,
    val error: ApiError? = null,
)

@Serializable
data class InvitationAcceptData(
    val tokens: AuthTokens,
    val accountId: String,
    val recoveryCodes: List<String>,
)

@Serializable
data class DeviceRegistrationRequest(
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
    val pushToken: String? = null,
)

@Serializable
data class SyncPushRequest(
    val batchId: String,
    val entries: List<OutboxEntry>,
)

/**
 * Wire format of an outbox entry.
 *
 * The Room entity with the same name lives in
 * `com.example.sande.data.entities.OutboxEntry`; this one is the JSON shape
 * sent to the backend.
 */
@Serializable
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

@Serializable
data class SyncPushResponse(
    val success: Boolean,
    val data: SyncPushData? = null,
    val error: ApiError? = null,
)

@Serializable
data class SyncPushData(
    val pushed: Int,
    val failed: Int,
)

@Serializable
data class SyncPullResponse(
    val success: Boolean,
    val data: SyncPullData? = null,
    val error: ApiError? = null,
)

@Serializable
data class SyncPullData(
    val cursor: Long,
    val changes: List<ChangeLogEntry>,
    val more: Boolean,
)

@Serializable
data class ChangeLogEntry(
    val operation: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
)

@Serializable
data class SyncStatusResponse(
    val success: Boolean,
    val data: SyncStatusData? = null,
    val error: ApiError? = null,
)

@Serializable
data class SyncStatusData(
    val cursor: Long = 0,
    val pendingPushCount: Int = 0,
    val lastPullAt: Long = 0,
    val lastPushAt: Long = 0,
)

@Serializable
data class BusinessInfoResponse(
    val success: Boolean,
    val data: BusinessInfoData? = null,
    val error: ApiError? = null,
)

@Serializable
data class BusinessInfoData(
    val id: String,
    val name: String,
    val category: String,
    val countryCode: String,
    val currencyCode: String,
    val timezone: String,
    val address: String?,
    val logoUri: String?,
    val negativeStockPolicy: String,
    val receiptFooter: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long,
    val schemaVersion: Int,
)

@Serializable
data class ProfileUpdateRequest(
    val name: String?,
    val category: String?,
    val phone: String?,
    val countryCode: String?,
    val currencyCode: String?,
    val timezone: String?,
    val address: String?,
    val logoUri: String?,
    val negativeStockPolicy: String?,
    val receiptFooter: String?,
)

@Serializable
data class InviteStaffRequest(
    val email: String,
    val role: String = "staff",
)

@Serializable
data class InviteStaffResponse(
    val success: Boolean,
    val data: InviteStaffData? = null,
    val error: ApiError? = null,
)

@Serializable
data class InviteStaffData(
    val id: String,
    val email: String,
    val role: String,
    val status: String,
)

@Serializable
data class DeviceInfo(
    val id: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
    val status: String,
    val lastSeenAt: Long,
    val lastSyncAt: Long?,
    val pushToken: String?,
)

@Serializable
data class DevicesResponse(
    val success: Boolean,
    val data: DevicesData? = null,
    val error: ApiError? = null,
)

@Serializable
data class DevicesData(
    val devices: List<DeviceInfo>,
)

@Serializable
data class BackupCreateResponse(
    val success: Boolean,
    val data: BackupCreateData? = null,
    val error: ApiError? = null,
)

@Serializable
data class BackupCreateData(
    val backupId: String,
    val sizeBytes: Long,
    val checksum: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: String? = null,
)

class ApiException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
) : Exception(message, cause)
