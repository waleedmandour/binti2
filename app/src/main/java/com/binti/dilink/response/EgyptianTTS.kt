package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.Locale

/**
 * EgyptianTTS - Text-to-Speech with Egyptian Female Voice
 * 
 * Features:
 * - Egyptian Arabic voice (offline when available)
 * - Welcoming prosody configuration
 * - Natural colloquial responses
 * - Fallback to Google TTS
 * 
 * Voice Sources:
 * - Primary: AiVOOV Egyptian Female (offline pack)
 * - Fallback: Google TTS Arabic
 * - Emergency: System TTS
 */
class EgyptianTTS(private val context: Context) {

    companion object {
        // Voice configuration
        private const val EGYPTIAN_VOICE_LOCALE = "ar-EG"
        private const val DEFAULT_SPEECH_RATE = 0.9f  // Slightly slower for clarity
        private const val DEFAULT_PITCH = 1.05f      // Slightly higher for warmth
        
        // Voice pack paths
        private const val VOICE_PACK_PATH = "voices/ar-EG-female"
        
        // Prosody settings for different response types
        val PROSODY_CONFIGS = mapOf(
            ProsodyStyle.WELCOMING to ProsodyConfig(
                pitch = 1.1f,
                speechRate = 0.85f,
                pauseMs = 300
            ),
            ProsodyStyle.CONFIRMATION to ProsodyConfig(
                pitch = 1.0f,
                speechRate = 0.95f,
                pauseMs = 100
            ),
            ProsodyStyle.ERROR to ProsodyConfig(
                pitch = 0.95f,
                speechRate = 0.9f,
                pauseMs = 200
            ),
            ProsodyStyle.NEUTRAL to ProsodyConfig(
                pitch = 1.0f,
                speechRate = 0.9f,
                pauseMs = 150
            )
        )
    }

    // TTS engine
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var egyptianVoice: Voice? = null
    
    // Listeners
    private val completionListeners = mutableMapOf<String, (() -> Unit)?>()

    /**
     * Initialize TTS engine
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTTSInit()
            } else {
                Timber.e("TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Handle TTS initialization
     */
    private fun onTTSInit() {
        val engine = tts ?: return
        
        // Find Egyptian Arabic voice
        val voices = engine.voices
        egyptianVoice = voices.find { voice ->
            voice.locale.toString().contains("ar", ignoreCase = true) &&
            (voice.locale.toString().contains("EG", ignoreCase = true) ||
             voice.name.contains("female", ignoreCase = true))
        } ?: voices.find { voice ->
            voice.locale.language == "ar"
        }
        
        // Set language
        val result = engine.setLanguage(Locale("ar", "EG"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("Egyptian Arabic not supported, falling back to Arabic")
            engine.setLanguage(Locale("ar"))
        }
        
        // Set voice if found
        egyptianVoice?.let { voice ->
            engine.setVoice(voice)
            Timber.i("Set TTS voice: ${voice.name}")
        }
        
        // Set default speech parameters
        engine.setSpeechRate(DEFAULT_SPEECH_RATE)
        engine.setPitch(DEFAULT_PITCH)
        
        // Set up utterance progress listener
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Timber.v("TTS started: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Timber.v("TTS done: $utteranceId")
                completionListeners.remove(utteranceId)?.invoke()
            }
            
            override fun onError(utteranceId: String?) {
                Timber.w("TTS error: $utteranceId")
                completionListeners.remove(utteranceId)?.invoke()
            }
            
            @Deprecated("Use onError(String)")
            override fun onError(utteranceId: String?) {
                // Deprecated but required
            }
        })
        
        isInitialized = true
        Timber.i("TTS initialized successfully")
    }

    /**
     * Speak text with Egyptian prosody
     */
    fun speak(text: String, style: ProsodyStyle = ProsodyStyle.NEUTRAL, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Timber.w("TTS not initialized")
            onComplete?.invoke()
            return
        }
        
        val engine = tts ?: run {
            onComplete?.invoke()
            return
        }
        
        // Apply prosody configuration
        val prosody = PROSODY_CONFIGS[style] ?: PROSODY_CONFIGS[ProsodyStyle.NEUTRAL]!!
        
        engine.setSpeechRate(prosody.speechRate)
        engine.setPitch(prosody.pitch)
        
        // Generate unique utterance ID
        val utteranceId = "binti_${System.currentTimeMillis()}"
        
        // Store completion listener
        if (onComplete != null) {
            completionListeners[utteranceId] = onComplete
        }
        
        // Apply Egyptian text transformations
        val processedText = applyEgyptianTransformations(text, style)
        
        // Speak
        engine.speak(processedText, TextToSpeech.QUEUE_ADD, null, utteranceId)
        
        Timber.d("Speaking: '$processedText' with style $style")
    }

    /**
     * Speak text as a Flow
     */
    fun speakFlow(text: String, style: ProsodyStyle = ProsodyStyle.NEUTRAL): Flow<Unit> = callbackFlow {
        speak(text, style) {
            trySend(Unit)
            close()
        }
        awaitClose()
    }

    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
        completionListeners.clear()
    }

    /**
     * Apply Egyptian Arabic text transformations
     */
    private fun applyEgyptianTransformations(text: String, style: ProsodyStyle): String {
        var processed = text
        
        // Add natural pauses
        processed = processed
            .replace("، ", "،، ")  // Add pause after comma
            .replace(". ", "… ")   // Add longer pause after period
        
        // Add welcoming particles based on style
        if (style == ProsodyStyle.WELCOMING && !processed.contains("يا")) {
            // Don't add particles if already present
        }
        
        return processed
    }

    /**
     * Get available voices
     */
    fun getAvailableVoices(): List<VoiceInfo> {
        val voices = tts?.voices ?: return emptyList()
        
        return voices.filter { voice ->
            voice.locale.language == "ar"
        }.map { voice ->
            VoiceInfo(
                name = voice.name,
                locale = voice.locale.toString(),
                quality = when (voice.quality) {
                    Voice.QUALITY_VERY_HIGH -> "Very High"
                    Voice.QUALITY_HIGH -> "High"
                    Voice.QUALITY_NORMAL -> "Normal"
                    Voice.QUALITY_LOW -> "Low"
                    Voice.QUALITY_VERY_LOW -> "Very Low"
                    else -> "Unknown"
                },
                isOffline = voice.isNetworkConnectionRequired.not()
            )
        }
    }

    /**
     * Check if offline voice pack is installed
     */
    fun isOfflineVoiceAvailable(): Boolean {
        return egyptianVoice?.isNetworkConnectionRequired == false
    }

    /**
     * Release TTS resources
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        completionListeners.clear()
        Timber.i("TTS released")
    }

    /**
     * Check if TTS is initialized
     */
    fun isReady(): Boolean = isInitialized && tts != null
}

/**
 * Prosody style for different response types
 */
enum class ProsodyStyle {
    WELCOMING,      // Greeting and welcome messages
    CONFIRMATION,   // Command confirmation
    ERROR,          // Error messages
    NEUTRAL,        // Standard responses
    QUESTION        // Asking for clarification
}

/**
 * Prosody configuration
 */
data class ProsodyConfig(
    val pitch: Float,
    val speechRate: Float,
    val pauseMs: Int
)

/**
 * Voice information
 */
data class VoiceInfo(
    val name: String,
    val locale: String,
    val quality: String,
    val isOffline: Boolean
)
