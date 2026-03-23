package com.binti.dilink.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.binti.dilink.BintiService
import timber.log.Timber

/**
 * BootReceiver - Starts Binti service on device boot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed received")
            
            // Check if auto-start is enabled
            val autoStart = runBlocking {
                PreferenceManager.isWakeWordEnabled()
            }
            
            if (autoStart) {
                // Start the Binti service
                val serviceIntent = Intent(context, BintiService::class.java).apply {
                    action = BintiService.ACTION_START_LISTENING
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
                Timber.i("Binti service started on boot")
            }
        }
    }
}
