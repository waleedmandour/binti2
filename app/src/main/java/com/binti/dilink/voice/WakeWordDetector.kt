package com.binti.dilink.voice

import android.content.Context

class WakeWordDetector(private val context: Context) {
    fun loadModel(): Boolean = true
    fun processAudioChunk(data: ShortArray): Boolean = false
    fun release() {}
}
