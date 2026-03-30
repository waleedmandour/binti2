package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        private val WAKE_WORD_VARIANTS = listOf(
            "يا بنتي",
            "يابنتي",
            "يا بنتى",
            "يابنتى"
        )
        
        // Grammar for wake word detection
        private const val GRAMMAR_JSON = """["يا بنتي", "[unk]"]"""
        
        // Vosk model path
        private const val VOSK_MODEL_PATH = "models/vosk-model-ar-mgb2"
        
        // Detection settings
        private const val COOLDOWN_MS = 3000L
        
        // Audio buffer settings
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }

    // Vosk components
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    // Detection state - Using SharedFlow for events
    private val _wakeWordFlow = MutableSharedFlow<Boolean>(replay = 0)
    val wakeWordFlow: SharedFlow<Boolean> = _wakeWordFlow.asSharedFlow()
    
    private var lastDetectionTime = 0L
    private var detectionCount = 0

    /**
     * Initialize the wake word detector
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Vosk wake word detector...")
            
            val modelDir = File(context.filesDir, VOSK_MODEL_PATH)
            val possiblePaths = listOf(
                modelDir,
                File(context.filesDir, "models/vosk-model-ar-mgb2-0.4"),
                File(context.filesDir, "vosk-model-ar-mgb2"),
                File(context.filesDir, "models/asr/vosk-model-ar-mgb2")
            )
            
            val actualModelDir = possiblePaths.firstOrNull { it.exists() && it.isDirectory }
            
            if (actualModelDir == null) {
                Log.e(TAG, "Vosk model not found.")
                throw IllegalStateException("Vosk model not found. Please download the Arabic model first.")
            }
            
            voskModel = Model(actualModelDir.absolutePath)
            recognizer = Recognizer(voskModel, SAMPLE_RATE, GRAMMAR_JSON)
            
            Log.i(TAG, "✅ Vosk wake word detector initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk wake word detector", e)
            throw e
        }
    }

    /**
     * Start listening for wake word
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext
        
        val currentRecognizer = recognizer ?: return@withContext
        
        try {
            Log.d(TAG, "🎤 Starting Vosk wake word detection...")
            
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
            
            while (isRecording && isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (read > 0) {
                    val isEndpoint = currentRecognizer.acceptWaveForm(buffer, read)
                    if (isEndpoint) {
                        processResult(currentRecognizer.result)
                    } else {
                        processPartialResult(currentRecognizer.partialResult)
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
            try { stop() } catch (e: Exception) {}
            release()
        }
        audioRecord = null
    }

    private fun processResult(jsonResult: String) {
        try {
            val text = JSONObject(jsonResult).optString("text", "")
            if (text.isNotEmpty()) checkForWakeWord(text)
        } catch (e: Exception) {}
    }

    private fun processPartialResult(jsonResult: String) {
        try {
            val partial = JSONObject(jsonResult).optString("partial", "")
            if (partial.isNotEmpty()) checkForWakeWord(partial)
        } catch (e: Exception) {}
    }

    private fun checkForWakeWord(text: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < COOLDOWN_MS) return
        
        val normalizedText = normalizeArabic(text)
        val wakeWordDetected = WAKE_WORD_VARIANTS.any { variant ->
            normalizeArabic(variant) in normalizedText
        }
        
        if (wakeWordDetected) {
            detectionCount++
            lastDetectionTime = currentTime
            Log.i(TAG, "🎯 Wake word detected! Count: $detectionCount")
            
            // Emit detection event
            _wakeWordFlow.tryEmit(true)
            
            recognizer?.reset()
        }
    }

    private fun normalizeArabic(text: String): String {
        return text.trim().lowercase()
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ة", "ه").replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F]"), "")
            .replace(Regex("\\s+"), " ")
    }

    fun isReady(): Boolean = voskModel != null && recognizer != null

    fun release() {
        stopListening()
        recognizer?.close()
        recognizer = null
    }
}
