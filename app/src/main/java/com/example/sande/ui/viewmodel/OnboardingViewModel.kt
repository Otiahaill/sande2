package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.auth.GoogleSignInResult
import com.example.sande.data.remote.AcceptInvitationRequest
import com.example.sande.data.remote.BusinessRegistrationRequest
import com.example.sande.data.remote.DeviceRegistrationRequest
import com.example.sande.data.auth.LegacyAdoptionManager
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val legacyAdoptionManager: LegacyAdoptionManager,
) : ViewModel() {

    // Expose session manager's auth state
    val authState: StateFlow<SessionManager.AuthState> = sessionManager.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SessionManager.AuthState.UNAUTHENTICATED)

    val leaseState: StateFlow<SessionManager.LeaseState> = sessionManager.leaseState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SessionManager.LeaseState.UNKNOWN)

    // Pending business registration request (for legacy adoption)
    private val _pendingRegistration = MutableStateFlow<BusinessRegistrationRequest?>(null)
    val pendingRegistration: StateFlow<BusinessRegistrationRequest?> = _pendingRegistration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // Legacy adoption data counts
    private val _legacyDataCounts = MutableStateFlow<LegacyAdoptionManager.LegacyDataCounts?>(null)
    val legacyDataCounts: StateFlow<LegacyAdoptionManager.LegacyDataCounts?> = _legacyDataCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // Legacy adoption progress
    private val _adoptionProgress = MutableStateFlow(0f)
    val adoptionProgress: StateFlow<Float> = _adoptionProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    private val _adoptionMessage = MutableStateFlow<String>("")
    val adoptionMessage: StateFlow<String> = _adoptionMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    private val _adoptionError = MutableStateFlow<String?>(null)
    val adoptionError: StateFlow<String?> = _adoptionError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _adoptionComplete = MutableStateFlow(false)
    val adoptionComplete: StateFlow<Boolean> = _adoptionComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun generateDeviceCode(): String {
        return "LOC" // Fallback, actual generation happens in TokenManager
    }

    fun handleGoogleSignIn(
        idToken: String,
        deviceCode: String,
        deviceName: String,
        deviceType: String,
    ) {
        sessionManager.handleGoogleSignIn(idToken, deviceCode, deviceName, deviceType)
    }

    fun registerBusiness(request: BusinessRegistrationRequest) {
        _pendingRegistration.value = request
        sessionManager.registerBusiness(request)
    }

    fun selectBusiness(businessId: String, deviceCode: String, deviceName: String, deviceType: String) {
        sessionManager.selectBusiness(businessId, deviceCode, deviceName, deviceType)
    }

    fun acceptInvitation(request: AcceptInvitationRequest) {
        sessionManager.acceptInvitation(request)
    }

    fun registerDevice(request: DeviceRegistrationRequest) {
        sessionManager.registerDevice(request)
    }

    suspend fun extendLease(days: Int) {
        sessionManager.extendLease(days)
    }

    fun checkSession() {
        sessionManager.checkSession()
    }

    fun signOut() {
        sessionManager.signOut()
    }

    /** Checks for legacy data and loads counts. */
    suspend fun checkLegacyData() {
        val hasData = legacyAdoptionManager.hasLegacyData().await()
        if (hasData) {
            val counts = legacyAdoptionManager.getLegacyDataCounts().await()
            _legacyDataCounts.value = counts
        } else {
            _legacyDataCounts.value = null
        }
    }

    /** Starts the legacy data adoption process. */
    suspend fun adoptLegacyData() {
        val request = _pendingRegistration.value
            ?: throw IllegalStateException("No pending registration request")

        _adoptionProgress.value = 0f
        _adoptionMessage.value = "Starting..."
        _adoptionError.value = null
        _adoptionComplete.value = false

        val result = legacyAdoptionManager.adoptLegacyData(request) { message, progress ->
            _adoptionMessage.value = message
            _adoptionProgress.value = progress
        }.await()

        result.onSuccess { adoptionResult ->
            _adoptionComplete.value = true
        }.onFailure { error ->
            _adoptionError.value = error.message ?: "Unknown error"
            _adoptionComplete.value = false
        }
    }

    fun resetAdoptionState() {
        _adoptionProgress.value = 0f
        _adoptionMessage.value = ""
        _adoptionError.value = null
        _adoptionComplete.value = false
    }
}
