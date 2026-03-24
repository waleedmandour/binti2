package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.binti.dilink.utils.HMSUtils
import com.huawei.hms.mlsdk.asr.MLAsrConstants
import com.huawei.hms.mlsdk.asr.MLAsrListener
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Voice Processor - ASR (Automatic Speech Recognition)
 * 
 * Handles speech-to-text conversion using:
 * - Primary: Vosk offline model for Egyptian Arabic
 * - Fallback: Huawei ML Kit ASR for cloud-based recognition
 * 
 * Model: Vosk Egyptian Arabic (50MB)
 * Sample Rate: 16kHz
 * 
 * @author Dr. Waleed Mandour
 */
class VoiceProcessor(private val context: Context) {

    companion object {
        private const val TAG = "VoiceProcessor"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000f
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Vosk model path
        private const val VOSK_MODEL_PATH = "vosk-model-egyptian"
        
        // Recognition settings
        private const val MAX_RECORDING_TIME_MS = 15000L
        private const val SILENCE_THRESHOLD = 500 // ms of silence to stop
    }

    // Vosk components
    private var voskModel: Model? = null
    
    // Huawei ML Kit ASR (fallback)
    private var huaweiAsrRecognizer: MLAsrRecognizer? = null
    
    // State
    private var isInitialized = false
    private var useCloudFallback = false

    /**
     * Initialize voice processor
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing voice processor...")
            
            // Check if we should use cloud-only mode
            useCloudFallback = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
                .getBoolean("cloud_only_mode", false)
            
            if (useCloudFallback) {
                initializeHuaweiASR()
            } else {
                initializeVosk()
            }
            
            isInitialized = true
            Log.i(TAG, "✅ Voice processor initialized (cloud=$useCloudFallback)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice processor", e)
            throw e
        }
    }

    /**
     * Initialize Vosk offline ASR
     */
    private fun initializeVosk() {
        try {
            // Check if model exists
            val modelDir = File(context.filesDir, "models/$VOSK_MODEL_PATH")
            
            if (!modelDir.exists()) {
                Log.w(TAG, "Vosk model not found, will use cloud fallback")
                useCloudFallback = true
                initializeHuaweiASR()
                return
            }
            
            // Load Vosk model
            voskModel = Model(modelDir.absolutePath)
            Log.d(TAG, "Vosk model loaded from ${modelDir.absolutePath}")
            
        } catch (e: Exception) {
            Log.w(TAG, "Vosk initialization failed, falling back to cloud ASR: ${e.message}")
            useCloudFallback = true
            initializeHuaweiASR()
        }
    }

    /**
     * Initialize Huawei ML Kit ASR
     */
    private fun initializeHuaweiASR() {
        if (!HMSUtils.isHuaweiDevice() || !HMSUtils.isHuaweiServicesAvailable(context)) {
            Log.e(TAG, "Huawei services not available for ASR fallback")
            throw IOException("No ASR backend available")
        }
        
        huaweiAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context)
        Log.d(TAG, "Huawei ML Kit ASR initialized")
    }

    /**
     * Listen and transcribe speech
     * 
     * @param timeoutMs Maximum recording time in milliseconds
     * @return Transcribed text in Egyptian Arabic
     */
    suspend fun listenAndTranscribe(timeoutMs: Long = MAX_RECORDING_TIME_MS): String {
        if (!isInitialized) {
            throw IllegalStateException("Voice processor not initialized")
        }
        
        return withTimeout(timeoutMs) {
            if (useCloudFallback) {
                transcribeWithHuawei()
            } else {
                transcribeWithVosk()
            }
        }
    }

    /**
     * Transcribe using Vosk offline model
     */
    private suspend fun transcribeWithVosk(): String = suspendCancellableCoroutine { continuation ->
        val model = voskModel ?: run {
            continuation.resumeWithException(IOException("Vosk model not loaded"))
            return@suspendCancellableCoroutine
        }
        
        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE.toInt(), CHANNEL_CONFIG, AUDIO_FORMAT
            )
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE.toInt(),
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            val buffer = ShortArray(bufferSize)
            val results = StringBuilder()
            var silenceCount = 0
            var hasSpeech = false
            
            audioRecord.startRecording()
            Log.d(TAG, "🎤 Vosk recording started")
            
            // Recording loop
            while (true) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                
                if (read > 0) {
                    val isEndpoint = recognizer.acceptWaveForm(buffer, read)
                    
                    if (isEndpoint) {
                        val result = recognizer.result
                        val text = parseVoskResult(result)
                        if (text.isNotEmpty()) {
                            results.append(text).append(" ")
                            hasSpeech = true
                        }
                    } else {
                        val partial = recognizer.partialResult
                        val partialText = parseVoskPartialResult(partial)
                        
                        // Log partial for debugging
                        if (partialText.isNotEmpty()) {
                            Log.v(TAG, "Partial: $partialText")
                            hasSpeech = true
                            silenceCount = 0
                        } else if (hasSpeech) {
                            silenceCount++
                            if (silenceCount > 20) { // ~1.5 seconds of silence
                                Log.d(TAG, "Silence detected, stopping")
                                break
                            }
                        }
                    }
                }
                
                // Check for cancellation
                if (continuation.isCancelled) {
                    break
                }
            }
            
            // Get final result
            val finalResult = recognizer.finalResult
            val finalText = parseVoskResult(finalResult)
            results.append(finalText)
            
            // Cleanup
            audioRecord.stop()
            audioRecord.release()
            recognizer.close()
            
            val transcription = results.toString().trim()
            Log.i(TAG, "📝 Vosk transcription: $transcription")
            
            continuation.resume(transcription)
            
        } catch (e: Exception) {
            Log.e(TAG, "Vosk transcription error", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Transcribe using Huawei ML Kit ASR
     */
    private suspend fun transcribeWithHuawei(): String = suspendCancellableCoroutine { continuation ->
        val recognizer = huaweiAsrRecognizer ?: run {
            continuation.resumeWithException(IOException("Huawei ASR not initialized"))
            return@suspendCancellableCoroutine
        }
        
        val listener = object : MLAsrListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getString(MLAsrConstants.ASR_RESULT) ?: ""
                Log.i(TAG, "📝 Huawei ASR result: $text")
                continuation.resume(text)
            }
            
            override fun onRecognizingResults(partialResults: Bundle?) {
                val partialText = partialResults?.getString(MLAsrConstants.ASR_RESULT) ?: ""
                Log.v(TAG, "Huawei partial: $partialText")
            }
            
            override fun onError(errorCode: Int, errorMessage: String?) {
                Log.e(TAG, "Huawei ASR error: $errorCode - $errorMessage")
                continuation.resumeWithException(IOException("ASR error: $errorMessage"))
            }
            
            override fun onStartListening() {
                Log.d(TAG, "Huawei ASR started listening")
            }
            
            override fun onStartingOfSpeech() {
                Log.d(TAG, "Huawei ASR speech started")
            }
            
            override fun onVoiceDataReceived(data: ByteArray?, energy: Float, params: Bundle?) {
                // Voice data received
            }
            
            override fun onState(state: Int, params: Bundle?) {
                Log.v(TAG, "Huawei ASR state: $state")
            }
        }
        
        recognizer.setAsrListener(listener)
        
        // Start recognition with Arabic language
        val intent = android.content.Intent().apply {
            putExtra(MLAsrConstants.LANGUAGE, "ar-EG") // Egyptian Arabic
            putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_WORDFLUX)
        }
        
        recognizer.startRecognizing(intent)
        
        continuation.invokeOnCancellation {
            recognizer.destroy()
        }
    }

    /**
     * Parse Vosk JSON result to extract text
     */
    private fun parseVoskResult(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.optString("text", "")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk result: $json")
            ""
        }
    }

    /**
     * Parse Vosk partial result
     */
    private fun parseVoskPartialResult(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Check if voice processor is ready
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Release resources
     */
    fun release() {
        voskModel?.close()
        voskModel = null
        
        huaweiAsrRecognizer?.destroy()
        huaweiAsrRecognizer = null
        
        isInitialized = false
        Log.d(TAG, "Voice processor released")
    }
}
