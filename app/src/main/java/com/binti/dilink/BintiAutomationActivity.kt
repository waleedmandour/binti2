// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Automation activity triggered")

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
