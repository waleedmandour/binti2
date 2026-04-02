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
    }

    private var androidTTS: TextToSpeech? = null
    private var isAndroidTTSReady = false
    private var mlTtsEngine: MLTtsEngine? = null
    private var isMlTtsReady  = false
    private var isInitialized = false
    private var preferredProvider = HMSUtils.TTSProvider.ANDROID_TTS

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            preferredProvider = HMSUtils.getPreferredTTSProvider(context)

            if (preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                initializeHuaweiTTS()
            }

            // FIX #1 — initializeAndroidTTS() is always called as a fallback even
            // when Huawei is preferred, which is correct. But if it throws (TTS
            // init failed) the exception was swallowed by the outer try/catch and
            // isInitialized was still set to true. Now we track whether at least
            // one engine is ready before setting isInitialized.
            try {
                initializeAndroidTTS()
            } catch (e: Exception) {
                Log.w(TAG, "Android TTS init failed: ${e.message}")
            }

            // FIX #1 — only mark ready if at least one engine is usable
            isInitialized = isAndroidTTSReady || isMlTtsReady
            if (!isInitialized) Log.e(TAG, "No TTS engine available")

        } catch (e: Exception) {
            Log.e(TAG, "TTS Init Error", e)
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
        if (!isInitialized || text.isBlank()) return false

        val naturalText = applyNaturalVocalization(text)
        Log.d(TAG, "🗣️ Speaking: $naturalText")

        return withContext(Dispatchers.Main) {
            when {
                isMlTtsReady && preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT -> {
                    val taskId = mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_APPEND)
                    !taskId.isNullOrEmpty()
                }
                isAndroidTTSReady -> {
                    val utteranceId = System.currentTimeMillis().toString()
                    androidTTS?.speak(naturalText, TextToSpeech.QUEUE_ADD, null, utteranceId) ==
                        TextToSpeech.SUCCESS
                }
                // FIX #2 — if preferred engine failed at runtime, try the other one
                isMlTtsReady -> {
                    val taskId = mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_APPEND)
                    !taskId.isNullOrEmpty()
                }
                else -> false
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

    // FIX #9 — release() set isInitialized = false but left isAndroidTTSReady /
    // isMlTtsReady true, so a subsequent speak() call after release() would attempt
    // to use a shut-down engine and crash. Reset all flags.
    fun release() {
        androidTTS?.shutdown()
        androidTTS = null
        mlTtsEngine?.shutdown()
        mlTtsEngine       = null
        isAndroidTTSReady = false
        isMlTtsReady      = false
        isInitialized     = false
    }
}
