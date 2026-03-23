package com.binti.dilink.nlp

import android.content.Context

data class Intent(
    val action: String,
    val confidence: Float = 0f,
    val parameters: Map<String, String> = emptyMap(),
    val originalText: String = ""
) {
    companion object {
        val UNKNOWN = Intent("UNKNOWN")
    }
}

class IntentClassifier(private val context: Context) {
    fun loadModel(): Boolean = true
    fun classify(text: String): Intent = Intent.UNKNOWN
    fun release() {}
}
