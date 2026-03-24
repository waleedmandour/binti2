package com.binti.dilink.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.binti.dilink.BintiService

/**
 * Boot Receiver
 * 
 * Starts Binti service on device boot if auto-start is enabled.
 * Note: On BYD vehicles, this might need special permissions.
 * 
 * @author Dr. Waleed Mandour
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(TAG, "🚗 Boot completed, checking auto-start settings...")
            
            // Check if auto-start is enabled in preferences
            val prefs = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)
            
            if (autoStart) {
                Log.i(TAG, "Auto-start enabled, starting Binti service")
                
                try {
                    val serviceIntent = Intent(context, BintiService::class.java).apply {
                        action = BintiService.ACTION_START
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service on boot", e)
                }
            } else {
                Log.d(TAG, "Auto-start disabled, not starting service")
            }
        }
    }
}
