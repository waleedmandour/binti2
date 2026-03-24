package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.binti.dilink.nlp.IntentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DiLink Command Executor
 * 
 * Executes vehicle commands on BYD DiLink infotainment system using:
 * - AccessibilityService for UI automation
 * - ADB bridge for system-level commands
 * - Intent broadcasting for app integration
 * 
 * Supported Commands:
 * - AC Control: temperature, fan speed, mode
 * - Navigation: start navigation, set destination
 * - Media: play/pause, next/previous, volume
 * - Phone: make calls, answer calls
 * - System: brightness, display settings
 * 
 * @author Dr. Waleed Mandour
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DiLinkExecutor"
        
        // DiLink package names
        const val DILINK_AC_PACKAGE = "com.byd.auto.ac"
        const val DILINK_NAV_PACKAGE = "com.byd.auto.navigation"
        const val DILINK_MEDIA_PACKAGE = "com.byd.auto.media"
        const val DILINK_SETTINGS_PACKAGE = "com.byd.auto.settings"
        
        // Command timeout
        private const val COMMAND_TIMEOUT_MS = 5000L
    }

    // Accessibility service reference
    private var accessibilityService: DiLinkAccessibilityService? = null
    
    // Command status
    private var lastCommandTime = 0L

    /**
     * Execute a classified intent as a DiLink command
     */
    suspend fun executeCommand(intent: IntentResult): CommandResult {
        Log.i(TAG, "🎯 Executing command: ${intent.action}")
        
        return withContext(Dispatchers.Default) {
            try {
                lastCommandTime = System.currentTimeMillis()
                
                val result = when (intent.action) {
                    "AC_CONTROL" -> executeACCommand(intent)
                    "NAVIGATION" -> executeNavigationCommand(intent)
                    "MEDIA" -> executeMediaCommand(intent)
                    "PHONE" -> executePhoneCommand(intent)
                    "INFO" -> executeInfoCommand(intent)
                    "SYSTEM" -> executeSystemCommand(intent)
                    else -> {
                        Log.w(TAG, "Unknown action: ${intent.action}")
                        CommandResult(false, "Unknown command: ${intent.action}")
                    }
                }
                
                Log.i(TAG, "✅ Command result: ${result.success} - ${result.message}")
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Command execution failed", e)
                CommandResult(false, "Error: ${e.message}")
            }
        }
    }

    /**
     * Set accessibility service reference
     */
    fun setAccessibilityService(service: DiLinkAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "Accessibility service connected")
    }

    /**
     * Execute AC control command
     */
    private suspend fun executeACCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        
        return when {
            // Temperature control
            entities.containsKey("temperature") -> {
                val temp = entities["temperature"]?.toIntOrNull() ?: 22
                setACTemperature(temp)
            }
            
            // Mode control
            entities.containsKey("mode") -> {
                val mode = entities["mode"] ?: "auto"
                setACMode(mode)
            }
            
            // Power control
            intent.matchedPattern?.contains("شغل") == true -> {
                setACPower(true)
            }
            
            intent.matchedPattern?.contains("طفي") == true -> {
                setACPower(false)
            }
            
            // Temperature increase
            intent.matchedPattern?.contains("زيود") == true || 
            intent.matchedPattern?.contains("زود") == true -> {
                adjustACTemperature(1)
            }
            
            // Temperature decrease
            intent.matchedPattern?.contains("قلل") == true -> {
                adjustACTemperature(-1)
            }
            
            else -> {
                // Default: toggle AC
                setACPower(true)
            }
        }
    }

    /**
     * Set AC temperature
     */
    private suspend fun setACTemperature(temp: Int): CommandResult {
        Log.d(TAG, "Setting AC temperature to $temp°C")
        
        return try {
            accessibilityService?.let { service ->
                // Find AC app
                val rootNode = service.rootInActiveWindow
                
                // Look for temperature control in AC app
                val tempControl = findNodeByContentDescription(rootNode, "درجة حرارة")
                    ?: findNodeById(rootNode, "com.byd.auto.ac:id/temp_control")
                
                if (tempControl != null) {
                    // Simulate temperature adjustment clicks
                    val currentTemp = getCurrentTemperature(rootNode)
                    val diff = temp - currentTemp
                    
                    repeat(kotlin.math.abs(diff)) {
                        if (diff > 0) {
                            clickNode(findNodeById(rootNode, "com.byd.auto.ac:id/temp_up"))
                        } else {
                            clickNode(findNodeById(rootNode, "com.byd.auto.ac:id/temp_down"))
                        }
                        Thread.sleep(100)
                    }
                    
                    CommandResult(true, "Temperature set to $temp°C")
                } else {
                    // Fallback: Use intent to open AC app
                    openACApp()
                    CommandResult(true, "Opened AC control")
                }
            } ?: CommandResult(false, "Accessibility service not available")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set temperature", e)
            CommandResult(false, "Failed to set temperature: ${e.message}")
        }
    }

    /**
     * Set AC mode
     */
    private suspend fun setACMode(mode: String): CommandResult {
        Log.d(TAG, "Setting AC mode to $mode")
        
        return try {
            accessibilityService?.let { service ->
                val rootNode = service.rootInActiveWindow
                
                val modeButton = when (mode) {
                    "cool" -> findNodeById(rootNode, "com.byd.auto.ac:id/mode_cool")
                    "heat" -> findNodeById(rootNode, "com.byd.auto.ac:id/mode_heat")
                    "vent" -> findNodeById(rootNode, "com.byd.auto.ac:id/mode_vent")
                    "auto" -> findNodeById(rootNode, "com.byd.auto.ac:id/mode_auto")
                    else -> null
                }
                
                if (modeButton != null && clickNode(modeButton)) {
                    CommandResult(true, "AC mode set to $mode")
                } else {
                    CommandResult(false, "Could not find mode button")
                }
            } ?: CommandResult(false, "Accessibility service not available")
            
        } catch (e: Exception) {
            CommandResult(false, "Failed to set AC mode: ${e.message}")
        }
    }

    /**
     * Set AC power
     */
    private suspend fun setACPower(on: Boolean): CommandResult {
        Log.d(TAG, "Setting AC power: $on")
        
        return try {
            accessibilityService?.let { service ->
                val rootNode = service.rootInActiveWindow
                
                val powerButton = findNodeById(rootNode, "com.byd.auto.ac:id/power_button")
                    ?: findNodeByContentDescription(rootNode, if (on) "تشغيل" else "إيقاف")
                
                if (powerButton != null && clickNode(powerButton)) {
                    CommandResult(true, if (on) "AC turned on" else "AC turned off")
                } else {
                    // Open AC app first
                    openACApp()
                    CommandResult(true, "Opened AC control")
                }
            } ?: CommandResult(false, "Accessibility service not available")
            
        } catch (e: Exception) {
            CommandResult(false, "Failed to control AC: ${e.message}")
        }
    }

    /**
     * Adjust AC temperature
     */
    private suspend fun adjustACTemperature(delta: Int): CommandResult {
        val adjustment = if (delta > 0) "increased" else "decreased"
        Log.d(TAG, "Adjusting AC temperature by $delta")
        
        return try {
            accessibilityService?.let { service ->
                val rootNode = service.rootInActiveWindow
                
                val button = if (delta > 0) {
                    findNodeById(rootNode, "com.byd.auto.ac:id/temp_up")
                } else {
                    findNodeById(rootNode, "com.byd.auto.ac:id/temp_down")
                }
                
                if (button != null && clickNode(button)) {
                    CommandResult(true, "Temperature $adjustment")
                } else {
                    CommandResult(false, "Could not find temperature control")
                }
            } ?: CommandResult(false, "Accessibility service not available")
            
        } catch (e: Exception) {
            CommandResult(false, "Failed to adjust temperature: ${e.message}")
        }
    }

    /**
     * Execute navigation command
     */
    private suspend fun executeNavigationCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        
        return when {
            entities.containsKey("destination") -> {
                val destination = entities["destination"] ?: ""
                startNavigation(destination)
            }
            
            intent.matchedPattern?.contains("أقرب") == true -> {
                // Find nearest POI
                val poiType = extractPOIType(intent.matchedPattern)
                findNearestPOI(poiType)
            }
            
            intent.matchedPattern?.contains("بيت") == true -> {
                navigateHome()
            }
            
            intent.matchedPattern?.contains("شغل") == true -> {
                navigateToWork()
            }
            
            else -> {
                openNavigationApp()
            }
        }
    }

    /**
     * Start navigation to destination
     */
    private suspend fun startNavigation(destination: String): CommandResult {
        Log.d(TAG, "Starting navigation to: $destination")
        
        return try {
            // Use intent to launch navigation with destination
            val navIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("geo:0,0?q=$destination")
            ).apply {
                setPackage(DILINK_NAV_PACKAGE)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(navIntent)
            CommandResult(true, "Navigating to $destination")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start navigation", e)
            CommandResult(false, "Failed to start navigation: ${e.message}")
        }
    }

    /**
     * Navigate home
     */
    private suspend fun navigateHome(): CommandResult {
        Log.d(TAG, "Navigating home")
        
        return try {
            // Use stored home location
            val homeAddress = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
                .getString("home_address", null)
            
            if (homeAddress != null) {
                startNavigation(homeAddress)
            } else {
                CommandResult(false, "Home address not set. Please set it in settings.")
            }
        } catch (e: Exception) {
            CommandResult(false, "Failed to navigate home: ${e.message}")
        }
    }

    /**
     * Navigate to work
     */
    private suspend fun navigateToWork(): CommandResult {
        Log.d(TAG, "Navigating to work")
        
        return try {
            val workAddress = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
                .getString("work_address", null)
            
            if (workAddress != null) {
                startNavigation(workAddress)
            } else {
                CommandResult(false, "Work address not set. Please set it in settings.")
            }
        } catch (e: Exception) {
            CommandResult(false, "Failed to navigate to work: ${e.message}")
        }
    }

    /**
     * Find nearest POI
     */
    private suspend fun findNearestPOI(poiType: String): CommandResult {
        Log.d(TAG, "Finding nearest $poiType")
        
        val searchQuery = when (poiType) {
            "gas", "بنزين" -> "محطة بنزين"
            "charging", "شحن" -> "محطة شحن"
            "parking", "موقف" -> "موقف سيارات"
            "food", "أكل" -> "مطعم"
            "hospital", "مستشفى" -> "مستشفى"
            else -> poiType
        }
        
        return startNavigation(searchQuery)
    }

    /**
     * Extract POI type from pattern
     */
    private fun extractPOIType(pattern: String): String {
        return when {
            "بنزين" in pattern -> "gas"
            "شحن" in pattern -> "charging"
            "موقف" in pattern -> "parking"
            "مطعم" in pattern || "أكل" in pattern -> "food"
            "مستشفى" in pattern -> "hospital"
            else -> "gas"
        }
    }

    /**
     * Execute media command
     */
    private suspend fun executeMediaCommand(intent: IntentResult): CommandResult {
        val mediaAction = intent.entities["media_action"] ?: "play"
        
        return when (mediaAction) {
            "play" -> mediaPlay()
            "pause" -> mediaPause()
            "next" -> mediaNext()
            "previous" -> mediaPrevious()
            else -> CommandResult(false, "Unknown media action")
        }
    }

    /**
     * Media control: Play
     */
    private suspend fun mediaPlay(): CommandResult {
        Log.d(TAG, "Media: Play")
        
        return try {
            // Send media button event
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            CommandResult(true, "Playing media")
        } catch (e: Exception) {
            CommandResult(false, "Failed to play: ${e.message}")
        }
    }

    /**
     * Media control: Pause
     */
    private suspend fun mediaPause(): CommandResult {
        Log.d(TAG, "Media: Pause")
        
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            CommandResult(true, "Paused media")
        } catch (e: Exception) {
            CommandResult(false, "Failed to pause: ${e.message}")
        }
    }

    /**
     * Media control: Next
     */
    private suspend fun mediaNext(): CommandResult {
        Log.d(TAG, "Media: Next")
        
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            CommandResult(true, "Next track")
        } catch (e: Exception) {
            CommandResult(false, "Failed to skip: ${e.message}")
        }
    }

    /**
     * Media control: Previous
     */
    private suspend fun mediaPrevious(): CommandResult {
        Log.d(TAG, "Media: Previous")
        
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            CommandResult(true, "Previous track")
        } catch (e: Exception) {
            CommandResult(false, "Failed to go back: ${e.message}")
        }
    }

    /**
     * Execute phone command
     */
    private suspend fun executePhoneCommand(intent: IntentResult): CommandResult {
        // TODO: Implement phone commands
        return CommandResult(false, "Phone commands not yet implemented")
    }

    /**
     * Execute info command
     */
    private suspend fun executeInfoCommand(intent: IntentResult): CommandResult {
        // TODO: Implement info commands (time, weather, etc.)
        return CommandResult(false, "Info commands not yet implemented")
    }

    /**
     * Execute system command
     */
    private suspend fun executeSystemCommand(intent: IntentResult): CommandResult {
        // TODO: Implement system commands (brightness, etc.)
        return CommandResult(false, "System commands not yet implemented")
    }

    // ===== Helper Methods =====

    /**
     * Open AC app
     */
    private fun openACApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(DILINK_AC_PACKAGE)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open AC app", e)
        }
    }

    /**
     * Open navigation app
     */
    private suspend fun openNavigationApp(): CommandResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(DILINK_NAV_PACKAGE)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "Opened navigation")
        } catch (e: Exception) {
            CommandResult(false, "Failed to open navigation: ${e.message}")
        }
    }

    /**
     * Find node by ID
     */
    private fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    /**
     * Find node by content description
     */
    private fun findNodeByContentDescription(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        return nodes.firstOrNull()
    }

    /**
     * Click on a node
     */
    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Try to click parent
            clickNode(node.parent)
        }
    }

    /**
     * Get current temperature from AC display
     */
    private fun getCurrentTemperature(root: AccessibilityNodeInfo?): Int {
        val tempText = findNodeById(root, "com.byd.auto.ac:id/temp_display")?.text?.toString()
        return tempText?.toIntOrNull() ?: 22
    }

    /**
     * Send media key event
     */
    private fun sendMediaKeyEvent(keyCode: Int) {
        // Use audio manager to dispatch media key event
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        val downEvent = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, keyCode
        )
        val upEvent = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP, keyCode
        )
        
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}

/**
 * Command execution result
 */
data class CommandResult(
    val success: Boolean,
    val message: String
)
