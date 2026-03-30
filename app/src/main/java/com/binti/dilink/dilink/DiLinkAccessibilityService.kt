package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * DiLink Accessibility Service - Enhanced Version
 *
 * Provides accessibility access to the BYD DiLink infotainment system.
 * This service enables voice-controlled interaction with vehicle functions
 * through UI automation.
 *
 * Supported Models:
 * - BYD Yuan Plus 2023
 * - BYD Atto 3
 * - BYD Dolphin
 * - BYD Seal
 * - BYD Han EV
 * - BYD Tang EV
 *
 * Capabilities:
 * - Read and interact with DiLink UI elements
 * - Perform clicks, scrolls, and text input
 * - Monitor app changes and events
 * - Execute gesture-based commands
 * - BYD-specific UI element detection
 * - Debug logging for element discovery
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
        "com.byd.auto.launcher",     // Home screen
        "com.byd.auto.climate",      // Climate control (some models)
        "com.byd.auto.vehicleinfo",  // Vehicle info
        "com.byd.auto.energy",       // Energy management
        "com.byd.auto.camera"        // Camera/360 view
    )

    // Current active package
    private var currentPackage: String? = null

    // Command executor reference
    private var commandExecutor: DiLinkCommandExecutor? = null

    // Debug mode for logging UI elements
    private var debugMode = true

    // Detected BYD model
    private var detectedModel: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "🚗 DiLink Accessibility Service created")
        detectBYDModel()
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

            // Flags
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

                    // Debug: Log available UI elements in new window
                    if (debugMode) {
                        logAvailableUIElements()
                    }
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

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                Log.v(TAG, "📝 Text changed: ${event.text}")
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
     * Detect BYD model from system properties
     */
    private fun detectBYDModel() {
        try {
            val props = listOf(
                "ro.product.model" to "Model",
                "ro.product.device" to "Device",
                "ro.build.fingerprint" to "Fingerprint",
                "ro.byd.model" to "BYD Model",
                "ro.byd.platform" to "BYD Platform"
            )

            val sb = StringBuilder("Detected BYD Info:\n")
            for ((prop, label) in props) {
                val value = getSystemProperty(prop)
                if (!value.isNullOrEmpty()) {
                    sb.append("$label: $value\n")
                    Log.d(TAG, "🔧 $label: $value")

                    // Detect specific model
                    when {
                        value.contains("Yuan", ignoreCase = true) ||
                        value.contains("Atto", ignoreCase = true) -> detectedModel = "Yuan Plus/Atto 3"
                        value.contains("Dolphin", ignoreCase = true) -> detectedModel = "Dolphin"
                        value.contains("Seal", ignoreCase = true) -> detectedModel = "Seal"
                        value.contains("Han", ignoreCase = true) -> detectedModel = "Han"
                        value.contains("Tang", ignoreCase = true) -> detectedModel = "Tang"
                    }
                }
            }
            Log.i(TAG, "🚙 Detected BYD Model: $detectedModel")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect BYD model", e)
        }
    }

    /**
     * Get system property using reflection
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Enable/disable debug mode
     */
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        Log.i(TAG, "Debug mode: $enabled")
    }

    /**
     * Get detected BYD model
     */
    fun getDetectedModel(): String = detectedModel

    /**
     * Log all available UI elements in current window (for debugging)
     */
    fun logAvailableUIElements() {
        val root = rootInActiveWindow ?: return

        Log.d(TAG, "=== UI Elements Discovery ===")
        Log.d(TAG, "Package: ${root.packageName}")
        Log.d(TAG, "Window ID: ${root.windowId}")

        logNodeTree(root, 0)
        Log.d(TAG, "=== End UI Elements ===")
    }

    /**
     * Recursively log node tree
     */
    private fun logNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        Log.d(TAG, "${indent}Node: ${node.className}")
        Log.d(TAG, "${indent}  ID: ${node.viewIdResourceName}")
        Log.d(TAG, "${indent}  Text: ${node.text}")
        Log.d(TAG, "${indent}  Desc: ${node.contentDescription}")
        Log.d(TAG, "${indent}  Bounds: $bounds")
        Log.d(TAG, "${indent}  Clickable: ${node.isClickable}")
        Log.d(TAG, "${indent}  Scrollable: ${node.isScrollable}")
        Log.d(TAG, "${indent}  Editable: ${node.isEditable}")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { logNodeTree(it, depth + 1) }
        }
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
     * Find a node by resource ID with fallback support
     */
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // Try exact ID first
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        if (nodes.isNotEmpty()) {
            return nodes.firstOrNull()
        }

        // Try fallback IDs
        val fallbackList = when {
            id.contains("power", ignoreCase = true) && id.contains("ac", ignoreCase = true) -> BYDModels.FallbackIDs.AC_POWER
            id.contains("temp_up", ignoreCase = true) -> BYDModels.FallbackIDs.AC_TEMP_UP
            id.contains("temp_down", ignoreCase = true) -> BYDModels.FallbackIDs.AC_TEMP_DOWN
            id.contains("answer", ignoreCase = true) -> BYDModels.FallbackIDs.PHONE_ANSWER
            id.contains("reject", ignoreCase = true) -> BYDModels.FallbackIDs.PHONE_REJECT
            else -> emptyList()
        }

        for (fallbackId in fallbackList) {
            val fallbackNodes = root.findAccessibilityNodeInfosByViewId(fallbackId)
            if (fallbackNodes.isNotEmpty()) {
                Log.d(TAG, "✅ Found node using fallback ID: $fallbackId")
                return fallbackNodes.firstOrNull()
            }
        }

        return null
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
     * Find node by multiple criteria (flexible search)
     */
    fun findNodeFlexibly(
        id: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        className: String? = null
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        return findNodeRecursive(root) { node ->
            var matches = true

            if (id != null) {
                matches = matches && node.viewIdResourceName?.contains(id, ignoreCase = true) == true
            }
            if (text != null) {
                matches = matches && node.text?.toString()?.contains(text, ignoreCase = true) == true
            }
            if (contentDesc != null) {
                matches = matches && node.contentDescription?.toString()?.contains(contentDesc, ignoreCase = true) == true
            }
            if (className != null) {
                matches = matches && node.className?.toString()?.contains(className, ignoreCase = true) == true
            }

            matches
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

        Log.d(TAG, "👆 Clicking: ${node.viewIdResourceName} - ${node.contentDescription}")

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

        val arguments = Bundle()
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
     * Open a DiLink app by package name
     */
    fun openDiLinkApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
            false
        }
    }

    /**
     * Get text content from a node
     */
    fun getNodeText(node: AccessibilityNodeInfo?): String? {
        return node?.text?.toString() ?: node?.contentDescription?.toString()
    }

    /**
     * Get text from node by ID
     */
    fun getTextById(id: String): String? {
        return getNodeText(findNodeById(id))
    }

    /**
     * Check if a node exists
     */
    fun nodeExists(id: String): Boolean {
        return findNodeById(id) != null
    }

    /**
     * Wait for a node to appear
     */
    suspend fun waitForNode(id: String, timeoutMs: Long = 5000): AccessibilityNodeInfo? {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val node = findNodeById(id)
            if (node != null) return node
            Thread.sleep(100)
        }

        return null
    }

    /**
     * Broadcast service state change
     */
    private fun broadcastServiceState(enabled: Boolean) {
        try {
            val intent = Intent("com.binti.dilink.ACCESSIBILITY_STATE").apply {
                putExtra("enabled", enabled)
                `package` = packageName
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast service state", e)
        }
    }

    // ========== BYD-Specific Helper Methods ==========

    /**
     * Check if AC app is active
     */
    fun isACAppActive(): Boolean {
        return currentPackage in listOf(
            BYDModels.YuanPlus2023.PACKAGE_AC,
            BYDModels.YuanPlus2023.PACKAGE_CLIMATE
        )
    }

    /**
     * Check if Phone app is active
     */
    fun isPhoneAppActive(): Boolean {
        return currentPackage == BYDModels.YuanPlus2023.PACKAGE_PHONE
    }

    /**
     * Check if Media app is active
     */
    fun isMediaAppActive(): Boolean {
        return currentPackage == BYDModels.YuanPlus2023.PACKAGE_MEDIA
    }

    /**
     * Check if Navigation app is active
     */
    fun isNavigationAppActive(): Boolean {
        return currentPackage == BYDModels.YuanPlus2023.PACKAGE_NAV
    }

    /**
     * Get current battery percentage
     */
    fun getBatteryPercentage(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_BATTERY)
        return node?.text?.toString()?.replace("%", "")?.toIntOrNull()
    }

    /**
     * Get current range
     */
    fun getRange(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_RANGE)
        return node?.text?.toString()?.replace("km", "")?.trim()?.toIntOrNull()
    }

    /**
     * Get outside temperature
     */
    fun getOutsideTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_TEMP_OUT)
        return node?.text?.toString()?.replace("°", "")?.trim()?.toIntOrNull()
    }

    /**
     * Get inside temperature
     */
    fun getInsideTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_TEMP_IN)
        return node?.text?.toString()?.replace("°", "")?.trim()?.toIntOrNull()
    }

    /**
     * Get current AC temperature
     */
    fun getACTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_AC_TEMP_DISPLAY)
        return node?.text?.toString()?.toIntOrNull()
    }

    /**
     * Check if AC is on
     */
    fun isACOn(): Boolean? {
        // This would need to check the state of the power button
        // Implementation depends on BYD's UI state representation
        val powerNode = findNodeById(BYDModels.YuanPlus2023.ID_AC_POWER)
        return powerNode?.isSelected
    }
}
