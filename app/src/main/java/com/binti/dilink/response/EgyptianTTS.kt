package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import timber.log.Timber
import java.util.Locale

enum class ProsodyStyle {
    WELCOMING, CONFIRMATION, ERROR, NEUTRAL, QUESTION
}

class EgyptianTTS(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "EG")
                Timber.i("TTS initialized with Egyptian Arabic")
            }
        }
    }
    
    fun speak(text: String, style: ProsodyStyle = ProsodyStyle.NEUTRAL, onComplete: (() -> Unit)? = null) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        Timber.d("Speaking: $text")
    }
    
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        Timber.i("TTS released")
    }
}
