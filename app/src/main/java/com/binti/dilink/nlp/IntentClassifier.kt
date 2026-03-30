package com.binti.dilink.nlp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Intent Classifier - Egyptian Arabic NLU
 * 
 * Classifies user intents from Egyptian Arabic transcriptions.
 * Uses rule-based matching + optional ML model for complex cases.
 * 
 * Supported Intents:
 * - AC_CONTROL: "شَغَّل التَّكْيِيف", "طَفِّي التَّكْيِيف", "زَوِّد الْحَرَّارَة"
 * - NAVIGATION: "خِدِينِي لِلْبِيت", "رُوح الشُّغْل", "أَقْرَب بَنْزِينَة"
 * - MEDIA: "شَغَّل مُوسِيقَى", "وَقَّف", "اِللِي بَعْدَهَا"
 * - PHONE: "كَلِّم أَحْمَد", "رُدّ عَالْمُكَالْمَة"
 * - INFO: "الْوَقْت إِيه", "حَرَّارَة بَرَّه إِيه"
 * 
 * @author Dr. Waleed Mandour
 */
class IntentClassifier(private val context: Context) {

    companion object {
        private const val TAG = "IntentClassifier"
        
        // Model path
        private const val MODEL_PATH = "nlu/egybert_tiny_int8.tflite"
        
        // Intent map path
        private const val INTENT_MAP_PATH = "commands/dilink_intent_map.json"
        
        // Confidence threshold for ML classification
        private const val CONFIDENCE_THRESHOLD = 0.7f
    }

    // TFLite interpreter for ML-based classification
    private var interpreter: Interpreter? = null
    
    // Intent patterns for rule-based matching
    private val intentPatterns = mutableMapOf<String, MutableList<IntentPattern>>()
    
    // Entity extractors
    private val entityExtractors = mutableMapOf<String, EntityExtractor>()
    
    // Initialized flag
    private var isInitialized = false

    /**
     * Initialize the intent classifier
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing intent classifier...")
            
            // Load intent patterns from JSON
            loadIntentPatterns()
            
            // Try to load ML model (optional, for complex cases)
            try {
                val modelBuffer = loadModelFile()
                interpreter = Interpreter(modelBuffer)
                Log.d(TAG, "ML model loaded for intent classification")
            } catch (e: Exception) {
                Log.w(TAG, "ML model not available, using rule-based only: ${e.message}")
            }
            
            // Setup entity extractors
            setupEntityExtractors()
            
            isInitialized = true
            Log.i(TAG, "✅ Intent classifier initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize intent classifier", e)
            throw e
        }
    }

    /**
     * Classify user intent from Egyptian Arabic text
     */
    fun classifyIntent(text: String): IntentResult {
        if (!isInitialized) {
            Log.w(TAG, "Classifier not initialized, using basic matching")
        }
        
        Log.d(TAG, "Classifying: $text")
        
        // Normalize text for Egyptian Arabic (Stripping diacritics for matching logic)
        val normalizedText = normalizeEgyptianArabic(text)
        Log.v(TAG, "Normalized: $normalizedText")
        
        // Step 1: Rule-based matching
        val ruleResult = matchByRules(normalizedText)
        if (ruleResult != null && ruleResult.confidence >= CONFIDENCE_THRESHOLD) {
            Log.i(TAG, "✅ Rule match: ${ruleResult.action} (${ruleResult.confidence})")
            return ruleResult
        }
        
        // Step 2: ML-based classification
        interpreter?.let { mlResult ->
            val result = classifyWithML(normalizedText)
            if (result != null && result.confidence >= CONFIDENCE_THRESHOLD) {
                Log.i(TAG, "✅ ML match: ${result.action} (${result.confidence})")
                return result
            }
        }
        
        // Step 3: Fallback to fuzzy matching
        val fuzzyResult = matchByFuzzy(normalizedText)
        if (fuzzyResult != null) {
            Log.i(TAG, "⚠️ Fuzzy match: ${fuzzyResult.action} (${fuzzyResult.confidence})")
            return fuzzyResult
        }
        
        // No match found
        Log.w(TAG, "❌ No intent matched for: $text")
        return IntentResult(
            action = "UNKNOWN",
            entities = emptyMap(),
            confidence = 0f,
            originalText = text
        )
    }

    /**
     * Normalize Egyptian Arabic text (Removes Tashkeel to ensure matching works)
     */
    private fun normalizeEgyptianArabic(text: String): String {
        return text
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("إزاي", "ازاي")
            .replace("عشان", "عشان")
            .replace("مش", "مش")
            // Remove diacritics for the comparison engine
            .replace(Regex("[\\u064B-\\u065F]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Rule-based intent matching
     */
    private fun matchByRules(text: String): IntentResult? {
        var bestMatch: IntentResult? = null
        var bestScore = 0f
        
        for ((action, patterns) in intentPatterns) {
            for (pattern in patterns) {
                val score = calculatePatternScore(text, pattern)
                
                if (score > bestScore) {
                    bestScore = score
                    
                    val entities = extractEntities(text, pattern, action)
                    
                    bestMatch = IntentResult(
                        action = action,
                        entities = entities,
                        confidence = score,
                        originalText = text,
                        matchedPattern = pattern.pattern,
                        matchedPatternResponse = pattern.response
                    )
                }
            }
        }
        
        return bestMatch
    }

    private fun calculatePatternScore(text: String, pattern: IntentPattern): Float {
        val patternWords = pattern.keywords
        var matchCount = 0
        var totalWeight = 0f
        
        for (keyword in patternWords) {
            totalWeight += keyword.weight
            if (keyword.word in text || keyword.aliases.any { it in text }) {
                matchCount++
            }
        }
        
        return if (totalWeight > 0) matchCount.toFloat() / patternWords.size else 0f
    }

    private fun extractEntities(
        text: String,
        pattern: IntentPattern,
        action: String
    ): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        for ((entityType, extractor) in entityExtractors) {
            val value = extractor.extract(text, action)
            if (value != null) {
                entities[entityType] = value
            }
        }
        
        when (action) {
            "AC_CONTROL" -> {
                val tempMatch = Regex("(\\d+)").find(text)
                if (tempMatch != null) {
                    entities["temperature"] = tempMatch.groupValues[1]
                }
                
                when {
                    "بَارِد" in text || "تَبْرِيد" in text -> entities["mode"] = "cool"
                    "سَاخِن" in text || "تَدْفِئَة" in text -> entities["mode"] = "heat"
                    "فَتْحَة" in text || "تَهْوِيَة" in text -> entities["mode"] = "vent"
                    "أُوتُومَاتِيك" in text -> entities["mode"] = "auto"
                }
            }
            
            "NAVIGATION" -> {
                val destKeywords = listOf("خِدِينِي", "رُوح", "أَوَدِّي", "نِرُوح", "وَدِّيني", "وَصِّلْنِي")
                for (keyword in destKeywords) {
                    if (keyword in text) {
                        val destStart = text.indexOf(keyword) + keyword.length
                        val destination = text.substring(destStart).trim()
                        if (destination.isNotEmpty()) {
                            entities["destination"] = destination
                        }
                        break
                    }
                }
            }
        }
        
        return entities
    }

    private fun classifyWithML(text: String): IntentResult? {
        return null
    }

    private fun matchByFuzzy(text: String): IntentResult? {
        return null
    }

    /**
     * Load intent patterns from JSON asset
     */
    private fun loadIntentPatterns() {
        try {
            val json = context.assets.open(INTENT_MAP_PATH).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val intents = jsonObject.getJSONObject("intents")
            
            for (action in intents.keys()) {
                val patternsArray = intents.getJSONArray(action)
                val patterns = mutableListOf<IntentPattern>()
                
                for (i in 0 until patternsArray.length()) {
                    val patternObj = patternsArray.getJSONObject(i)
                    val keywords = mutableListOf<Keyword>()
                    
                    val keywordsArray = patternObj.getJSONArray("keywords")
                    for (j in 0 until keywordsArray.length()) {
                        val kwObj = keywordsArray.getJSONObject(j)
                        keywords.add(
                            Keyword(
                                word = kwObj.getString("word"),
                                weight = kwObj.optDouble("weight", 1.0).toFloat(),
                                aliases = kwObj.optJSONArray("aliases")?.let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                } ?: emptyList()
                            )
                        )
                    }
                    
                    patterns.add(
                        IntentPattern(
                            pattern = patternObj.getString("pattern"),
                            keywords = keywords,
                            response = patternObj.optString("response", "")
                        )
                    )
                }
                
                intentPatterns[action] = patterns
            }
            
        } catch (e: Exception) {
            loadDefaultPatterns()
        }
    }

    /**
     * Load default intent patterns with expressive Tashkeel for the responses
     */
    private fun loadDefaultPatterns() {
        intentPatterns["AC_CONTROL"] = mutableListOf(
            IntentPattern(
                pattern = "شَغَّل التَّكْيِيف",
                keywords = listOf(
                    Keyword("شَغَّل", 1.0f, listOf("تِشَغَّل", "اِفْتَح", "شَغَّلِي")),
                    Keyword("تَكْيِيف", 1.0f, listOf("تِكِييف", "مُكَيِّف"))
                ),
                response = "عِنَيَّا حَاضِر، شَغَّلْتِلَّك التَّكْيِيف يَا بَاشَا"
            ),
            IntentPattern(
                pattern = "طَفِّي التَّكْيِيف",
                keywords = listOf(
                    Keyword("طَفِّي", 1.0f, listOf("اِطْفِي", "قَفَّل", "سَكَّر")),
                    Keyword("تَكْيِيف", 1.0f, listOf("تِكِييف"))
                ),
                response = "مِنْ عِنَيَّا، طَفَّيت التَّكْيِيف خَلَاص"
            )
        )
    }

    /**
     * Setup entity extractors with vocalized keywords
     */
    private fun setupEntityExtractors() {
        entityExtractors["temperature"] = EntityExtractor { text, _ ->
            Regex("(\\d+)\\s*(دَرَجَة|د)?").find(text)?.groupValues?.get(1)
        }
        
        entityExtractors["location"] = EntityExtractor { text, _ ->
            val locationKeywords = listOf("خِدِينِي", "رُوح", "أَوَدِّي", "نِرُوح", "وَدِّيني", "وَصِّلْنِي")
            for (keyword in locationKeywords) {
                if (keyword in text) {
                    val start = text.indexOf(keyword) + keyword.length
                    return@EntityExtractor text.substring(start).trim().takeIf { it.isNotEmpty() }
                }
            }
            null
        }
        
        entityExtractors["number"] = EntityExtractor { text, _ ->
            Regex("\\d+").find(text)?.value
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("models/$MODEL_PATH")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    data class IntentPattern(
        val pattern: String,
        val keywords: List<Keyword>,
        val response: String
    )
    
    data class Keyword(
        val word: String,
        val weight: Float,
        val aliases: List<String>
    )
    
    fun interface EntityExtractor {
        fun extract(text: String, action: String): String?
    }
}

data class IntentResult(
    val action: String,
    val entities: Map<String, String>,
    val confidence: Float,
    val originalText: String,
    val matchedPattern: String? = null,
    val matchedPatternResponse: String = ""
)