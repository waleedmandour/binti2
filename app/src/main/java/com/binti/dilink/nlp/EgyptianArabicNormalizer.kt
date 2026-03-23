package com.binti.dilink.nlp

class EgyptianArabicNormalizer {
    fun normalize(text: String): String = text.trim()
    fun extractNumber(text: String): Int? = null
    fun extractEntity(text: String, intentType: String): String? = null
}
