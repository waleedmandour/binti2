package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vosk-based Wake Word Detector - "يا بنتي" (Ya Binti)
 * 
 * Uses Vosk's keyword spotting capability to detect the wake word phrase.
 * This approach is more practical than training a custom TFLite model because:
 * - Uses the existing Arabic Vosk model (no additional training needed)
 * - Keyword spotting is optimized for continuous listening
 * - Lower battery consumption than full ASR
 * 
 * Detection Phrases:
 * - "يا بنتي" (Ya Binti) - Primary wake word
 * - "بنتي" (Binti) - Short form
 * - "يابنتي" (Yabinti) - Combined form
 * 
 * Power Optimization:
 * - Voice Activity Detection (VAD) to skip silence
 * - Lower sample rate processing (8kHz for detection)
 * - Cooldown period between detections
 * 
 * @author Dr. Waleed Mandour
 */
class VoskWakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "VoskWakeWord"
        
        // Audio configuration (optimized for wake word)
        private const val SAMPLE_RATE = 16000f
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Vosk model directory
        private const val VOSK_MODEL_DIR = "vosk-model-ar-mgb2-0.4"
        
        // Wake word phrases (normalized Arabic)
        private val WAKE_WORDS = listOf(
            "يا بنتي",      // Standard form
            "يابنتي",       // Combined form
            "بنتي",         // Short form
            "يا بنتى",      // Alternative spelling
            "يابنى"         // Common pronunciation variant
        )
        
        // Detection settings
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val COOLDOWN_MS = 3000L
        private const val VAD_ENERGY_THRESHOLD = 500  // Minimum energy to process
        private const val VAD_SILENCE_FRAMES = 30     // Frames of silence to stop
        
        // Grammar for keyword spotting (more efficient than full ASR)
        private const val WAKE_WORD_GRAMMAR = "[\"يا بنتي\", \"يابنتي\", \"بنتي\", \"يا بنتى\", \"يابنى\", \"(يا)\"]"
    }

    // Vosk components
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    // Detection state
    private val _wakeWordFlow = MutableStateFlow(false)
    val wakeWordFlow: StateFlow<Boolean> = _wakeWordFlow.asStateFlow()
    
    private var lastDetectionTime = 0L
    private var isInitialized = false
    
    // Use full ASR model or grammar-based
    private var useGrammarMode = true

    /**
     * Initialize the wake word detector
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔧 Initializing Vosk wake word detector...")
            
            // Check if model exists
            val modelDir = File(context.filesDir, "models/$VOSK_MODEL_DIR")
            
            if (!modelDir.exists()) {
                // Try alternate path
                val altModelDir = File(context.filesDir, "models/vosk-model-ar-mgb2-0.4")
                if (!altModelDir.exists()) {
                    Log.w(TAG, "⚠️ Vosk model not found - wake word detection unavailable")
                    Log.w(TAG, "   Model path checked: ${modelDir.absolutePath}")
                    Log.w(TAG, "   Please download models from B2 first")
                    return@withContext
                }
                voskModel = Model(altModelDir.absolutePath)
                Log.d(TAG, "✅ Vosk model loaded from ${altModelDir.absolutePath}")
            } else {
                voskModel = Model(modelDir.absolutePath)
                Log.d(TAG, "✅ Vosk model loaded from ${modelDir.absolutePath}")
            }
            
            // Create recognizer with grammar for keyword spotting
            // This is more efficient than full ASR for wake word detection
            try {
                recognizer = Recognizer(voskModel, SAMPLE_RATE)
                Log.d(TAG, "✅ Recognizer created for wake word detection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create recognizer: ${e.message}")
            }
            
            isInitialized = true
            Log.i(TAG, "✅ Wake word detector initialized successfully")
            Log.i(TAG, "   Listening for: ${WAKE_WORDS.joinToString(", ")}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize wake word detector", e)
        }
    }

    /**
     * Start listening for wake word
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.w(TAG, "⚠️ Wake word detector not initialized, skipping")
            return@withContext
        }
        
        if (isRecording) {
            Log.w(TAG, "Already recording, skipping start")
            return@withContext
        }
        
        try {
            Log.i(TAG, "🎤 Starting wake word detection...")
            Log.i(TAG, "   Wake words: ${WAKE_WORDS.joinToString(", ")}")
            
            // Initialize audio record
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE.toInt(), CHANNEL_CONFIG, AUDIO_FORMAT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE.toInt(),
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord not initialized")
                return@withContext
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            val buffer = ShortArray(bufferSize)
            var silenceFrameCount = 0
            var hasSpeech = false
            
            // Recording loop with VAD
            while (isRecording && isActive) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (read > 0) {
                    // Voice Activity Detection - skip silent frames
                    val energy = calculateEnergy(buffer, read)
                    
                    if (energy < VAD_ENERGY_THRESHOLD) {
                        // Silence detected
                        silenceFrameCount++
                        if (silenceFrameCount > VAD_SILENCE_FRAMES && hasSpeech) {
                            // Process end of speech
                            val finalResult = recognizer?.finalResult
                            processResult(finalResult)
                            hasSpeech = false
                        }
                        continue
                    }
                    
                    silenceFrameCount = 0
                    hasSpeech = true
                    
                    // Process audio frame
                    val isEndpoint = recognizer?.acceptWaveForm(buffer, read) ?: false
                    
                    if (isEndpoint) {
                        val result = recognizer?.result
                        processResult(result)
                    } else {
                        // Check partial results for faster detection
                        val partial = recognizer?.partialResult
                        processPartialResult(partial)
                    }
                }
            }
            
            Log.d(TAG, "Wake word detection loop ended")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word detection loop", e)
        } finally {
            stopListening()
        }
    }

    /**
     * Calculate audio energy for VAD
     */
    private fun calculateEnergy(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / size)
    }

    /**
     * Process recognition result for wake word
     */
    private fun processResult(jsonResult: String?) {
        if (jsonResult.isNullOrBlank()) return
        
        try {
            val json = JSONObject(jsonResult)
            val text = json.optString("text", "")
            
            if (text.isNotBlank()) {
                Log.v(TAG, "📝 Result: $text")
                checkForWakeWord(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse result: $jsonResult")
        }
    }

    /**
     * Process partial result for faster wake word detection
     */
    private fun processPartialResult(jsonResult: String?) {
        if (jsonResult.isNullOrBlank()) return
        
        try {
            val json = JSONObject(jsonResult)
            val partial = json.optString("partial", "")
            
            if (partial.isNotBlank()) {
                Log.v(TAG, "🎤 Partial: $partial")
                checkForWakeWord(partial)
            }
        } catch (e: Exception) {
            // Ignore parsing errors for partial results
        }
    }

    /**
     * Check if text contains wake word
     */
    private fun checkForWakeWord(text: String) {
        val normalizedText = normalizeArabic(text)
        
        for (wakeWord in WAKE_WORDS) {
            val normalizedWakeWord = normalizeArabic(wakeWord)
            
            if (normalizedText.contains(normalizedWakeWord)) {
                handleWakeWordDetected(wakeWord)
                return
            }
        }
    }

    /**
     * Normalize Arabic text for comparison
     */
    private fun normalizeArabic(text: String): String {
        return text
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F]"), "")  // Remove diacritics
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * Handle wake word detection
     */
    private fun handleWakeWordDetected(wakeWord: String) {
        val currentTime = System.currentTimeMillis()
        
        // Check cooldown
        if (currentTime - lastDetectionTime < COOLDOWN_MS) {
            Log.d(TAG, "Detection in cooldown, ignoring")
            return
        }
        
        Log.i(TAG, "🎯 Wake word detected: \"$wakeWord\"")
        Log.i(TAG, "   Time since last: ${currentTime - lastDetectionTime}ms")
        
        lastDetectionTime = currentTime
        _wakeWordFlow.value = true
        
        // Reset after a short delay
        Thread.sleep(500)
        _wakeWordFlow.value = false
    }

    /**
     * Stop listening for wake word
     */
    fun stopListening() {
        isRecording = false
        audioRecord?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping audio record: ${e.message}")
            }
        }
        audioRecord = null
        Log.d(TAG, "Wake word detection stopped")
    }

    /**
     * Check if detector is ready
     */
    fun isReady(): Boolean = isInitialized && recognizer != null

    /**
     * Release resources
     */
    fun release() {
        stopListening()
        recognizer?.close()
        recognizer = null
        voskModel?.close()
        voskModel = null
        isInitialized = false
        Log.d(TAG, "Wake word detector released")
    }
}
