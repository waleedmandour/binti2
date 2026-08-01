

package com.binti.dilink.dilink.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Tier 2 consent state — shared between main source and the enthusiast flavor.
 *
 * This class lives in main source so the developer debug screen, voice
 * response formatter, and any future UI can query consent state without a
 * direct dependency on `AdvancedTelemetryProbe` (which only exists in the
 * `enthusiast` flavor).
 *
 * Consent is a three-state contract:
 *  - NOT_SHOWN  — user has never been asked. The enthusiast onboarding must
 *                 fire before any probe activity.
 *  - GRANTED    — user explicitly accepted the warning and enabled the toggle.
 *  - REVOKED    — user explicitly turned the toggle off after granting.
 *
 * Consent is also versioned: bumping [CONSENT_VERSION] forces a re-prompt
 * after the consent text changes (e.g. new probed services added).
 *
 * @author Dr. Waleed Mandour
 */
class TelemetryConsentManager private constructor(private val prefs: SharedPreferences) {

    enum class ConsentState { NOT_SHOWN, GRANTED, REVOKED }

    val state: ConsentState
        get() {
            val granted = prefs.getBoolean(KEY_GRANTED, false)
            val revoked = prefs.getBoolean(KEY_REVOKED, false)
            val version = prefs.getInt(KEY_VERSION, 0)
            return when {
                !granted && !revoked -> ConsentState.NOT_SHOWN
                granted && version == CONSENT_VERSION -> ConsentState.GRANTED
                else -> ConsentState.REVOKED
            }
        }

    val isGranted: Boolean
        get() = state == ConsentState.GRANTED

    /** Record explicit user consent. Call ONLY from the onboarding confirmation. */
    fun grant() {
        prefs.edit()
            .putBoolean(KEY_GRANTED, true)
            .putBoolean(KEY_REVOKED, false)
            .putInt(KEY_VERSION, CONSENT_VERSION)
            .putLong(KEY_GRANTED_AT, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "Advanced Telemetry consent GRANTED")
    }

    /** Record explicit user opt-out. Reversible — `grant()` can be called again later. */
    fun revoke() {
        prefs.edit()
            .putBoolean(KEY_GRANTED, false)
            .putBoolean(KEY_REVOKED, true)
            .remove(KEY_GRANTED_AT)
            .apply()
        Log.i(TAG, "Advanced Telemetry consent REVOKED")
    }

    /** Reset to NOT_SHOWN. Used by tests and by the "Reset All Settings" flow. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    val grantedAtMillis: Long
        get() = prefs.getLong(KEY_GRANTED_AT, 0L)

    companion object {
        private const val TAG = "TelemetryConsent"
        private const val PREFS_NAME = "binti_telemetry_consent"
        private const val KEY_GRANTED    = "consent_granted"
        private const val KEY_REVOKED    = "consent_revoked"
        private const val KEY_VERSION    = "consent_version"
        private const val KEY_GRANTED_AT = "consent_granted_at"

        /**
         * Bump when the consent text or the list of probed services changes
         * materially — existing grants are treated as revoked so the user
         * is re-prompted.
         */
        const val CONSENT_VERSION: Int = 1

        @Volatile private var instance: TelemetryConsentManager? = null

        @JvmStatic
        fun get(context: Context): TelemetryConsentManager =
            instance ?: synchronized(this) {
                instance ?: TelemetryConsentManager(
                    context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
