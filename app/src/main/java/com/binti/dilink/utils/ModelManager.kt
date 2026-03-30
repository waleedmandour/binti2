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
import java.util.concurrent.TimeUnit

/**
 * Model Download Manager - Enhanced for Google Drive
 *
 * Manages downloading and verification of AI models.
 *
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // Google Drive Configuration
        private const val GOOGLE_DRIVE_BASE_URL = "https://drive.google.com/uc?export=download"

        // Correct Google Drive File IDs provided by user
        private const val VOSK_MODEL_FILE_ID = "1bK1-pUCH5xykvKdB7nB7mZ_1wBKH05qZ"

        // Model definitions
        val MODELS = listOf(
            ModelDefinition(
                name = "Vosk Arabic Model",
                fileName = "vosk-model-ar-mgb2-0.4.zip",
                googleDriveId = VOSK_MODEL_FILE_ID,
                downloadUrl = "$GOOGLE_DRIVE_BASE_URL&id=$VOSK_MODEL_FILE_ID",
                relativePath = "models",
                sizeMB = 1247,
                sha256 = "",
                required = true,
                extract = true,
                description = "Arabic speech recognition model (Vosk MGB2)"
            )
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val modelsDir = File(context.filesDir, "models")

    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L

        for (model in MODELS) {
            val extractDir = File(context.filesDir, model.relativePath)
            val isReady = extractDir.exists() && extractDir.isDirectory && extractDir.list()?.isNotEmpty() == true

            if (isReady) {
                readyCount++
                totalSize += model.sizeMB.toLong() * 1024 * 1024
            }
        }

        ModelStatus(
            readyCount = readyCount,
            totalCount = MODELS.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == MODELS.size,
            partialModelsReady = readyCount > 0,
            missingModels = MODELS.filter { model ->
                val extractDir = File(context.filesDir, model.relativePath)
                !(extractDir.exists() && extractDir.isDirectory && extractDir.list()?.isNotEmpty() == true)
            }
        )
    }

    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            modelsDir.mkdirs()
            val missingModels = checkModelsStatus().missingModels
            
            for ((index, model) in missingModels.withIndex()) {
                if (model.googleDriveId.isEmpty()) continue

                val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
                modelFile.parentFile?.mkdirs()

                try {
                    downloadFromGoogleDrive(model, modelFile) { progress ->
                        val overallProgress = ((index * 100) + progress) / missingModels.size
                        onProgress(overallProgress, model.name)
                    }
                    if (model.extract) {
                        extractModel(model, modelFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download ${model.name}", e)
                    if (model.required) throw e
                }
            }
            onComplete()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadFromGoogleDrive(
        model: ModelDefinition,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadUrl = "$GOOGLE_DRIVE_BASE_URL&id=${model.googleDriveId}"
        
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

            val body = response.body ?: throw Exception("Empty body")
            val contentType = response.header("Content-Type", "") ?: ""
            
            if (contentType.contains("text/html")) {
                val html = body.string()
                val confirmToken = extractConfirmToken(html)
                if (confirmToken != null) {
                    val confirmedUrl = "$downloadUrl&confirm=$confirmToken"
                    val confirmRequest = Request.Builder().url(confirmedUrl).build()
                    httpClient.newCall(confirmRequest).execute().use { confirmedResponse ->
                        saveBody(confirmedResponse, targetFile, onProgress)
                    }
                } else {
                    throw Exception("Google Drive virus scan warning bypass failed")
                }
            } else {
                saveBody(response, targetFile, onProgress)
            }
        }
    }

    private fun saveBody(response: okhttp3.Response, targetFile: File, onProgress: (Int) -> Unit) {
        val body = response.body ?: throw Exception("Empty body")
        val length = body.contentLength()
        val buffer = ByteArray(8192)
        var total = 0L
        FileOutputStream(targetFile).use { out ->
            body.byteStream().use { inp ->
                var read: Int
                while (inp.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    total += read
                    if (length > 0) onProgress(((total * 100) / length).toInt())
                }
            }
        }
    }

    private fun extractConfirmToken(html: String): String? {
        val regex = Regex("confirm=([0-9A-Za-z_]+)")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun extractModel(model: ModelDefinition, zipFile: File) {
        val targetDir = zipFile.parentFile ?: return
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: java.util.zip.ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val file = File(targetDir, entry!!.name)
                if (entry!!.isDirectory) file.mkdirs()
                else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        zipFile.delete()
    }
}

data class ModelDefinition(
    val name: String,
    val fileName: String,
    val googleDriveId: String,
    val downloadUrl: String,
    val relativePath: String,
    val sizeMB: Int,
    val sha256: String,
    val required: Boolean = true,
    val extract: Boolean = false,
    val description: String = ""
)

data class ModelStatus(
    val readyCount: Int,
    val totalCount: Int,
    val totalSizeMB: Int,
    val allModelsReady: Boolean,
    val partialModelsReady: Boolean,
    val missingModels: List<ModelDefinition>
)
