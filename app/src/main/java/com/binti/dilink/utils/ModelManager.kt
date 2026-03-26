package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Model Download Manager
 *
 * Manages downloading and verification of AI models from Google Drive.
 *
 * Models (Total: ~1.4GB):
 * - Wake Word: ya_binti_detector.tflite (~5MB)
 * - ASR: vosk-model-ar-mgb2 (~1.2GB) - Modern Standard Arabic
 * - NLU: egybert_tiny_int8.onnx (~25MB)
 * - TTS: ar-eg-female voice (~80MB)
 *
 * Download Sources:
 * - Primary: Google Drive direct links
 * - Fallback: User's own Google Drive (they can host models)
 * - Local: Load from USB/SD card path
 *
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // Google Drive Configuration
        // Replace with your actual Google Drive file IDs
        private const val GOOGLE_DRIVE_BASE_URL = "https://drive.google.com/uc?export=download"

        // Google Drive File IDs
        // ASR Model - Required (the only model that must be downloaded)
        private const val ASR_MODEL_FILE_ID = "1bK1-pUCH5xykvKdB7nB7mZ_1wBKH05qZ"

        // Optional models - file IDs to be set when models are ready
        // private const val WAKE_WORD_FILE_ID = "" // Optional - uses Vosk grammar-based detection
        // private const val NLU_MODEL_FILE_ID = "" // Optional - can use rule-based matching
        // private const val TTS_VOICE_FILE_ID = "" // Optional - can use Android TTS
        // Intent map is bundled in assets, no download needed

        // Mirror URLs for fallback (can be user-configured)
        private const val MIRROR_PRIMARY = "https://mirror1.binti.app/models"
        private const val MIRROR_SECONDARY = "https://mirror2.binti.app/models"

        // Preferences
        private const val PREFS_NAME = "binti_model_prefs"
        private const val KEY_CUSTOM_DRIVE_FOLDER = "custom_drive_folder"
        private const val KEY_LOCAL_MODEL_PATH = "local_model_path"
        private const val KEY_USE_LOCAL_MODELS = "use_local_models"

        // Model definitions with Google Drive URLs
        val MODELS = listOf(
            ModelDefinition(
                name = "Wake Word Detector",
                fileName = "ya_binti_detector.tflite",
                googleDriveId = "",
                downloadUrl = "",
                mirrorUrls = listOf(
                    "$MIRROR_PRIMARY/wake/ya_binti_detector.tflite",
                    "$MIRROR_SECONDARY/wake/ya_binti_detector.tflite"
                ),
                relativePath = "models/wake",
                sizeMB = 5,
                sha256 = "compute_after_upload",
                required = false, // Optional - uses Vosk grammar-based wake word detection
                description = "Detects 'يا بنتي' wake word (optional - using Vosk grammar-based detection)"
            ),
            ModelDefinition(
                name = "Arabic ASR (Vosk MGB2)",
                fileName = "vosk-model-ar-mgb2-0.4.zip",
                googleDriveId = ASR_MODEL_FILE_ID,
                downloadUrl = "$GOOGLE_DRIVE_BASE_URL&id=$ASR_MODEL_FILE_ID",
                mirrorUrls = listOf(
                    "$MIRROR_PRIMARY/asr/vosk-model-ar-mgb2-0.4.zip",
                    "$MIRROR_SECONDARY/asr/vosk-model-ar-mgb2-0.4.zip"
                ),
                relativePath = "models",
                sizeMB = 1247,
                sha256 = "compute_after_upload",
                required = true,
                extract = true,
                description = "Arabic speech recognition model (Vosk MGB2 v0.4)"
            ),
            ModelDefinition(
                name = "Intent Classifier (EgyBERT)",
                fileName = "egybert_tiny_int8.onnx",
                googleDriveId = "",
                downloadUrl = "",
                mirrorUrls = listOf(
                    "$MIRROR_PRIMARY/nlu/egybert_tiny_int8.onnx",
                    "$MIRROR_SECONDARY/nlu/egybert_tiny_int8.onnx"
                ),
                relativePath = "models/nlu",
                sizeMB = 25,
                sha256 = "compute_after_upload",
                required = false, // Optional - can use rule-based intent matching
                description = "Egyptian Arabic intent classification (optional - fallback to rule-based matching)"
            ),
            ModelDefinition(
                name = "Egyptian TTS Voice",
                fileName = "ar-eg-female.zip",
                googleDriveId = "",
                downloadUrl = "",
                mirrorUrls = listOf(
                    "$MIRROR_PRIMARY/tts/ar-eg-female.zip",
                    "$MIRROR_SECONDARY/tts/ar-eg-female.zip"
                ),
                relativePath = "voices",
                sizeMB = 80,
                sha256 = "compute_after_upload",
                required = false, // Optional - can use Android TTS or Huawei ML Kit TTS
                extract = true,
                description = "Egyptian female voice for TTS (optional - fallback to Android TTS)"
            ),
            ModelDefinition(
                name = "Intent Patterns",
                fileName = "dilink_intent_map.json",
                googleDriveId = "",
                downloadUrl = "",
                mirrorUrls = listOf(
                    "$MIRROR_PRIMARY/nlp/dilink_intent_map.json",
                    "$MIRROR_SECONDARY/nlp/dilink_intent_map.json"
                ),
                relativePath = "assets/commands",
                sizeMB = 0,
                sha256 = "compute_after_upload",
                required = true, // Required - bundled in app assets
                description = "Intent patterns for command matching (bundled in app assets)"
            )
        )
    }

    // HTTP client with longer timeouts for large files
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Model directory in app's files directory
    private val modelsDir = File(context.filesDir, "models")

    // Shared preferences for user configuration
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Set custom Google Drive folder ID for user-hosted models
     */
    fun setCustomDriveFolder(folderId: String) {
        prefs.edit().putString(KEY_CUSTOM_DRIVE_FOLDER, folderId).apply()
        Log.i(TAG, "Custom Google Drive folder set: $folderId")
    }

    /**
     * Set local model path (USB/SD card)
     */
    fun setLocalModelPath(path: String) {
        prefs.edit().putString(KEY_LOCAL_MODEL_PATH, path).apply()
        Log.i(TAG, "Local model path set: $path")
    }

    /**
     * Enable/disable local model usage
     */
    fun setUseLocalModels(useLocal: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_MODELS, useLocal).apply()
        Log.i(TAG, "Use local models: $useLocal")
    }

    /**
     * Check if local models should be used
     */
    fun shouldUseLocalModels(): Boolean {
        return prefs.getBoolean(KEY_USE_LOCAL_MODELS, false)
    }

    /**
     * Get local model path
     */
    fun getLocalModelPath(): String? {
        return prefs.getString(KEY_LOCAL_MODEL_PATH, null)
    }

    /**
     * Check the status of all models
     */
    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L

        // Check if using local models
        if (shouldUseLocalModels()) {
            val localPath = getLocalModelPath()
            if (localPath != null) {
                return@withContext checkLocalModelsStatus(localPath)
            }
        }

        for (model in MODELS) {
            val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
            if (modelFile.exists() && verifyModel(model, modelFile)) {
                readyCount++
                totalSize += modelFile.length()
                Log.d(TAG, "✅ ${model.name}: Ready")
            } else {
                Log.d(TAG, "❌ ${model.name}: Missing or invalid")
            }
        }

        val requiredReady = MODELS.filter { it.required }.count { model ->
            val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
            modelFile.exists() && verifyModel(model, modelFile)
        }

        ModelStatus(
            readyCount = readyCount,
            totalCount = MODELS.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == MODELS.size,
            partialModelsReady = requiredReady == MODELS.count { it.required },
            missingModels = MODELS.filter { model ->
                val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
                !modelFile.exists() || !verifyModel(model, modelFile)
            }
        )
    }

    /**
     * Check status of models from local path (USB/SD card)
     */
    private fun checkLocalModelsStatus(localPath: String): ModelStatus {
        var readyCount = 0
        var totalSize = 0L

        for (model in MODELS) {
            val modelFile = File(localPath, "${model.relativePath}/${model.fileName}")
            if (modelFile.exists() && verifyModel(model, modelFile)) {
                readyCount++
                totalSize += modelFile.length()
                Log.d(TAG, "✅ ${model.name}: Ready (local)")
            } else {
                Log.d(TAG, "❌ ${model.name}: Missing (local)")
            }
        }

        val requiredReady = MODELS.filter { it.required }.count { model ->
            val modelFile = File(localPath, "${model.relativePath}/${model.fileName}")
            modelFile.exists() && verifyModel(model, modelFile)
        }

        return ModelStatus(
            readyCount = readyCount,
            totalCount = MODELS.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == MODELS.size,
            partialModelsReady = requiredReady == MODELS.count { it.required },
            missingModels = MODELS.filter { model ->
                val modelFile = File(localPath, "${model.relativePath}/${model.fileName}")
                !modelFile.exists() || !verifyModel(model, modelFile)
            }
        )
    }

    /**
     * Download all models
     */
    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🚀 Starting model download from Google Drive...")

            // Ensure models directory exists
            modelsDir.mkdirs()

            var totalProgress = 0
            val totalModels = MODELS.size
            var downloadedCount = 0

            for ((index, model) in MODELS.withIndex()) {
                onProgress(
                    (totalProgress * 100) / (totalModels * 100),
                    model.name
                )

                val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")

                // Skip if already downloaded and verified
                if (modelFile.exists() && verifyModel(model, modelFile)) {
                    Log.d(TAG, "✅ Model ${model.name} already exists, skipping")
                    totalProgress += 100
                    downloadedCount++
                    continue
                }

                // Create parent directory
                modelFile.parentFile?.mkdirs()

                // Try downloading from multiple sources
                var downloadSuccess = false
                val downloadErrors = mutableListOf<String>()

                // 1. Try Google Drive first
                try {
                    downloadFromGoogleDrive(model, modelFile) { progress ->
                        val overallProgress = ((index * 100) + progress) / totalModels
                        onProgress(overallProgress, model.name)
                    }
                    downloadSuccess = true
                    Log.i(TAG, "✅ Downloaded ${model.name} from Google Drive")
                } catch (e: Exception) {
                    Log.w(TAG, "Google Drive download failed for ${model.name}: ${e.message}")
                    downloadErrors.add("Google Drive: ${e.message}")
                }

                // 2. Try mirror URLs if Google Drive failed
                if (!downloadSuccess && model.mirrorUrls.isNotEmpty()) {
                    for (mirrorUrl in model.mirrorUrls) {
                        try {
                            downloadFromUrl(mirrorUrl, modelFile) { progress ->
                                val overallProgress = ((index * 100) + progress) / totalModels
                                onProgress(overallProgress, model.name)
                            }
                            downloadSuccess = true
                            Log.i(TAG, "✅ Downloaded ${model.name} from mirror: $mirrorUrl")
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "Mirror download failed for ${model.name}: ${e.message}")
                            downloadErrors.add("Mirror: ${e.message}")
                        }
                    }
                }

                // 3. Try custom Google Drive folder if configured
                if (!downloadSuccess) {
                    val customFolder = prefs.getString(KEY_CUSTOM_DRIVE_FOLDER, null)
                    if (customFolder != null) {
                        try {
                            val customUrl = "$GOOGLE_DRIVE_BASE_URL&id=${model.googleDriveId}"
                            downloadFromGoogleDrive(model.copy(downloadUrl = customUrl), modelFile) { progress ->
                                val overallProgress = ((index * 100) + progress) / totalModels
                                onProgress(overallProgress, model.name)
                            }
                            downloadSuccess = true
                            Log.i(TAG, "✅ Downloaded ${model.name} from custom Google Drive")
                        } catch (e: Exception) {
                            downloadErrors.add("Custom Drive: ${e.message}")
                        }
                    }
                }

                if (!downloadSuccess) {
                    throw ModelDownloadException(
                        "Failed to download ${model.name}. Errors: ${downloadErrors.joinToString("; ")}"
                    )
                }

                // Extract if needed
                if (model.extract) {
                    extractModel(model, modelFile)
                }

                totalProgress += 100
                downloadedCount++
            }

            Log.i(TAG, "✅ Downloaded $downloadedCount/${MODELS.size} models successfully")
            onComplete()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Model download failed", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Download from Google Drive with virus scan warning handling
     */
    private suspend fun downloadFromGoogleDrive(
        model: ModelDefinition,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "📥 Downloading from Google Drive: ${model.name}")

        var downloadUrl = model.downloadUrl

        // First request to get potential virus scan warning
        val initialRequest = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        httpClient.newCall(initialRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModelDownloadException("Google Drive request failed: HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw ModelDownloadException("Empty response body")

            // Check for virus scan warning page (Google shows this for large files)
            val htmlContent = responseBody.string()

            if (htmlContent.contains("Google Drive - Virus scan warning") ||
                htmlContent.contains("quot")) {
                Log.d(TAG, "⚠️ Virus scan warning detected, extracting confirm token...")

                // Extract confirm token from the HTML
                val confirmToken = extractGoogleDriveConfirmToken(htmlContent)
                if (confirmToken != null) {
                    downloadUrl = "${model.downloadUrl}&confirm=$confirmToken"
                    Log.d(TAG, "✅ Got confirm token: $confirmToken")
                }
            } else {
                // No virus warning, content might be the actual file
                // Check if it's actually the file by looking at content-type
                val contentType = response.header("Content-Type", "")
                if (!contentType.contains("text/html")) {
                    // This is the actual file, save it
                    targetFile.writeText(htmlContent)
                    Log.d(TAG, "✅ Downloaded ${model.name} (small file, direct)")
                    return@withContext
                }
            }
        }

        // Second request with confirm token (or original URL)
        downloadFromUrl(downloadUrl, targetFile, onProgress)
    }

    /**
     * Extract Google Drive confirm token from HTML response
     */
    private fun extractGoogleDriveConfirmToken(html: String): String? {
        // Pattern for extracting confirm token from Google Drive HTML
        val patterns = listOf(
            Regex("confirm=([0-9]+)"),
            Regex("\"confirm\"\\s*:\\s*\"([^\"]+)\""),
            Regex("name=\"confirm\"\\s+value=\"([^\"]+)\"")
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    /**
     * Download a file from a direct URL
     */
    private suspend fun downloadFromUrl(
        url: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "📥 Downloading from URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .header("Accept", "*/*")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModelDownloadException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw ModelDownloadException("Empty response body")
            val contentLength = body.contentLength()

            // Check available storage
            val availableSpace = targetFile.parentFile?.freeSpace ?: 0
            if (contentLength > 0 && contentLength > availableSpace) {
                throw ModelDownloadException(
                    "Insufficient storage. Need ${contentLength / (1024*1024)}MB, " +
                    "available ${availableSpace / (1024*1024)}MB"
                )
            }

            var totalRead = 0L
            val buffer = ByteArray(8192)

            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read

                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }
        }

        Log.i(TAG, "✅ Downloaded to ${targetFile.path} (${totalRead / (1024*1024)}MB)")
    }

    /**
     * Copy models from local storage (USB/SD card) to app storage
     */
    suspend fun copyModelsFromLocal(
        localPath: String,
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📁 Copying models from local storage: $localPath")

            val localDir = File(localPath)
            if (!localDir.exists() || !localDir.canRead()) {
                throw ModelDownloadException("Cannot access local path: $localPath")
            }

            var copiedCount = 0
            val totalModels = MODELS.size

            for ((index, model) in MODELS.withIndex()) {
                onProgress((index * 100) / totalModels, model.name)

                val sourceFile = File(localDir, "${model.relativePath}/${model.fileName}")
                val targetFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")

                if (!sourceFile.exists()) {
                    Log.w(TAG, "⚠️ Source file not found: ${sourceFile.path}")
                    continue
                }

                // Create parent directory
                targetFile.parentFile?.mkdirs()

                // Copy file
                sourceFile.copyTo(targetFile, overwrite = true)

                // Extract if needed
                if (model.extract) {
                    extractModel(model, targetFile)
                }

                copiedCount++
                Log.d(TAG, "✅ Copied ${model.name}")
            }

            Log.i(TAG, "✅ Copied $copiedCount/${MODELS.size} models from local storage")
            onComplete()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Local model copy failed", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Extract a zipped model
     */
    private fun extractModel(model: ModelDefinition, zipFile: File) {
        if (!model.extract) return

        Log.d(TAG, "📦 Extracting ${model.name}...")

        try {
            val targetDir = zipFile.parentFile
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry: java.util.zip.ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val entryFile = File(targetDir, entry!!.name)

                    // Security: prevent zip slip
                    val canonicalPath = entryFile.canonicalPath
                    if (!canonicalPath.startsWith(targetDir!!.canonicalPath)) {
                        throw SecurityException("Zip slip detected")
                    }

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                }
            }

            // Delete zip file after extraction
            zipFile.delete()
            Log.i(TAG, "✅ Extracted ${model.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to extract ${model.name}", e)
            throw e
        }
    }

    /**
     * Verify model integrity using SHA256
     */
    private fun verifyModel(model: ModelDefinition, file: File): Boolean {
        if (!file.exists()) return false

        // Check file size (basic validation)
        val fileSize = file.length()
        if (fileSize == 0L) return false

        // If SHA256 is not computed yet, just check file exists and has content
        if (model.sha256 == "compute_after_upload" || model.sha256.isBlank()) {
            Log.d(TAG, "⚠️ SHA256 not set for ${model.name}, skipping hash verification")
            return fileSize > 0
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }

            val hash = digest.digest().joinToString("") {
                "%02x".format(it)
            }

            hash.equals(model.sha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify ${model.name}", e)
            false
        }
    }

    /**
     * Delete all downloaded models
     */
    fun deleteAllModels() {
        try {
            modelsDir.deleteRecursively()
            File(context.filesDir, "voices").deleteRecursively()
            Log.i(TAG, "🗑️ All models deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete models", e)
        }
    }

    /**
     * Get models directory size
     */
    fun getModelsSize(): Long {
        var totalSize = 0L

        for (model in MODELS) {
            val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
            if (modelFile.exists()) {
                totalSize += modelFile.length()
            }
        }

        return totalSize
    }

    /**
     * Get model file path for a specific model
     */
    fun getModelPath(modelName: String): File? {
        val model = MODELS.find { it.name == modelName } ?: return null

        // Check local path first if enabled
        if (shouldUseLocalModels()) {
            val localPath = getLocalModelPath()
            if (localPath != null) {
                val localFile = File(localPath, "${model.relativePath}/${model.fileName}")
                if (localFile.exists()) {
                    return localFile
                }
            }
        }

        return File(context.filesDir, "${model.relativePath}/${model.fileName}")
    }

    /**
     * Generate Google Drive direct download URL from file ID
     */
    fun getGoogleDriveDirectUrl(fileId: String): String {
        return "$GOOGLE_DRIVE_BASE_URL&id=$fileId"
    }

    /**
     * Generate Google Drive folder listing URL
     */
    fun getGoogleDriveFolderUrl(folderId: String): String {
        return "https://drive.google.com/drive/folders/$folderId"
    }
}

/**
 * Model definition
 */
data class ModelDefinition(
    val name: String,
    val fileName: String,
    val googleDriveId: String = "",
    val downloadUrl: String,
    val mirrorUrls: List<String> = emptyList(),
    val relativePath: String,
    val sizeMB: Int,
    val sha256: String,
    val required: Boolean = false,
    val extract: Boolean = false,
    val description: String = ""
)

/**
 * Model status
 */
data class ModelStatus(
    val readyCount: Int,
    val totalCount: Int,
    val totalSizeMB: Int,
    val allModelsReady: Boolean,
    val partialModelsReady: Boolean,
    val missingModels: List<ModelDefinition>
)

/**
 * Custom exception for model download errors
 */
class ModelDownloadException(message: String) : Exception(message)
