// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.voice

import android.content.Context
import android.util.Log
import org.vosk.Model
import java.io.File

/**
 * P1 FIX (model dedup): [WakeWordDetectorVosk] and [VoiceProcessor] each used
 * to construct their own `org.vosk.Model` from the same ~318 MB Arabic model
 * directory, doubling native memory and startup time on a low-RAM head unit.
 *
 * Vosk's `Model` is a native object designed to be shared across multiple
 * `Recognizer` instances (each recognizer keeps its own decode state and can
 * apply its own grammar), so ONE process-wide Model is correct here:
 *  - wake word  → Recognizer(model, 16000, grammar["يا بنتي","[unk]"])
 *  - command ASR→ Recognizer(model, 16000)   // full vocabulary
 *
 * The provider lazily loads the model once and never closes it — it lives
 * for the lifetime of the process. Callers must therefore NOT call
 * `Model.close()` on the returned instance.
 *
 * @author Dr. Waleed Mandour
 */
object VoskModelProvider {

    private const val TAG = "VoskModelProvider"

    /**
     * Candidate model directories inside filesDir, ordered by preference.
     * Covers the naming conventions used by ModelManager across app versions
     * (see assets/models/model_config.json → relative_path "models").
     */
    private val CANDIDATE_PATHS = listOf(
        "models/vosk-model-ar-mgb2",
        "models/vosk-model-ar-mgb2-0.4",
        "vosk-model-ar-mgb2",
        "models/asr/vosk-model-ar-mgb2"
    )

    /** Resolves the first existing model directory, or null if none downloaded yet. */
    fun resolveModelDir(context: Context): File? {
        val filesDir = context.filesDir
        return CANDIDATE_PATHS
            .map { File(filesDir, it) }
            .firstOrNull { it.exists() && it.isDirectory }
    }

    @Volatile
    private var cachedModel: Model? = null

    /**
     * Get the shared Vosk Model, loading it on first use.
     * Must be called from a background thread — native model loading is slow.
     *
     * @throws IllegalStateException when the model has not been downloaded.
     */
    fun obtain(context: Context): Model {
        cachedModel?.let { return it }

        synchronized(this) {
            cachedModel?.let { return it }

            val dir = resolveModelDir(context)
                ?: throw IllegalStateException(
                    "Vosk model not found. Please download the Arabic model first " +
                    "(Settings → Model, or assets/models/model_config.json)."
                )

            val model = Model(dir.absolutePath)
            cachedModel = model
            Log.i(TAG, "✅ Shared Vosk model loaded from ${dir.absolutePath} (single instance)")
            return model
        }
    }

    /** True once the shared model has been loaded into native memory. */
    fun isLoaded(): Boolean = cachedModel != null

    /**
     * Intentionally NOT exposing a close() — the model is process-lifetime.
     * Only a future `onTrimMemory` strategy should ever release it.
     */
}
