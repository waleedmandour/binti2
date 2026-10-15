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
 * (res/raw/launch_greeting.wav) — when the user opens the app.
 *
 * **Why a bundled clip instead of TTS?** A pre-baked recording:
 *  - plays instantly (no engine warm-up on a low-power head unit),
 *  - sounds identical on every car (no dependence on whatever Arabic voice
 *    — if any — is installed on the DiLink unit),
 *  - works even when no TTS engine is available at first launch.
 *
 * v3.0.2 FIX (greeted only once per process): the old `hasGreetedThisProcess`
 * flag was static and NEVER reset while BintiService (a foreground service)
 * kept the process alive — so closing and reopening the app played nothing.
 * The policy now lives with the caller ([com.binti.dilink.MainActivity]):
 *  - a fresh activity launch (onCreate with no saved state) plays the greeting,
 *  - returning to the app after a longer absence (≥ 60 s) replays it,
 *  - rotations / quick app switches do NOT (guard lives in the activity).
 *
 * This class keeps only an "is playing" guard so two overlapping calls can
 * never double-play or leak two MediaPlayers.
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

    /** True while the clip is actually playing — prevents double-play overlap. */
    @Volatile
    var isPlaying: Boolean = false
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Play the launch greeting. Returns false only when a greeting is already
     * playing or the clip failed — the caller decides the launch/replay
     * policy (see the class doc).
     *
     * Safe to call from the main thread — playback is asynchronous.
     *
     * @return true when playback was started.
     */
    @Synchronized
    fun play(context: Context): Boolean {
        if (isPlaying) return false

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
            val player = MediaPlayer.create(appContext, com.binti.dilink.R.raw.launch_greeting)
            if (player == null) {
                Log.e(TAG, "Could not create MediaPlayer for launch_greeting.wav")
                runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                false
            } else {
                isPlaying = true
                player.setOnCompletionListener { mp ->
                    isPlaying = false
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra — releasing")
                    isPlaying = false
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    true
                }
                mediaPlayer = player
                player.start()
                Log.i(TAG, "Launch greeting playing: أهلا بيك")
                true
            }
        } catch (e: Exception) {
            isPlaying = false
            Log.e(TAG, "Failed to play launch greeting", e)
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest!!) }
            false
        }
    }

    /** Stop playback early (e.g. service shutdown). Safe to call anytime. */
    @Synchronized
    fun stop() {
        isPlaying = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
