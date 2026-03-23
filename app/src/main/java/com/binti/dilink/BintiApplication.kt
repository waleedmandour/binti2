package com.binti.dilink

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import timber.log.Timber

class BintiApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_SERVICE = "binti_service_channel"
        const val NOTIFICATION_CHANNEL_MODELS = "binti_models_channel"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Create notification channels
        createNotificationChannels()
        
        Timber.i("Binti Application initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_service_description)
                setShowBadge(false)
            }
            
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
}
