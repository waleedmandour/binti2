

package com.binti.dilink.response

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Pair
import com.binti.dilink.utils.HMSUtils
import com.huawei.hms.mlsdk.tts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EgyptianTTS(private val context: Context) {

    companion object {
        private const val TAG = "EgyptianTTS"
        private const val DEFAULT_SPEECH_RATE = 0.95f
        private const val DEFAULT_PITCH       = 1.02f
        private const val PREFS_NAME = "binti_prefs"
        private const val KEY_TTS_ENGINE = "tts_engine"
    }

    /**
     * Available TTS engines. The user can select which one to use in Settings.
     *  - ANDROID_TTS: Built-in Android TTS (always available, lower quality)
     *  - HUAWEI_ML_KIT: Huawei ML Kit TTS (if HMS Core installed, good quality)
     *  - SHERPA_ONNX: Neural TTS via sherpa-onnx VITS/MMS (best quality, requires
     *    downloading the ~65 MB Arabic model, 100% offline)
     */
    enum class TTSEngine {
        ANDROID_TTS,       // Default — always available
        HUAWEI_ML_KIT,     // If HMS Core is installed
        SHERPA_ONNX        // Best quality — requires model download
    }

    private var androidTTS: TextToSpeech? = null
    private var isAndroidTTSReady = false
    private var mlTtsEngine: MLTtsEngine? = null
    private var isMlTtsReady  = false
    private var isInitialized = false
    private var preferredProvider = HMSUtils.TTSProvider.ANDROID_TTS
    private var selectedEngine: TTSEngine = TTSEngine.ANDROID_TTS
    private var sherpaTts: Any? = null  // com.k2fsa.sherpa.onnx.OfflineTts — loaded via reflection
    private var isSherpaReady = false

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            // Load user's TTS engine preference
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val engineName = prefs.getString(KEY_TTS_ENGINE, TTSEngine.ANDROID_TTS.name)
            selectedEngine = try {
                TTSEngine.valueOf(engineName ?: TTSEngine.ANDROID_TTS.name)
            } catch (e: IllegalArgumentException) {
                TTSEngine.ANDROID_TTS
            }
            Log.i(TAG, "Selected TTS engine: $selectedEngine")

            preferredProvider = HMSUtils.getPreferredTTSProvider(context)

            if (preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                initializeHuaweiTTS()
            }

            // Always initialize Android TTS as fallback
            try {
                initializeAndroidTTS()
            } catch (e: Exception) {
                Log.w(TAG, "Android TTS init failed: ${e.message}")
            }

            // Initialize sherpa-onnx if selected and model is available
            if (selectedEngine == TTSEngine.SHERPA_ONNX) {
                try {
                    initializeSherpaTTS()
                } catch (e: Exception) {
                    Log.w(TAG, "Sherpa-ONNX TTS init failed: ${e.message}")
                    // Fall back to Android TTS
                    selectedEngine = TTSEngine.ANDROID_TTS
                }
            }

            isInitialized = isAndroidTTSReady || isMlTtsReady || isSherpaReady
            if (!isInitialized) Log.e(TAG, "No TTS engine available")

        } catch (e: Exception) {
            Log.e(TAG, "TTS Init Error", e)
        }
    }

    /**
     * Set the TTS engine preference. Takes effect on next initialize() call.
     */
    fun setTTSEngine(engine: TTSEngine) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TTS_ENGINE, engine.name).apply()
        selectedEngine = engine
        Log.i(TAG, "TTS engine preference set to: $engine")
    }

    /**
     * Get the current TTS engine preference.
     */
    fun getTTSEngine(): TTSEngine = selectedEngine

    /**
     * Check if the sherpa-onnx Arabic model is downloaded and available.
     */
    fun isSherpaModelAvailable(): Boolean {
        val modelDir = java.io.File(context.filesDir, "voices/sherpa")
        val modelFile = java.io.File(modelDir, "model.onnx")
        return modelFile.exists()
    }

    private fun initializeSherpaTTS() {
        try {
            val modelDir = java.io.File(context.filesDir, "voices/sherpa")
            val modelFile = java.io.File(modelDir, "model.onnx")
            val tokensFile = java.io.File(modelDir, "tokens.txt")

            if (!modelFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "Sherpa-ONNX model not found at ${modelFile.absolutePath}")
                isSherpaReady = false
                return
            }

            // Load sherpa-onnx via reflection — the library is optional
            // (not on Maven Central; user must manually add the JAR/AAR to app/libs/)
            val configClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            val modelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig")
            val vitsConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig")
            val ttsClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")

            val vitsConfig = vitsConfigClass.getConstructor().newInstance()
            vitsConfigClass.getField("model").set(vitsConfig, modelFile.absolutePath)
            vitsConfigClass.getField("tokens").set(vitsConfig, tokensFile.absolutePath)
            vitsConfigClass.getField("dataDir").set(vitsConfig, modelDir.absolutePath)

            val modelConfig = modelConfigClass.getConstructor().newInstance()
            modelConfigClass.getField("vits").set(modelConfig, vitsConfig)

            val config = configClass.getConstructor().newInstance()
            configClass.getField("model").set(config, modelConfig)
            configClass.getField("maxNumSentences").set(config, 1)
            configClass.getField("numThreads").set(config, 2)

            sherpaTts = ttsClass.getConstructor(configClass).newInstance(config)
            isSherpaReady = true
            Log.i(TAG, "✅ Sherpa-ONNX TTS initialized — neural Arabic voice ready")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Sherpa-ONNX library not found — add the JAR/AAR to app/libs/. " +
                       "Falling back to Android TTS.")
            isSherpaReady = false
        } catch (e: Exception) {
            Log.w(TAG, "Sherpa-ONNX TTS unavailable: ${e.message}")
            isSherpaReady = false
        }
    }

    private fun initializeHuaweiTTS() {
        try {
            val mlConfigs = MLTtsConfig()
                .setLanguage(MLTtsConstants.TTS_LAN_AR_AR)
                .setPerson(MLTtsConstants.TTS_SPEAKER_FEMALE_AR)
                .setSpeed(1.0f)
                .setVolume(1.0f)

            mlTtsEngine = MLTtsEngine(mlConfigs)
            mlTtsEngine?.setTtsCallback(object : MLTtsCallback {
                override fun onError(taskId: String, err: MLTtsError) {
                    Log.e(TAG, "Huawei TTS error: ${err.errorMsg}")
                    // FIX #2 — if Huawei engine errors at runtime, fall back to Android TTS
                    isMlTtsReady = false
                }
                override fun onWarn(taskId: String, warn: MLTtsWarn) {}
                override fun onRangeStart(taskId: String, start: Int, end: Int) {}
                override fun onAudioAvailable(taskId: String, audioData: MLTtsAudioFragment, offset: Int, range: Pair<Int, Int>, bundle: Bundle) {}
                override fun onEvent(taskId: String, eventId: Int, bundle: Bundle?) {
                    // FIX #3 — bundle parameter must be nullable (MLTtsCallback contract);
                    // original declared it non-null, causing a crash on some HMS versions
                    // that pass null for this parameter.
                }
            })
            isMlTtsReady = true
        } catch (e: Exception) {
            Log.w(TAG, "Huawei TTS unavailable: ${e.message}")
            isMlTtsReady = false
        }
    }

    private suspend fun initializeAndroidTTS() = suspendCancellableCoroutine<Unit> { continuation ->
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // FIX #4 — setLanguage() returns a result code; LANG_MISSING_DATA or
                // LANG_NOT_SUPPORTED mean Arabic TTS data isn't installed. Log and
                // degrade gracefully rather than silently using the wrong language.
                val langResult = androidTTS?.setLanguage(Locale("ar", "EG"))
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Arabic TTS language data missing or unsupported (code $langResult)")
                }
                androidTTS?.setSpeechRate(DEFAULT_SPEECH_RATE)
                androidTTS?.setPitch(DEFAULT_PITCH)
                isAndroidTTSReady = true
                if (continuation.isActive) continuation.resume(Unit)
            } else {
                if (continuation.isActive)
                    continuation.resumeWithException(Exception("Android TTS init failed (status $status)"))
            }
        }

        // FIX #5 — if the coroutine is cancelled while waiting for TTS init,
        // shut down the engine to avoid leaking it
        continuation.invokeOnCancellation {
            androidTTS?.shutdown()
            androidTTS = null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Speech
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false

        // FIX: If TTS wasn't initialized (or initialization failed/never completed),
        // try to initialize now before giving up. This handles the case where the
        // user taps "sample instruction" before the background init coroutine finishes,
        // or when the init failed silently on first launch.
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized — attempting lazy init before speak()")
            try {
                initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Lazy TTS init failed", e)
            }
        }

        if (!isInitialized) {
            Log.e(TAG, "TTS still not ready after lazy init — cannot speak")
            return false
        }

        val naturalText = applyNaturalVocalization(text)
        Log.d(TAG, "🗣️ Speaking: $naturalText")

        return withContext(Dispatchers.Main) {
            // Try engines in priority order based on user selection
            when {
                // Sherpa-ONNX (best quality, if selected and ready)
                isSherpaReady && selectedEngine == TTSEngine.SHERPA_ONNX -> {
                    try {
                        val generateMethod = sherpaTts?.javaClass?.getMethod("generate", String::class.java)
                        val audio = generateMethod?.invoke(sherpaTts, naturalText)
                        if (audio != null) {
                            // Extract samples and sampleRate via reflection
                            val samplesField = audio.javaClass.getField("samples")
                            val samples = samplesField.get(audio) as? FloatArray
                            val sampleRateField = audio.javaClass.getField("sampleRate")
                            val sampleRate = sampleRateField.get(audio) as? Int ?: 22050
                            if (samples != null && samples.isNotEmpty()) {
                                playSherpaAudio(samples, sampleRate)
                                true
                            } else false
                        } else false
                    } catch (e: Exception) {
                        Log.e(TAG, "Sherpa TTS failed, falling back", e)
                        if (isAndroidTTSReady) {
                            val utteranceId = System.currentTimeMillis().toString()
                            androidTTS?.speak(naturalText, TextToSpeech.QUEUE_ADD, null, utteranceId) ==
                                TextToSpeech.SUCCESS
                        } else false
                    }
                }
                // Huawei ML Kit
                isMlTtsReady && preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT -> {
                    val taskId = mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_APPEND)
                    !taskId.isNullOrEmpty()
                }
                // Android TTS (always available fallback)
                isAndroidTTSReady -> {
                    val utteranceId = System.currentTimeMillis().toString()
                    androidTTS?.speak(naturalText, TextToSpeech.QUEUE_ADD, null, utteranceId) ==
                        TextToSpeech.SUCCESS
                }
                // Try ML Kit as last resort even if not preferred
                isMlTtsReady -> {
                    val taskId = mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_APPEND)
                    !taskId.isNullOrEmpty()
                }
                else -> false
            }
        }
    }

    /**
     * Non-suspend convenience method for UI click handlers. Creates a temporary
     * TextToSpeech instance if the main TTS engine isn't ready, speaks the text,
     * and releases the temporary instance after speaking completes.
     *
     * This is the most reliable way to play a short spoken message from a button
     * click — it doesn't depend on the async initialization having completed.
     */
    fun speakNow(text: String, context: Context) {
        if (text.isBlank()) return

        // Try the existing engine first
        if (isInitialized) {
            val naturalText = applyNaturalVocalization(text)
            when {
                isMlTtsReady && preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT -> {
                    mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_FLUSH)
                    return
                }
                isAndroidTTSReady -> {
                    val utteranceId = "speakNow_${System.currentTimeMillis()}"
                    val result = androidTTS?.speak(naturalText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    if (result == TextToSpeech.SUCCESS) return
                }
            }
        }

        // Fallback: create a temporary TTS instance inline
        Log.i(TAG, "speakNow() — creating temporary TTS instance for immediate playback")
        val naturalText = applyNaturalVocalization(text)
        var tempTts: TextToSpeech? = null
        tempTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val tts = tempTts ?: return@TextToSpeech
                    tts.setLanguage(Locale("ar", "EG"))
                    tts.setSpeechRate(DEFAULT_SPEECH_RATE)
                    tts.setPitch(DEFAULT_PITCH)
                    tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            tempTts?.shutdown()
                            tempTts = null
                        }
                        override fun onError(utteranceId: String?) {
                            tempTts?.shutdown()
                            tempTts = null
                        }
                    })
                    tts.speak(naturalText, TextToSpeech.QUEUE_FLUSH, null, "speakNow_temp")
                } catch (e: Exception) {
                    Log.e(TAG, "speakNow() temp TTS setup failed", e)
                    tempTts?.shutdown()
                    tempTts = null
                }
            } else {
                Log.e(TAG, "speakNow() temporary TTS init failed (status $status)")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Vocalization
    // ──────────────────────────────────────────────────────────────────────────

    private fun applyNaturalVocalization(text: String): String {
        var processed = text

        // 1. MSA → Egyptian colloquial substitutions
        // FIX #6 — "من" → "مين" replacement is too broad: it matches the preposition
        // "من" (from/of) which appears constantly in normal sentences and should NOT
        // be replaced. Removed. Same issue with "سوف" → "هـ" which breaks Arabic
        // phonetically (a standalone ه sounds like a breath). Removed.
        val dialectMap = mapOf(
            "لماذا"    to "ليه",
            "ماذا"     to "إيه",
            "كيف"      to "إزاي",
            "حسناً"    to "حاضر",
            "الآن"     to "دلوقتي",
            "جداً"     to "قوي",
            "نعم"      to "أيوة",
            "لا أستطيع" to "مش قادرة",
            "أستطيع"   to "أقدر",
            "التي"     to "اللي",
            "الذي"     to "اللي",
            "هذا"      to "ده",
            "هذه"      to "دي",
            "هؤلاء"    to "دول",
            "أين"      to "فين",
            "متى"      to "إمتى",
            "أريد"     to "عايزة",
            "تفضل"     to "اتفضل"
        )

        // FIX #7 — Regex word-boundary \b does not work correctly with Arabic script
        // because Arabic letters are not in the \w character class. Use lookahead/
        // lookbehind on whitespace and string boundaries instead.
        dialectMap.forEach { (msa, egyptian) ->
            processed = processed.replace(
                Regex("(?<![\\u0600-\\u06FF])${Regex.escape(msa)}(?![\\u0600-\\u06FF])"),
                egyptian
            )
        }

        // 2. Strip short vowel diacritics at word endings but preserve Sukoon (ْ U+0652)
        // FIX #8 — original regex [\u064B-\u0651]+ excluded Sukoon correctly BUT the
        // range \u064B-\u0651 also excludes Shadda (\u0651). Shadda mid-word is
        // linguistically significant (geminates consonants). The correct range to
        // strip at word endings is \u064B-\u0650 (Fathatan→Kasra), leaving Shadda
        // and Sukoon untouched.
        processed = processed.replace(Regex("[\\u064B-\\u0650]+(?=\\s|$)"), "")

        // 3. Spacing around punctuation for natural cadence
        processed = processed
            .replace("،", " ، ")
            .replace(".", " . ")
            .replace("؟", " ؟ ")
            .replace("!", " ! ")
            .replace(Regex("\\s+"), " ")

        return processed.trim()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    fun stop() {
        androidTTS?.stop()
        mlTtsEngine?.stop()
    }

    /**
     * Play audio samples generated by sherpa-onnx via Android AudioTrack.
     */
    private fun playSherpaAudio(samples: FloatArray, sampleRate: Int) {
        try {
            // Use AudioTrack.Builder (API 21+) instead of the deprecated
            // constructor to avoid crashes on newer Android versions.
            val audioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 4)
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .build()
            audioTrack.write(samples, 0, samples.size, android.media.AudioTrack.WRITE_BLOCKING)
            audioTrack.play()
            // Release after playback completes
            audioTrack.setNotificationMarkerPosition(samples.size)
            audioTrack.setPlaybackPositionUpdateListener(object : android.media.AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: android.media.AudioTrack?) {
                    track?.stop()
                    track?.release()
                }
                override fun onPeriodicNotification(track: android.media.AudioTrack?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sherpa audio", e)
        }
    }

    fun release() {
        androidTTS?.shutdown()
        androidTTS = null
        mlTtsEngine?.shutdown()
        mlTtsEngine       = null
        try {
            sherpaTts?.javaClass?.getMethod("release")?.invoke(sherpaTts)
        } catch (e: Exception) { /* ignore */ }
        sherpaTts         = null
        isAndroidTTSReady = false
        isMlTtsReady      = false
        isSherpaReady     = false
        isInitialized     = false
    }
}
