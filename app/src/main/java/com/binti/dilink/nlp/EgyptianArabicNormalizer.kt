package com.binti.dilink.nlp

import timber.log.Timber

/**
 * EgyptianArabicNormalizer - Normalizes Egyptian dialect to standard form
 * 
 * Handles:
 * - Egyptian colloquialisms to MSA mapping
 * - Phonetic variations
 * - Slang and informal expressions
 * - Diacritics normalization
 * - Number and date normalization
 */
class EgyptianArabicNormalizer {

    companion object {
        // Egyptian to MSA word mappings
        private val EGYPTIAN_TO_MSA = mapOf(
            // Common verbs
            "عايز" to "أريد",
            "عايزة" to "أريد",
            "مش عايز" to "لا أريد",
            "مش عايزة" to "لا أريد",
            "ايه" to "ماذا",
            "ازاي" to "كيف",
            "ليه" to "لماذا",
            "فيين" to "أين",
            "امتى" to "متى",
            "ادين" to "أعطني",
            "جيلي" to "تعال",
            "روح" to "اذهب",
            "جيب" to "أحضر",
            "شوف" to "انظر",
            "سمع" to "استمع",
            
            // Common expressions
            "ايه الاخبار" to "ما الأخبار",
            "ازيك" to "كيف حالك",
            "الحمد لله" to "الحمد لله",
            "تمام" to "حسنا",
            "أوكيه" to "حسنا",
            "يا باشا" to "",
            "يا حبيبي" to "",
            "يا أستاذ" to "",
            
            // Negations
            "مش" to "لا",
            "مفيش" to "لا يوجد",
            "معلش" to "عذرا",
            
            // Directions and places
            "البيت" to "المنزل",
            "الشغل" to "العمل",
            "النادي" to "النادي",
            "المصيف" to "منتجع",
            
            // Numbers (Egyptian pronunciation)
            "واحد" to "واحد",
            "اتنين" to "اثنان",
            "تلاتة" to "ثلاثة",
            "أربعة" to "أربعة",
            "خمسة" to "خمسة",
            "ستة" to "ستة",
            "سبعة" to "سبعة",
            "تمانية" to "ثمانية",
            "تسعة" to "تسعة",
            "عشرة" to "عشرة",
            
            // Climate related
            "التكييف" to "مكيف الهواء",
            "السخان" to "سخان الماء",
            "المراوح" to "المروحة",
            "الفتح" to "افتح",
            "القفل" to "أغلق",
            
            // Navigation
            "خديني" to "خذني إلى",
            "وديني" to "أوصلني إلى",
            "ارجعني" to "أعدني إلى",
            "الطريق" to "الطريق",
            
            // Media
            "المزيكا" to "الموسيقى",
            "الأغنية" to "الأغنية",
            "الراديو" to "الراديو",
            "بلوتوث" to "بلوتوث"
        )
        
        // Egyptian phonetic variations
        private val PHONETIC_NORMALIZATIONS = mapOf(
            "ج" to "ج",  // Egyptian hard g
            "ق" to "ء",  // Egyptian glottal stop for qaf
            "ث" to "ت",  // Egyptian uses ta instead of tha
            "ذ" to "د",  // Egyptian uses dal instead of dhal
            "ظ" to "ز",  // Egyptian uses zay instead of zha
        )
        
        // Common Egyptian slang patterns
        private val SLANG_PATTERNS = listOf(
            Regex("(يا )?باشا[،.]?") to "",
            Regex("(يا )?حبيب[يهاى]+[،.]?") to "",
            Regex("(يا )?أستاذ[،.]?") to "",
            Regex("يعني[،.]?") to "",
            Regex("بقى[،.]?") to "",
            Regex("ده") to "هذا",
            Regex("دي") to "هذه",
            Regex("دول") to "هؤلاء"
        )
        
        // Number word patterns
        private val NUMBER_WORDS = mapOf(
            "واحد" to 1, "اتنين" to 2, "تلاتة" to 3, "أربعة" to 4,
            "خمسة" to 5, "ستة" to 6, "سبعة" to 7, "تمانية" to 8,
            "تسعة" to 9, "عشرة" to 10, "عشرين" to 20, "تلاتين" to 30
        )
    }

    /**
     * Normalize Egyptian Arabic text
     * 
     * @param text Input text in Egyptian Arabic
     * @return Normalized text suitable for NLU processing
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return text
        
        var normalized = text.trim()
        
        // Remove diacritics for easier matching
        normalized = removeDiacritics(normalized)
        
        // Apply slang pattern removals
        for ((pattern, replacement) in SLANG_PATTERNS) {
            normalized = pattern.replace(normalized, replacement)
        }
        
        // Apply Egyptian to MSA word mappings
        normalized = applyWordMappings(normalized)
        
        // Normalize numbers (both digits and words)
        normalized = normalizeNumbers(normalized)
        
        // Clean up extra spaces
        normalized = normalized
            .replace(Regex("\\s+"), " ")
            .trim()
        
        Timber.d("Normalized: '$text' -> '$normalized'")
        return normalized
    }

    /**
     * Remove Arabic diacritics (tashkeel)
     */
    private fun removeDiacritics(text: String): String {
        return text.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    }

    /**
     * Apply Egyptian to MSA word mappings
     */
    private fun applyWordMappings(text: String): String {
        var result = text
        
        for ((egyptian, msa) in EGYPTIAN_TO_MSA) {
            result = result.replace(egyptian, msa)
        }
        
        return result
    }

    /**
     * Normalize numbers (convert words to digits for numeric parameters)
     */
    private fun normalizeNumbers(text: String): String {
        var result = text
        
        // Convert Arabic numerals to Western numerals
        val arabicNumerals = mapOf(
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
        )
        
        for ((arabic, western) in arabicNumerals) {
            result = result.replace(arabic, western)
        }
        
        // Convert number words to digits in specific contexts
        // Example: "درجة تلاتة" -> "درجة 3"
        for ((word, digit) in NUMBER_WORDS) {
            // Only convert if preceded by specific keywords
            val pattern = Regex("(درجة|سرعة|رقم)\\s+$word")
            result = pattern.replace(result) { matchResult ->
                "${matchResult.groupValues[1]} $digit"
            }
        }
        
        return result
    }

    /**
     * Extract numeric value from text
     * 
     * @param text Input text containing a number
     * @return Extracted number or null
     */
    fun extractNumber(text: String): Int? {
        // Try to find digits directly
        val digitMatch = Regex("\\d+").find(text)
        if (digitMatch != null) {
            return digitMatch.value.toIntOrNull()
        }
        
        // Try to match number words
        for ((word, digit) in NUMBER_WORDS) {
            if (text.contains(word)) {
                return digit
            }
        }
        
        return null
    }

    /**
     * Check if text contains Egyptian colloquialisms
     */
    fun containsEgyptianDialect(text: String): Boolean {
        for ((egyptian, _) in EGYPTIAN_TO_MSA) {
            if (text.contains(egyptian)) {
                return true
            }
        }
        return false
    }

    /**
     * Extract location/entity from text
     * 
     * @param text Input text
     * @param intentType Type of intent (NAVIGATE, etc.)
     * @return Extracted entity or null
     */
    fun extractEntity(text: String, intentType: String): String? {
        return when (intentType) {
            "NAVIGATE" -> extractLocation(text)
            "CLIMATE" -> extractClimateParameter(text)
            "MUSIC" -> extractMusicEntity(text)
            else -> null
        }
    }

    /**
     * Extract location from navigation command
     */
    private fun extractLocation(text: String): String? {
        // Common patterns
        val patterns = listOf(
            Regex("(?:إلى|لـ?|عند)\\s+(.+?)(?:\\s*$|\\s*،)"),
            Regex("(?:البيت|المنزل)"), // Home
            Regex("(?:الشغل|العمل)"), // Work
            Regex("(?:النادي)"), // Club
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.trim() ?: match.value
            }
        }
        
        return null
    }

    /**
     * Extract climate parameter from text
     */
    private fun extractClimateParameter(text: String): String? {
        // Temperature patterns
        val tempPattern = Regex("(\\d+)\\s*(?:درجة)?")
        val tempMatch = tempPattern.find(text)
        if (tempMatch != null) {
            return "temperature:${tempMatch.groupValues[1]}"
        }
        
        // On/off patterns
        return when {
            text.contains("شغّل") || text.contains("افتح") -> "on"
            text.contains("أغلق") || text.contains("أطفئ") || text.contains("قفّل") -> "off"
            text.contains("ارفع") || text.contains("زود") -> "increase"
            text.contains("اخفض") || text.contains("قلل") -> "decrease"
            else -> null
        }
    }

    /**
     * Extract music entity (song name, artist, etc.)
     */
    private fun extractMusicEntity(text: String): String? {
        // Simple extraction for now
        val patterns = listOf(
            Regex("(?:شغّل|اسمع)\\s+(.+?)(?:\\s*$|\\s*لـ)"),
            Regex("(?:أغنية|سمعنا)\\s+(.+?)\\s*$")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
}
