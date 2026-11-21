package com.example.sande.data.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.remote.ApiClient
import com.example.sande.data.remote.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Manages the user session: token lifecycle, auto-refresh, offline lease, and auth state.
 */
class SessionManager(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    // Auth state
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _leaseState = MutableLiveData<LeaseState>()
    val leaseState: LiveData<LeaseState> = _leaseState

    private var refreshJob: Job? = null
    private var leaseCheckJob: Job? = nil

    enum class AuthState {
        UNAUTHENTICATED,
        AUTHENTICATING,
        AUTHENTICATED(StoredTokens),
        NEEDS_BUSINESS_SELECTION(AccountInfo, List<MembershipInfo>),
        ERROR(String);

        data class Authenticated(val tokens: StoredTokens) : AuthState()
        data class NeedsBusinessSelection(val account: AccountInfo, val memberships: List<MembershipInfo>) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    enum class LeaseState {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    UNKNOWN
}

    data class AccountInfo(
        val id: String,
        val email: String,
        val displayName: String?,
        val photoUrl: String?,
    )

    data class MembershipInfo(
        val id: String,
        val businessId: String,
        val businessName: String,
        val role: String,
        val status: String,
        val deviceCode: String?,
    )

    init {
        checkSession()
    }

    /**
     * Checks for existing valid session on app start.
     */
    fun checkSession() {
        scope.launch {
            _authState.value = AuthState.AUTHENTICATING
            
            val tokens = tokenManager.getTokens()
            
            if (tokens != null && tokens.isRefreshTokenValid()) {
                if (tokens.isAccessTokenValid()) {
                    // Session is fully valid
                    _authState.value = AuthState.Authenticated(tokens)
                    startTokenRefreshLoop(tokens)
                    startLeaseMonitoring()
                    updateLeaseState(tokens.refreshTokenExpiry)
                } else {
                    // Access token expired, try refresh
                    refreshAccessToken(tokens.refreshToken)
                        .onSuccess { newTokens ->
                            _authState.value = AuthState.Authenticated(newTokens)
                            startTokenRefreshLoop(newTokens)
                            startLeaseMonitoring()
                            updateLeaseState(newTokens.refreshTokenExpiry)
                        }
                        .onFailure { error ->
                            // Refresh failed, need full re-auth
                            _authState.value = AuthState.UNAUTHENTICATED
                        }
                }
            } else if (tokens != null && !tokens.isRefreshTokenValid()) {
                // Lease expired
                _authState.value = AuthState.UNAUTHENTICATED
                _leaseState.value = LeaseState.EXPIRED
            } else {
                // No tokens
                _authState.value = AuthState.UNAUTHENTICATED
                _leaseState.value = LeaseState.UNKNOWN
            }
        }
    }

    /**
     * Handles Google Sign-In completion.
     */
    fun handleGoogleSignIn(
        idToken: String,
        deviceCode: String,
        deviceName: String,
        deviceType: String,
    ) {
        scope.launch {
            _authState.value = AuthState.AUTHENTICATING
            
            apiClient.googleAuth(GoogleAuthRequest(
                idToken = idToken,
                deviceCode = deviceCode,
                deviceName = deviceName,
                deviceType = deviceType,
            )).onSuccess { response ->
                when {
                    response.needsBusinessSelection -> {
                        val account = AccountInfo(
                            id = response.account?.id ?: "",
                            email = response.account?.email ?: "",
                            displayName = response.account?.displayName,
                            photoUrl = response.account?.photoUrl,
                        )
                        val memberships = response.memberships?.map { m ->
                            MembershipInfo(
                                id = m.id,
                                businessId = m.businessId,
                                businessName = m.businessName,
                                role = m.role,
                                status = m.status,
                                deviceCode = m.deviceCode,
                            )
                        } ?: emptyList()
                        _authState.value = AuthState.NeedsBusinessSelection(account, memberships)
                    }
                    response.tokens != null -> {
                        val tokens = response.tokens!!
                        tokenManager.saveTokens(tokens, response.account?.id ?: "")
                            .await()
                        _authState.value = AuthState.Authenticated(tokenManager.getTokens().await()!!)
                        startTokenRefreshLoop(tokenManager.getTokens().await()!!)
                        startLeaseMonitoring()
                        updateLeaseState(tokens.membership.deviceCode) // Will be updated with actual expiry
                    }
                    else -> {
                        _authState.value = AuthState.Error("Unexpected response from server")
                    }
                }
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Sign in failed")
            }
        }
    }

    /**
     * Registers a new business (owner flow).
     */
    fun registerBusiness(request: BusinessRegistrationRequest) {
        scope.launch {
            _authState.value = AuthState.AUTHENTICATING
            
            apiClient.registerBusiness(request).onSuccess { response ->
                val tokens = response.tokens!!
                tokenManager.saveTokens(tokens, response.accountId!!).await()
                tokenManager.saveRecoveryCodes(response.recoveryCodes!!).await()
                _authState.value = AuthState.Authenticated(tokenManager.getTokens().await()!!)
                startTokenRefreshLoop(tokenManager.getTokens().await()!!)
                startLeaseMonitoring()
                updateLeaseState(tokens.refreshTokenExpiry)
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Registration failed")
            }
        }
    }

    /**
     * Selects an existing business from membership list.
     */
    fun selectBusiness(businessId: String, deviceCode: String, deviceName: String, deviceType: String) {
        scope.launch {
            _authState.value = AuthState.AUTHENTICATING
            
            apiClient.googleAuth(GoogleAuthRequest(
                idToken = "", // Will be re-authed via refresh token
                deviceCode = deviceCode,
                deviceName = deviceName,
                deviceType = deviceType,
            )).onSuccess { response ->
                response.tokens?.let { tokens ->
                    tokenManager.saveTokens(tokens, response.account?.id ?: "").await()
                    _authState.value = AuthState.Authenticated(tokenManager.getTokens().await()!!)
                    startTokenRefreshLoop(tokenManager.getTokens().await()!!)
                    startLeaseMonitoring()
                    updateLeaseState(tokens.refreshTokenExpiry)
                }
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Business selection failed")
            }
        }
    }

    /**
     * Accepts a staff invitation.
     */
    fun acceptInvitation(request: AcceptInvitationRequest) {
        scope.launch {
            _authState.value = AuthState.AUTHENTICATING
            
            apiClient.acceptInvitation(request).onSuccess { response ->
                val tokens = response.tokens!!
                tokenManager.saveTokens(tokens, response.accountId!!).await()
                _authState.value = AuthState.Authenticated(tokenManager.getTokens().await()!!)
                startTokenRefreshLoop(tokenManager.getTokens().await()!!)
                startLeaseMonitoring()
                updateLeaseState(tokens.refreshTokenExpiry)
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Invitation acceptance failed")
            }
        }
    }

    /**
     * Registers a new device.
     */
    fun registerDevice(request: DeviceRegistrationRequest) {
        scope.launch {
            apiClient.registerDevice(request).onSuccess { _ ->
                // Device registered successfully
            }.onFailure { error ->
                // Handle error
            }
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     */
    private suspend fun refreshAccessToken(refreshToken: String): Result<StoredTokens> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.success && response.data != null) {
                val tokens = response.data!!
                tokenManager.updateTokensAfterRefresh(
                    tokens.accessToken,
                    tokens.refreshToken,
                    tokens.expiresIn
                ).await()
                return Result.success(tokenManager.getTokens().await()!!)
            } else {
                return Result.failure(ApiException(response.error?.message ?: "Token refresh failed"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Starts the background token refresh loop.
     * Refreshes access token 5 minutes before expiry.
     */
    private fun startTokenRefreshLoop(tokens: StoredTokens) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                val currentTokens = tokenManager.getTokens().await()
                if (currentTokens == null) break
                
                val timeUntilExpiry = currentTokens.accessTokenExpiry - System.currentTimeMillis()
                val refreshIn = maxOf(timeUntilExpiry - 5 * 60 * 1000, 1000) // 5 min before expiry
                
                delay(refreshIn)
                
                val refreshedTokens = tokenManager.getTokens().await()
                if (refreshedTokens == null || !refreshedTokens.isRefreshTokenValid()) break
                
                refreshAccessToken(refreshedTokens.refreshToken)
                    .onSuccess { _ -> }
                    .onFailure { error ->
                        // If refresh fails, check if lease expired
                        val latest = tokenManager.getTokens().await()
                        if (latest == null || !latest.isRefreshTokenValid()) {
                            _authState.postValue(AuthState.UNAUTHENTICATED)
                            _leaseState.postValue(LeaseState.EXPIRED)
                        }
                        break
                    }
            }
        }
    }

    /**
     * Monitors the offline lease expiry.
     */
    private fun startLeaseMonitoring() {
        leaseCheckJob?.cancel()
        leaseCheckJob = scope.launch {
            while (true) {
                val tokens = tokenManager.getTokens().await()
                if (tokens == null) break
                
                updateLeaseState(tokens.refreshTokenExpiry)
                
                val timeUntilExpiry = tokens.refreshTokenExpiry - System.currentTimeMillis()
                val checkInterval = minOf(timeUntilExpiry / 4, TimeUnit.HOURS.toMillis(1)) // Check at least hourly
                
                delay(maxOf(checkInterval, TimeUnit.MINUTES.toMillis(5)))
            }
        }
    }

    private fun updateLeaseState(refreshTokenExpiry: Long) {
        val now = System.currentTimeMillis()
        val timeLeft = refreshTokenExpiry - now
        
        _leaseState.postValue(when {
            timeLeft <= 0 -> LeaseState.EXPIRED
            timeLeft < TimeUnit.HOURS.toMillis(24) -> LeaseState.EXPIRING_SOON(refreshTokenExpiry)
            else -> LeaseState.VALID(refreshTokenExpiry)
        })
    }

    /**
     * Extends the offline lease (owner only).
     */
    suspend fun extendLease(days: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tokenManager.extendOfflineLease(days).await()
            updateLeaseState(tokenManager.getOfflineLeaseExpiry().await())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        scope.launch {
            refreshJob?.cancel()
            leaseCheckJob?.cancel()
            tokenManager.clearTokens().await()
            _authState.value = AuthState.UNAUTHENTICATED
            _leaseState.value = LeaseState.UNKNOWN
        }
    }

    /**
     * Gets the current access token, refreshing if needed.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val tokens = tokenManager.getTokens() ?: return@withContext null
        
        if (tokens.isAccessTokenValid()) {
            return tokens.accessToken
        }
        
        // Try refresh
        refreshAccessToken(tokens.refreshToken)
            .onSuccess { newTokens -> return@withContext newTokens.accessToken }
            .onFailure { return@withContext null }
        
        return null
    }

    /**
     * Checks if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    /**
     * Gets current tokens synchronously (for use in interceptors).
     */
    fun getCurrentTokens(): StoredTokens? {
        return tokenManager.getTokens().await()
    }

    fun destroy() {
        scope.coroutineContext.cancelChildren()
    }
}

/**
 * Result wrapper for async operations.
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
}
