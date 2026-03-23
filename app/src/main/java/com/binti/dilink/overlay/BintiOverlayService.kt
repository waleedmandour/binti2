package com.binti.dilink.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BintiOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
