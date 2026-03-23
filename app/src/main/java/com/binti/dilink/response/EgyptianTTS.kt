package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class EgyptianTTS(private val context: Context) {
    private var tts: TextToSpeech? = null
    
    fun initialize() {
        tts = TextToSpeech(context) { tts?.language = Locale("ar", "EG") }
    }
    
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }
    
    fun release() {
        tts?.shutdown()
        tts = null
    }
}
