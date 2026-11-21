package com.example.sande.data.auth

import android.content.Context
import com.example.sande.data.prefs.ConnectionSettings
import com.example.sande.data.prefs.TokenManager
import kotlinx.coroutines.runBlocking

/**
 * Resolves the effective connection configuration at runtime.
 *
 * The Connection screen (Settings → Connection) stores overrides in
 * [ConnectionSettings]; [com.example.sande.data.AppSettings] holds the
 * build-time defaults. Whatever the user saved wins.
 *
 * Reading is synchronous because the Ktor client and the Credential Manager
 * sign-in flow both need the values at construction time, before any
 * coroutine scope exists.
 */
class ConnectionResolver(
    private val context: Context,
    private val connectionSettings: ConnectionSettings,
    private val tokenManager: TokenManager,
    private val appSettings: com.example.sande.data.AppSettings,
) {

    /** Google OAuth client ID currently in effect. */
    fun googleClientId(): String {
        return runBlocking {
            connectionSettings.getGoogleClientId()
        }?.takeIf { it.isNotBlank() } ?: appSettings.googleClientId
    }

    /** Google Android OAuth client ID currently in effect. */
    fun googleAndroidClientId(): String {
        return runBlocking {
            connectionSettings.getGoogleClientId()
        }?.takeIf { it.isNotBlank() } ?: appSettings.googleAndroidClientId
    }

    /** Backend base URL currently in effect. */
    fun apiBaseUrl(): String {
        return runBlocking {
            connectionSettings.getApiBaseUrl()
        }.takeIf { it.isNotBlank() } ?: appSettings.apiBaseUrl
    }

    /** Device code for this install, created on first use. */
    fun deviceCode(): String {
        return runBlocking {
            connectionSettings.getOrCreateDeviceCode()
        }
    }

    /** Business ID for this install, set during business registration/adoption. */
    fun getBusinessId(): String {
        return runBlocking {
            tokenManager.getBusinessId()
        } ?: "default-business"
    }

    /** Human-readable device name. */
    fun deviceName(): String {
        return runBlocking {
            connectionSettings.getDeviceName()
        }
    }

    /** True when the install has everything needed to talk to the backend. */
    fun isConfigured(): Boolean {
        return runBlocking {
            connectionSettings.checkConfiguration().isReady
        }
    }
}
