package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.binti.dilink.nlp.EgyptianArabicNormalizer
import com.binti.dilink.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * VoiceProcessor - Handles ASR and audio preprocessing for Egyptian Arabic
 * 
 * Components:
 * - Audio preprocessing (noise suppression, normalization)
 * - ASR using quantized HuBERT-Egyptian (ONNX)
 * - Voice Activity Detection (VAD)
 * - Egyptian Arabic text normalization
 * 
 * Model: Quantized HuBERT-Egyptian INT8 (~150MB compressed)
 */
class VoiceProcessor(private val context: Context) {

    companion object {
        private const val MODEL_PATH = "models/asr/hubert_egyptian_int8.onnx"
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECORDING_DURATION_MS = 10000
        private const val VAD_ENERGY_THRESHOLD = 500.0
        private const val SILENCE_DURATION_MS = 1500 // Stop after 1.5s silence
    }

    // ONNX Runtime
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var modelLoaded = false
    
    // Audio processing
    private val normalizer = EgyptianArabicNormalizer()
    
    // Tokenizer vocabulary (Arabic tokens)
    private lateinit var vocab: Map<String, Int>
    private lateinit var idToToken: Map<Int, String>

    /**
     * Initialize ASR model
     */
    fun initialize(): Boolean {
        return try {
            // Initialize ONNX Runtime
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Load model from assets or internal storage
            val modelBytes = loadModelBytes()
            
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
                setGraphOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            
            // Load vocabulary
            loadVocabulary()
            
            modelLoaded = true
            Timber.i("ASR model loaded successfully")
            Timber.d("Model inputs: ${ortSession?.inputNames}")
            Timber.d("Model outputs: ${ortSession?.outputNames}")
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to load ASR model")
            modelLoaded = false
            false
        }
    }

    /**
     * Record voice command with VAD
     */
    suspend fun recordCommand(maxDurationMs: Int = MAX_RECORDING_DURATION_MS): ShortArray {
        return withContext(Dispatchers.IO) {
            val audioChunks = mutableListOf<ShortArray>()
            var totalSamples = 0
            var silenceStart: Long? = null
            val startTime = System.currentTimeMillis()
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            
            audioRecord.startRecording()
            Timber.d("Started recording command")
            
            val buffer = ShortArray(bufferSize / 2)
            
            while (System.currentTimeMillis() - startTime < maxDurationMs) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                
                if (read > 0) {
                    // Check for voice activity
                    val energy = calculateEnergy(buffer, read)
                    
                    if (energy > VAD_ENERGY_THRESHOLD) {
                        // Voice detected
                        audioChunks.add(buffer.copyOf(read))
                        totalSamples += read
                        silenceStart = null
                    } else {
                        // Silence
                        if (silenceStart == null) {
                            silenceStart = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStart > SILENCE_DURATION_MS) {
                            // Stop recording after prolonged silence
                            Timber.d("Silence detected, stopping recording")
                            break
                        }
                        
                        // Include some silence for natural ending
                        if (audioChunks.isNotEmpty()) {
                            audioChunks.add(buffer.copyOf(read))
                            totalSamples += read
                        }
                    }
                }
            }
            
            audioRecord.stop()
            audioRecord.release()
            
            // Combine all chunks
            val result = ShortArray(totalSamples)
            var offset = 0
            for (chunk in audioChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.size)
                offset += chunk.size
            }
            
            Timber.d("Recorded ${result.size} samples (${result.size * 1000 / SAMPLE_RATE}ms)")
            result
        }
    }

    /**
     * Transcribe audio to Egyptian Arabic text
     */
    suspend fun transcribe(audioData: ShortArray): String {
        if (!modelLoaded) {
            Timber.w("ASR model not loaded")
            return ""
        }
        
        return withContext(Dispatchers.Default) {
            try {
                // Preprocess audio
                val features = extractFeatures(audioData)
                
                // Run ONNX inference
                val transcription = runInference(features)
                
                // Normalize Egyptian Arabic text
                val normalized = normalizer.normalize(transcription)
                
                Timber.i("Transcription: '$transcription' -> '$normalized'")
                normalized
                
            } catch (e: Exception) {
                Timber.e(e, "Transcription failed")
                ""
            }
        }
    }

    /**
     * Extract features from audio (log-mel spectrogram for HuBERT)
     */
    private fun extractFeatures(audioData: ShortArray): FloatArray {
        // Convert to float
        val floatAudio = FloatArray(audioData.size) { i ->
            audioData[i].toFloat() / Short.MAX_VALUE
        }
        
        // Normalize audio
        val maxVal = floatAudio.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val normalized = FloatArray(floatAudio.size) { i ->
            floatAudio[i] / maxVal
        }
        
        // Apply noise reduction (spectral subtraction)
        val denoised = applyNoiseReduction(normalized)
        
        // Extract log-mel features (simplified)
        // HuBERT expects: [batch, time, features]
        return extractLogMelSpectrogram(denoised)
    }

    /**
     * Simple spectral subtraction noise reduction
     */
    private fun applyNoiseReduction(audio: FloatArray): FloatArray {
        // Estimate noise from first 100ms
        val noiseWindow = minOf(1600, audio.size / 10) // 100ms at 16kHz
        var noiseFloor = 0f
        for (i in 0 until noiseWindow) {
            noiseFloor += kotlin.math.abs(audio[i])
        }
        noiseFloor /= noiseWindow
        
        // Apply noise gate
        return FloatArray(audio.size) { i ->
            val sample = audio[i]
            val magnitude = kotlin.math.abs(sample)
            if (magnitude < noiseFloor * 2) {
                sample * (magnitude / (noiseFloor * 2)) // Soft gating
            } else {
                sample
            }
        }
    }

    /**
     * Extract log-mel spectrogram features
     */
    private fun extractLogMelSpectrogram(audio: FloatArray): FloatArray {
        // Frame size: 25ms, Hop: 10ms
        val frameSize = (SAMPLE_RATE * 0.025).toInt() // 400 samples
        val hopSize = (SAMPLE_RATE * 0.010).toInt()  // 160 samples
        val numFrames = (audio.size - frameSize) / hopSize + 1
        val numMelBins = 80 // HuBERT uses 80 mel bins
        
        val features = FloatArray(numFrames * numMelBins)
        
        for (frameIdx in 0 until numFrames) {
            val start = frameIdx * hopSize
            val frame = audio.copyOfRange(start, minOf(start + frameSize, audio.size))
            
            // Apply window
            val windowed = applyHannWindow(frame)
            
            // Compute FFT magnitude
            val spectrum = computeFFTMagnitude(windowed)
            
            // Apply mel filterbank
            val melEnergies = applyMelFilterbank(spectrum, numMelBins)
            
            // Log scale
            for (i in 0 until numMelBins) {
                features[frameIdx * numMelBins + i] = kotlin.math.log(melEnergies[i] + 1e-10f)
            }
        }
        
        return features
    }

    /**
     * Apply Hann window
     */
    private fun applyHannWindow(frame: FloatArray): FloatArray {
        return FloatArray(frame.size) { i ->
            val hann = 0.5f * (1 - kotlin.math.cos(2 * kotlin.math.PI * i / (frame.size - 1)))
            frame[i] * hann
        }
    }

    /**
     * Compute FFT magnitude spectrum
     */
    private fun computeFFTMagnitude(frame: FloatArray): FloatArray {
        val n = frame.size
        val magnitude = FloatArray(n / 2 + 1)
        
        // Simplified DFT (production would use proper FFT)
        for (k in 0..n / 2) {
            var realSum = 0.0
            var imagSum = 0.0
            for (j in 0 until n) {
                val angle = -2 * kotlin.math.PI * k * j / n
                realSum += frame[j] * kotlin.math.cos(angle)
                imagSum += frame[j] * kotlin.math.sin(angle)
            }
            magnitude[k] = kotlin.math.sqrt(realSum * realSum + imagSum * imagSum).toFloat()
        }
        
        return magnitude
    }

    /**
     * Apply mel filterbank to spectrum
     */
    private fun applyMelFilterbank(spectrum: FloatArray, numBins: Int): FloatArray {
        val melEnergies = FloatArray(numBins)
        val fftSize = (spectrum.size - 1) * 2
        
        // Create mel filterbank
        val lowMel = hzToMel(0f)
        val highMel = hzToMel(SAMPLE_RATE / 2f)
        
        val melPoints = FloatArray(numBins + 2) { i ->
            lowMel + (highMel - lowMel) * i / (numBins + 1)
        }
        
        val freqPoints = melPoints.map { melToHz(it) }
        val binPoints = freqPoints.map { (it * fftSize / SAMPLE_RATE).toInt() }
        
        for (i in 0 until numBins) {
            var sum = 0f
            for (j in binPoints[i] until binPoints[i + 1]) {
                if (j < spectrum.size) {
                    sum += spectrum[j] * (j - binPoints[i]).toFloat() / (binPoints[i + 1] - binPoints[i])
                }
            }
            for (j in binPoints[i + 1] until binPoints[i + 2]) {
                if (j < spectrum.size) {
                    sum += spectrum[j] * (binPoints[i + 2] - j).toFloat() / (binPoints[i + 2] - binPoints[i + 1])
                }
            }
            melEnergies[i] = sum
        }
        
        return melEnergies
    }

    private fun hzToMel(hz: Float): Float = 2595f * kotlin.math.ln(1f + hz / 700f) / kotlin.math.ln(10f)
    private fun melToHz(mel: Float): Float = 700f * (kotlin.math.exp(mel * kotlin.math.ln(10f) / 2595f) - 1f)

    /**
     * Run ONNX inference for transcription
     */
    private fun runInference(features: FloatArray): String {
        val session = ortSession ?: return ""
        val env = ortEnvironment ?: return ""
        
        try {
            // Prepare input tensor
            val numFrames = features.size / 80
            val inputShape = longArrayOf(1, numFrames.toLong(), 80)
            val inputBuffer = FloatBuffer.wrap(features)
            val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)
            
            // Run inference
            val inputs = mapOf("input" to inputTensor)
            val outputs = session.run(inputs)
            
            // Get output tokens
            val outputTensor = outputs.get(0)
            val outputIds = outputTensor.value as Array<LongArray>
            
            // Decode tokens to text
            val transcription = decodeTokens(outputIds[0])
            
            // Clean up
            inputTensor.close()
            outputs.close()
            
            return transcription
            
        } catch (e: Exception) {
            Timber.e(e, "ONNX inference failed")
            return ""
        }
    }

    /**
     * Decode token IDs to Arabic text
     */
    private fun decodeTokens(tokenIds: LongArray): String {
        val sb = StringBuilder()
        
        for (tokenId in tokenIds) {
            val token = idToToken[tokenId.toInt()]
            if (token != null && token != "<pad>" && token != "<eos>" && token != "<s>") {
                sb.append(token)
            }
        }
        
        return sb.toString()
            .replace("▁", " ") // SentencePiece space token
            .trim()
    }

    /**
     * Load vocabulary from assets
     */
    private fun loadVocabulary() {
        vocab = mutableMapOf()
        idToToken = mutableMapOf()
        
        try {
            val vocabStream = context.assets.open("models/asr/vocab.txt")
            vocabStream.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val token = line.trim()
                    (vocab as MutableMap)[token] = index
                    (idToToken as MutableMap)[index] = token
                }
            }
            Timber.d("Loaded ${vocab.size} vocabulary tokens")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load vocabulary")
        }
    }

    /**
     * Load model bytes from assets or internal storage
     */
    private fun loadModelBytes(): ByteArray {
        // Try internal storage first (downloaded model)
        val internalModel = File(context.filesDir, MODEL_PATH)
        if (internalModel.exists()) {
            Timber.d("Loading ASR model from internal storage")
            return internalModel.readBytes()
        }
        
        // Fall back to assets (bundled model)
        Timber.d("Loading ASR model from assets")
        return context.assets.open(MODEL_PATH).readBytes()
    }

    /**
     * Calculate audio energy for VAD
     */
    private fun calculateEnergy(audio: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += audio[i].toDouble() * audio[i].toDouble()
        }
        return sum / length
    }

    /**
     * Release resources
     */
    fun release() {
        ortSession?.close()
        ortSession = null
        modelLoaded = false
        Timber.i("ASR model released")
    }

    /**
     * Check if model is loaded
     */
    fun isModelLoaded(): Boolean = modelLoaded
}
