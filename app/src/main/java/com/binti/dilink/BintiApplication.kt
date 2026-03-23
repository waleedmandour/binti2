package com.binti.dilink

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.binti.dilink.utils.ModelUpdateWorker
import com.binti.dilink.utils.PreferenceManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Binti Application - Egyptian Arabic Voice Assistant for BYD DiLink
 * 
 * Main application class that initializes:
 * - Logging infrastructure (Timber)
 * - Notification channels for foreground service
 * - Model update scheduler
 * - Preference management
 */
class BintiApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_SERVICE = "binti_service_channel"
        const val NOTIFICATION_CHANNEL_MODELS = "binti_models_channel"
        
        @Volatile
        private var instance: BintiApplication? = null
        
        fun getInstance(): BintiApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize logging
        initializeLogging()
        
        // Initialize preferences
        PreferenceManager.init(this)
        
        // Create notification channels
        createNotificationChannels()
        
        // Schedule periodic model update checks
        scheduleModelUpdateChecks()
        
        Timber.i("Binti Application initialized - Egyptian Arabic Voice Assistant")
    }

    /**
     * Initialize Timber logging based on build type
     */
    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Production: Only log warnings and errors
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= android.util.Log.WARN) {
                        // Could integrate with crash reporting here
                    }
                }
            })
        }
    }

    /**
     * Create notification channels for Android O+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Service notification channel
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_service_description)
                setShowBadge(false)
            }
            
            // Model update notification channel
            val modelsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MODELS,
                getString(R.string.notification_channel_models),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_models_description)
            }
            
            notificationManager.createNotificationChannels(listOf(serviceChannel, modelsChannel))
        }
    }

    /**
     * Schedule weekly model update checks using WorkManager
     */
    private fun scheduleModelUpdateChecks() {
        val updateWork = PeriodicWorkRequestBuilder<ModelUpdateWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "model_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWork
        )
        
        Timber.d("Scheduled weekly model update checks")
    }
}
