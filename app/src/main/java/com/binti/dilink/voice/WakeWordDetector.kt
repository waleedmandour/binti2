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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wake Word Detector - "يا بنتي" (Ya Binti)
 * 
 * Uses TensorFlow Lite CNN model to detect the wake word phrase.
 * Implements continuous audio monitoring with low power consumption.
 * 
 * Model: Custom trained TFLite model (5MB)
 * Sample Rate: 16kHz
 * Frame Size: 1000ms with 500ms overlap
 * 
 * @author Dr. Waleed Mandour
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer sizes
        private const val FRAME_SIZE_MS = 1000
        private const val HOP_SIZE_MS = 500
        private const val FRAME_SIZE_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000
        private const val HOP_SIZE_SAMPLES = SAMPLE_RATE * HOP_SIZE_MS / 1000
        
        // Model parameters
        private const val MODEL_PATH = "ya_binti_detector.tflite"
        private const val CONFIDENCE_THRESHOLD = 0.85f
        private const val COOLDOWN_MS = 3000L
        
        // Feature extraction parameters (matching training)
        private const val NUM_MFCC = 40
        private const val NUM_FRAMES = 98
    }

    // TFLite interpreter
    private var interpreter: Interpreter? = null
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    // Detection state
    private val _wakeWordFlow = MutableStateFlow(false)
    val wakeWordFlow: StateFlow<Boolean> = _wakeWordFlow.asStateFlow()
    
    private var lastDetectionTime = 0L
    private var consecutiveDetections = 0
    private val REQUIRED_CONSECUTIVE = 2

    /**
     * Initialize the wake word detector
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing wake word detector...")
            
            // Load TFLite model
            val modelBuffer = loadModelFile()
            
            // Configure interpreter options
            val options = Interpreter.Options().apply {
                // Use CPU with multiple threads for wake word detection
                // Note: GPU delegate API varies between TFLite versions, so we use CPU for reliability
                setNumThreads(4)
                Log.d(TAG, "Using CPU with 4 threads for wake word detection")
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            Log.i(TAG, "✅ Wake word detector initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word detector", e)
            throw e
        }
    }

    /**
     * Start listening for wake word
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isRecording) {
            Log.w(TAG, "Already recording, skipping start")
            return@withContext
        }
        
        try {
            Log.d(TAG, "🎤 Starting wake word detection...")
            
            // Initialize audio record
            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
                FRAME_SIZE_SAMPLES * 2
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also { it.startRecording() }
            
            isRecording = true
            
            // Circular buffer for audio
            val audioBuffer = ShortArray(FRAME_SIZE_SAMPLES + HOP_SIZE_SAMPLES)
            var bufferPosition = 0
            
            while (isRecording && isActive) {
                // Read audio samples
                val samplesRead = audioRecord?.read(
                    audioBuffer, bufferPosition, HOP_SIZE_SAMPLES
                ) ?: 0
                
                if (samplesRead > 0) {
                    bufferPosition += samplesRead
                    
                    // Process when we have enough samples
                    if (bufferPosition >= FRAME_SIZE_SAMPLES) {
                        val frame = audioBuffer.copyOf(FRAME_SIZE_SAMPLES)
                        
                        // Detect wake word
                        val confidence = detectWakeWord(frame)
                        
                        // Handle detection with cooldown
                        handleDetection(confidence)
                        
                        // Shift buffer by hop size
                        System.arraycopy(
                            audioBuffer, HOP_SIZE_SAMPLES,
                            audioBuffer, 0, bufferPosition - HOP_SIZE_SAMPLES
                        )
                        bufferPosition -= HOP_SIZE_SAMPLES
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
        Log.d(TAG, "Wake word detection stopped")
    }

    /**
     * Detect wake word in audio frame
     */
    private fun detectWakeWord(audioFrame: ShortArray): Float {
        // Extract MFCC features from audio
        val features = extractMFCC(audioFrame)
        
        // Run TFLite inference
        val inputBuffer = features.clone()
        val outputBuffer = Array(1) { FloatArray(2) } // [not_wake_word, wake_word]
        
        interpreter?.run(inputBuffer, outputBuffer)
        
        // Return wake word confidence
        return outputBuffer[0][1]
    }

    /**
     * Extract MFCC features from audio frame
     * Simplified implementation - in production use TFLite Support Library
     */
    private fun extractMFCC(audioFrame: ShortArray): Array<Array<FloatArray>> {
        // Convert short array to float
        val floatAudio = FloatArray(audioFrame.size) { audioFrame[it].toFloat() / Short.MAX_VALUE }
        
        // Pre-emphasis
        val preEmphasis = FloatArray(floatAudio.size)
        preEmphasis[0] = floatAudio[0]
        for (i in 1 until floatAudio.size) {
            preEmphasis[i] = floatAudio[i] - 0.97f * floatAudio[i - 1]
        }
        
        // Frame the signal (25ms frames with 10ms hop)
        val frameLength = (0.025 * SAMPLE_RATE).toInt()
        val hopLength = (0.010 * SAMPLE_RATE).toInt()
        val numFrames = (floatAudio.size - frameLength) / hopLength + 1
        
        // Apply Hamming window and compute FFT-based MFCC
        // Simplified: return normalized features
        val features = Array(1) { Array(NUM_FRAMES) { FloatArray(NUM_MFCC) } }
        
        for (i in 0 until minOf(numFrames, NUM_FRAMES)) {
            val start = i * hopLength
            val frame = FloatArray(frameLength) { j ->
                if (start + j < preEmphasis.size) {
                    val hamming = 0.54f - 0.46f * kotlin.math.cos(
                        2.0 * kotlin.math.PI * j / (frameLength - 1)
                    ).toFloat()
                    preEmphasis[start + j] * hamming
                } else 0f
            }
            
            // Compute MFCC for this frame
            val mfcc = computeMFCC(frame)
            features[0][i] = mfcc
        }
        
        return features
    }

    /**
     * Compute MFCC coefficients for a frame
     */
    private fun computeMFCC(frame: FloatArray): FloatArray {
        // Simplified MFCC computation
        // In production, use TFLite AudioProcessor or similar
        val mfcc = FloatArray(NUM_MFCC)
        
        // FFT magnitude (simplified)
        val fftSize = frame.size
        for (i in mfcc.indices) {
            var sum = 0f
            val binStart = i * fftSize / (NUM_MFCC * 2)
            val binEnd = (i + 1) * fftSize / (NUM_MFCC * 2)
            
            for (j in binStart until minOf(binEnd, fftSize / 2)) {
                val real = frame.getOrElse(j) { 0f }
                val imag = frame.getOrElse(j + fftSize / 2) { 0f }
                sum += kotlin.math.sqrt(real * real + imag * imag)
            }
            
            // Apply mel filter bank weights (simplified)
            mfcc[i] = sum / (binEnd - binStart + 1)
        }
        
        // Apply DCT for final MFCC
        return mfcc.mapIndexed { i, v ->
            var dctSum = 0f
            mfcc.forEachIndexed { j, mj ->
                dctSum += mj * kotlin.math.cos(
                    kotlin.math.PI * i * (j + 0.5) / NUM_MFCC
                ).toFloat()
            }
            dctSum
        }.toFloatArray()
    }

    /**
     * Handle wake word detection with cooldown
     */
    private fun handleDetection(confidence: Float) {
        val currentTime = System.currentTimeMillis()
        
        if (confidence >= CONFIDENCE_THRESHOLD) {
            consecutiveDetections++
            
            if (consecutiveDetections >= REQUIRED_CONSECUTIVE) {
                if (currentTime - lastDetectionTime >= COOLDOWN_MS) {
                    Log.i(TAG, "🎯 Wake word detected! Confidence: $confidence")
                    _wakeWordFlow.value = true
                    lastDetectionTime = currentTime
                } else {
                    Log.d(TAG, "Detection in cooldown, ignoring")
                }
                consecutiveDetections = 0
            }
        } else {
            consecutiveDetections = 0
        }
    }

    /**
     * Load TFLite model from assets
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("models/wake/$MODEL_PATH")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Release resources
     */
    fun release() {
        stopListening()
        interpreter?.close()
        interpreter = null
        
        Log.d(TAG, "Wake word detector released")
    }
}
