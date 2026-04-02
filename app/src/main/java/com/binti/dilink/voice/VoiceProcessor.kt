package com.binti.dilink.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiceProcessor(private val context: Context) {

    companion object {
        private const val TAG = "VoiceProcessor"

        private const val SAMPLE_RATE    = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT

        private const val VOSK_MODEL_PATH        = "vosk-model-ar-mgb2"
        private const val MAX_RECORDING_TIME_MS  = 15000L
        // FIX #1 — silence threshold was 20 loop iterations with no guaranteed
        // timing. Each AudioRecord.read() blocks for ~bufferSize/sampleRate seconds.
        // At 16 kHz with a typical min buffer of ~3200 samples that's ~100 ms/read,
        // so 20 iterations ≈ 2 s. Made the constant explicit in milliseconds and
        // converted to iterations at runtime so it's clear and adjustable.
        private const val SILENCE_THRESHOLD_MS   = 1500L
    }

    private var voskModel: Model? = null
    private var isInitialized = false

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing voice processor...")
            initializeVosk()
            isInitialized = true
            Log.i(TAG, "✅ Voice processor initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice processor", e)
            throw e
        }
    }

    private fun initializeVosk() {
        val modelDir = File(context.filesDir, "models/$VOSK_MODEL_PATH")
        if (!modelDir.exists()) {
            throw IOException("Vosk model not found at ${modelDir.absolutePath}. Download it first.")
        }
        voskModel = Model(modelDir.absolutePath)
        Log.d(TAG, "Vosk model loaded from ${modelDir.absolutePath}")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun listenAndTranscribe(timeoutMs: Long = MAX_RECORDING_TIME_MS): String {
        if (!isInitialized) throw IllegalStateException("Voice processor not initialized")
        return withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { transcribeWithVosk() }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Recording & transcription
    // ──────────────────────────────────────────────────────────────────────────

    // FIX #2 — original ran the blocking AudioRecord loop inside
    // suspendCancellableCoroutine, which ran on whatever thread the coroutine
    // was on (Default/Main). Audio recording must be on an IO thread and the
    // blocking loop must not sit inside a continuation. Restructured to run
    // entirely on Dispatchers.IO (called from listenAndTranscribe above).
    private suspend fun transcribeWithVosk(): String = withContext(Dispatchers.IO) {
        val model = voskModel ?: throw IOException("Vosk model not loaded")

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            3200  // FIX #3 — getMinBufferSize() can return ERROR or ERROR_BAD_VALUE (-1/-2).
                  // Clamp to a safe minimum so we never pass a negative size.
        )

        // FIX #4 — AudioRecord was never checked for RECORDSTATE_RECORDING / ERROR
        // after construction. An invalid AudioRecord silently reads zeros.
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            bufferSize * 2
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IOException("AudioRecord failed to initialise — check RECORD_AUDIO permission")
        }

        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        val results    = StringBuilder()

        // Convert silence threshold from ms to loop-iteration count
        val msPerRead          = (bufferSize.toFloat() / SAMPLE_RATE * 1000).toLong().coerceAtLeast(1)
        val silenceIterations  = (SILENCE_THRESHOLD_MS / msPerRead).toInt().coerceAtLeast(1)

        var silenceCount = 0
        var hasSpeech    = false
        val buffer       = ShortArray(bufferSize)

        try {
            audioRecord.startRecording()
            Log.d(TAG, "🎤 Recording started (silenceLimit=$silenceIterations iters)")

            // FIX #5 — `while(true)` with no coroutine-cooperative check means the
            // loop cannot be cancelled by withTimeout or Job cancellation until
            // AudioRecord.read() happens to return. Added isActive check.
            while (isActive) {
                val read = audioRecord.read(buffer, 0, bufferSize)

                // FIX #3 — read() can return error codes (negative values); skip them
                if (read <= 0) continue

                if (recognizer.acceptWaveForm(buffer, read)) {
                    val text = parseVoskResult(recognizer.result)
                    if (text.isNotEmpty()) {
                        results.append(text).append(" ")
                        hasSpeech    = true
                        silenceCount = 0
                    }
                } else {
                    val partial = parseVoskPartialResult(recognizer.partialResult)
                    if (partial.isNotEmpty()) {
                        Log.v(TAG, "Partial: $partial")
                        hasSpeech    = true
                        silenceCount = 0
                    } else if (hasSpeech) {
                        silenceCount++
                        if (silenceCount >= silenceIterations) {
                            Log.d(TAG, "Silence detected, stopping")
                            break
                        }
                    }
                }
            }

            // Flush final result
            val finalText = parseVoskResult(recognizer.finalResult)
            if (finalText.isNotEmpty()) results.append(finalText)

        } finally {
            // FIX #6 — original only cleaned up on the happy path after the loop.
            // Moved into finally so resources are always released even on exception
            // or coroutine cancellation.
            try { audioRecord.stop()    } catch (_: Exception) {}
            try { audioRecord.release() } catch (_: Exception) {}
            try { recognizer.close()    } catch (_: Exception) {}
        }

        val transcription = results.toString().trim()
        Log.i(TAG, "📝 Transcription: $transcription")
        transcription
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Parsing
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseVoskResult(json: String): String = try {
        JSONObject(json).optString("text", "")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse Vosk result: $json")
        ""
    }

    private fun parseVoskPartialResult(json: String): String = try {
        JSONObject(json).optString("partial", "")
    } catch (_: Exception) { "" }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    fun isReady(): Boolean = isInitialized

    fun release() {
        voskModel?.close()
        voskModel     = null
        isInitialized = false
        Log.d(TAG, "Voice processor released")
    }
}
