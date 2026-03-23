package com.binti.dilink.voice

import android.content.Context

class VoiceProcessor(private val context: Context) {
    fun initialize(): Boolean = true
    suspend fun recordCommand(maxDurationMs: Int = 10000): ShortArray = ShortArray(0)
    suspend fun transcribe(audioData: ShortArray): String = ""
    fun release() {}
}
