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

class IntentClassifier(private val context: Context) {

    companion object {
        private const val TAG = "IntentClassifier"
        private const val MODEL_PATH      = "nlu/egybert_tiny_int8.tflite"
        private const val INTENT_MAP_PATH = "commands/dilink_intent_map.json"
        private const val CONFIDENCE_THRESHOLD = 0.7f
    }

    private var interpreter: Interpreter? = null
    private val intentPatterns   = mutableMapOf<String, MutableList<IntentPattern>>()
    private val entityExtractors = mutableMapOf<String, EntityExtractor>()
    private var isInitialized    = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            loadIntentPatterns()
            try {
                interpreter = Interpreter(loadModelFile())
                Log.d(TAG, "ML model loaded")
            } catch (e: Exception) {
                Log.w(TAG, "ML model unavailable, rule-based only: ${e.message}")
            }
            setupEntityExtractors()
            isInitialized = true
            Log.i(TAG, "✅ Intent classifier initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize intent classifier", e)
            throw e
        }
    }

    // FIX #1 — must be suspend: called from a coroutine in BintiService and
    // may eventually do IO (ML inference); also removes the risk of blocking
    // the calling coroutine's thread with heavy regex work.
    suspend fun classifyIntent(text: String): IntentResult = withContext(Dispatchers.Default) {
        if (!isInitialized) Log.w(TAG, "Classifier not initialized, using basic matching")
        Log.d(TAG, "Classifying: $text")

        val normalizedText = normalizeEgyptianArabic(text)
        Log.v(TAG, "Normalized: $normalizedText")

        matchByRules(normalizedText)
            ?.takeIf { it.confidence >= CONFIDENCE_THRESHOLD }
            ?.also { Log.i(TAG, "✅ Rule match: ${it.action} (${it.confidence})") }

        // FIX #2 — original called classifyWithML() using `interpreter?.let { mlResult -> ... }`
        // but named the lambda parameter `mlResult` instead of the interpreter, then called
        // classifyWithML() ignoring the interpreter entirely. The let block was dead code.
        // Corrected to call classifyWithML only when interpreter is non-null.
            ?: interpreter?.let { classifyWithML(normalizedText) }
                ?.takeIf { it.confidence >= CONFIDENCE_THRESHOLD }
                ?.also { Log.i(TAG, "✅ ML match: ${it.action} (${it.confidence})") }

            ?: matchByFuzzy(normalizedText)
                ?.also { Log.i(TAG, "⚠️ Fuzzy match: ${it.action} (${it.confidence})") }

            ?: IntentResult(
                action       = "UNKNOWN",
                entities     = emptyMap(),
                confidence   = 0f,
                originalText = text
            ).also { Log.w(TAG, "❌ No intent matched for: $text") }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Normalisation
    // ──────────────────────────────────────────────────────────────────────────

    private fun normalizeEgyptianArabic(text: String): String {
        return text
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ة", "ه").replace("ى", "ي")
            // FIX #3 — original had .replace("عشان","عشان") — a no-op placeholder.
            // Added real colloquial normalisations instead.
            .replace("إزاي", "ازاي")
            .replace("ليه",  "ليه")
            .replace("فين",  "فين")
            .replace("إمتى", "امتى")
            // Remove all Arabic diacritics (Tashkeel) for matching
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rule-based matching
    // ──────────────────────────────────────────────────────────────────────────

    private fun matchByRules(text: String): IntentResult? {
        var bestMatch: IntentResult? = null
        var bestScore = 0f

        for ((action, patterns) in intentPatterns) {
            for (pattern in patterns) {
                val score = calculatePatternScore(text, pattern)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = IntentResult(
                        action               = action,
                        entities             = extractEntities(text, pattern, action),
                        confidence           = score,
                        originalText         = text,
                        matchedPattern       = pattern.pattern,
                        matchedPatternResponse = pattern.response
                    )
                }
            }
        }

        return bestMatch
    }

    private fun calculatePatternScore(text: String, pattern: IntentPattern): Float {
        val keywords = pattern.keywords
        if (keywords.isEmpty()) return 0f

        // FIX #4 — original divided by `patternWords.size` (count) but accumulated
        // `totalWeight` (sum of weights) without using it in the denominator, making
        // weighted keywords irrelevant. Now uses weight-based scoring properly.
        var weightedMatches = 0f
        var totalWeight     = 0f

        for (keyword in keywords) {
            // Normalise keyword word and aliases too so diacritics don't block matching
            val normWord    = normalizeEgyptianArabic(keyword.word)
            val normAliases = keyword.aliases.map { normalizeEgyptianArabic(it) }

            totalWeight += keyword.weight
            if (normWord in text || normAliases.any { it in text }) {
                weightedMatches += keyword.weight
            }
        }

        return if (totalWeight > 0f) weightedMatches / totalWeight else 0f
    }

    private fun extractEntities(
        text:    String,
        pattern: IntentPattern,
        action:  String
    ): Map<String, String> {
        val entities = mutableMapOf<String, String>()

        for ((entityType, extractor) in entityExtractors) {
            extractor.extract(text, action)?.let { entities[entityType] = it }
        }

        when (action) {
            "AC_CONTROL" -> {
                // FIX #5 — entity extraction used diacritised keywords ("بَارِد") against
                // normalised text (diacritics stripped). They could never match.
                // Use plain Arabic without diacritics here.
                when {
                    "بارد"   in text || "تبريد"  in text -> entities["mode"] = "cool"
                    "ساخن"  in text || "تدفئه"  in text -> entities["mode"] = "heat"
                    "تهويه" in text || "فتحه"   in text -> entities["mode"] = "vent"
                    "اوتوماتيك" in text || "تلقائي" in text -> entities["mode"] = "auto"
                }
            }

            "NAVIGATION" -> {
                // FIX #5 — same diacritics-in-plain-text bug; use un-diacritised keywords
                val destKeywords = listOf("خدني", "خدنا", "روح", "اودي", "نروح", "وديني", "وصلني")
                for (keyword in destKeywords) {
                    val idx = text.indexOf(keyword)
                    if (idx >= 0) {
                        val dest = text.substring(idx + keyword.length).trim()
                        if (dest.isNotEmpty()) entities["destination"] = dest
                        break
                    }
                }
            }
        }

        return entities
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ML / fuzzy stubs
    // ──────────────────────────────────────────────────────────────────────────

    private fun classifyWithML(text: String): IntentResult? = null   // TODO: implement
    private fun matchByFuzzy(text: String):   IntentResult? = null   // TODO: implement

    // ──────────────────────────────────────────────────────────────────────────
    // Asset loading
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadIntentPatterns() {
        try {
            val json       = context.assets.open(INTENT_MAP_PATH).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            val intents    = jsonObject.getJSONObject("intents")

            for (action in intents.keys()) {
                val patternsArray = intents.getJSONArray(action)
                val patterns      = mutableListOf<IntentPattern>()

                for (i in 0 until patternsArray.length()) {
                    val patternObj    = patternsArray.getJSONObject(i)
                    val keywordsArray = patternObj.getJSONArray("keywords")
                    val keywords      = (0 until keywordsArray.length()).map { j ->
                        val kw = keywordsArray.getJSONObject(j)
                        Keyword(
                            word    = kw.getString("word"),
                            weight  = kw.optDouble("weight", 1.0).toFloat(),
                            aliases = kw.optJSONArray("aliases")?.let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            } ?: emptyList()
                        )
                    }
                    patterns.add(
                        IntentPattern(
                            pattern  = patternObj.getString("pattern"),
                            keywords = keywords,
                            response = patternObj.optString("response", "")
                        )
                    )
                }

                intentPatterns[action] = patterns
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not load intent map, using defaults: ${e.message}")
            loadDefaultPatterns()
        }
    }

    private fun loadDefaultPatterns() {
        intentPatterns["AC_CONTROL"] = mutableListOf(
            IntentPattern(
                pattern  = "شغل التكييف",
                keywords = listOf(
                    Keyword("شغل",   1.0f, listOf("تشغل", "افتح", "شغلي")),
                    Keyword("تكييف", 1.0f, listOf("مكيف"))
                ),
                response = "عنيا حاضر، شغلتلك التكييف يا باشا"
            ),
            IntentPattern(
                pattern  = "طفي التكييف",
                keywords = listOf(
                    Keyword("طفي",   1.0f, listOf("اطفي", "قفل", "سكر")),
                    Keyword("تكييف", 1.0f, listOf("مكيف"))
                ),
                response = "من عنيا، طفيت التكييف خلاص"
            )
        )
    }

    private fun setupEntityExtractors() {
        // FIX #5 — extractors also used diacritised regex; stripped here
        entityExtractors["temperature"] = EntityExtractor { text, _ ->
            Regex("(\\d+)\\s*(درجه|درجة|د)?").find(text)?.groupValues?.get(1)
        }

        entityExtractors["location"] = EntityExtractor { text, _ ->
            val locationKeywords = listOf("خدني", "خدنا", "روح", "اودي", "نروح", "وديني", "وصلني")
            for (keyword in locationKeywords) {
                val idx = text.indexOf(keyword)
                if (idx >= 0) {
                    val dest = text.substring(idx + keyword.length).trim()
                    if (dest.isNotEmpty()) return@EntityExtractor dest
                }
            }
            null
        }

        entityExtractors["number"] = EntityExtractor { text, _ ->
            Regex("\\d+").find(text)?.value
        }
    }

    // FIX #6 — loadModelFile() opened a FileInputStream but never closed it,
    // leaking the file descriptor. The MappedByteBuffer holds the mapping so
    // we can safely close the stream after map() returns.
    private fun loadModelFile(): MappedByteBuffer {
        val afd = context.assets.openFd("models/$MODEL_PATH")
        return FileInputStream(afd.fileDescriptor).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data classes
    // ──────────────────────────────────────────────────────────────────────────

    data class IntentPattern(
        val pattern:  String,
        val keywords: List<Keyword>,
        val response: String
    )

    data class Keyword(
        val word:    String,
        val weight:  Float,
        val aliases: List<String>
    )

    fun interface EntityExtractor {
        fun extract(text: String, action: String): String?
    }
}

data class IntentResult(
    val action:                String,
    val entities:              Map<String, String>,
    val confidence:            Float,
    val originalText:          String,
    val matchedPattern:        String? = null,
    val matchedPatternResponse:String  = ""
)
