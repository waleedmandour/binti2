

package com.binti.dilink

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.binti.dilink.utils.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Binti Application - Egyptian Arabic Voice Assistant for BYD DiLink
 *
 * Main application class that initializes:
 * - Notification channels for foreground service
 * - Model download manager
 * - Advanced Telemetry bridge (flavor-dependent — see below)
 *
 * **Tier 2 wiring:** At startup we call `TelemetryInitializer.init(this)`.
 * The `TelemetryInitializer` class is provided by each product flavor:
 *  - `store` flavor       → `com.binti.dilink.telemetry.TelemetryInitializer`
 *                           is a no-op that leaves the NoOpAdvancedTelemetryBridge
 *                           in place. No `android.hardware.bydauto.*` code
 *                           is loaded.
 *  - `enthusiast` flavor  → registers a real `AdvancedTelemetryProbe` that
 *                           reflects against `android.hardware.bydauto.*`
 *                           (consent-gated, comfort/info only).
 *
 * Main source never imports the enthusiast class directly — only the shared
 * `AdvancedTelemetryLoader` / `AdvancedTelemetryBridge` contract.
 *
 * @author Dr. Waleed Mandour
 */
class BintiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "BintiApp"
        const val NOTIFICATION_CHANNEL_ID = "binti_voice_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Binti Voice Assistant"

        @Volatile
        private var instance: BintiApplication? = null

        fun getInstance(): BintiApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "🚗 Binti Application starting... (flavor=${BuildConfig.FLAVOR})")

        // Tier 2 wiring — flavor-dependent. The active flavor's
        // TelemetryInitializer is in `src/<flavor>/java/...`. Both flavors
        // expose the same `init(Context)` signature.
        try {
            com.binti.dilink.telemetry.TelemetryInitializer.init(this)
        } catch (e: Throwable) {
            Log.w(TAG, "TelemetryInitializer.init() failed — Tier 2 unavailable", e)
        }

        // Initialize in background to avoid blocking startup
        applicationScope.launch {
            initializeNotificationChannel()
            checkModelsStatus()
        }

        Log.i(TAG, "✅ Binti Application initialized successfully")
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun initializeNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Check if AI models are downloaded and ready
     */
    private suspend fun checkModelsStatus() {
        val modelManager = ModelManager(this)
        val status = modelManager.checkModelsStatus()
        
        when {
            status.allModelsReady -> {
                Log.i(TAG, "✅ All AI models ready (${status.totalSizeMB}MB)")
            }
            status.partialModelsReady -> {
                Log.w(TAG, "⚠️ Some models missing (${status.readyCount}/${status.totalCount})")
            }
            else -> {
                Log.i(TAG, "📥 Models not downloaded yet, will prompt user on first run")
            }
        }
    }
}
