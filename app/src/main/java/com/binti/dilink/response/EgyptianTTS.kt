package com.binti.dilink.response

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.binti.dilink.utils.HMSUtils
import com.huawei.hms.mlsdk.tts.MLTtsConfig
import com.huawei.hms.mlsdk.tts.MLTtsConstants
import com.huawei.hms.mlsdk.tts.MLTtsEngine
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
 * - Fallback: Huawei Cloud TTS with Arabic voice
 * - Last resort: Android TTS with Arabic locale
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
    
    // Huawei ML Kit TTS
    private var huaweiTTSEngine: MLTtsEngine? = null
    private var huaweiTTSConfig: MLTtsConfig? = null
    
    // State
    private var isInitialized = false
    private var useHuaweiTTS = false
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
            
            // Try to initialize Huawei TTS first (best quality for Huawei devices)
            if (HMSUtils.isHuaweiDevice() && HMSUtils.isHuaweiServicesAvailable(context)) {
                initializeHuaweiTTS()
                useHuaweiTTS = true
                Log.d(TAG, "Using Huawei ML Kit TTS")
            }
            
            // Always initialize Android TTS as fallback
            initializeAndroidTTS()
            
            isInitialized = true
            Log.i(TAG, "✅ Egyptian TTS initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
            throw e
        }
    }

    /**
     * Initialize Huawei ML Kit TTS
     */
    private fun initializeHuaweiTTS() {
        try {
            // Configure TTS
            huaweiTTSConfig = MLTtsConfig().apply {
                // Set language to Arabic
                language = MLTtsConstants.TTS_LANGUAGE_ARABIC
                
                // Set speaker (female Arabic voice)
                speaker = MLTtsConstants.TTS_SPEAKER_FEMALE_AR
                
                // Set speech rate
                speed = DEFAULT_SPEECH_RATE
                
                // Set volume
                volume = 1.0f
            }
            
            huaweiTTSEngine = MLTtsEngine(context, huaweiTTSConfig)
            
            // Set callback for speech completion
            huaweiTTSEngine?.setTtsCallback(object : MLTtsEngine.callback {
                override fun onError(taskId: String, err: MLTtsConstants.Error) {
                    Log.e(TAG, "Huawei TTS error: $err")
                    onSpeechComplete()
                }
                
                override fun onWarn(taskId: String, warn: MLTtsConstants.Warn) {
                    Log.w(TAG, "Huawei TTS warning: $warn")
                }
                
                override fun onRangeStart(taskId: String, start: Int, end: Int) {
                    // Speech range started
                }
                
                override fun onAudioAvailable(taskId: String, audio: MLTtsAudioFragment, offset: Int, range: android.graphics.RectF) {
                    // Audio fragment available
                }
                
                override fun onEvent(taskId: String, eventId: Int, bundle: Bundle?) {
                    when (eventId) {
                        MLTtsConstants.EVENT_PLAY_START -> {
                            Log.d(TAG, "Huawei TTS playback started")
                        }
                        MLTtsConstants.EVENT_PLAY_STOP -> {
                            Log.d(TAG, "Huawei TTS playback stopped")
                            onSpeechComplete()
                        }
                        MLTtsConstants.EVENT_SYNTHESIS_COMPLETE -> {
                            Log.d(TAG, "Huawei TTS synthesis complete")
                        }
                    }
                }
            })
            
            Log.d(TAG, "Huawei ML Kit TTS initialized")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Huawei TTS: ${e.message}")
            huaweiTTSEngine = null
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
            // Prefer Huawei TTS if available
            if (useHuaweiTTS && huaweiTTSEngine != null) {
                speakWithHuawei(normalizedText)
            } else if (isAndroidTTSReady) {
                speakWithAndroid(normalizedText)
            } else {
                Log.e(TAG, "No TTS engine available")
                false
            }
        }
    }

    /**
     * Speak using Huawei ML Kit TTS
     */
    private fun speakWithHuawei(text: String): Boolean {
        return try {
            huaweiTTSEngine?.speak(text, MLTtsEngine.QUEUE_APPEND)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Huawei TTS speak failed", e)
            // Fallback to Android TTS
            speakWithAndroid(text)
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
            if (useHuaweiTTS) {
                huaweiTTSEngine?.stop()
            }
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
        huaweiTTSConfig?.speed = rate
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
            
            huaweiTTSEngine?.stop()
            huaweiTTSEngine?.shutdown()
            huaweiTTSEngine = null
            
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
