package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Model Download Manager
 * 
 * Manages downloading and verification of AI models from GitHub Releases.
 * 
 * Models:
 * - Wake Word: ya_binti_detector.tflite (~5MB)
 * - ASR: vosk-model-egyptian (~50MB)
 * - NLU: egybert_tiny_int8.tflite (~30MB)
 * - TTS: ar-EG-female voice (~80MB)
 * 
 * Total: ~165MB compressed
 * 
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        
        // GitHub Releases base URL
        private const val GITHUB_RELEASES_URL = 
            "https://github.com/waleedmandour/binti2/releases/download"
        
        // Model version
        private const val MODEL_VERSION = "v1.0.0"
        
        // Model definitions
        val MODELS = listOf(
            ModelDefinition(
                name = "Wake Word Detector",
                fileName = "ya_binti_detector.tflite",
                relativePath = "models/wake",
                sizeMB = 5,
                sha256 = "placeholder_sha256",
                required = true
            ),
            ModelDefinition(
                name = "Egyptian ASR",
                fileName = "vosk-model-egyptian.zip",
                relativePath = "models",
                sizeMB = 50,
                sha256 = "placeholder_sha256",
                required = true,
                extract = true
            ),
            ModelDefinition(
                name = "Intent Classifier",
                fileName = "egybert_tiny_int8.tflite",
                relativePath = "models/nlu",
                sizeMB = 30,
                sha256 = "placeholder_sha256",
                required = true
            ),
            ModelDefinition(
                name = "Egyptian TTS Voice",
                fileName = "ar-eg-female.zip",
                relativePath = "voices",
                sizeMB = 80,
                sha256 = "placeholder_sha256",
                required = false,
                extract = true
            )
        )
    }

    // HTTP client
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // Model directory
    private val modelsDir = File(context.filesDir, "models")

    /**
     * Check the status of all models
     */
    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L
        
        for (model in MODELS) {
            val modelFile = File(modelsDir, "${model.relativePath}/${model.fileName}")
            if (modelFile.exists() && verifyModel(model, modelFile)) {
                readyCount++
                totalSize += modelFile.length()
            }
        }
        
        val requiredReady = MODELS.filter { it.required }.count { model ->
            val modelFile = File(modelsDir, "${model.relativePath}/${model.fileName}")
            modelFile.exists() && verifyModel(model, modelFile)
        }
        
        ModelStatus(
            readyCount = readyCount,
            totalCount = MODELS.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == MODELS.size,
            partialModelsReady = requiredReady == MODELS.count { it.required },
            missingModels = MODELS.filter { model ->
                val modelFile = File(modelsDir, "${model.relativePath}/${model.fileName}")
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
            Log.i(TAG, "Starting model download...")
            
            // Ensure models directory exists
            modelsDir.mkdirs()
            
            var totalProgress = 0
            val totalModels = MODELS.size
            
            for ((index, model) in MODELS.withIndex()) {
                onProgress(
                    (totalProgress * 100) / (totalModels * 100),
                    model.name
                )
                
                val modelFile = File(modelsDir, "${model.relativePath}/${model.fileName}")
                
                // Skip if already downloaded and verified
                if (modelFile.exists() && verifyModel(model, modelFile)) {
                    Log.d(TAG, "Model ${model.name} already exists, skipping")
                    totalProgress += 100
                    continue
                }
                
                // Create parent directory
                modelFile.parentFile?.mkdirs()
                
                // Download model
                downloadModel(model, modelFile) { progress ->
                    val overallProgress = ((index * 100) + progress) / totalModels
                    onProgress(overallProgress, model.name)
                }
                
                // Extract if needed
                if (model.extract) {
                    extractModel(model, modelFile)
                }
                
                totalProgress += 100
            }
            
            Log.i(TAG, "✅ All models downloaded successfully")
            onComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Download a single model
     */
    private suspend fun downloadModel(
        model: ModelDefinition,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = "$GITHUB_RELEASES_URL/$MODEL_VERSION/${model.fileName}"
        Log.d(TAG, "Downloading from: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            
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
        
        Log.d(TAG, "Downloaded ${model.name} to ${targetFile.absolutePath}")
    }

    /**
     * Extract a zipped model
     */
    private fun extractModel(model: ModelDefinition, zipFile: File) {
        if (!model.extract) return
        
        Log.d(TAG, "Extracting ${model.name}...")
        
        try {
            val targetDir = zipFile.parentFile
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry: java.util.zip.ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val entryFile = File(targetDir, entry!!.name)
                    
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
            Log.d(TAG, "Extracted ${model.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ${model.name}", e)
            throw e
        }
    }

    /**
     * Verify model integrity using SHA256
     */
    private fun verifyModel(model: ModelDefinition, file: File): Boolean {
        if (!file.exists()) return false
        
        // Skip verification for placeholder SHA256
        if (model.sha256 == "placeholder_sha256") {
            return file.length() > 0
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
            Log.i(TAG, "All models deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete models", e)
        }
    }

    /**
     * Get models directory size
     */
    fun getModelsSize(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}

/**
 * Model definition
 */
data class ModelDefinition(
    val name: String,
    val fileName: String,
    val relativePath: String,
    val sizeMB: Int,
    val sha256: String,
    val required: Boolean = false,
    val extract: Boolean = false
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
