package com.example.sande.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Connection settings for the online backend.
 *
 * These are configuration values, not credentials — the Google OAuth client ID
 * is public by design (it is embedded in every APK), and the API base URL is
 * public. The client *secret* never lives on the device; it stays in Cloudflare
 * Workers Secrets.
 */
class ConnectionSettings(private val context: Context) {

    private val prefs = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "sande_connection",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fall back to plain prefs if the keystore is unavailable (rare, but
        // better than crashing on launch).
        context.getSharedPreferences("sande_connection_fallback", Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ENVIRONMENT = "environment"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_SYNC_ERROR = "sync_error"

        const val DEFAULT_API_BASE_URL = "https://sande-backend-dev.your-subdomain.workers.dev"
        const val ENV_DEV = "dev"
        const val ENV_STAGING = "staging"
        const val ENV_PRODUCTION = "production"
    }

    /** Google OAuth Web client ID (public). */
    suspend fun getGoogleClientId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_GOOGLE_CLIENT_ID, null)
    }

    suspend fun setGoogleClientId(clientId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, clientId).apply()
    }

    /**
     * Base URL of the SANDE backend Worker.
     *
     * Resolution order:
     *   1. the URL the user saved on the Connection screen (runtime override),
     *   2. the URL baked in at build time from `BuildConfig.API_BASE_URL`
     *      (injected by CI from the `API_BASE_URL` secret),
     *   3. the placeholder below, which only exists so the Connection screen
     *      has something to show.
     *
     * Step 2 used to be missing: this method always returned a non-blank
     * placeholder, so `ConnectionResolver.apiBaseUrl()` never fell through to
     * `AppSettings.apiBaseUrl` and the build-time URL was ignored.
     */
    suspend fun getApiBaseUrl(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: com.example.sande.BuildConfig.API_BASE_URL.takeIf { it.isNotBlank() }
            ?: DEFAULT_API_BASE_URL
    }

    suspend fun setApiBaseUrl(url: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_BASE_URL, url.trimEnd('/')).apply()
    }

    /** Stable per-install device code (6 chars, no ambiguous glyphs). */
    suspend fun getDeviceCode(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_CODE, null)
    }

    suspend fun setDeviceCode(code: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_DEVICE_CODE, code).apply()
    }

    suspend fun getOrCreateDeviceCode(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_CODE, null) ?: generateDeviceCode().also {
            prefs.edit().putString(KEY_DEVICE_CODE, it).apply()
        }
    }

    suspend fun getDeviceName(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL ?: "Android Device") ?: "Android Device"
    }

    suspend fun setDeviceName(name: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    /** Which backend environment this install talks to. */
    suspend fun getEnvironment(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ENVIRONMENT, ENV_DEV) ?: ENV_DEV
    }

    suspend fun setEnvironment(env: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ENVIRONMENT, env).apply()
    }

    suspend fun getLastSyncAt(): Long = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_LAST_SYNC_AT, 0)
    }

    suspend fun setLastSyncAt(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, timestamp).apply()
    }

    suspend fun getSyncError(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_SYNC_ERROR, null)
    }

    suspend fun setSyncError(error: String?) = withContext(Dispatchers.IO) {
        if (error != null) prefs.edit().putString(KEY_SYNC_ERROR, error).apply()
        else prefs.edit().remove(KEY_SYNC_ERROR).apply()
    }

    /** Generates a random 6-character device code using unambiguous glyphs. */
    private fun generateDeviceCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Checks that the install has everything it needs to talk to the backend.
     */
    suspend fun checkConfiguration(): ConfigurationStatus = withContext(Dispatchers.IO) {
        val clientId = getGoogleClientId()
        val url = getApiBaseUrl()
        val deviceCode = getDeviceCode()

        ConfigurationStatus(
            hasGoogleClientId = !clientId.isNullOrBlank(),
            googleClientId = clientId,
            apiBaseUrl = url,
            hasDeviceCode = !deviceCode.isNullOrBlank(),
            isReady = !clientId.isNullOrBlank() && url.startsWith("https://") && !deviceCode.isNullOrBlank(),
        )
    }

    data class ConfigurationStatus(
        val hasGoogleClientId: Boolean,
        val googleClientId: String?,
        val apiBaseUrl: String,
        val hasDeviceCode: Boolean,
        val isReady: Boolean,
    )
}
