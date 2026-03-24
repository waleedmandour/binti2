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
 * Manages downloading and verification of AI models from Backblaze B2.
 * 
 * Models (Total: ~1.4GB):
 * - Wake Word: ya_binti_detector.tflite (~5MB)
 * - ASR: vosk-model-ar-mgb2 (~1.2GB) - Modern Standard Arabic
 * - NLU: egybert_tiny_int8.onnx (~25MB)
 * - TTS: ar-eg-female voice (~80MB)
 * 
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        
        // Backblaze B2 Configuration
        // Replace with your actual B2 bucket URL
        private const val B2_BASE_URL = 
            "https://f001.backblazeb2.com/file/binti2-models"
        
        // Manifest URL
        private const val MANIFEST_URL = "$B2_BASE_URL/manifest.json"
        
        // Model definitions with actual B2 URLs
        val MODELS = listOf(
            ModelDefinition(
                name = "Wake Word Detector",
                fileName = "ya_binti_detector.tflite",
                downloadUrl = "$B2_BASE_URL/wake/ya_binti_detector.tflite",
                relativePath = "models/wake",
                sizeMB = 5,
                sha256 = "compute_after_upload",
                required = true,
                description = "Detects 'يا بنتي' wake word"
            ),
            ModelDefinition(
                name = "Arabic ASR (Vosk MGB2)",
                fileName = "vosk-model-ar-mgb2.zip",
                downloadUrl = "$B2_BASE_URL/asr/vosk-model-ar-mgb2.zip",
                relativePath = "models",
                sizeMB = 1247,
                sha256 = "compute_after_upload",
                required = true,
                extract = true,
                description = "Arabic speech recognition model"
            ),
            ModelDefinition(
                name = "Intent Classifier (EgyBERT)",
                fileName = "egybert_tiny_int8.onnx",
                downloadUrl = "$B2_BASE_URL/nlu/egybert_tiny_int8.onnx",
                relativePath = "models/nlu",
                sizeMB = 25,
                sha256 = "compute_after_upload",
                required = true,
                description = "Egyptian Arabic intent classification"
            ),
            ModelDefinition(
                name = "Egyptian TTS Voice",
                fileName = "ar-eg-female.zip",
                downloadUrl = "$B2_BASE_URL/tts/ar-eg-female.zip",
                relativePath = "voices",
                sizeMB = 80,
                sha256 = "compute_after_upload",
                required = false,
                extract = true,
                description = "Egyptian female voice for TTS"
            ),
            ModelDefinition(
                name = "Intent Patterns",
                fileName = "dilink_intent_map.json",
                downloadUrl = "$B2_BASE_URL/nlp/dilink_intent_map.json",
                relativePath = "assets/commands",
                sizeMB = 0,
                sha256 = "compute_after_upload",
                required = true,
                description = "Intent patterns for command matching"
            )
        )
    }

    // HTTP client with longer timeouts for large files
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Model directory in app's files directory
    private val modelsDir = File(context.filesDir, "models")

    /**
     * Check the status of all models
     */
    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L
        
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
     * Download all models
     */
    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🚀 Starting model download from Backblaze B2...")
            
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
                
                // Download model with progress
                downloadModel(model, modelFile) { progress ->
                    val overallProgress = ((index * 100) + progress) / totalModels
                    onProgress(overallProgress, model.name)
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
     * Download a single model with progress tracking
     */
    private suspend fun downloadModel(
        model: ModelDefinition,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "📥 Downloading: ${model.name}")
        Log.d(TAG, "   URL: ${model.downloadUrl}")
        Log.d(TAG, "   Size: ~${model.sizeMB}MB")
        
        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "Binti/1.0 Android")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModelDownloadException(
                    "Download failed for ${model.name}: HTTP ${response.code}"
                )
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
        
        Log.i(TAG, "✅ Downloaded ${model.name} (${totalRead / (1024*1024)}MB)")
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
        return File(context.filesDir, "${model.relativePath}/${model.fileName}")
    }
}

/**
 * Model definition
 */
data class ModelDefinition(
    val name: String,
    val fileName: String,
    val downloadUrl: String,
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
