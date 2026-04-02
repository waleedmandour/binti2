package com.binti.dilink.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.binti.dilink.BintiService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        Log.i(TAG, "Boot action received: $action")

        // FIX #1 — LOCKED_BOOT_COMPLETED fires before the user has unlocked the
        // device (Direct Boot mode). SharedPreferences (credential-encrypted by
        // default) is NOT accessible at this point and getSharedPreferences()
        // throws or silently returns a fresh empty prefs, making autoStart always
        // false. Use device-protected storage for the auto-start flag, which is
        // readable before unlock.
        val prefs = if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            context.createDeviceProtectedStorageContext()
                .getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
        } else {
            context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
        }

        val autoStart = prefs.getBoolean("auto_start", false)

        if (!autoStart) {
            Log.d(TAG, "Auto-start disabled, not starting service")
            return
        }

        Log.i(TAG, "Auto-start enabled, starting BintiService")

        try {
            val serviceIntent = Intent(context, BintiService::class.java).apply {
                action = BintiService.ACTION_START
            }
            // FIX #2 — replaced Build.VERSION.SDK_INT >= O branch with
            // ContextCompat.startForegroundService() which handles the version
            // check internally and is the recommended AndroidX approach.
            ContextCompat.startForegroundService(context, serviceIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BintiService on boot", e)
        }
    }
}
