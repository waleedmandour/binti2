package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.binti.dilink.utils.HMSUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Egyptian TTS - Text-to-Speech with Arabic Voice
 * 
 * Provides spoken responses in Arabic.
 * 
 * Implementation:
 * - Primary: Android TTS with Arabic locale
 * - Fallback: Egyptian colloquial normalization
 * 
 * Voice Characteristics:
 * - Language: Arabic (Egypt) - ar-EG
 * - Gender: Female (when available)
 * - Style: Friendly, conversational
 * 
 * Note: HMS TTS is optional and enabled when available on Huawei devices.
 * 
 * @author Dr. Waleed Mandour
 */
class EgyptianTTS(private val context: Context) {

    companion object {
        private const val TAG = "EgyptianTTS"
        
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
    
    // Speech queue
    private val speechQueue = mutableListOf<SpeechItem>()
    private var isSpeaking = false

    /**
     * Initialize TTS engine
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Initializing TTS...")
            
            // Initialize Android TTS
            initializeAndroidTTS()
            
            isInitialized = true
            Log.i(TAG, "✅ TTS initialized")
            
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
                        Log.v(TAG, "TTS started: $utteranceId")
                        isSpeaking = true
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.v(TAG, "TTS done: $utteranceId")
                        onSpeechComplete()
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error: $utteranceId")
                        onSpeechComplete()
                    }
                    
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
                        onSpeechComplete()
                    }
                })
                
                isAndroidTTSReady = true
                Log.d(TAG, "Android TTS initialized with Arabic")
                
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            } else {
                Log.e(TAG, "Failed to initialize TTS: status=$status")
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
     * Speak text in Arabic
     * 
     * @param text Text to speak (in Arabic)
     * @return True if speech started successfully
     */
    suspend fun speak(text: String): Boolean {
        if (!isInitialized || text.isBlank()) {
            return false
        }
        
        // Normalize Arabic text
        val normalizedText = normalizeArabicText(text)
        Log.d(TAG, "🗣️ Speaking: $normalizedText")
        
        return withContext(Dispatchers.Main) {
            if (isAndroidTTSReady) {
                speakWithAndroid(normalizedText)
            } else {
                Log.e(TAG, "TTS engine not ready")
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
            Log.e(TAG, "TTS speak failed", e)
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
     * Normalize Arabic text for better pronunciation
     */
    private fun normalizeArabicText(text: String): String {
        return text
            // Normalize alef variants
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            // Normalize teh marbuta
            .replace("ة", "ه")
            // Normalize yeh variants
            .replace("ى", "ي")
            // Handle common Egyptian colloquialisms
            .replace("إزاي", "ازاي")
            .replace("عايز", "عايز") // Keep as is
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
            speakWithAndroid(next.text)
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
            
            Log.d(TAG, "TTS released")
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
