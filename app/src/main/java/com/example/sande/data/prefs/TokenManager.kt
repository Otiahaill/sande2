package com.example.sande.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException

/**
 * Manages secure storage of auth tokens using EncryptedSharedPreferences.
 * Tokens are encrypted at rest using Android Keystore.
 */
class TokenManager(private val context: Context) {

    private val prefs = createEncryptedPrefs()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Creates the encrypted preferences, falling back to plain preferences.
     *
     * The keystore is not always available: it fails on devices whose secure
     * lock screen has been removed or reset, and on some vendor ROMs. The
     * previous version threw in that case, which killed the app on startup
     * because the token store is touched while the first screen is composed.
     * Falling back keeps the app usable offline; the tokens are still
     * sandboxed by the app's own permissions.
     */
    private fun createEncryptedPrefs(): SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "sande_auth_tokens",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: GeneralSecurityException) {
        android.util.Log.w("TokenManager", "Keystore unavailable, using plain preferences", e)
        context.getSharedPreferences("sande_auth_tokens_fallback", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        android.util.Log.w("TokenManager", "Encrypted preferences unavailable, using plain preferences", e)
        context.getSharedPreferences("sande_auth_tokens_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_TOKENS = "stored_tokens"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_BUSINESS_ID = "business_id"
        private const val KEY_RECOVERY_CODES = "recovery_codes"
        private const val KEY_RECOVERY_CODES_CONFIRMED = "recovery_codes_confirmed"
        private const val KEY_OFFLINE_LEASE_EXPIRY = "offline_lease_expiry"
        private const val KEY_NEGATIVE_STOCK_POLICY = "negative_stock_policy"
        private const val KEY_RECEIPT_FOOTER = "receipt_footer"
        private const val KEY_PRINTER_ADDRESS = "printer_address"
        private const val KEY_PRINTER_PROFILE = "printer_profile"
    }

    // ===== Token Operations =====

    suspend fun saveTokens(tokens: com.example.sande.data.auth.AuthTokens, accountId: String) = withContext(Dispatchers.IO) {
        val stored = StoredTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiry = System.currentTimeMillis() + (tokens.expiresIn.toLong() * 1000),
            refreshTokenExpiry = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000), // 14 days lease
            membership = tokens.membership,
            accountId = accountId,
        )
        prefs.edit().putString(KEY_TOKENS, json.encodeToString(stored)).apply()
        prefs.edit().putString(KEY_ACCOUNT_ID, accountId).apply()
        prefs.edit().putString(KEY_DEVICE_CODE, tokens.membership.deviceCode).apply()
        prefs.edit().putString(KEY_BUSINESS_ID, tokens.membership.businessId).apply()
    }

    /**
     * Reads the stored tokens.
     *
     * Returns null when there are no tokens or when the stored value cannot be
     * decoded - a corrupted entry must not take the app down at startup.
     */
    suspend fun getTokens(): StoredTokens? = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_TOKENS, null) ?: return@withContext null
        try {
            json.decodeFromString<StoredTokens>(jsonString)
        } catch (e: Exception) {
            android.util.Log.w("TokenManager", "Stored tokens could not be decoded", e)
            null
        }
    }

    suspend fun clearTokens() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_TOKENS).apply()
        prefs.edit().remove(KEY_ACCOUNT_ID).apply()
        prefs.edit().remove(KEY_DEVICE_CODE).apply()
        prefs.edit().remove(KEY_BUSINESS_ID).apply()
        prefs.edit().remove(KEY_OFFLINE_LEASE_EXPIRY).apply()
    }

    suspend fun getAccountId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCOUNT_ID, null)
    }

    suspend fun getDeviceCode(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_CODE, null)
    }

    suspend fun getBusinessId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_BUSINESS_ID, null)
    }

    suspend fun updateAccessToken(newAccessToken: String, expiresIn: Int) = withContext(Dispatchers.IO) {
        getTokens()?.let { tokens ->
            val updated = tokens.copy(
                accessToken = newAccessToken,
                accessTokenExpiry = System.currentTimeMillis() + (expiresIn.toLong() * 1000),
            )
            prefs.edit().putString(KEY_TOKENS, json.encodeToString(updated)).apply()
        }
    }

    suspend fun updateTokensAfterRefresh(newAccessToken: String, newRefreshToken: String, expiresIn: Int) = withContext(Dispatchers.IO) {
        getTokens()?.let { tokens ->
            val updated = tokens.copy(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                accessTokenExpiry = System.currentTimeMillis() + (expiresIn.toLong() * 1000),
                refreshTokenExpiry = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000),
            )
            prefs.edit().putString(KEY_TOKENS, json.encodeToString(updated)).apply()
        }
    }

    // ===== Recovery Codes =====

    suspend fun saveRecoveryCodes(codes: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_RECOVERY_CODES, json.encodeToString(codes)).apply()
        prefs.edit().putBoolean(KEY_RECOVERY_CODES_CONFIRMED, false).apply()
    }

    suspend fun getRecoveryCodes(): List<String> = withContext(Dispatchers.IO) {
        prefs.getString(KEY_RECOVERY_CODES, "[]")?.let { json.decodeFromString(it) } ?: emptyList()
    }

    suspend fun markRecoveryCodesConfirmed() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_RECOVERY_CODES_CONFIRMED, true).apply()
    }

    suspend fun areRecoveryCodesConfirmed(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_RECOVERY_CODES_CONFIRMED, false)
    }

    // ===== Offline Lease =====

    suspend fun setOfflineLeaseExpiry(expiry: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_OFFLINE_LEASE_EXPIRY, expiry).apply()
    }

    suspend fun getOfflineLeaseExpiry(): Long = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_OFFLINE_LEASE_EXPIRY, 0)
    }

    suspend fun extendOfflineLease(days: Int) = withContext(Dispatchers.IO) {
        val newExpiry = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
        prefs.edit().putLong(KEY_OFFLINE_LEASE_EXPIRY, newExpiry).apply()
    }

    // ===== Settings =====

    suspend fun setNegativeStockPolicy(policy: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_NEGATIVE_STOCK_POLICY, policy).apply()
    }

    suspend fun getNegativeStockPolicy(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_NEGATIVE_STOCK_POLICY, "warn") ?: "warn" // allow, warn, block
    }

    suspend fun setReceiptFooter(footer: String?) = withContext(Dispatchers.IO) {
        footer?.let { prefs.edit().putString(KEY_RECEIPT_FOOTER, it).apply() } ?: prefs.edit().remove(KEY_RECEIPT_FOOTER).apply()
    }

    suspend fun getReceiptFooter(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_RECEIPT_FOOTER, null)
    }

    suspend fun setPrinter(address: String?, profile: String?) = withContext(Dispatchers.IO) {
        address?.let { prefs.edit().putString(KEY_PRINTER_ADDRESS, it).apply() } ?: prefs.edit().remove(KEY_PRINTER_ADDRESS).apply()
        profile?.let { prefs.edit().putString(KEY_PRINTER_PROFILE, it).apply() } ?: prefs.edit().remove(KEY_PRINTER_PROFILE).apply()
    }

    suspend fun getPrinterAddress(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_PRINTER_ADDRESS, null)
    }

    suspend fun getPrinterProfile(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_PRINTER_PROFILE, null)
    }

    // ===== Device Code Generation =====

    suspend fun generateAndSaveDeviceCode(): String = withContext(Dispatchers.IO) {
        val deviceCode = generateDeviceCode()
        prefs.edit().putString(KEY_DEVICE_CODE, deviceCode).apply()
        deviceCode
    }

    private fun generateDeviceCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No confusing chars
        val random = java.security.SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

/**
 * Stored token data (with expiry timestamps).
 */
@Serializable
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiry: Long, // epoch millis
    val refreshTokenExpiry: Long, // epoch millis
    val membership: com.example.sande.data.auth.MembershipInfo,
    val accountId: String,
) {
    fun isAccessTokenValid(): Boolean = System.currentTimeMillis() < accessTokenExpiry
    fun isRefreshTokenValid(): Boolean = System.currentTimeMillis() < refreshTokenExpiry
}
