package com.binti.dilink

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BintiService : Service() {

    companion object {
        const val ACTION_START_LISTENING = "com.binti.dilink.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.binti.dilink.action.STOP_LISTENING"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        val notification = NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle("Binti")
            .setContentText("Listening...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
