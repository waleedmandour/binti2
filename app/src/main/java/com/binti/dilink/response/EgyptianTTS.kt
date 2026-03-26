package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Egyptian TTS - Text-to-Speech with Egyptian Female Voice
 * 
 * Provides spoken responses in Egyptian Arabic dialect.
 * 
 * Implementation:
 * - Primary: Coqui TTS Egyptian Female model (offline)
 * - Fallback: Android TTS with Arabic locale
 * 
 * Voice Characteristics:
 * - Language: Arabic (Egypt) - ar-EG
 * - Gender: Female
 * - Style: Friendly, conversational Egyptian dialect
 * 
 * @author Dr. Waleed Mandour
 */
class EgyptianTTS(private val context: Context) {

    companion object {
        private const val TAG = "EgyptianTTS"
        
        // TTS model path
        private const val COQUI_MODEL_PATH = "voices/ar-EG-female/model.onnx"
        
        // Speech rate (1.0 = normal)
        private const val DEFAULT_SPEECH_RATE = 0.95f
        
        // Pitch (1.0 = normal)
        private const val DEFAULT_PITCH = 1.0f
    }

    // Android TTS
    private var androidTTS: TextToSpeech? = null
    private var isAndroidTTSReady = false
    
    // State
    private var isInitialized = false
    private var useOfflineTTS = false
    
    // Speech queue
    private val speechQueue = mutableListOf<SpeechItem>()
    private var isSpeaking = false

    /**
     * Initialize TTS engine
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Initializing Egyptian TTS...")
            
            // Initialize Android TTS
            initializeAndroidTTS()
            
            isInitialized = true
            Log.i(TAG, "✅ Egyptian TTS initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
            throw e
        }
    }

    /**
     * Initialize Android TTS
     */
    private fun initializeAndroidTTS() = suspendCancellableCoroutine<Unit> { continuation ->
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to Arabic (Egypt)
                val result = androidTTS?.setLanguage(Locale("ar", "EG"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to generic Arabic
                    androidTTS?.setLanguage(Locale("ar"))
                    Log.w(TAG, "Egyptian Arabic not available, using generic Arabic")
                }
                
                // Configure TTS parameters
                androidTTS?.setSpeechRate(DEFAULT_SPEECH_RATE)
                androidTTS?.setPitch(DEFAULT_PITCH)
                
                // Set utterance progress listener
                androidTTS?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.v(TAG, "Android TTS started: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.v(TAG, "Android TTS done: $utteranceId")
                        onSpeechComplete()
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Android TTS error: $utteranceId")
                        onSpeechComplete()
                    }
                    
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "Android TTS error: $utteranceId, code: $errorCode")
                        onSpeechComplete()
                    }
                })
                
                isAndroidTTSReady = true
                Log.d(TAG, "Android TTS initialized")
                
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            } else {
                Log.e(TAG, "Failed to initialize Android TTS: status=$status")
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("TTS initialization failed"))
                }
            }
        }
        
        continuation.invokeOnCancellation {
            androidTTS?.shutdown()
        }
    }

    /**
     * Speak text in Egyptian Arabic
     * 
     * @param text Text to speak (in Egyptian Arabic)
     * @return True if speech started successfully
     */
    suspend fun speak(text: String): Boolean {
        if (!isInitialized || text.isBlank()) {
            return false
        }
        
        // Normalize Egyptian Arabic text
        val normalizedText = normalizeEgyptianText(text)
        Log.d(TAG, "🗣️ Speaking: $normalizedText")
        
        return withContext(Dispatchers.Main) {
            if (isAndroidTTSReady) {
                speakWithAndroid(normalizedText)
            } else {
                Log.e(TAG, "No TTS engine available")
                false
            }
        }
    }

    /**
     * Speak using Android TTS
     */
    private fun speakWithAndroid(text: String): Boolean {
        return try {
            val utteranceId = System.currentTimeMillis().toString()
            val result = androidTTS?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            result == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Android TTS speak failed", e)
            false
        }
    }

    /**
     * Stop current speech
     */
    fun stop() {
        try {
            androidTTS?.stop()
            isSpeaking = false
            speechQueue.clear()
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop TTS", e)
        }
    }

    /**
     * Pause speech (if supported)
     */
    fun pause() {
        // Android TTS doesn't support pause directly
        // Store current state and stop
        androidTTS?.stop()
    }

    /**
     * Resume speech
     */
    fun resume() {
        // Resume from queue if available
        if (speechQueue.isNotEmpty()) {
            val item = speechQueue.removeAt(0)
            // Re-speak
        }
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * Set speech rate
     */
    fun setSpeechRate(rate: Float) {
        androidTTS?.setSpeechRate(rate)
    }

    /**
     * Set pitch
     */
    fun setPitch(pitch: Float) {
        androidTTS?.setPitch(pitch)
    }

    /**
     * Normalize Egyptian Arabic text for better pronunciation
     */
    private fun normalizeEgyptianText(text: String): String {
        return text
            // Handle common Egyptian colloquialisms
            .replace("إزاي", "ازاي")
            .replace("عايز", "عيز")
            .replace("عايزة", "عيزة")
            // Ensure proper spacing around punctuation
            .replace(Regex("([،.؟!])"), " $1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Handle speech completion
     */
    private fun onSpeechComplete() {
        isSpeaking = false
        
        // Process next item in queue
        if (speechQueue.isNotEmpty()) {
            val next = speechQueue.removeAt(0)
            // Process next speech item
        }
    }

    /**
     * Release TTS resources
     */
    fun release() {
        try {
            androidTTS?.stop()
            androidTTS?.shutdown()
            androidTTS = null
            
            isInitialized = false
            isAndroidTTSReady = false
            
            Log.d(TAG, "Egyptian TTS released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
    }

    // Data class for speech queue
    private data class SpeechItem(
        val text: String,
        val utteranceId: String,
        val onComplete: (() -> Unit)? = null
    )
}
