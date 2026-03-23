package com.binti.dilink.nlp

import android.content.Context
import timber.log.Timber

data class Intent(
    val action: String,
    val confidence: Float = 0f,
    val parameters: Map<String, String> = emptyMap(),
    val originalText: String = ""
) {
    companion object {
        val UNKNOWN = Intent(action = "UNKNOWN", confidence = 0f)
        
        fun navigate(destination: String, confidence: Float = 1f) = Intent(
            action = "NAVIGATE",
            confidence = confidence,
            parameters = mapOf("destination" to destination)
        )
        
        fun climateOn(confidence: Float = 1f) = Intent(
            action = "CLIMATE_ON",
            confidence = confidence
        )
        
        fun climateOff(confidence: Float = 1f) = Intent(
            action = "CLIMATE_OFF",
            confidence = confidence
        )
        
        fun musicPlay(confidence: Float = 1f) = Intent(
            action = "MUSIC_PLAY",
            confidence = confidence
        )
        
        fun volumeUp(confidence: Float = 1f) = Intent(
            action = "VOLUME_UP",
            confidence = confidence
        )
        
        fun volumeDown(confidence: Float = 1f) = Intent(
            action = "VOLUME_DOWN",
            confidence = confidence
        )
    }
}

class IntentClassifier(private val context: Context) {
    
    private val normalizer = EgyptianArabicNormalizer()
    private var isLoaded = false
    
    fun loadModel(): Boolean {
        Timber.i("Intent classifier model loading...")
        isLoaded = true
        return true
    }
    
    fun classify(text: String): Intent {
        if (!isLoaded || text.isBlank()) return Intent.UNKNOWN
        
        val normalized = normalizer.normalize(text)
        
        return when {
            containsAny(normalized, listOf("导航", "خديني", "وديني", "البيت", "الشغل")) -> {
                val dest = normalizer.extractEntity(text, "NAVIGATE") ?: ""
                Intent.navigate(dest)
            }
            containsAny(normalized, listOf("تكييف", "شغّل", "افتح")) -> Intent.climateOn()
            containsAny(normalized, listOf("أطفئ", "أغلق", "قفّل")) -> Intent.climateOff()
            containsAny(normalized, listOf("موسيقى", "مزيكا", "شغّل")) -> Intent.musicPlay()
            containsAny(normalized, listOf("ارفع", "زود", "صوت")) -> Intent.volumeUp()
            containsAny(normalized, listOf("اخفض", "قلل")) -> Intent.volumeDown()
            else -> Intent.UNKNOWN
        }
    }
    
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
    
    fun release() {
        isLoaded = false
        Timber.i("Intent classifier released")
    }
    
    fun isModelLoaded(): Boolean = isLoaded
}
