package com.binti.dilink.voice

import android.content.Context
import timber.log.Timber

class WakeWordDetector(private val context: Context) {
    
    private var isLoaded = false
    
    fun loadModel(): Boolean {
        Timber.i("Wake word model loading...")
        isLoaded = true
        return true
    }
    
    fun processAudioChunk(pcmData: ShortArray): Boolean {
        if (!isLoaded) return false
        // Placeholder: would detect "يا بنتي"
        return false
    }
    
    fun release() {
        isLoaded = false
        Timber.i("Wake word model released")
    }
    
    fun isModelLoaded(): Boolean = isLoaded
}
