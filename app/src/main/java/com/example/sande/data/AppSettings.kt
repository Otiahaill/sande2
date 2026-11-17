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

    companion object {
        const val POLICY_WARN = "WARN"
        const val POLICY_BLOCK = "BLOCK"
        const val POLICY_ALLOW = "ALLOW"
        private const val KEY_NEGATIVE_STOCK = "negative_stock_policy"
    }
}
