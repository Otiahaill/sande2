package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.prefs.ConnectionSettings
import com.example.sande.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val connectionSettings: ConnectionSettings,
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    data class ConnectionState(
        val googleClientId: String?,
        val apiBaseUrl: String,
        val environment: String,
        val deviceName: String,
    )

    /** Loads the current connection configuration. */
    suspend fun load(): ConnectionState {
        val status = connectionSettings.checkConfiguration()
        _isReady.value = status.isReady

        return ConnectionState(
            googleClientId = status.googleClientId,
            apiBaseUrl = status.apiBaseUrl,
            environment = connectionSettings.getEnvironment(),
            deviceName = connectionSettings.getDeviceName(),
        )
    }

    /**
     * Tests a backend URL by calling its /health endpoint.
     * Returns a human-readable result.
     */
    suspend fun testConnection(baseUrl: String): String {
        return try {
            val url = baseUrl.trimEnd('/')
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                apiClient.healthCheck(url)
            }
            _isReady.value = true
            response
        } catch (e: Exception) {
            "Could not reach the backend: ${e.message}"
        }
    }

    /**
     * Saves the connection configuration.
     */
    suspend fun save(
        googleClientId: String,
        apiBaseUrl: String,
        environment: String,
        deviceName: String,
    ): String {
        return try {
            if (googleClientId.isNotBlank()) {
                connectionSettings.setGoogleClientId(googleClientId)
            }
            if (apiBaseUrl.isNotBlank()) {
                connectionSettings.setApiBaseUrl(apiBaseUrl)
            }
            connectionSettings.setEnvironment(environment)
            if (deviceName.isNotBlank()) {
                connectionSettings.setDeviceName(deviceName)
            }
            // Make sure a device code exists
            connectionSettings.getOrCreateDeviceCode()

            val status = connectionSettings.checkConfiguration()
            _isReady.value = status.isReady

            if (status.isReady) {
                "Saved. The app will use these settings on the next sign-in."
            } else {
                val missing = buildList {
                    if (!status.hasGoogleClientId) add("Google client ID")
                    if (!status.hasDeviceCode) add("device code")
                    if (!status.apiBaseUrl.startsWith("https://")) add("valid backend URL")
                }
                "Saved, but still missing: ${missing.joinToString(", ")}"
            }
        } catch (e: Exception) {
            "Could not save: ${e.message}"
        }
    }
}
