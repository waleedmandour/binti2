package com.binti.dilink.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.binti.dilink.BintiService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PreferenceManager.isWakeWordEnabled()) {
                context.startService(Intent(context, BintiService::class.java))
            }
        }
    }
}
