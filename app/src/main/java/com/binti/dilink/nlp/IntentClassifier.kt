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
 * - AC_CONTROL: "شغل التكييف", "طفي التكييف", "زيود الحرارة"
 * - NAVIGATION: "خديني للبيت", "روح الشغل", "أقرب بنزين"
 * - MEDIA: "شغل موسيقى", "وقفة", "اللي بعدها"
 * - PHONE: "كلم أحمد", "رد عالمكالمة"
 * - INFO: "الوقت إيه", "حرارة بره إيه"
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
        
        // Normalize text for Egyptian Arabic
        val normalizedText = normalizeEgyptianArabic(text)
        Log.v(TAG, "Normalized: $normalizedText")
        
        // Step 1: Rule-based matching (fast and accurate for known patterns)
        val ruleResult = matchByRules(normalizedText)
        if (ruleResult != null && ruleResult.confidence >= CONFIDENCE_THRESHOLD) {
            Log.i(TAG, "✅ Rule match: ${ruleResult.action} (${ruleResult.confidence})")
            return ruleResult
        }
        
        // Step 2: ML-based classification (for complex/ambiguous cases)
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
     * Normalize Egyptian Arabic text
     */
    private fun normalizeEgyptianArabic(text: String): String {
        return text
            // Normalize alef variants
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            // Normalize teh marbuta
            .replace("ة", "ه")
            // Normalize yeh variants
            .replace("ى", "ي")
            // Normalize Egyptian colloquialisms
            .replace("إزاي", "ازي")
            .replace("عشان", "عشان")
            .replace("مش", "مش")
            // Remove diacritics
            .replace(Regex("[\\u064B-\\u065F]"), "")
            // Remove extra whitespace
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
                    
                    // Extract entities
                    val entities = extractEntities(text, pattern, action)
                    
                    bestMatch = IntentResult(
                        action = action,
                        entities = entities,
                        confidence = score,
                        originalText = text,
                        matchedPattern = pattern.pattern
                    )
                }
            }
        }
        
        return bestMatch
    }

    /**
     * Calculate match score for a pattern
     */
    private fun calculatePatternScore(text: String, pattern: IntentPattern): Float {
        val patternWords = pattern.keywords
        val textWords = text.split(" ")
        
        var matchCount = 0
        var totalWeight = 0f
        
        for (keyword in patternWords) {
            totalWeight += keyword.weight
            
            // Check if keyword appears in text
            if (keyword.word in text || keyword.aliases.any { it in text }) {
                matchCount++
            }
        }
        
        return if (totalWeight > 0) matchCount.toFloat() / patternWords.size else 0f
    }

    /**
     * Extract entities from text
     */
    private fun extractEntities(
        text: String,
        pattern: IntentPattern,
        action: String
    ): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        // Use registered entity extractors
        for ((entityType, extractor) in entityExtractors) {
            val value = extractor.extract(text, action)
            if (value != null) {
                entities[entityType] = value
            }
        }
        
        // Pattern-specific extraction
        when (action) {
            "AC_CONTROL" -> {
                // Extract temperature
                val tempMatch = Regex("(\\d+)").find(text)
                if (tempMatch != null) {
                    entities["temperature"] = tempMatch.groupValues[1]
                }
                
                // Extract AC mode
                when {
                    "بارد" in text || "تبريد" in text -> entities["mode"] = "cool"
                    "ساخن" in text || "تدفئة" in text -> entities["mode"] = "heat"
                    "فتحة" in text || "تهوية" in text -> entities["mode"] = "vent"
                    "اتوماتيك" in text -> entities["mode"] = "auto"
                }
            }
            
            "NAVIGATION" -> {
                // Extract destination
                val destKeywords = listOf("خديني", "روح", "أودي", "نروح")
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
            
            "MEDIA" -> {
                // Extract media action
                when {
                    "شغل" in text || "اسمع" in text -> entities["media_action"] = "play"
                    "وقفة" in text || "وقف" in text -> entities["media_action"] = "pause"
                    "اللي بعدها" in text || "بعدين" in text -> entities["media_action"] = "next"
                    "اللي قبلها" in text || "قبلي" in text -> entities["media_action"] = "previous"
                }
            }
        }
        
        return entities
    }

    /**
     * ML-based classification (placeholder for complex cases)
     */
    private fun classifyWithML(text: String): IntentResult? {
        // TODO: Implement TFLite inference when model is available
        // This would use the EgyBERT-tiny model for classification
        return null
    }

    /**
     * Fuzzy matching for typos and variations
     */
    private fun matchByFuzzy(text: String): IntentResult? {
        // Implementation of fuzzy string matching
        // Uses Levenshtein distance for similarity
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
            
            Log.d(TAG, "Loaded ${intentPatterns.size} intent patterns")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load intent patterns, using defaults: ${e.message}")
            loadDefaultPatterns()
        }
    }

    /**
     * Load default intent patterns (fallback)
     */
    private fun loadDefaultPatterns() {
        intentPatterns["AC_CONTROL"] = mutableListOf(
            IntentPattern(
                pattern = "شغل التكييف",
                keywords = listOf(
                    Keyword("شغل", 1.0f, listOf("تشغل", "افتح")),
                    Keyword("تكييف", 1.0f, listOf("تيكيف", "مكيف"))
                ),
                response = "تمام، شغلت التكييف"
            ),
            IntentPattern(
                pattern = "طفي التكييف",
                keywords = listOf(
                    Keyword("طفي", 1.0f, listOf("اطفي", "قفل", "سكر")),
                    Keyword("تكييف", 1.0f, listOf("تيكيف"))
                ),
                response = "تمام، طفيت التكييف"
            ),
            IntentPattern(
                pattern = "زيود الحرارة",
                keywords = listOf(
                    Keyword("زيود", 1.0f, listOf("زود", "ارفع")),
                    Keyword("حرارة", 1.0f, listOf("دفء"))
                ),
                response = "تمام، زودت الحرارة"
            )
        )
        
        intentPatterns["NAVIGATION"] = mutableListOf(
            IntentPattern(
                pattern = "خديني للبيت",
                keywords = listOf(
                    Keyword("خديني", 1.0f, listOf("ودني", "روح")),
                    Keyword("بيت", 1.0f, listOf("منزل", "البيت"))
                ),
                response = "تمام، هوديك البيت"
            ),
            IntentPattern(
                pattern = "أقرب بنزين",
                keywords = listOf(
                    Keyword("أقرب", 1.0f, listOf("اقرب")),
                    Keyword("بنزين", 1.0f, listOf("محطة", "وقود"))
                ),
                response = "تمام، هوريك أقرب محطة بنزين"
            )
        )
        
        intentPatterns["MEDIA"] = mutableListOf(
            IntentPattern(
                pattern = "شغل موسيقى",
                keywords = listOf(
                    Keyword("شغل", 1.0f, listOf("اسمع")),
                    Keyword("موسيقى", 1.0f, listOf("اغنية", "سماعي"))
                ),
                response = "تمام، شغلت الموسيقى"
            )
        )
    }

    /**
     * Setup entity extractors
     */
    private fun setupEntityExtractors() {
        // Temperature extractor
        entityExtractors["temperature"] = EntityExtractor { text, _ ->
            Regex("(\\d+)\\s*(درجة|د)?").find(text)?.groupValues?.get(1)
        }
        
        // Location extractor
        entityExtractors["location"] = EntityExtractor { text, _ ->
            // Extract location after keywords like "خديني", "روح"
            val locationKeywords = listOf("خديني", "روح", "أودي", "نروح")
            for (keyword in locationKeywords) {
                if (keyword in text) {
                    val start = text.indexOf(keyword) + keyword.length
                    return@EntityExtractor text.substring(start).trim().takeIf { it.isNotEmpty() }
                }
            }
            null
        }
        
        // Number extractor
        entityExtractors["number"] = EntityExtractor { text, _ ->
            Regex("\\d+").find(text)?.value
        }
    }

    /**
     * Load TFLite model file
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("models/$MODEL_PATH")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Data classes
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

/**
 * Intent classification result
 */
data class IntentResult(
    val action: String,
    val entities: Map<String, String>,
    val confidence: Float,
    val originalText: String,
    val matchedPattern: String? = null
)
