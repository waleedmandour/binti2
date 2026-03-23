package com.binti.dilink.response

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import timber.log.Timber
import java.util.Locale
import java.util.UUID

enum class ProsodyStyle {
    WELCOMING, CONFIRMATION, ERROR, NEUTRAL, QUESTION
}

class EgyptianTTS(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "EG")
                isInitialized = true
                Timber.i("TTS initialized with Egyptian Arabic")
            } else {
                Timber.e("TTS initialization failed")
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Timber.v("TTS started: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Timber.v("TTS done: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                Timber.w("TTS error: $utteranceId")
            }
        })
    }
    
    fun speak(text: String, style: ProsodyStyle = ProsodyStyle.NEUTRAL, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Timber.w("TTS not initialized")
            onComplete?.invoke()
            return
        }
        
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        Timber.d("Speaking: $text (style: $style)")
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Timber.i("TTS released")
    }
    
    fun isReady(): Boolean = isInitialized
}
