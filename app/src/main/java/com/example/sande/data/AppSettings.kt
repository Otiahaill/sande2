package com.example.sande.data

import android.content.Context

/** Small on-device settings store (works fully offline). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sande_settings", Context.MODE_PRIVATE)

    /** What happens when a sale would take stock below zero: WARN, BLOCK or ALLOW. */
    var negativeStockPolicy: String
        get() = prefs.getString(KEY_NEGATIVE_STOCK, POLICY_WARN) ?: POLICY_WARN
        set(value) {
            prefs.edit().putString(KEY_NEGATIVE_STOCK, value).apply()
        }

    /**
     * Google OAuth client ID (build-time default).
     *
     * This is the Web client ID. The Connection screen can override this at runtime via
     * [com.example.sande.data.prefs.ConnectionSettings]; that value wins.
     * This field holds the fallback baked in at build time.
     */
    var googleClientId: String
        get() = prefs.getString(KEY_GOOGLE_CLIENT_ID, DEFAULT_GOOGLE_CLIENT_ID) ?: DEFAULT_GOOGLE_CLIENT_ID
        set(value) {
            prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, value).apply()
        }

    /** Google Android OAuth client ID (build-time default). */
    var googleAndroidClientId: String
        get() = prefs.getString(KEY_GOOGLE_ANDROID_CLIENT_ID, DEFAULT_GOOGLE_ANDROID_CLIENT_ID) ?: DEFAULT_GOOGLE_ANDROID_CLIENT_ID
        set(value) {
            prefs.edit().putString(KEY_GOOGLE_ANDROID_CLIENT_ID, value).apply()
        }

    /** API base URL (build-time default; overridable at runtime). */
    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_API_BASE_URL, value).apply()
        }

    /** Device code for this installation. */
    var deviceCode: String
        get() = prefs.getString(KEY_DEVICE_CODE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEVICE_CODE, value).apply()
        }

    /** Receipt footer text. */
    var receiptFooter: String?
        get() = prefs.getString(KEY_RECEIPT_FOOTER, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_RECEIPT_FOOTER, value).apply()
            else prefs.edit().remove(KEY_RECEIPT_FOOTER).apply()
        }

    /** Printer MAC address. */
    var printerAddress: String?
        get() = prefs.getString(KEY_PRINTER_ADDRESS, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_PRINTER_ADDRESS, value).apply()
            else prefs.edit().remove(KEY_PRINTER_ADDRESS).apply()
        }

    /** Printer profile (58mm or 80mm). */
    var printerProfile: String
        get() = prefs.getString(KEY_PRINTER_PROFILE, "58mm") ?: "58mm"
        set(value) {
            prefs.edit().putString(KEY_PRINTER_PROFILE, value).apply()
        }

    companion object {
        const val POLICY_WARN = "warn"
        const val POLICY_BLOCK = "block"
        const val POLICY_ALLOW = "allow"

        /**
         * Build-time defaults. Override at runtime from the Connection screen
         * (Settings → Connection) once the backend is deployed.
         */
        const val DEFAULT_GOOGLE_CLIENT_ID = "30937762168-1q32ltfb5co7t0l5763fjn23dv2rtsbq.apps.googleusercontent.com"
        const val DEFAULT_GOOGLE_ANDROID_CLIENT_ID = "30937762168-5c4b7ph38sk85nc3mqanedngbjv02n3f.apps.googleusercontent.com"
        const val DEFAULT_API_BASE_URL = "https://sande-backend-dev.your-subdomain.workers.dev"

        private const val KEY_NEGATIVE_STOCK = "negative_stock_policy"
        private const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        private const val KEY_GOOGLE_ANDROID_CLIENT_ID = "google_android_client_id"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_RECEIPT_FOOTER = "receipt_footer"
        private const val KEY_PRINTER_ADDRESS = "printer_address"
        private const val KEY_PRINTER_PROFILE = "printer_profile"
    }
}
