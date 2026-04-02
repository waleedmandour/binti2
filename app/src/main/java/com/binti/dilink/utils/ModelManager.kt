package com.binti.dilink.utils

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val PREFS_NAME = "binti_prefs"
        private const val GITHUB_RELEASES_BASE_URL =
            "https://github.com/waleedmandour/binti2/releases/download"
        private const val MODEL_RELEASE_TAG = "v2.2.0-beta"
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    private val externalDownloadsDir: File by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data types
    // ──────────────────────────────────────────────────────────────────────────

    data class ModelDefinition(
        val name: String,
        val fileName: String,
        val downloadUrl: String,
        val relativePath: String,
        val sizeMB: Int,
        val sha256: String = "",
        val required: Boolean = true,
        val extract: Boolean = false,
        val description: String = ""
    )

    private val models: List<ModelDefinition> = listOf(
        ModelDefinition(
            name        = "Vosk Arabic Model",
            fileName    = "vosk-model-ar-mgb2-0.4.zip",
            downloadUrl = "$GITHUB_RELEASES_BASE_URL/$MODEL_RELEASE_TAG/vosk-model-ar-mgb2-0.4.zip",
            relativePath = "models",
            sizeMB      = 318,
            required    = true,
            extract     = true,
            description = "Arabic speech recognition model (Vosk MGB2) – 318MB"
        )
    )

    sealed class DownloadInfo {
        data object Pending : DownloadInfo()
        data class Running(val progress: Int, val bytesDownloaded: Long, val bytesTotal: Long) : DownloadInfo()
        data class Completed(val filePath: String) : DownloadInfo()
        data class Failed(val reason: String) : DownloadInfo()
    }

    data class ModelStatus(
        val readyCount: Int,
        val totalCount: Int,
        val totalSizeMB: Int,
        val allModelsReady: Boolean,
        val partialModelsReady: Boolean,
        val missingModels: List<ModelDefinition>
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0

        for (model in models) {
            val extractDir = File(context.filesDir, model.relativePath)
            if (extractDir.exists() && extractDir.isDirectory &&
                extractDir.list()?.isNotEmpty() == true) {
                readyCount++
            }
        }

        val missingModels = models.filter { model ->
            val d = File(context.filesDir, model.relativePath)
            !(d.exists() && d.isDirectory && d.list()?.isNotEmpty() == true)
        }

        ModelStatus(
            readyCount         = readyCount,
            totalCount         = models.size,
            // FIX #1 — original computed totalSize by summing sizeMB of READY models
            // then converting back, losing precision. Use missing list for UI clarity.
            totalSizeMB        = missingModels.sumOf { it.sizeMB },
            allModelsReady     = readyCount == models.size,
            partialModelsReady = readyCount > 0,
            missingModels      = missingModels
        )
    }

    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError:    (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            modelsDir.mkdirs()
            val missingModels = checkModelsStatus().missingModels

            if (missingModels.isEmpty()) {
                Log.i(TAG, "All models already available")
                onComplete()
                return@withContext
            }

            for ((index, model) in missingModels.withIndex()) {
                Log.i(TAG, "📥 Downloading: ${model.name} (${model.sizeMB}MB)")

                val downloadId = enqueueDownload(model)
                if (downloadId == -1L) {
                    val msg = "Failed to enqueue ${model.name}"
                    Log.e(TAG, msg)
                    if (model.required) { onError(msg); return@withContext }
                    continue
                }

                prefs.edit().putLong("dl_${model.fileName}", downloadId).apply()

                val file = pollDownloadProgress(downloadId, model.name, index, missingModels.size, onProgress)

                if (file != null) {
                    if (model.extract) {
                        Log.d(TAG, "Extracting ${model.fileName}…")
                        extractModel(model, file)
                        // FIX #2 — delete ZIP only after successful extraction;
                        // original deleted even if extractModel() threw, leaving no
                        // ZIP and no extracted content — unrecoverable without re-download.
                        file.delete()
                    }
                    prefs.edit().remove("dl_${model.fileName}").apply()
                } else {
                    val msg = "Download failed for ${model.name}"
                    Log.e(TAG, msg)
                    if (model.required) { onError(msg); return@withContext }
                }
            }

            onComplete()

        } catch (e: Exception) {
            Log.e(TAG, "Model download error", e)
            onError(e.message ?: "Unknown error during model download")
        }
    }

    fun getDownloadState(model: ModelDefinition): DownloadInfo {
        val downloadId = prefs.getLong("dl_${model.fileName}", -1L)
        if (downloadId == -1L) return DownloadInfo.Pending
        return queryDownloadInfo(downloadId, model.name)
    }

    fun cancelAllDownloads() {
        for (model in models) {
            val downloadId = prefs.getLong("dl_${model.fileName}", -1L)
            if (downloadId != -1L) {
                downloadManager.remove(downloadId)
                prefs.edit().remove("dl_${model.fileName}").apply()
                Log.i(TAG, "Cancelled download: ${model.name}")
            }
        }
    }

    fun getModels(): List<ModelDefinition> = models.toList()

    // ──────────────────────────────────────────────────────────────────────────
    // DownloadManager helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun enqueueDownload(model: ModelDefinition): Long {
        return try {
            val destinationFile = File(externalDownloadsDir, model.fileName)
            if (destinationFile.exists()) destinationFile.delete()

            val request = DownloadManager.Request(Uri.parse(model.downloadUrl)).apply {
                setTitle("Binti – ${model.name}")
                setDescription(model.description)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, model.fileName)
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverRoaming(true)
                setMimeType("application/zip")
            }

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue download", e)
            -1L
        }
    }

    private suspend fun pollDownloadProgress(
        downloadId:  Long,
        modelName:   String,
        modelIndex:  Int,
        totalModels: Int,
        onProgress:  (Int, String) -> Unit
    ): File? {
        // FIX #3 — `while(true)` with no coroutine-cooperative exit; if the
        // download is cancelled or the coroutine scope is cancelled, this loop
        // runs forever. Added isActive check.
        while (isActive) {
            when (val info = queryDownloadInfo(downloadId, modelName)) {
                is DownloadInfo.Pending  -> { /* keep polling */ }
                is DownloadInfo.Running  -> {
                    val overall = ((modelIndex * 100) + info.progress) / totalModels
                    onProgress(overall, modelName)
                }
                is DownloadInfo.Completed -> return File(info.filePath)
                is DownloadInfo.Failed    -> {
                    Log.e(TAG, "Download failed: ${info.reason}")
                    return null
                }
            }
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
        // Coroutine was cancelled while polling
        Log.w(TAG, "Download polling cancelled for $modelName")
        return null
    }

    private fun queryDownloadInfo(downloadId: Long, modelName: String): DownloadInfo {
        val query  = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null

        return try {
            cursor = downloadManager.query(query)
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "Empty cursor for download $downloadId")
                return DownloadInfo.Pending
            }

            val statusIndex          = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex          = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIndex      = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIndex        = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            if (statusIndex == -1) return DownloadInfo.Pending

            val status         = cursor.getInt(statusIndex)
            val bytesDownloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
            val bytesTotal     = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
            val localUri       = if (localUriIndex >= 0) cursor.getString(localUriIndex) else null

            when (status) {
                DownloadManager.STATUS_PENDING -> DownloadInfo.Pending

                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                    DownloadInfo.Running(progress, bytesDownloaded, bytesTotal)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val filePath = localUri?.let { Uri.parse(it).path }
                    if (filePath != null) {
                        Log.i(TAG, "✅ Download complete: $modelName → $filePath")
                        DownloadInfo.Completed(filePath)
                    } else {
                        // FIX #4 — fallback used model.name instead of model.fileName
                        // to construct the fallback path, so the File would never exist.
                        val fallback = File(externalDownloadsDir,
                            localUri?.substringAfterLast('/') ?: "")
                        if (fallback.exists()) DownloadInfo.Completed(fallback.absolutePath)
                        else DownloadInfo.Failed("Download complete but file path unknown")
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = if (reasonIndex >= 0) {
                        when (cursor.getInt(reasonIndex)) {
                            DownloadManager.ERROR_CANNOT_RESUME        -> "Cannot resume"
                            DownloadManager.ERROR_DEVICE_NOT_FOUND     -> "Device not found"
                            DownloadManager.ERROR_FILE_ALREADY_EXISTS  -> "File already exists"
                            DownloadManager.ERROR_FILE_ERROR           -> "File error"
                            DownloadManager.ERROR_HTTP_DATA_ERROR      -> "HTTP data error"
                            DownloadManager.ERROR_INSUFFICIENT_SPACE   -> "Insufficient storage space"
                            DownloadManager.ERROR_TOO_MANY_REDIRECTS   -> "Too many redirects"
                            DownloadManager.ERROR_UNHANDLED_HTTP_CODE  -> "Unhandled HTTP code"
                            else -> "Error code ${cursor.getInt(reasonIndex)}"
                        }
                    } else "Unknown failure reason"
                    Log.e(TAG, "❌ Download failed ($modelName): $reason")
                    DownloadInfo.Failed(reason)
                }

                else -> DownloadInfo.Pending
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying download status", e)
            DownloadInfo.Pending
        } finally {
            cursor?.close()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ZIP extraction
    // ──────────────────────────────────────────────────────────────────────────

    private fun extractModel(model: ModelDefinition, zipFile: File) {
        val targetDir = File(context.filesDir, model.relativePath)
        targetDir.mkdirs()
        Log.d(TAG, "Extracting ${zipFile.name} → ${targetDir.absolutePath}")

        // FIX #5 — if extraction fails mid-way, a partial directory exists and
        // checkModelsStatus() will report it as ready (non-empty dir). Extract
        // into a temp dir first, then atomically rename on success.
        val tempDir = File(context.filesDir, "${model.relativePath}_tmp")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)

                    // Zip-slip guard
                    if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                        Log.w(TAG, "Skipping zip-slip entry: ${entry.name}")
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out, bufferSize = 8192) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Atomic swap: remove old dir, rename temp into place
            targetDir.deleteRecursively()
            if (!tempDir.renameTo(targetDir)) {
                // renameTo can fail across filesystems; fall back to copy
                tempDir.copyRecursively(targetDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            Log.i(TAG, "✅ Extraction complete: ${model.name}")

        } catch (e: Exception) {
            tempDir.deleteRecursively()   // clean up partial extraction
            Log.e(TAG, "❌ Extraction failed for ${model.name}", e)
            throw e
        }
    }
}
