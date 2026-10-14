// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.binti.dilink.contract.BintiIntent

/**
 * Binti Automation Activity — transparent no-history trampoline for
 * external automation triggers (Tasker, MacroDroid, ADB shell scripts).
 *
 * This activity receives `binti://` deep links and translates them to
 * [BintiService] foreground service starts. It finishes immediately after
 * dispatching, so the user never sees a UI — it appears as if the voice
 * assistant "just started" in response to the automation trigger.
 *
 * **Supported deep links:**
 * - `binti://start-voice` — start voice listening
 * - `binti://stop` — stop the voice service
 * - `binti://toggle-ac` — toggle AC power
 * - `binti://navigate-home` — navigate to saved home
 * - `binti://media-play` — play/pause media
 * - `binti://next-track` — skip to next track
 * - `binti://find-station` — find nearest EV charging station
 *
 * **ADB usage:**
 * ```bash
 * adb shell am start -a android.intent.action.VIEW -d "binti://start-voice"
 * ```
 *
 * **Manifest declaration:**
 * The activity is declared with:
 * - `android:exported="true"` — so external apps can target it
 * - `android:excludeFromRecents="true"` — doesn't appear in recent apps
 * - `android:noHistory="true"` — finishes before appearing in back stack
 * - `android:taskAffinity=""` — runs in its own task, not the main app task
 * - `android:theme="@android:style/Theme.Translucent.NoTitleBar"` — invisible
 *
 * @author Dr. Waleed Mandour
 */
class BintiAutomationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BintiAutomation"
        private const val PREFS_NAME = "binti_prefs"

        /**
         * When false (default), only Binti itself or apps signed with the
         * same key may fire binti:// triggers. Set this pref to true to
         * re-enable third-party automation (Tasker, MacroDroid, ADB scripts).
         */
        const val KEY_ALLOW_EXTERNAL_AUTOMATION = "allow_external_automation"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Automation activity triggered")

        // P1 FIX (open entry point): the activity used to accept binti://
        // triggers from ANY installed app — any app could toggle the AC or
        // start a voice session silently. Same-signature callers (and ADB,
        // which has no referrer) pass; third-party apps now need the user's
        // explicit opt-in.
        if (!isCallerAllowed()) {
            Log.w(TAG, "Blocked binti:// trigger from untrusted caller " +
                       "(referrer=${referrer?.host ?: "null"}). " +
                       "Enable '$KEY_ALLOW_EXTERNAL_AUTOMATION' to allow third-party automation.")
            finish()
            return
        }

        val data = intent?.data
        if (data == null) {
            Log.w(TAG, "No deep link data — finishing")
            finish()
            return
        }

        val host = data.host
        Log.i(TAG, "Deep link host: $host")

        when (host) {
            BintiIntent.HOST_START_VOICE -> startVoiceService(BintiService.ACTION_START_LISTENING)
            BintiIntent.HOST_STOP -> stopVoiceService()
            BintiIntent.HOST_TOGGLE_AC -> executeCommand("AC_CONTROL", "شغل المكيف")
            BintiIntent.HOST_NAVIGATE_HOME -> executeCommand("NAVIGATION", "خديني للبيت")
            BintiIntent.HOST_MEDIA_PLAY -> executeCommand("MEDIA", "شغل")
            BintiIntent.HOST_NEXT_TRACK -> executeCommand("MEDIA", "التالي")
            BintiIntent.HOST_FIND_STATION -> executeCommand("STATION", "أقرب محطة شحن")
            else -> {
                Log.w(TAG, "Unknown deep link host: $host")
            }
        }

        // Always finish immediately — this is a trampoline, not a UI
        finish()
    }

    /**
     * P1 FIX: caller policy for the automation trampoline.
     *
     * Allowed without opt-in:
     *  - callers with no referrer (ADB shell, system trampolines)
     *  - Binti's own components
     *  - apps signed with the same signing key
     *
     * Everything else (Tasker, MacroDroid, unknown apps) requires the user
     * to have set [KEY_ALLOW_EXTERNAL_AUTOMATION] in binti_prefs.
     */
    private fun isCallerAllowed(): Boolean {
        val caller = try { referrer?.host } catch (e: Exception) { null }

        // ADB `am start` and system-intent trampolines carry no referrer
        if (caller.isNullOrBlank()) return true

        // Our own components
        if (caller == packageName) return true

        // Same signing key (e.g. a future Binti companion)
        try {
            val pm = packageManager
            if (pm.checkSignatures(packageName, caller) ==
                android.content.pm.PackageManager.SIGNATURE_MATCH) return true
        } catch (e: Exception) { /* caller not installed / unknown — fall through */ }

        // Explicit user opt-in for third-party automation
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_EXTERNAL_AUTOMATION, false)
    }

    /**
     * Start [BintiService] with the given action.
     */
    private fun startVoiceService(action: String) {
        val intent = Intent(this, BintiService::class.java).apply {
            this.action = action
        }
        startForegroundServiceCompat(intent)
    }

    /**
     * Stop [BintiService].
     */
    private fun stopVoiceService() {
        val intent = Intent(this, BintiService::class.java)
        stopService(intent)
    }

    /**
     * Send a command to [BintiService] for execution.
     */
    private fun executeCommand(action: String, pattern: String) {
        val intent = Intent(this, BintiService::class.java).apply {
            this.action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", org.json.JSONObject().apply {
                put("action", action)
                put("pattern", pattern)
            }.toString())
        }
        startForegroundServiceCompat(intent)
    }

    /**
     * Start a foreground service, handling the API 26+ requirement for
     * startForegroundService() vs. startService().
     */
    private fun startForegroundServiceCompat(intent: Intent) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }
}
