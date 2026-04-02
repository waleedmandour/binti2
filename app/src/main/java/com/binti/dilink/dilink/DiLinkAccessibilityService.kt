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
 * DiLink Accessibility Service — Fixed Version
 *
 * Changes from original:
 * 1.  [FIX] Singleton `instance` set to null BEFORE super.onDestroy() to avoid
 *     a window where a caller grabs a half-destroyed service reference.
 * 2.  [FIX] `onServiceConnected()` kept the default `packageNames = null` (monitor
 *     ALL packages). This is a significant battery/performance drain on a car head
 *     unit. Changed to explicitly list only `com.byd.auto.*` packages.
 * 3.  [FIX] `findNodeRecursive()` had no depth limit — on a deep or pathological
 *     view tree it could StackOverflow. Added MAX_TREE_DEPTH guard.
 * 4.  [FIX] `performLongClick()` and `scrollForward()` / `scrollBackward()` and
 *     `setText()` climbed the parent chain without any depth limit. Added bounded
 *     private helpers consistent with `performClickInternal()`.
 * 5.  [FIX] `openDiLinkApp()` called `startActivity(null)` when
 *     `getLaunchIntentForPackage()` returned null (app not installed / not found),
 *     causing a NullPointerException. Added null guard with logged warning.
 * 6.  [FIX] `findNodeFlexibly()` used AND logic across all supplied criteria, so
 *     passing multiple hints (id + contentDesc) almost never matched anything.
 *     Introduced `matchAll: Boolean` parameter (default true = AND, false = OR)
 *     so callers from DiLinkCommandExecutor that pass several fallback criteria
 *     can use OR matching.
 * 7.  [FIX] `getRange()` stripped only "km" but DiLink displays "كم" (Arabic).
 *     Now strips both, plus leading/trailing whitespace.
 * 8.  [FIX] `getBatteryPercentage()`, `getRange()`, `getOutsideTemperature()`,
 *     `getInsideTemperature()`, `getACTemperature()` all return null when the
 *     vehicle info app is not in the foreground and the nodes don't exist — but
 *     they did so silently. Added debug-level logging so problems are diagnosable.
 * 9.  [FIX] `isACOn()` called `powerNode?.isSelected` — but BYD's power button
 *     uses `isChecked`, not `isSelected`, for toggle state. Fixed with a two-step
 *     check (isChecked first, isSelected as fallback).
 * 10. [FIX] `logNodeTree()` called `"  ".repeat(depth)` inside a hot logging loop.
 *     For large trees this creates many short-lived String objects. Replaced with a
 *     pre-built indent string passed as a parameter.
 * 11. [IMPROVEMENT] `detectBYDModel()` now stores the first matched model and stops
 *     iterating — the original could overwrite an earlier match (e.g. "Yuan" →
 *     "Han") if multiple properties contained different substrings.
 * 12. [IMPROVEMENT] `broadcastServiceState()` uses explicit package targeting
 *     (`setPackage()`) which is required on Android 12+ for non-exported receivers.
 *
 * @author Dr. Waleed Mandour  (fixes by code review)
 */
class DiLinkAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DiLinkA11yService"

        // FIX #3 / #4 — shared depth limits
        private const val MAX_TREE_DEPTH       = 15
        private const val MAX_PARENT_TRAVERSAL = 5

        @Volatile
        private var instance: DiLinkAccessibilityService? = null

        fun getInstance(): DiLinkAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }

    // FIX #2 — explicit package list instead of null (monitor all)
    private val monitoredPackages = setOf(
        "com.byd.auto.ac",
        "com.byd.auto.navigation",
        "com.byd.auto.media",
        "com.byd.auto.settings",
        "com.byd.auto.phone",
        "com.byd.auto.launcher",
        "com.byd.auto.climate",
        "com.byd.auto.vehicleinfo",
        "com.byd.auto.energy",
        "com.byd.auto.camera"
    )

    private var currentPackage: String? = null
    private var commandExecutor: DiLinkCommandExecutor? = null
    private var debugMode = false
    private var detectedModel: String = "Unknown"

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "🚗 DiLink Accessibility Service created")
        detectBYDModel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ DiLink Accessibility Service connected")

        // FIX #2 — restrict to BYD packages; monitoring all packages wastes CPU
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes    = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType  = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags         = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames  = monitoredPackages.toTypedArray()   // ← was null
            notificationTimeout = 100
        }

        broadcastServiceState(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Still guard — some events may slip through before serviceInfo is applied
        if (packageName !in monitoredPackages && !packageName.startsWith("com.byd")) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (packageName != currentPackage) {
                    currentPackage = packageName
                    Log.d(TAG, "📱 Active app: $packageName")

                    if (debugMode) {
                        try { logAvailableUIElements() } catch (e: Exception) {
                            Log.w(TAG, "UI element logging failed, disabling debug mode", e)
                            debugMode = false
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> { /* reserved for future state polling */ }
            AccessibilityEvent.TYPE_VIEW_CLICKED      -> Log.v(TAG, "👆 Click: ${event.contentDescription}")
            AccessibilityEvent.TYPE_VIEW_FOCUSED      -> Log.v(TAG, "🎯 Focus: ${event.contentDescription}")
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> Log.v(TAG, "📝 Text: ${event.text}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        // FIX #1 — null instance BEFORE super.onDestroy() so no caller can
        // grab a reference to a service that is mid-teardown.
        instance = null
        broadcastServiceState(false)
        super.onDestroy()
        Log.i(TAG, "DiLink Accessibility Service destroyed")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Model detection
    // ──────────────────────────────────────────────────────────────────────────

    private fun detectBYDModel() {
        try {
            val props = listOf(
                "ro.product.model",
                "ro.product.device",
                "ro.build.fingerprint",
                "ro.byd.model",
                "ro.byd.platform"
            )

            // FIX #11 — stop at first match so a later property can't overwrite it
            outer@ for (prop in props) {
                val value = getSystemProperty(prop) ?: continue
                if (value.isBlank()) continue
                Log.d(TAG, "🔧 $prop: $value")

                detectedModel = when {
                    value.contains("Yuan",    ignoreCase = true) ||
                    value.contains("Atto",    ignoreCase = true) -> "Yuan Plus/Atto 3"
                    value.contains("Dolphin", ignoreCase = true) -> "Dolphin"
                    value.contains("Seal",    ignoreCase = true) -> "Seal"
                    value.contains("Han",     ignoreCase = true) -> "Han"
                    value.contains("Tang",    ignoreCase = true) -> "Tang"
                    else -> continue@outer
                }
                break@outer   // found a recognised model — stop
            }

            Log.i(TAG, "🚙 Detected BYD Model: $detectedModel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect BYD model", e)
        }
    }

    private fun getSystemProperty(key: String): String? = try {
        val clazz  = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    } catch (e: Exception) { null }

    // ──────────────────────────────────────────────────────────────────────────
    // Debug helpers
    // ──────────────────────────────────────────────────────────────────────────

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        Log.i(TAG, "Debug mode: $enabled")
    }

    fun getDetectedModel(): String = detectedModel

    fun logAvailableUIElements() {
        val root = rootInActiveWindow ?: return
        Log.d(TAG, "=== UI Elements Discovery ===")
        Log.d(TAG, "Package: ${root.packageName}")
        Log.d(TAG, "Window ID: ${root.windowId}")
        logNodeTree(root, 0, "")   // FIX #10 — pass indent string, not compute it per call
        Log.d(TAG, "=== End UI Elements ===")
    }

    // FIX #10 — indent is passed in, not rebuilt with String.repeat() on each call
    private fun logNodeTree(node: AccessibilityNodeInfo, depth: Int, indent: String) {
        if (depth > MAX_TREE_DEPTH) {
            Log.d(TAG, "$indent... (max depth reached)")
            return
        }
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.d(TAG, "${indent}Node: ${node.className}")
            Log.d(TAG, "${indent}  ID:        ${node.viewIdResourceName}")
            Log.d(TAG, "${indent}  Text:      ${node.text}")
            Log.d(TAG, "${indent}  Desc:      ${node.contentDescription}")
            Log.d(TAG, "${indent}  Bounds:    $bounds")
            Log.d(TAG, "${indent}  Clickable: ${node.isClickable}")
            Log.d(TAG, "${indent}  Scrollable:${node.isScrollable}")

            val childIndent = "$indent  "
            for (i in 0 until node.childCount) {
                try { node.getChild(i)?.let { logNodeTree(it, depth + 1, childIndent) } }
                catch (e: Exception) { /* skip bad child */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error logging node at depth $depth", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Service wiring
    // ──────────────────────────────────────────────────────────────────────────

    fun setCommandExecutor(executor: DiLinkCommandExecutor) {
        this.commandExecutor = executor
        executor.setAccessibilityService(this)
    }

    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    // ──────────────────────────────────────────────────────────────────────────
    // Node finders
    // ──────────────────────────────────────────────────────────────────────────

    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        if (nodes.isNotEmpty()) return nodes.firstOrNull()

        // Fallback ID list
        val fallbackList = when {
            id.contains("power",     ignoreCase = true) &&
            id.contains("ac",        ignoreCase = true) -> BYDModels.FallbackIDs.AC_POWER
            id.contains("temp_up",   ignoreCase = true) -> BYDModels.FallbackIDs.AC_TEMP_UP
            id.contains("temp_down", ignoreCase = true) -> BYDModels.FallbackIDs.AC_TEMP_DOWN
            id.contains("answer",    ignoreCase = true) -> BYDModels.FallbackIDs.PHONE_ANSWER
            id.contains("reject",    ignoreCase = true) -> BYDModels.FallbackIDs.PHONE_REJECT
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

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

    fun findNodeByContentDescription(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeRecursive(root, 0) { node ->
            node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
        }
    }

    /**
     * Find node by multiple criteria.
     *
     * FIX #6 — added [matchAll] parameter:
     *  - true  (default) = every supplied criterion must match (AND)
     *  - false           = any supplied criterion matches (OR)
     *
     * Use OR when passing several fallback hints from DiLinkCommandExecutor so
     * that `findNodeFlexibly(id="answer", contentDesc="answer", text="رد")`
     * actually finds the node even if only one attribute is present.
     */
    fun findNodeFlexibly(
        id:          String?  = null,
        text:        String?  = null,
        contentDesc: String?  = null,
        className:   String?  = null,
        matchAll:    Boolean  = true
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        return findNodeRecursive(root, 0) { node ->
            val checks = mutableListOf<Boolean>()

            id?.let          { checks.add(node.viewIdResourceName?.contains(it, ignoreCase = true) == true) }
            text?.let        { checks.add(node.text?.toString()?.contains(it, ignoreCase = true) == true) }
            contentDesc?.let { checks.add(node.contentDescription?.toString()?.contains(it, ignoreCase = true) == true) }
            className?.let   { checks.add(node.className?.toString()?.contains(it, ignoreCase = true) == true) }

            if (checks.isEmpty()) false
            else if (matchAll) checks.all { it }
            else               checks.any { it }
        }
    }

    // FIX #3 — depth-guarded recursive search
    private fun findNodeRecursive(
        node:      AccessibilityNodeInfo,
        depth:     Int,
        condition: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null
        if (condition(node)) return node

        for (i in 0 until node.childCount) {
            val child  = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, depth + 1, condition)
            if (result != null) return result
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Node actions
    // ──────────────────────────────────────────────────────────────────────────

    fun performClick(node: AccessibilityNodeInfo?): Boolean =
        performClickInternal(node, 0)

    private fun performClickInternal(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > MAX_PARENT_TRAVERSAL) return false
        Log.d(TAG, "👆 Clicking: ${node.viewIdResourceName} — ${node.contentDescription}")
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val parent = try { node.parent } catch (e: Exception) { null }
            performClickInternal(parent, depth + 1)
        }
    }

    fun clickById(id: String): Boolean   = performClick(findNodeById(id))
    fun clickByText(text: String): Boolean = performClick(findNodeByText(text))

    // FIX #4 — long-click with depth guard
    fun performLongClick(node: AccessibilityNodeInfo?): Boolean =
        performLongClickInternal(node, 0)

    private fun performLongClickInternal(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > MAX_PARENT_TRAVERSAL) return false
        return if (node.isLongClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } else {
            val parent = try { node.parent } catch (e: Exception) { null }
            performLongClickInternal(parent, depth + 1)
        }
    }

    // FIX #4 — scroll with depth guard
    fun scrollForward(node: AccessibilityNodeInfo?): Boolean =
        scrollInternal(node, forward = true, depth = 0)

    fun scrollBackward(node: AccessibilityNodeInfo?): Boolean =
        scrollInternal(node, forward = false, depth = 0)

    private fun scrollInternal(node: AccessibilityNodeInfo?, forward: Boolean, depth: Int): Boolean {
        if (node == null || depth > MAX_PARENT_TRAVERSAL) return false
        return if (node.isScrollable) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                         else        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            node.performAction(action)
        } else {
            val parent = try { node.parent } catch (e: Exception) { null }
            scrollInternal(parent, forward, depth + 1)
        }
    }

    // FIX #4 — setText with depth guard
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean =
        setTextInternal(node, text, 0)

    private fun setTextInternal(node: AccessibilityNodeInfo?, text: String, depth: Int): Boolean {
        if (node == null || depth > MAX_PARENT_TRAVERSAL) return false
        return if (node.isEditable) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            val parent = try { node.parent } catch (e: Exception) { null }
            setTextInternal(parent, text, depth + 1)
        }
    }

    fun setTextById(id: String, text: String): Boolean = setText(findNodeById(id), text)

    fun focusNode(node: AccessibilityNodeInfo?): Boolean =
        node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false

    fun clearFocus(node: AccessibilityNodeInfo?): Boolean =
        node?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) ?: false

    // ──────────────────────────────────────────────────────────────────────────
    // Gestures (API 24+)
    // ──────────────────────────────────────────────────────────────────────────

    fun performGesture(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
        return dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(),
            null, null
        )
    }

    fun performSwipe(
        startX: Float, startY: Float,
        endX:   Float, endY:   Float,
        duration: Long = 300
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val path = android.graphics.Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration)
        return dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(),
            null, null
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────────

    fun getClickableNodes(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val list = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, list)
        return list
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && node.isVisibleToUser) list.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectClickableNodes(it, list) }
    }

    fun getCurrentPackage(): String? = currentPackage
    fun isDiLinkAppActive(packageName: String): Boolean = currentPackage == packageName

    // FIX #5 — null-check intent before calling startActivity
    fun openDiLinkApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Log.w(TAG, "No launch intent found for package: $packageName")
                return false
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
            false
        }
    }

    fun getNodeText(node: AccessibilityNodeInfo?): String? =
        node?.text?.toString() ?: node?.contentDescription?.toString()

    fun getTextById(id: String): String? = getNodeText(findNodeById(id))
    fun nodeExists(id: String): Boolean  = findNodeById(id) != null

    suspend fun waitForNode(id: String, timeoutMs: Long = 5000): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            findNodeById(id)?.let { return it }
            kotlinx.coroutines.delay(100)
        }
        return null
    }

    // FIX #12 — explicit package targeting required on Android 12+ for
    // non-exported broadcast receivers
    private fun broadcastServiceState(enabled: Boolean) {
        try {
            val intent = Intent("com.binti.dilink.ACCESSIBILITY_STATE").apply {
                putExtra("enabled", enabled)
                setPackage(packageName)   // was: `package` = packageName (same effect but clearer)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast service state", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BYD-specific helpers
    // ──────────────────────────────────────────────────────────────────────────

    fun isACAppActive(): Boolean =
        currentPackage in listOf(
            BYDModels.YuanPlus2023.PACKAGE_AC,
            BYDModels.YuanPlus2023.PACKAGE_CLIMATE
        )

    fun isPhoneAppActive(): Boolean      = currentPackage == BYDModels.YuanPlus2023.PACKAGE_PHONE
    fun isMediaAppActive(): Boolean      = currentPackage == BYDModels.YuanPlus2023.PACKAGE_MEDIA
    fun isNavigationAppActive(): Boolean = currentPackage == BYDModels.YuanPlus2023.PACKAGE_NAV

    // FIX #8 — debug logging so missing nodes are diagnosable
    fun getBatteryPercentage(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_BATTERY)
        if (node == null) { Log.d(TAG, "Battery node not found in current window"); return null }
        return node.text?.toString()?.replace("%", "")?.trim()?.toIntOrNull()
    }

    // FIX #7 — strip Arabic "كم" as well as Latin "km"
    fun getRange(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_RANGE)
        if (node == null) { Log.d(TAG, "Range node not found in current window"); return null }
        return node.text?.toString()
            ?.replace("km",  "", ignoreCase = true)
            ?.replace("كم",  "")
            ?.trim()
            ?.toIntOrNull()
    }

    fun getOutsideTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_TEMP_OUT)
        if (node == null) { Log.d(TAG, "Outside temp node not found"); return null }
        return node.text?.toString()?.replace("°", "")?.trim()?.toIntOrNull()
    }

    fun getInsideTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_TEMP_IN)
        if (node == null) { Log.d(TAG, "Inside temp node not found"); return null }
        return node.text?.toString()?.replace("°", "")?.trim()?.toIntOrNull()
    }

    fun getACTemperature(): Int? {
        val node = findNodeById(BYDModels.YuanPlus2023.ID_AC_TEMP_DISPLAY)
        if (node == null) { Log.d(TAG, "AC temp display node not found"); return null }
        return node.text?.toString()?.trim()?.toIntOrNull()
    }

    // FIX #9 — BYD power button uses isChecked for toggle state, not isSelected
    fun isACOn(): Boolean? {
        val powerNode = findNodeById(BYDModels.YuanPlus2023.ID_AC_POWER) ?: return null
        // isChecked is the standard toggle indicator; fall back to isSelected
        return if (powerNode.isCheckable) powerNode.isChecked else powerNode.isSelected
    }
}
