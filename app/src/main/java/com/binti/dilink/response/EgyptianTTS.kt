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

/**
 * Egyptian TTS - Professional Speech Engine for BYD DiLink
 * 
 * Provides natural spoken responses in Egyptian Arabic dialect (Ammiya).
 * Optimized for automotive environment and natural human tone.
 * 
 * @author Dr. Waleed Mandour
 */
class EgyptianTTS(private val context: Context) {

    companion object {
        private const val TAG = "EgyptianTTS"
        private const val DEFAULT_SPEECH_RATE = 0.95f // Balanced for Egyptian rhythm
        private const val DEFAULT_PITCH = 1.02f       // Friendly feminine tone
    }

    private var androidTTS: TextToSpeech? = null
    private var isAndroidTTSReady = false
    private var mlTtsEngine: MLTtsEngine? = null
    private var isMlTtsReady = false
    private var isInitialized = false
    private var preferredProvider = HMSUtils.TTSProvider.ANDROID_TTS

    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            preferredProvider = HMSUtils.getPreferredTTSProvider(context)
            if (preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                initializeHuaweiTTS()
            }
            initializeAndroidTTS()
            isInitialized = true
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
                override fun onError(taskId: String, err: MLTtsError) { Log.e(TAG, "Huawei Error: ${err.errorMsg}") }
                override fun onWarn(taskId: String, warn: MLTtsWarn) {}
                override fun onRangeStart(taskId: String, start: Int, end: Int) {}
                override fun onAudioAvailable(taskId: String, audioData: MLTtsAudioFragment, offset: Int, range: Pair<Int, Int>, bundle: Bundle) {}
                override fun onEvent(taskId: String, eventId: Int, bundle: Bundle) {}
            })
            isMlTtsReady = true
        } catch (e: Exception) {
            isMlTtsReady = false
        }
    }

    private suspend fun initializeAndroidTTS() = suspendCancellableCoroutine<Unit> { continuation ->
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTTS?.setLanguage(Locale("ar", "EG"))
                androidTTS?.setSpeechRate(DEFAULT_SPEECH_RATE)
                androidTTS?.setPitch(DEFAULT_PITCH)
                isAndroidTTSReady = true
                if (continuation.isActive) continuation.resume(Unit)
            } else {
                if (continuation.isActive) continuation.resumeWithException(Exception("Android TTS failed"))
            }
        }
    }

    suspend fun speak(text: String): Boolean {
        if (!isInitialized || text.isBlank()) return false
        
        val naturalText = applyNaturalVocalization(text)
        Log.d(TAG, "🗣️ Natural Egyptian Speech: $naturalText")
        
        return withContext(Dispatchers.Main) {
            if (isMlTtsReady && preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                val taskId = mlTtsEngine?.speak(naturalText, MLTtsEngine.QUEUE_APPEND)
                !taskId.isNullOrEmpty()
            } else if (isAndroidTTSReady) {
                val utteranceId = System.currentTimeMillis().toString()
                androidTTS?.speak(naturalText, TextToSpeech.QUEUE_ADD, null, utteranceId) == TextToSpeech.SUCCESS
            } else false
        }
    }

    /**
     * Expert Phonetic Normalization for Egyptian Arabic
     * Converts MSA text to natural "Ammiya" sounding speech for the TTS engine.
     * Uses Sukoon for endings and replaces MSA words with colloquial equivalents.
     */
    private fun applyNaturalVocalization(text: String): String {
        var processed = text

        // 1. Dialectal Phonetic Substitution for Natural Egyptian Tone
        val dialectMap = mapOf(
            "لماذا" to "ليه",
            "ماذا" to "إيه",
            "كيف" to "إزاي",
            "حسناً" to "حاضر",
            "الآن" to "دلوقتي",
            "جداً" to "قوي",
            "سوف" to "هـ",
            "نعم" to "أيوة",
            "أستطيع" to "أقدر",
            "لا أستطيع" to "مش قادرة",
            "التي" to "اللي",
            "الذي" to "اللي",
            "هذا" to "ده",
            "هذه" to "دي",
            "هؤلاء" to "دول",
            "أين" to "فين",
            "متى" to "إمتى",
            "من" to "مين",
            "أريد" to "عايزة",
            "تفضل" to "اتفضل",
            "شكراً" to "شكراً يا باشا",
            "يا بنتي" to "يا بنتي"
        )

        dialectMap.forEach { (msa, egyptian) ->
            processed = processed.replace(Regex("\\b$msa\\b"), egyptian)
        }

        // 2. Remove Fatha/Damma/Kasra/Shadda from word endings but KEEP Sukoon (ْ)
        // Sukoon at word endings gives natural Egyptian cadence to the TTS output
        processed = processed.replace(Regex("[\u064B-\u0651]+(?=\\s|$)"), "")

        // 3. Human cadence - adding breathing space and emphasis
        processed = processed.replace("،", " ، ")
            .replace(".", " . ")
            .replace("؟", " ؟ ")
            .replace("!", " ! ")
            .replace(Regex("\\s+"), " ")

        return processed.trim()
    }

    fun stop() {
        androidTTS?.stop()
        mlTtsEngine?.stop()
    }

    fun release() {
        androidTTS?.shutdown()
        mlTtsEngine?.shutdown()
        isInitialized = false
    }
}
