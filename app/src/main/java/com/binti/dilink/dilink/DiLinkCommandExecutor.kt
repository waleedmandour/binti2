package com.binti.dilink.dilink

import android.content.Context
import com.binti.dilink.nlp.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)

class DiLinkCommandExecutor(private val context: Context) {
    
    suspend fun execute(intent: Intent): CommandResult {
        Timber.i("Executing intent: ${intent.action}")
        
        return withContext(Dispatchers.IO) {
            when (intent.action) {
                "NAVIGATE" -> executeNavigation(intent)
                "CLIMATE_ON" -> executeClimateOn()
                "CLIMATE_OFF" -> executeClimateOff()
                "MUSIC_PLAY" -> executeMediaPlay()
                "VOLUME_UP" -> executeVolumeUp()
                "VOLUME_DOWN" -> executeVolumeDown()
                else -> CommandResult(false, "Unknown command")
            }
        }
    }
    
    private fun executeNavigation(intent: Intent): CommandResult {
        val destination = intent.parameters["destination"] ?: "Unknown"
        Timber.i("Navigation to: $destination")
        return CommandResult(true, "Navigating to $destination")
    }
    
    private fun executeClimateOn(): CommandResult {
        Timber.i("Turning climate on")
        return CommandResult(true, "Climate turned on")
    }
    
    private fun executeClimateOff(): CommandResult {
        Timber.i("Turning climate off")
        return CommandResult(true, "Climate turned off")
    }
    
    private fun executeMediaPlay(): CommandResult {
        Timber.i("Playing media")
        return CommandResult(true, "Media playing")
    }
    
    private fun executeVolumeUp(): CommandResult {
        Timber.i("Volume up")
        return CommandResult(true, "Volume increased")
    }
    
    private fun executeVolumeDown(): CommandResult {
        Timber.i("Volume down")
        return CommandResult(true, "Volume decreased")
    }
}
