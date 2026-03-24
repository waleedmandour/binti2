package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * DiLink Accessibility Service
 * 
 * Provides accessibility access to the BYD DiLink infotainment system.
 * This service enables voice-controlled interaction with vehicle functions
 * through UI automation.
 * 
 * Capabilities:
 * - Read and interact with DiLink UI elements
 * - Perform clicks, scrolls, and text input
 * - Monitor app changes and events
 * - Execute gesture-based commands
 * 
 * Security Note: This service requires explicit user permission and
 * only operates when enabled in Android Accessibility Settings.
 * 
 * @author Dr. Waleed Mandour
 */
class DiLinkAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DiLinkA11yService"
        
        @Volatile
        private var instance: DiLinkAccessibilityService? = null
        
        /**
         * Get the running instance of the accessibility service
         */
        fun getInstance(): DiLinkAccessibilityService? = instance
        
        /**
         * Check if the service is enabled
         */
        fun isServiceEnabled(): Boolean = instance != null
    }

    // DiLink packages we want to monitor
    private val monitoredPackages = setOf(
        "com.byd.auto.ac",           // AC control
        "com.byd.auto.navigation",   // Navigation
        "com.byd.auto.media",        // Media player
        "com.byd.auto.settings",     // Settings
        "com.byd.auto.phone",        // Phone
        "com.byd.auto.launcher"      // Home screen
    )
    
    // Current active package
    private var currentPackage: String? = null
    
    // Command executor reference
    private var commandExecutor: DiLinkCommandExecutor? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "🚗 DiLink Accessibility Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        Log.i(TAG, "✅ DiLink Accessibility Service connected")
        
        // Configure service info
        serviceInfo = AccessibilityServiceInfo().apply {
            // Event types to listen for
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            
            // Feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // Flags - use appropriate flags for accessibility service
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            // Package names to monitor (null = all packages)
            // We monitor all to catch DiLink apps
            packageNames = null
            
            // Notification timeout
            notificationTimeout = 100
        }
        
        // Notify service is ready
        broadcastServiceState(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Filter events from DiLink packages
        val packageName = event.packageName?.toString() ?: return
        
        if (packageName !in monitoredPackages && !packageName.startsWith("com.byd")) {
            return
        }
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Window changed - update current package
                if (packageName != currentPackage) {
                    currentPackage = packageName
                    Log.d(TAG, "📱 Active app: $packageName")
                }
            }
            
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content changed in current window
                // Could be useful for detecting UI state changes
            }
            
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.v(TAG, "👆 Click: ${event.contentDescription}")
            }
            
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                Log.v(TAG, "🎯 Focus: ${event.contentDescription}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        broadcastServiceState(false)
        Log.i(TAG, "DiLink Accessibility Service destroyed")
    }

    /**
     * Set the command executor for this service
     */
    fun setCommandExecutor(executor: DiLinkCommandExecutor) {
        this.commandExecutor = executor
        executor.setAccessibilityService(this)
    }

    /**
     * Get the root node of the current active window
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * Find a node by resource ID
     */
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    /**
     * Find a node by text content
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    /**
     * Find a node by content description
     */
    fun findNodeByContentDescription(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        
        return findNodeRecursive(root) { node ->
            node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
        }
    }

    /**
     * Recursively find a node matching a condition
     */
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        condition: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (condition(node)) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, condition)
            if (result != null) return result
        }
        
        return null
    }

    /**
     * Perform a click on a node
     */
    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Try clicking parent
            performClick(node.parent)
        }
    }

    /**
     * Perform a click by node ID
     */
    fun clickById(id: String): Boolean {
        return performClick(findNodeById(id))
    }

    /**
     * Perform a click by text
     */
    fun clickByText(text: String): Boolean {
        return performClick(findNodeByText(text))
    }

    /**
     * Perform a long click on a node
     */
    fun performLongClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isLongClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } else {
            performLongClick(node.parent)
        }
    }

    /**
     * Scroll forward in a scrollable node
     */
    fun scrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isScrollable) {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            scrollForward(node.parent)
        }
    }

    /**
     * Scroll backward in a scrollable node
     */
    fun scrollBackward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isScrollable) {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } else {
            scrollBackward(node.parent)
        }
    }

    /**
     * Set text in an editable node
     */
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        
        if (!node.isEditable) {
            return setText(node.parent, text)
        }
        
        val arguments = android.os.Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Set text by node ID
     */
    fun setTextById(id: String, text: String): Boolean {
        return setText(findNodeById(id), text)
    }

    /**
     * Focus on a node
     */
    fun focusNode(node: AccessibilityNodeInfo?): Boolean {
        return node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
    }

    /**
     * Clear focus from a node
     */
    fun clearFocus(node: AccessibilityNodeInfo?): Boolean {
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) ?: false
    }

    /**
     * Perform a gesture at specified coordinates
     */
    fun performGesture(x: Float, y: Float): Boolean {
        // Requires API 24+ for dispatchGesture
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            val clickPath = android.graphics.Path().apply {
                moveTo(x, y)
            }
            
            gestureBuilder.addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    clickPath, 0, 100
                )
            )
            
            return dispatchGesture(gestureBuilder.build(), null, null)
        }
        
        return false
    }

    /**
     * Perform a swipe gesture
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            val swipePath = android.graphics.Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            gestureBuilder.addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    swipePath, 0, duration
                )
            )
            
            return dispatchGesture(gestureBuilder.build(), null, null)
        }
        
        return false
    }

    /**
     * Get all clickable nodes in current window
     */
    fun getClickableNodes(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val clickables = mutableListOf<AccessibilityNodeInfo>()
        
        collectClickableNodes(root, clickables)
        return clickables
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && node.isVisibleToUser) {
            list.add(node)
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectClickableNodes(it, list) }
        }
    }

    /**
     * Get current package name
     */
    fun getCurrentPackage(): String? = currentPackage

    /**
     * Check if a specific DiLink app is active
     */
    fun isDiLinkAppActive(packageName: String): Boolean {
        return currentPackage == packageName
    }

    /**
     * Broadcast service state change
     */
    private fun broadcastServiceState(enabled: Boolean) {
        val intent = Intent("com.binti.dilink.ACCESSIBILITY_STATE").apply {
            putExtra("enabled", enabled)
        }
        sendBroadcast(intent)
    }
}
