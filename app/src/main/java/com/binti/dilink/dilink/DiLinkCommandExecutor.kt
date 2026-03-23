package com.binti.dilink.dilink

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.binti.dilink.nlp.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * DiLinkCommandExecutor - Executes commands on BYD DiLink system
 * 
 * Methods of execution:
 * 1. AccessibilityService - UI automation (non-root)
 * 2. ADB Shell - Direct commands (requires ADB enabled)
 * 3. Intent broadcasts - DiLink public APIs
 * 4. HTTP API - If DiLink exposes local server
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        // DiLink package names
        const val DILINK_PACKAGE = "com.byd.dilink"
        const val DILINK_NAV_PACKAGE = "com.byd.navigation"
        const val DILINK_MEDIA_PACKAGE = "com.byd.media"
        const val DILINK_CLIMATE_PACKAGE = "com.byd.climate"
        
        // DiLink intent actions
        const val ACTION_NAVIGATE = "com.byd.navigation.ACTION_NAVIGATE"
        const val ACTION_CLIMATE_CONTROL = "com.byd.climate.ACTION_CONTROL"
        const val ACTION_MEDIA_CONTROL = "com.byd.media.ACTION_CONTROL"
        
        // Accessibility service reference
        @Volatile
        private var accessibilityService: DiLinkAccessibilityService? = null
        
        fun setAccessibilityService(service: DiLinkAccessibilityService?) {
            accessibilityService = service
        }
    }

    // ADB executor
    private val adbExecutor = ADBExecutor(context)
    
    // Intent mapper
    private val intentMapper = DiLinkIntentMapper()

    /**
     * Execute a classified intent
     */
    suspend fun execute(intent: Intent): CommandResult {
        Timber.i("Executing intent: $intent")
        
        return when (intent.action) {
            "NAVIGATE" -> executeNavigation(intent)
            "CLIMATE_ON" -> executeClimateOn(intent)
            "CLIMATE_OFF" -> executeClimateOff(intent)
            "CLIMATE_TEMP" -> executeClimateTemp(intent)
            "MUSIC_PLAY" -> executeMusicPlay(intent)
            "MUSIC_PAUSE" -> executeMusicPause(intent)
            "MUSIC_NEXT" -> executeMusicNext(intent)
            "MUSIC_PREV" -> executeMusicPrev(intent)
            "VOLUME_UP" -> executeVolumeUp(intent)
            "VOLUME_DOWN" -> executeVolumeDown(intent)
            "UNKNOWN" -> CommandResult(false, "Unknown command")
            else -> CommandResult(false, "Unsupported action: ${intent.action}")
        }
    }

    /**
     * Execute navigation command
     */
    private suspend fun executeNavigation(intent: Intent): CommandResult {
        val destination = intent.parameters["destination"]
        val destinationType = intent.parameters["destination_type"]
        
        return try {
            // Method 1: Try Intent broadcast
            val navIntent = Intent(ACTION_NAVIGATE).apply {
                setPackage(DILINK_NAV_PACKAGE)
                if (destinationType != null) {
                    putExtra("destination_type", destinationType)
                }
                if (destination != null) {
                    putExtra("destination", destination)
                }
                putExtra("language", "ar-EG")
            }
            
            context.sendBroadcast(navIntent)
            Timber.d("Sent navigation intent: $navIntent")
            
            // Method 2: Try ADB if broadcast fails
            // adbExecutor.executeNavigation(destination)
            
            CommandResult(true, "Navigation started to: ${destination ?: destinationType}")
            
        } catch (e: Exception) {
            Timber.e(e, "Navigation command failed")
            CommandResult(false, "Failed to start navigation: ${e.message}")
        }
    }

    /**
     * Execute climate on command
     */
    private suspend fun executeClimateOn(intent: Intent): CommandResult {
        return executeClimateCommand("on", null)
    }

    /**
     * Execute climate off command
     */
    private suspend fun executeClimateOff(intent: Intent): CommandResult {
        return executeClimateCommand("off", null)
    }

    /**
     * Execute climate temperature command
     */
    private suspend fun executeClimateTemp(intent: Intent): CommandResult {
        val temp = intent.parameters["temperature"]?.toIntOrNull() ?: 22
        return executeClimateCommand("temp", temp)
    }

    /**
     * Generic climate command executor
     */
    private suspend fun executeClimateCommand(action: String, value: Int?): CommandResult {
        return try {
            // Method 1: Accessibility Service UI automation
            val service = accessibilityService
            if (service != null) {
                val result = executeViaAccessibility(service, "climate", action, value)
                if (result.success) {
                    return result
                }
            }
            
            // Method 2: Intent broadcast
            val climateIntent = Intent(ACTION_CLIMATE_CONTROL).apply {
                setPackage(DILINK_CLIMATE_PACKAGE)
                putExtra("action", action)
                if (value != null) {
                    putExtra("value", value)
                }
            }
            
            context.sendBroadcast(climateIntent)
            Timber.d("Sent climate intent: action=$action, value=$value")
            
            CommandResult(true, "Climate command executed: $action")
            
        } catch (e: Exception) {
            Timber.e(e, "Climate command failed")
            CommandResult(false, "Climate control failed: ${e.message}")
        }
    }

    /**
     * Execute music play command
     */
    private suspend fun executeMusicPlay(intent: Intent): CommandResult {
        val query = intent.parameters["query"]
        return executeMediaCommand("play", query)
    }

    /**
     * Execute music pause command
     */
    private suspend fun executeMusicPause(intent: Intent): CommandResult {
        return executeMediaCommand("pause", null)
    }

    /**
     * Execute music next command
     */
    private suspend fun executeMusicNext(intent: Intent): CommandResult {
        return executeMediaCommand("next", null)
    }

    /**
     * Execute music previous command
     */
    private suspend fun executeMusicPrev(intent: Intent): CommandResult {
        return executeMediaCommand("prev", null)
    }

    /**
     * Generic media command executor
     */
    private suspend fun executeMediaCommand(action: String, query: String?): CommandResult {
        return try {
            // Method 1: Accessibility Service
            val service = accessibilityService
            if (service != null) {
                val result = executeViaAccessibility(service, "media", action, null)
                if (result.success) {
                    return result
                }
            }
            
            // Method 2: Intent broadcast
            val mediaIntent = Intent(ACTION_MEDIA_CONTROL).apply {
                setPackage(DILINK_MEDIA_PACKAGE)
                putExtra("action", action)
                if (query != null) {
                    putExtra("query", query)
                }
            }
            
            context.sendBroadcast(mediaIntent)
            Timber.d("Sent media intent: action=$action")
            
            // Method 3: KeyEvent for media buttons
            when (action) {
                "play", "pause" -> sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "next" -> sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                "prev" -> sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            
            CommandResult(true, "Media command executed: $action")
            
        } catch (e: Exception) {
            Timber.e(e, "Media command failed")
            CommandResult(false, "Media control failed: ${e.message}")
        }
    }

    /**
     * Execute volume up command
     */
    private suspend fun executeVolumeUp(intent: Intent): CommandResult {
        return executeVolumeCommand(true)
    }

    /**
     * Execute volume down command
     */
    private suspend fun executeVolumeDown(intent: Intent): CommandResult {
        return executeVolumeCommand(false)
    }

    /**
     * Generic volume command executor
     */
    private suspend fun executeVolumeCommand(increase: Boolean): CommandResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val direction = if (increase) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            
            audioManager.adjustVolume(
                direction,
                android.media.AudioManager.FLAG_SHOW_UI
            )
            
            Timber.d("Volume ${if (increase) "increased" else "decreased"}")
            CommandResult(true, "Volume adjusted")
            
        } catch (e: Exception) {
            Timber.e(e, "Volume command failed")
            CommandResult(false, "Volume control failed: ${e.message}")
        }
    }

    /**
     * Execute command via Accessibility Service
     */
    private fun executeViaAccessibility(
        service: DiLinkAccessibilityService,
        category: String,
        action: String,
        value: Int?
    ): CommandResult {
        return try {
            val rootInActiveWindow = service.rootInActiveWindow ?: return CommandResult(false, "No active window")
            
            when (category) {
                "climate" -> executeClimateAccessibility(rootInActiveWindow, action, value)
                "media" -> executeMediaAccessibility(rootInActiveWindow, action)
                else -> CommandResult(false, "Unknown category")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Accessibility execution failed")
            CommandResult(false, "Accessibility failed: ${e.message}")
        }
    }

    /**
     * Execute climate commands via Accessibility
     */
    private fun executeClimateAccessibility(root: AccessibilityNodeInfo, action: String, value: Int?): CommandResult {
        // Find climate app button
        val climateButton = findNodeByText(root, "تكييف") 
            ?: findNodeByContentDescription(root, "Climate")
            ?: findNodeById(root, "com.byd.dilink:id/climate_button")
        
        if (climateButton != null && action == "on") {
            climateButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return CommandResult(true, "Climate turned on via UI")
        }
        
        // Find temperature controls
        if (action == "temp" && value != null) {
            // Look for temperature up/down buttons
            val tempUpBtn = findNodeById(root, "com.byd.climate:id/temp_up")
            val tempDownBtn = findNodeById(root, "com.byd.climate:id/temp_down")
            
            // Adjust temperature (simplified - would need to read current temp first)
            // For now, just click up/down buttons
            val btn = if (value > 22) tempUpBtn else tempDownBtn
            btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        return CommandResult(true, "Climate UI interaction attempted")
    }

    /**
     * Execute media commands via Accessibility
     */
    private fun executeMediaAccessibility(root: AccessibilityNodeInfo, action: String): CommandResult {
        val button = when (action) {
            "play", "pause" -> findNodeById(root, "com.byd.media:id/play_pause")
                ?: findNodeByContentDescription(root, "Play")
                ?: findNodeByContentDescription(root, "Pause")
            "next" -> findNodeById(root, "com.byd.media:id/next")
                ?: findNodeByContentDescription(root, "Next")
            "prev" -> findNodeById(root, "com.byd.media:id/prev")
                ?: findNodeByContentDescription(root, "Previous")
            else -> null
        }
        
        return if (button != null) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            CommandResult(true, "Media button clicked: $action")
        } else {
            CommandResult(false, "Media button not found")
        }
    }

    /**
     * Find accessibility node by text
     */
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    /**
     * Find accessibility node by content description
     */
    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(desc) // Also searches content description
        return nodes.firstOrNull()
    }

    /**
     * Find accessibility node by ID
     */
    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    /**
     * Send media key event
     */
    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            
            // Send to audio service
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to send media key event")
        }
    }
}

/**
 * Result of command execution
 */
data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)

/**
 * ADB command executor for DiLink
 */
class ADBExecutor(private val context: Context) {
    
    suspend fun executeNavigation(destination: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Would need wireless ADB enabled
                // Example: adb shell am start -n com.byd.navigation/.MainActivity --es destination "القاهرة"
                Timber.d("ADB navigation would execute: destination=$destination")
                true
            } catch (e: Exception) {
                Timber.e(e, "ADB execution failed")
                false
            }
        }
    }
}
