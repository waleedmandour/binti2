package com.binti.dilink.nlp

import timber.log.Timber

class EgyptianArabicNormalizer {
    
    private val egyptianToMSA = mapOf(
        "عايز" to "أريد",
        "ايه" to "ماذا",
        "ازاي" to "كيف",
        "ليه" to "لماذا",
        "تمام" to "حسنا"
    )
    
    fun normalize(text: String): String {
        var result = text.trim()
        for ((egyptian, msa) in egyptianToMSA) {
            result = result.replace(egyptian, msa)
        }
        return result
    }
    
    fun extractNumber(text: String): Int? {
        val numbers = Regex("\\d+").findAll(text)
        return numbers.firstOrNull()?.value?.toIntOrNull()
    }
    
    fun extractEntity(text: String, intentType: String): String? {
        return when (intentType) {
            "NAVIGATE" -> extractLocation(text)
            "CLIMATE" -> extractClimateParam(text)
            else -> null
        }
    }
    
    private fun extractLocation(text: String): String? {
        val patterns = listOf("البيت", "الشغل", "النادي")
        for (pattern in patterns) {
            if (text.contains(pattern)) return pattern
        }
        return null
    }
    
    private fun extractClimateParam(text: String): String? {
        return when {
            text.contains("شغّل") || text.contains("افتح") -> "on"
            text.contains("أطفئ") || text.contains("أغلق") -> "off"
            else -> null
        }
    }
}
