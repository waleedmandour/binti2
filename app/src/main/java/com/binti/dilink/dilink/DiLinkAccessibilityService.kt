package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.binti.dilink.BintiService
import timber.log.Timber

/**
 * DiLinkAccessibilityService - Accessibility service for DiLink UI control
 * 
 * Provides:
 * - UI automation for DiLink controls
 * - Screen state monitoring
 * - Gesture simulation
 * - Node traversal and interaction
 * 
 * Required for non-root control of DiLink system
 */
class DiLinkAccessibilityService : AccessibilityService() {

    companion object {
        private const val GESTURE_DURATION_MS = 100L
        
        @Volatile
        private var instance: DiLinkAccessibilityService? = null
        
        fun getInstance(): DiLinkAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }

    // Screen state tracking
    private var currentPackage: String? = null
    private var currentScreen: ScreenType = ScreenType.UNKNOWN
    
    // Node cache
    private var lastRootNode: AccessibilityNodeInfo? = null

    enum class ScreenType {
        HOME,
        NAVIGATION,
        CLIMATE,
        MEDIA,
        SETTINGS,
        UNKNOWN
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DiLinkCommandExecutor.setAccessibilityService(this)
        
        Timber.i("DiLink Accessibility Service connected")
        
        // Configure service
        serviceInfo = serviceInfo.apply {
            // Set event types we're interested in
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityService.FEEDBACK_GENERIC
            flags = flags or 
                AccessibilityService.FLAG_REPORT_VIEW_IDS or
                AccessibilityService.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Track current package
                currentPackage = event.packageName?.toString()
                
                // Detect screen type
                currentScreen = detectScreenType(event)
                
                Timber.v("Screen changed: $currentPackage -> $currentScreen")
            }
            
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content updated, cache root node
                lastRootNode = rootInActiveWindow
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        DiLinkCommandExecutor.setAccessibilityService(null)
        Timber.i("DiLink Accessibility Service destroyed")
    }

    /**
     * Detect current screen type from accessibility event
     */
    private fun detectScreenType(event: AccessibilityEvent): ScreenType {
        val packageName = event.packageName?.toString() ?: return ScreenType.UNKNOWN
        
        return when {
            packageName.contains("navigation") -> ScreenType.NAVIGATION
            packageName.contains("climate") -> ScreenType.CLIMATE
            packageName.contains("media") -> ScreenType.MEDIA
            packageName.contains("settings") -> ScreenType.SETTINGS
            packageName.contains("launcher") || packageName.contains("home") -> ScreenType.HOME
            else -> ScreenType.UNKNOWN
        }
    }

    /**
     * Get current screen type
     */
    fun getCurrentScreen(): ScreenType = currentScreen

    /**
     * Get current root node
     */
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    /**
     * Find node by text (Arabic-aware)
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    /**
     * Find node by view ID
     */
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    /**
     * Find node by content description
     */
    fun findNodeByContentDescription(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        
        return findNodeRecursive(root) { node ->
            node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
        }
    }

    /**
     * Recursive node finder
     */
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) {
                return found
            }
        }
        
        return null
    }

    /**
     * Click on a node
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // Try direct click first
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        // Try click on parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        
        // Fall back to gesture
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return performClick(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * Perform click gesture at coordinates
     */
    fun performClick(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Timber.w("Gesture dispatch requires Android N+")
            return false
        }
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Perform swipe gesture
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Timber.w("Gesture dispatch requires Android N+")
            return false
        }
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Scroll in a direction
     */
    fun scroll(direction: ScrollDirection): Boolean {
        val root = rootInActiveWindow ?: return false
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val scrollDistance = bounds.height() * 0.3f
        
        return when (direction) {
            ScrollDirection.UP -> performSwipe(
                centerX, centerY + scrollDistance,
                centerX, centerY - scrollDistance
            )
            ScrollDirection.DOWN -> performSwipe(
                centerX, centerY - scrollDistance,
                centerX, centerY + scrollDistance
            )
            ScrollDirection.LEFT -> performSwipe(
                centerX + scrollDistance, centerY,
                centerX - scrollDistance, centerY
            )
            ScrollDirection.RIGHT -> performSwipe(
                centerX - scrollDistance, centerY,
                centerX + scrollDistance, centerY
            )
        }
    }

    enum class ScrollDirection {
        UP, DOWN, LEFT, RIGHT
    }

    /**
     * Navigate to home screen
     */
    fun goToHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Navigate back
     */
    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Open recent apps
     */
    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open quick settings
     */
    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * Open notifications
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Get text from a node
     */
    fun getNodeText(node: AccessibilityNodeInfo): String? {
        return node.text?.toString() 
            ?: node.contentDescription?.toString()
    }

    /**
     * Set text in an editable node
     */
    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        // Focus the node
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        // Clear existing text
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
        
        return true
    }

    /**
     * Print node hierarchy for debugging
     */
    fun printNodeHierarchy(node: AccessibilityNodeInfo? = null, depth: Int = 0) {
        val rootNode = node ?: rootInActiveWindow ?: return
        val indent = "  ".repeat(depth)
        
        val text = rootNode.text?.toString()?.take(50) ?: ""
        val contentDesc = rootNode.contentDescription?.toString()?.take(50) ?: ""
        val viewId = rootNode.viewIdResourceName ?: ""
        
        Timber.v("$indent Node: ${rootNode.className} [$text] [$contentDesc] id=$viewId")
        
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            printNodeHierarchy(child, depth + 1)
        }
    }
}
