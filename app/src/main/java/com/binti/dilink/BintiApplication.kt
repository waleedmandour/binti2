

package com.binti.dilink

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.binti.dilink.utils.ModelManager
import dagger.hilt.android.HiltAndroidApp
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
 * - Hilt dependency injection graph (Phase 2)
 *
 * **Phase 2 — Hilt:** Annotated with [@HiltAndroidApp] to trigger Hilt's
 * code generation. Existing singletons (VehicleDataRepository,
 * StationManager, EgyptianTTS, etc.) continue to use their `getInstance()`
 * pattern — they will be migrated to @Inject constructors incrementally
 * in subsequent phases. The Hilt graph is set up now so new code can use
 * @Inject from the start.
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
@HiltAndroidApp
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

        // v3.0.2 FIX (English UI on English devices): Binti is Egyptian-market
        // only and ships ONE Arabic UI. values-en / values-ar are deleted, so
        // the default (Arabic) resources resolve on every device. This explicit
        // per-app locale lock makes that guarantee airtight — even if some OEM
        // ROM or future resource set would otherwise resolve to the device
        // locale, AppCompat wraps every activity context in Arabic.
        // appcompat 1.7.0 backports per-app locales to all supported API levels.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))

        // Tier 2 wiring — flavor-dependent. The active flavor's
        // TelemetryInitializer is in `src/<flavor>/java/...`. Both flavors
        // expose the same `init(Context)` signature.
        try {
            com.binti.dilink.telemetry.TelemetryInitializer.init(this)
        } catch (e: Throwable) {
            Log.w(TAG, "TelemetryInitializer.init() failed — Tier 2 unavailable", e)
        }

        // Brightness control wiring — flavor-dependent. The enthusiast flavor
        // registers a RealBrightnessBridge (with WRITE_SETTINGS declared in the
        // enthusiast manifest). The store flavor leaves the NoOpBrightnessBridge
        // in place — no WRITE_SETTINGS permission in the store build.
        try {
            com.binti.dilink.dilink.settings.BrightnessInitializer.init(this)
        } catch (e: Throwable) {
            Log.w(TAG, "BrightnessInitializer.init() failed — brightness control unavailable", e)
        }

        // Direct control wiring — flavor-dependent. The enthusiast flavor
        // registers a DirectControlBridge that uses app_process + reflection
        // against bydauto.* services as a fallback when accessibility can't
        // be enabled (common on BYD DiLink 3 head units). The store flavor
        // is a no-op (accessibility-only mode).
        try {
            com.binti.dilink.dilink.control.DirectControlInitializer.init(this)
        } catch (e: Throwable) {
            Log.w(TAG, "DirectControlInitializer.init() failed — direct control unavailable", e)
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
