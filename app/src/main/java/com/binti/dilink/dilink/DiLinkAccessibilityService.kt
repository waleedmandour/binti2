package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class DiLinkAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
