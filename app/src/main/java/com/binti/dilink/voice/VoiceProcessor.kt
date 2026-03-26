package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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
 * - Vosk offline model for Egyptian Arabic
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
        private const val VOSK_MODEL_PATH = "vosk-model-ar-mgb2"
        
        // Recognition settings
        private const val MAX_RECORDING_TIME_MS = 15000L
        private const val SILENCE_THRESHOLD = 500 // ms of silence to stop
    }

    // Vosk components
    private var voskModel: Model? = null
    
    // State
    private var isInitialized = false

    /**
     * Initialize voice processor
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing voice processor...")
            
            initializeVosk()
            
            isInitialized = true
            Log.i(TAG, "✅ Voice processor initialized")
            
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
                Log.w(TAG, "Vosk model not found at ${modelDir.absolutePath}")
                throw IOException("Vosk model not found. Please download the model first.")
            }
            
            // Load Vosk model
            voskModel = Model(modelDir.absolutePath)
            Log.d(TAG, "Vosk model loaded from ${modelDir.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Vosk initialization failed: ${e.message}")
            throw e
        }
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
            transcribeWithVosk()
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
        
        isInitialized = false
        Log.d(TAG, "Voice processor released")
    }
}
