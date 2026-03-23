package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

class DiLinkAccessibilityService : AccessibilityService() {
    
    companion object {
        @Volatile
        private var instance: DiLinkAccessibilityService? = null
        
        fun getInstance(): DiLinkAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("DiLink Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Handle DiLink screen events
    }
    
    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("DiLink Accessibility Service destroyed")
    }
}
