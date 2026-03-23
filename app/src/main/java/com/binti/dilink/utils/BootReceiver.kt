package com.binti.dilink.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.binti.dilink.BintiService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed")
            
            if (PreferenceManager.isWakeWordEnabled()) {
                val serviceIntent = Intent(context, BintiService::class.java).apply {
                    action = BintiService.ACTION_START_LISTENING
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start service on boot")
                }
            }
        }
    }
}
