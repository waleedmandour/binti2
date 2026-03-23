package com.binti.dilink.dilink

import timber.log.Timber

enum class CommandMethod {
    INTENT, ADB, ACCESSIBILITY, KEY_EVENT, AUDIO_MANAGER, HTTP, UNKNOWN
}

data class DiLinkCommand(
    val method: CommandMethod,
    val action: String = "",
    val extras: Map<String, Any> = emptyMap()
)

class DiLinkIntentMapper {
    
    private val triggerPhrases = mapOf(
        "خديني" to "NAVIGATE",
        "وديني" to "NAVIGATE",
        "شغّل التكييف" to "CLIMATE_ON",
        "افتح التكييف" to "CLIMATE_ON",
        "أطفئ التكييف" to "CLIMATE_OFF",
        "أغلق التكييف" to "CLIMATE_OFF",
        "شغّل المزيكا" to "MUSIC_PLAY",
        "ارفع الصوت" to "VOLUME_UP",
        "اخفض الصوت" to "VOLUME_DOWN"
    )
    
    fun mapToCommand(text: String): DiLinkCommand? {
        for ((phrase, intent) in triggerPhrases) {
            if (text.contains(phrase)) {
                return DiLinkCommand(
                    method = CommandMethod.INTENT,
                    action = "com.byd.dilink.$intent"
                )
            }
        }
        return null
    }
    
    fun getSupportedIntents(): List<String> {
        return triggerPhrases.values.distinct()
    }
    
    fun getTriggerPhrases(): Map<String, String> = triggerPhrases
}
