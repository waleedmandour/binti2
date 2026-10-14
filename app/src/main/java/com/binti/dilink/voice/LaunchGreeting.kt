// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/**
 * Plays the bundled launch greeting — "أهلا بيك" in a clear female voice
 * (res/raw/launch_greeting.wav, 24 kHz mono PCM) — immediately when the user
 * launches the app.
 *
 * **Why a bundled clip instead of TTS?** A pre-baked recording:
 *  - plays instantly (no engine warm-up on a low-power head unit),
 *  - sounds identical on every car (no dependence on whatever Arabic voice
 *    — if any — is installed on the DiLink unit),
 *  - works even when no TTS engine is available at first launch.
 *
 * Dynamic spoken responses keep using [com.binti.dilink.response.EgyptianTTS].
 * If the clip is ever missing or fails, callers can fall back to
 * `egyptianTTS.speak(R.string.launch_greeting)`.
 *
 * Audio focus: transient request with ducking (API 26+ AudioFocusRequest —
 * the project's minSdk is 26), abandoned as soon as playback completes.
 *
 * @author Dr. Waleed Mandour
 */
object LaunchGreeting {

    private const val TAG = "LaunchGreeting"

    /** Guard so rotation / activity recreation doesn't replay the greeting. */
    @Volatile
    var hasGreetedThisProcess: Boolean = false
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Play the launch greeting once per process. Subsequent calls in the same
     * process are no-ops unless [resetForTesting] was called.
     *
     * Safe to call from the main thread — playback is asynchronous.
     *
     * @param force replay even if this process already greeted (used by the
     *              "play greeting" developer action if one is added later).
     * @return true when playback was started.
     */
    @Synchronized
    fun play(context: Context, force: Boolean = false): Boolean {
        if (hasGreetedThisProcess && !force) return false

        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Request transient focus so any in-car media pauses/ducks politely
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()

        val focusGranted = audioManager.requestAudioFocus(focusRequest!!) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusGranted) {
            Log.w(TAG, "Audio focus not granted — skipping launch greeting")
            return false
        }

        return try {
            mediaPlayer = MediaPlayer.create(appContext, com.binti.dilink.R.raw.launch_greeting)?.also { player ->
                player.setOnCompletionListener { mp ->
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra — releasing")
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    true
                }
                player.start()
                hasGreetedThisProcess = true
                Log.i(TAG, "👋 Launch greeting playing: أهلا بيك")
                true
            } ?: run {
                Log.e(TAG, "Could not create MediaPlayer for launch_greeting.wav")
                runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play launch greeting", e)
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
            false
        }
    }

    /** Stop playback early (e.g. service shutdown). Safe to call anytime. */
    @Synchronized
    fun stop() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    /** Test seam only. */
    fun resetForTesting() {
        hasGreetedThisProcess = false
    }
}
