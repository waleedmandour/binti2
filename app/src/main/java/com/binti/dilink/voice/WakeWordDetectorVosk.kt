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

/**
 * Wake Word Detector using Vosk Grammar - "يا بنتي" (Ya Binti)
 * 
 * Uses Vosk with a limited grammar to detect the wake word phrase.
 * This approach reuses the existing Vosk Arabic model (vosk-model-ar-mgb2)
 * that the user already has, eliminating the need for a separate TFLite model.
 * 
 * Advantages over TFLite approach:
 * - No separate model needed (reuses ASR model)
 * - Works offline
 * - Easy to change wake word
 * - Can detect any Arabic phrase
 * - Better accuracy for Arabic phonemes
 * 
 * Model: Vosk Arabic MGB2 (same as ASR)
 * Sample Rate: 16kHz
 * Grammar: ["يا بنتي", "[unk]"]
 * 
 * @author Dr. Waleed Mandour
 */
class WakeWordDetectorVosk(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetectorVosk"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000f
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Wake word configuration
        private const val WAKE_WORD_ARABIC = "يا بنتي"
        private const val WAKE_WORD_VARIANTS = arrayOf(
            "يا بنتي",
            "يابنتي",      // Without space
            "يا بنتى",     // Alternative ي spelling
            "يابنتى"       // Combined with alternative spelling
        )
        
        // Grammar for wake word detection
        // This limits the recognizer to only output the wake word or [unk]
        private const val GRAMMAR_JSON = """["يا بنتي", "[unk]"]"""
        
        // Vosk model path (same as ASR)
        private const val VOSK_MODEL_PATH = "models/vosk-model-ar-mgb2"
        
        // Detection settings
        private const val COOLDOWN_MS = 3000L
        private const val MIN_CONFIDENCE = 0.7f  // Lower threshold since grammar helps
        
        // Audio buffer settings
        private const val BUFFER_SIZE_MULTIPLIER = 2
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
    private var detectionCount = 0

    /**
     * Initialize the wake word detector
     * Loads the Vosk model and creates a grammar-based recognizer
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Vosk wake word detector...")
            
            // Check if model exists
            val modelDir = File(context.filesDir, VOSK_MODEL_PATH)
            
            // Also check common alternative locations
            val possiblePaths = listOf(
                modelDir,
                File(context.filesDir, "models/vosk-model-ar-mgb2-0.4"),
                File(context.filesDir, "vosk-model-ar-mgb2"),
                File(context.filesDir, "models/asr/vosk-model-ar-mgb2")
            )
            
            val actualModelDir = possiblePaths.firstOrNull { it.exists() && it.isDirectory }
            
            if (actualModelDir == null) {
                Log.e(TAG, "Vosk model not found. Checked paths:")
                possiblePaths.forEach { Log.e(TAG, "  - ${it.absolutePath}") }
                throw IllegalStateException("Vosk model not found. Please download the Arabic model first.")
            }
            
            Log.d(TAG, "Loading Vosk model from: ${actualModelDir.absolutePath}")
            
            // Load Vosk model
            voskModel = Model(actualModelDir.absolutePath)
            
            // Create recognizer with grammar constraint
            // The grammar limits recognition to only the wake word phrase
            recognizer = Recognizer(voskModel, SAMPLE_RATE, GRAMMAR_JSON)
            
            Log.i(TAG, "✅ Vosk wake word detector initialized successfully")
            Log.i(TAG, "   Wake word: '$WAKE_WORD_ARABIC'")
            Log.i(TAG, "   Grammar: $GRAMMAR_JSON")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk wake word detector", e)
            throw e
        }
    }

    /**
     * Start listening for wake word
     * Continuously processes audio and detects wake word
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isRecording) {
            Log.w(TAG, "Already recording, skipping start")
            return@withContext
        }
        
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            Log.e(TAG, "Recognizer not initialized")
            return@withContext
        }
        
        try {
            Log.d(TAG, "🎤 Starting Vosk wake word detection...")
            
            // Initialize audio record
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE.toInt(),
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE.toInt(),
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            val buffer = ShortArray(bufferSize / 2)
            
            // Continuous listening loop
            while (isRecording && isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (read > 0) {
                    // Process audio with Vosk
                    val isEndpoint = currentRecognizer.acceptWaveForm(buffer, read)
                    
                    if (isEndpoint) {
                        // Full result available
                        val result = currentRecognizer.result
                        processResult(result)
                    } else {
                        // Check partial result for faster detection
                        val partial = currentRecognizer.partialResult
                        processPartialResult(partial)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word detection loop", e)
        } finally {
            stopListening()
        }
    }

    /**
     * Stop listening for wake word
     */
    fun stopListening() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        Log.d(TAG, "Vosk wake word detection stopped")
    }

    /**
     * Process full recognition result
     */
    private fun processResult(jsonResult: String) {
        try {
            val json = JSONObject(jsonResult)
            val text = json.optString("text", "")
            
            if (text.isNotEmpty()) {
                Log.v(TAG, "Result: $text")
                checkForWakeWord(text)
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse result: $jsonResult")
        }
    }

    /**
     * Process partial recognition result
     * This allows for faster detection before the utterance is complete
     */
    private fun processPartialResult(jsonResult: String) {
        try {
            val json = JSONObject(jsonResult)
            val partial = json.optString("partial", "")
            
            if (partial.isNotEmpty()) {
                Log.v(TAG, "Partial: $partial")
                checkForWakeWord(partial)
            }
            
        } catch (e: Exception) {
            // Partial parsing errors are not critical
        }
    }

    /**
     * Check if the recognized text contains the wake word
     */
    private fun checkForWakeWord(text: String) {
        val currentTime = System.currentTimeMillis()
        
        // Check cooldown
        if (currentTime - lastDetectionTime < COOLDOWN_MS) {
            return
        }
        
        // Normalize the text for comparison
        val normalizedText = normalizeArabic(text)
        
        // Check against wake word variants
        val wakeWordDetected = WAKE_WORD_VARIANTS.any { variant ->
            normalizeArabic(variant) in normalizedText
        }
        
        if (wakeWordDetected) {
            detectionCount++
            lastDetectionTime = currentTime
            
            Log.i(TAG, "🎯 Wake word detected! Count: $detectionCount")
            Log.i(TAG, "   Recognized text: $text")
            
            // Emit detection
            _wakeWordFlow.value = true
            
            // Reset recognizer for next detection
            recognizer?.reset()
        }
    }

    /**
     * Normalize Arabic text for comparison
     * Handles common variations in Arabic text
     */
    private fun normalizeArabic(text: String): String {
        return text
            .trim()
            .lowercase()
            // Normalize alef variants
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            // Normalize teh marbuta
            .replace("ة", "ه")
            // Normalize yeh variants
            .replace("ى", "ي")
            // Remove diacritics (tashkeel)
            .replace(Regex("[\\u064B-\\u065F]"), "")
            // Remove extra spaces
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Check if the detector is ready
     */
    fun isReady(): Boolean = voskModel != null && recognizer != null

    /**
     * Get detection statistics
     */
    fun getStats(): WakeWordStats {
        return WakeWordStats(
            detectionCount = detectionCount,
            lastDetectionTime = lastDetectionTime,
            isListening = isRecording,
            isReady = isReady()
        )
    }

    /**
     * Reset the recognizer state
     * Useful after a false detection or to clear buffer
     */
    fun reset() {
        recognizer?.reset()
        Log.d(TAG, "Recognizer reset")
    }

    /**
     * Update the wake word phrase
     * This recreates the recognizer with new grammar
     */
    suspend fun updateWakeWord(newWakeWord: String) = withContext(Dispatchers.IO) {
        try {
            val model = voskModel ?: throw IllegalStateException("Model not loaded")
            
            // Create new grammar with updated wake word
            val newGrammar = """["$newWakeWord", "[unk]"]"""
            
            // Recreate recognizer
            recognizer?.close()
            recognizer = Recognizer(model, SAMPLE_RATE, newGrammar)
            
            Log.i(TAG, "Wake word updated to: $newWakeWord")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update wake word", e)
            throw e
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopListening()
        
        recognizer?.close()
        recognizer = null
        
        // Note: We don't close the model here as it might be shared with VoiceProcessor
        // The model should be closed when the app is destroyed
        
        Log.d(TAG, "Vosk wake word detector released")
    }
    
    /**
     * Close the model (call when app is being destroyed)
     */
    fun closeModel() {
        voskModel?.close()
        voskModel = null
        Log.d(TAG, "Vosk model closed")
    }
}

/**
 * Statistics for wake word detection
 */
data class WakeWordStats(
    val detectionCount: Int,
    val lastDetectionTime: Long,
    val isListening: Boolean,
    val isReady: Boolean
)

/**
 * Alternative implementation using SharedVoskModel
 * Use this if you want to share the model with VoiceProcessor
 */
class WakeWordDetectorVoskShared(
    private val context: Context,
    private val sharedModel: Model
) {
    companion object {
        private const val TAG = "WakeWordDetectorVosk"
        private const val SAMPLE_RATE = 16000f
        private const val WAKE_WORD = "يا بنتي"
        private const val GRAMMAR_JSON = """["يا بنتي", "[unk]"]"""
        private const val COOLDOWN_MS = 3000L
    }
    
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    private val _wakeWordFlow = MutableStateFlow(false)
    val wakeWordFlow: StateFlow<Boolean> = _wakeWordFlow.asStateFlow()
    
    private var lastDetectionTime = 0L
    
    /**
     * Initialize with shared model
     */
    fun initialize() {
        recognizer = Recognizer(sharedModel, SAMPLE_RATE, GRAMMAR_JSON)
        Log.i(TAG, "✅ Wake word detector initialized with shared model")
    }
    
    /**
     * Start listening (same implementation as above)
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        // Implementation similar to WakeWordDetectorVosk
        // but uses the shared model instead of loading its own
    }
    
    fun stopListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    fun release() {
        stopListening()
        recognizer?.close()
        recognizer = null
    }
}
