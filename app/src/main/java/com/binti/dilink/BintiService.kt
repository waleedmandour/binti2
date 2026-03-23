package com.binti.dilink

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class BintiService : Service() {

    companion object {
        const val ACTION_START_LISTENING = "com.binti.dilink.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.binti.dilink.action.STOP_LISTENING"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("BintiService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Timber.i("Started listening for wake word")
    }

    private fun stopListening() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("Stopped listening")
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
