package com.binti.dilink.nlp

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import timber.log.Timber
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * IntentClassifier - Classifies user intents from Egyptian Arabic text
 * 
 * Uses quantized EgyBERT-tiny (INT8, ~80MB) for:
 * - Intent classification
 * - Slot/entity extraction
 * - Confidence scoring
 * 
 * Supported Intents:
 * - NAVIGATE: Navigation commands
 * - CLIMATE_ON/OFF/TEMP: Climate control
 * - MUSIC_PLAY/PAUSE/NEXT/PREV: Media control
 * - VOLUME_UP/DOWN: Volume control
 * - CALL: Phone calls
 * - WEATHER: Weather information
 * - UNKNOWN: Fallback intent
 */
class IntentClassifier(private val context: Context) {

    companion object {
        private const val MODEL_PATH = "models/nlu/egybert_tiny_int8.tflite"
        private const val LABELS_PATH = "models/nlu/intent_labels.txt"
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val MAX_SEQ_LENGTH = 128
    }

    // TFLite interpreter
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    // Intent labels
    private val intentLabels = mutableListOf<String>()
    
    // Normalizer
    private val normalizer = EgyptianArabicNormalizer()
    
    // Vocabulary for tokenization
    private lateinit var vocab: Map<String, Int>

    /**
     * Data class for intent classification result
     */
    data class ClassificationResult(
        val intent: String,
        val confidence: Float,
        val slots: Map<String, String> = emptyMap()
    )

    /**
     * Load model and labels
     */
    fun loadModel(): Boolean {
        return try {
            // Load TFLite model
            val modelBuffer = loadModelFile()
            
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors())
                setUseNNAPI(true)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            
            // Load intent labels
            loadLabels()
            
            // Load vocabulary
            loadVocabulary()
            
            modelLoaded = true
            Timber.i("Intent classifier model loaded successfully")
            Timber.d("Intent labels: $intentLabels")
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to load intent classifier model")
            modelLoaded = false
            false
        }
    }

    /**
     * Classify text to intent
     */
    fun classify(text: String): Intent {
        if (!modelLoaded || text.isBlank()) {
            return Intent.UNKNOWN
        }
        
        return try {
            // Normalize text
            val normalized = normalizer.normalize(text)
            
            // Run classification
            val result = runClassification(normalized)
            
            // Extract slots/entities
            val slots = extractSlots(normalized, result.intent)
            
            // Build intent with parameters
            buildIntent(result, slots, text)
            
        } catch (e: Exception) {
            Timber.e(e, "Intent classification failed")
            Intent.UNKNOWN
        }
    }

    /**
     * Run TFLite classification
     */
    private fun runClassification(text: String): ClassificationResult {
        val interp = interpreter ?: return ClassificationResult("UNKNOWN", 0f)
        
        // Tokenize text
        val tokens = tokenize(text)
        
        // Prepare input tensors
        val inputIds = LongArray(MAX_SEQ_LENGTH) { i ->
            tokens.getOrElse(i) { 0L }
        }
        val attentionMask = LongArray(MAX_SEQ_LENGTH) { i ->
            if (i < tokens.size) 1L else 0L
        }
        
        // Create input array
        val inputArray = arrayOf(inputIds, attentionMask)
        
        // Output array for classification
        val output = Array(1) { FloatArray(intentLabels.size) }
        
        // Run inference
        interp.runForMultipleInputsOutputs(inputArray, mapOf(0 to output))
        
        // Get predicted class
        val scores = output[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val confidence = scores[maxIndex]
        val intent = intentLabels.getOrElse(maxIndex) { "UNKNOWN" }
        
        Timber.d("Classification: '$text' -> $intent ($confidence)")
        
        return ClassificationResult(intent, confidence)
    }

    /**
     * Simple whitespace tokenization with vocabulary lookup
     */
    private fun tokenize(text: String): LongArray {
        // Simple character-level tokenization for Arabic
        // In production, use proper BERT tokenizer
        
        val chars = text.toCharArray()
        val tokens = mutableListOf<Long>()
        
        // Add CLS token
        tokens.add(101L) // [CLS] token ID
        
        // Add character tokens
        for (char in chars.take(MAX_SEQ_LENGTH - 2)) {
            val tokenId = vocab[char.toString()] ?: (char.code.toLong() + 1000)
            tokens.add(tokenId)
        }
        
        // Add SEP token
        tokens.add(102L) // [SEP] token ID
        
        return tokens.toLongArray()
    }

    /**
     * Extract named entities / slots from text
     */
    private fun extractSlots(text: String, intent: String): Map<String, String> {
        val slots = mutableMapOf<String, String>()
        
        when (intent) {
            "NAVIGATE" -> {
                // Extract destination
                val location = normalizer.extractEntity(text, "NAVIGATE")
                if (location != null) {
                    slots["destination"] = location
                }
                
                // Check for home/work shortcuts
                when {
                    text.contains("البيت") || text.contains("المنزل") -> {
                        slots["destination_type"] = "home"
                    }
                    text.contains("الشغل") || text.contains("العمل") -> {
                        slots["destination_type"] = "work"
                    }
                }
            }
            
            "CLIMATE_TEMP" -> {
                // Extract temperature
                val temp = normalizer.extractNumber(text)
                if (temp != null && temp in 16..30) {
                    slots["temperature"] = temp.toString()
                }
            }
            
            "CLIMATE_ON", "CLIMATE_OFF" -> {
                // Climate on/off doesn't need slots
            }
            
            "MUSIC_PLAY" -> {
                // Extract song/artist
                val entity = normalizer.extractEntity(text, "MUSIC")
                if (entity != null) {
                    slots["query"] = entity
                }
            }
            
            "VOLUME" -> {
                // Extract level or direction
                when {
                    text.contains("كثير") || text.contains("أعلى") || text.contains("ارفع") -> {
                        slots["direction"] = "up"
                    }
                    text.contains("قليل") || text.contains("أخفض") || text.contains("اخفض") -> {
                        slots["direction"] = "down"
                    }
                }
                
                val level = normalizer.extractNumber(text)
                if (level != null) {
                    slots["level"] = level.toString()
                }
            }
        }
        
        return slots
    }

    /**
     * Build Intent object from classification result
     */
    private fun buildIntent(result: ClassificationResult, slots: Map<String, String>, originalText: String): Intent {
        return when {
            result.confidence < CONFIDENCE_THRESHOLD -> Intent.UNKNOWN
            
            result.intent == "NAVIGATE" -> Intent(
                action = "NAVIGATE",
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
            
            result.intent == "CLIMATE_ON" -> Intent(
                action = "CLIMATE_ON",
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
            
            result.intent == "CLIMATE_OFF" -> Intent(
                action = "CLIMATE_OFF",
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
            
            result.intent == "CLIMATE_TEMP" -> Intent(
                action = "CLIMATE_TEMP",
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
            
            result.intent == "MUSIC_PLAY" -> Intent(
                action = "MUSIC_PLAY",
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
            
            result.intent == "MUSIC_PAUSE" -> Intent(
                action = "MUSIC_PAUSE",
                confidence = result.confidence,
                originalText = originalText
            )
            
            result.intent == "MUSIC_NEXT" -> Intent(
                action = "MUSIC_NEXT",
                confidence = result.confidence,
                originalText = originalText
            )
            
            result.intent == "MUSIC_PREV" -> Intent(
                action = "MUSIC_PREV",
                confidence = result.confidence,
                originalText = originalText
            )
            
            result.intent == "VOLUME_UP" -> Intent(
                action = "VOLUME_UP",
                confidence = result.confidence,
                originalText = originalText
            )
            
            result.intent == "VOLUME_DOWN" -> Intent(
                action = "VOLUME_DOWN",
                confidence = result.confidence,
                originalText = originalText
            )
            
            else -> Intent(
                action = result.intent,
                confidence = result.confidence,
                parameters = slots,
                originalText = originalText
            )
        }
    }

    /**
     * Load intent labels from file
     */
    private fun loadLabels() {
        try {
            val labels = context.assets.open(LABELS_PATH).bufferedReader().readLines()
            intentLabels.clear()
            intentLabels.addAll(labels)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load labels, using defaults")
            // Default intent labels
            intentLabels.addAll(listOf(
                "NAVIGATE",
                "CLIMATE_ON",
                "CLIMATE_OFF", 
                "CLIMATE_TEMP",
                "MUSIC_PLAY",
                "MUSIC_PAUSE",
                "MUSIC_NEXT",
                "MUSIC_PREV",
                "VOLUME_UP",
                "VOLUME_DOWN",
                "CALL",
                "WEATHER",
                "UNKNOWN"
            ))
        }
    }

    /**
     * Load vocabulary for tokenization
     */
    private fun loadVocabulary() {
        vocab = mutableMapOf()
        try {
            val vocabFile = context.assets.open("models/nlu/vocab.txt")
            vocabFile.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val token = line.trim()
                    (vocab as MutableMap)[token] = index
                }
            }
            Timber.d("Loaded ${vocab.size} vocabulary tokens")
        } catch (e: Exception) {
            Timber.w(e, "Failed to load vocabulary")
        }
    }

    /**
     * Load TFLite model file
     */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Release model resources
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
        Timber.i("Intent classifier model released")
    }

    /**
     * Check if model is loaded
     */
    fun isModelLoaded(): Boolean = modelLoaded
}

/**
 * Intent data class representing a classified user intent
 */
data class Intent(
    val action: String,
    val confidence: Float = 0f,
    val parameters: Map<String, String> = emptyMap(),
    val originalText: String = ""
) {
    companion object {
        val UNKNOWN = Intent(action = "UNKNOWN", confidence = 0f)
        
        // Convenience constructors
        fun navigate(destination: String, confidence: Float = 1f) = Intent(
            action = "NAVIGATE",
            confidence = confidence,
            parameters = mapOf("destination" to destination)
        )
        
        fun climateOn(confidence: Float = 1f) = Intent(
            action = "CLIMATE_ON",
            confidence = confidence
        )
        
        fun climateOff(confidence: Float = 1f) = Intent(
            action = "CLIMATE_OFF",
            confidence = confidence
        )
        
        fun climateTemp(temperature: Int, confidence: Float = 1f) = Intent(
            action = "CLIMATE_TEMP",
            confidence = confidence,
            parameters = mapOf("temperature" to temperature.toString())
        )
        
        fun musicPlay(query: String? = null, confidence: Float = 1f) = Intent(
            action = "MUSIC_PLAY",
            confidence = confidence,
            parameters = if (query != null) mapOf("query" to query) else emptyMap()
        )
        
        fun volumeUp(confidence: Float = 1f) = Intent(
            action = "VOLUME_UP",
            confidence = confidence
        )
        
        fun volumeDown(confidence: Float = 1f) = Intent(
            action = "VOLUME_DOWN",
            confidence = confidence
        )
    }
    
    override fun toString(): String {
        return "Intent(action='$action', confidence=$confidence, parameters=$parameters)"
    }
}
