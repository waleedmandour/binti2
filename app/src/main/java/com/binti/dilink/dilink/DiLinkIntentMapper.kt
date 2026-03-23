package com.binti.dilink.dilink

import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream

/**
 * DiLinkIntentMapper - Maps Egyptian Arabic intents to DiLink commands
 * 
 * Provides:
 * - Intent to DiLink action mapping
 * - Parameter transformation
 * - Command validation
 * - Fallback handling
 */
class DiLinkIntentMapper {

    companion object {
        // Intent map loaded from JSON
        private lateinit var intentMap: JSONObject
        
        // Egyptian Arabic trigger phrases
        private val TRIGGER_PHRASES = mapOf(
            // Navigation
            "خديني" to "NAVIGATE",
            "وديني" to "NAVIGATE",
            "روح" to "NAVIGATE",
            "ابدأ رحلة" to "NAVIGATE",
            "الطريق لـ" to "NAVIGATE",
            
            // Climate control
            "شغّل التكييف" to "CLIMATE_ON",
            "افتح التكييف" to "CLIMATE_ON",
            "التكييف شغال" to "CLIMATE_ON",
            "أطفئ التكييف" to "CLIMATE_OFF",
            "أغلق التكييف" to "CLIMATE_OFF",
            "قفّل التكييف" to "CLIMATE_OFF",
            "التكييف مطفي" to "CLIMATE_OFF",
            "درجة" to "CLIMATE_TEMP",
            "حرارة" to "CLIMATE_TEMP",
            "الجو حر" to "CLIMATE_COOL",
            "الجو برد" to "CLIMATE_HEAT",
            "خفف التكييف" to "CLIMATE_FAN_LOW",
            "زود التكييف" to "CLIMATE_FAN_HIGH",
            
            // Media control
            "شغّل المزيكا" to "MUSIC_PLAY",
            "اسمع أغنية" to "MUSIC_PLAY",
            "شغّل الأغنية" to "MUSIC_PLAY",
            "وقّف المزيكا" to "MUSIC_PAUSE",
            "إيقاف مؤقت" to "MUSIC_PAUSE",
            "الأغنية الجاية" to "MUSIC_NEXT",
            "التالي" to "MUSIC_NEXT",
            "الأغنية اللي فاتت" to "MUSIC_PREV",
            "السابق" to "MUSIC_PREV",
            
            // Volume control
            "ارفع الصوت" to "VOLUME_UP",
            "زود الصوت" to "VOLUME_UP",
            "الصوت عالي" to "VOLUME_UP",
            "اخفض الصوت" to "VOLUME_DOWN",
            "قلل الصوت" to "VOLUME_DOWN",
            "الصوت واطي" to "VOLUME_DOWN",
            "صفر الصوت" to "VOLUME_MUTE",
            "اكتم الصوت" to "VOLUME_MUTE"
        )
        
        // Location shortcuts
        private val LOCATION_SHORTCUTS = mapOf(
            "البيت" to "home",
            "المنزل" to "home",
            "الشغل" to "work",
            "العمل" to "work",
            "النادي" to "club",
            "المصيف" to "vacation"
        )
    }

    /**
     * Load intent map from JSON file
     */
    fun loadFromJson(inputStream: InputStream) {
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            intentMap = JSONObject(json)
            Timber.i("Loaded intent map with ${intentMap.length()} entries")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load intent map")
            // Initialize with empty map
            intentMap = JSONObject()
        }
    }

    /**
     * Map text to DiLink command
     */
    fun mapToCommand(text: String): DiLinkCommand? {
        // Try direct phrase match
        for ((phrase, intent) in TRIGGER_PHRASES) {
            if (text.contains(phrase)) {
                return createCommandFromIntent(intent, text)
            }
        }
        
        // Try fuzzy matching
        return tryFuzzyMatch(text)
    }

    /**
     * Create DiLink command from intent
     */
    private fun createCommandFromIntent(intent: String, text: String): DiLinkCommand {
        return when (intent) {
            "NAVIGATE" -> createNavigationCommand(text)
            "CLIMATE_ON" -> DiLinkCommand(
                method = CommandMethod.INTENT,
                action = "com.byd.climate.ACTION_CONTROL",
                extras = mapOf("power" to "on")
            )
            "CLIMATE_OFF" -> DiLinkCommand(
                method = CommandMethod.INTENT,
                action = "com.byd.climate.ACTION_CONTROL",
                extras = mapOf("power" to "off")
            )
            "CLIMATE_TEMP" -> createTemperatureCommand(text)
            "CLIMATE_COOL" -> DiLinkCommand(
                method = CommandMethod.INTENT,
                action = "com.byd.climate.ACTION_CONTROL",
                extras = mapOf("power" to "on", "mode" to "cool", "temperature" to "20")
            )
            "CLIMATE_HEAT" -> DiLinkCommand(
                method = CommandMethod.INTENT,
                action = "com.byd.climate.ACTION_CONTROL",
                extras = mapOf("power" to "on", "mode" to "heat", "temperature" to "25")
            )
            "MUSIC_PLAY" -> DiLinkCommand(
                method = CommandMethod.KEY_EVENT,
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            )
            "MUSIC_PAUSE" -> DiLinkCommand(
                method = CommandMethod.KEY_EVENT,
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            )
            "MUSIC_NEXT" -> DiLinkCommand(
                method = CommandMethod.KEY_EVENT,
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            )
            "MUSIC_PREV" -> DiLinkCommand(
                method = CommandMethod.KEY_EVENT,
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            )
            "VOLUME_UP" -> DiLinkCommand(
                method = CommandMethod.AUDIO_MANAGER,
                action = "ADJUST_RAISE"
            )
            "VOLUME_DOWN" -> DiLinkCommand(
                method = CommandMethod.AUDIO_MANAGER,
                action = "ADJUST_LOWER"
            )
            "VOLUME_MUTE" -> DiLinkCommand(
                method = CommandMethod.AUDIO_MANAGER,
                action = "ADJUST_MUTE"
            )
            else -> DiLinkCommand(method = CommandMethod.UNKNOWN)
        }
    }

    /**
     * Create navigation command with destination extraction
     */
    private fun createNavigationCommand(text: String): DiLinkCommand {
        var destination: String? = null
        var destinationType: String? = null
        
        // Check location shortcuts
        for ((shortcut, type) in LOCATION_SHORTCUTS) {
            if (text.contains(shortcut)) {
                destinationType = type
                break
            }
        }
        
        // Extract specific destination
        if (destinationType == null) {
            val patterns = listOf(
                Regex("(?:إلى|لـ?|عند)\\s+(.+?)(?:\\s*$|\\s*،)"),
                Regex("(?:خديني|وديني)\\s+(.+?)(?:\\s*$|\\s*،)")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(text)
                if (match != null) {
                    destination = match.groupValues[1].trim()
                    break
                }
            }
        }
        
        return DiLinkCommand(
            method = CommandMethod.INTENT,
            action = "com.byd.navigation.ACTION_NAVIGATE",
            extras = mapOf(
                "destination" to (destination ?: ""),
                "destination_type" to (destinationType ?: "custom"),
                "language" to "ar-EG"
            )
        )
    }

    /**
     * Create temperature command with value extraction
     */
    private fun createTemperatureCommand(text: String): DiLinkCommand {
        // Extract temperature value
        val numberPattern = Regex("\\d+")
        val match = numberPattern.find(text)
        var temperature = match?.value?.toIntOrNull() ?: 22
        
        // Clamp to valid range
        temperature = temperature.coerceIn(16, 30)
        
        return DiLinkCommand(
            method = CommandMethod.INTENT,
            action = "com.byd.climate.ACTION_CONTROL",
            extras = mapOf(
                "power" to "on",
                "temperature" to temperature.toString()
            )
        )
    }

    /**
     * Try fuzzy matching for unrecognized text
     */
    private fun tryFuzzyMatch(text: String): DiLinkCommand? {
        // Levenshtein distance based matching
        var bestMatch: Pair<String, Int>? = null
        
        for ((phrase, intent) in TRIGGER_PHRASES) {
            if (text.contains(phrase)) {
                continue // Already handled by direct match
            }
            
            // Check for partial matches
            val similarity = calculateSimilarity(text, phrase)
            if (similarity > 0.7) {
                if (bestMatch == null || similarity > bestMatch.second / 100.0) {
                    bestMatch = intent to (similarity * 100).toInt()
                }
            }
        }
        
        return bestMatch?.let { 
            createCommandFromIntent(it.first, text) 
        }
    }

    /**
     * Calculate similarity between two strings (0-1)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toDouble()
    }

    /**
     * Calculate Levenshtein distance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        
        for (i in 0..s2.length) {
            costs[i] = i
        }
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            
            for (j in 1..s2.length) {
                val cj = minOf(
                    1 + costs[j], // deletion
                    1 + costs[j - 1], // insertion
                    nw + if (s1[i - 1] == s2[j - 1]) 0 else 1 // substitution
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        
        return costs[s2.length]
    }

    /**
     * Get all supported intents
     */
    fun getSupportedIntents(): List<String> {
        return TRIGGER_PHRASES.values.distinct()
    }

    /**
     * Get trigger phrases for an intent
     */
    fun getTriggerPhrases(intent: String): List<String> {
        return TRIGGER_PHRASES.filter { it.value == intent }.keys.toList()
    }
}

/**
 * Method of command execution
 */
enum class CommandMethod {
    INTENT,         // Android Intent broadcast
    ADB,           // ADB shell command
    ACCESSIBILITY, // Accessibility service gesture
    KEY_EVENT,     // Key event injection
    AUDIO_MANAGER, // AudioManager API
    HTTP,          // HTTP API call
    UNKNOWN
}

/**
 * DiLink command data class
 */
data class DiLinkCommand(
    val method: CommandMethod,
    val action: String = "",
    val extras: Map<String, Any> = emptyMap(),
    val keyCode: Int = 0,
    val adbCommand: String = "",
    val gesture: GestureSpec? = null
)

/**
 * Gesture specification for accessibility gestures
 */
data class GestureSpec(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long = 300
)
