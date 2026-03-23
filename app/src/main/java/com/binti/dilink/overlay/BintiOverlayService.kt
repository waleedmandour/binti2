package com.binti.dilink.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.binti.dilink.R
import timber.log.Timber

class BintiOverlayService : Service() {
    
    companion object {
        const val ACTION_SHOW = "com.binti.dilink.overlay.SHOW"
        const val ACTION_HIDE = "com.binti.dilink.overlay.HIDE"
        const val ACTION_UPDATE = "com.binti.dilink.overlay.UPDATE"
        
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Timber.i("Overlay service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_UPDATE -> updateOverlay(
                intent.getStringExtra(EXTRA_STATE) ?: "",
                intent.getStringExtra(EXTRA_MESSAGE)
            )
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun showOverlay() {
        if (overlayView != null) return
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }
        
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_voice_card, null)
        
        try {
            windowManager?.addView(overlayView, layoutParams)
            Timber.i("Overlay shown")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show overlay")
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
            Timber.i("Overlay hidden")
        }
    }
    
    private fun updateOverlay(state: String, message: String?) {
        overlayView?.let { view ->
            val statusText = view.findViewById<TextView>(R.id.status_text)
            statusText?.text = message ?: state
            Timber.d("Overlay updated: $state")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        Timber.i("Overlay service destroyed")
    }
}
