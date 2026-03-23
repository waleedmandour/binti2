package com.binti.dilink.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

class BintiOverlayService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("Overlay service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
