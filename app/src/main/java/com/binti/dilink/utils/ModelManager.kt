package com.binti.dilink.utils

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * ModelManager - AI Model Download Manager (DownloadManager Implementation)
 *
 * Manages downloading and verification of AI models using Android's built-in
 * [DownloadManager] instead of OkHttp. This ensures better system-level
 * integration, automatic retry on connectivity changes, and proper notification
 * handling for large downloads.
 *
 * Features:
 * - Downloads models via GitHub Releases using [DownloadManager]
 * - Visible notification with progress and completion status
 * - Progress polling every 500ms
 * - Automatic ZIP extraction after successful download
 * - SharedPreferences-based download state persistence
 * - Sealed-class based download state machine
 *
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        /** SharedPreferences file name (shared with other components) */
        private const val PREFS_NAME = "binti_prefs"

        /** GitHub Releases base URL for model downloads */
        private const val GITHUB_RELEASES_BASE_URL =
            "https://github.com/waleedmandour/binti2/releases/download"

        /** The release tag for the current model bundle */
        private const val MODEL_RELEASE_TAG = "v2.2.0-beta"

        /** Progress polling interval in milliseconds */
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
    }

    /** System DownloadManager instance */
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    /** SharedPreferences for persisting download state */
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Models directory inside app internal storage */
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    /** App-specific external downloads directory */
    private val externalDownloadsDir: File by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    }

    // ================================================================== //
    //  Model definitions                                                  //
    // ================================================================== //

    /**
     * Defines a single downloadable model.
     *
     * @property name         Human-readable model name.
     * @property fileName     Archive file name (e.g. "vosk-model-ar-mgb2-0.4.zip").
     * @property downloadUrl  Full download URL (built from GitHub Releases base + tag).
     * @property relativePath Directory inside app storage where the extracted model lives.
     * @property sizeMB       Expected file size in megabytes (for UI display).
     * @property sha256       SHA-256 checksum of the archive (empty = skip verification).
     * @property required     Whether the model is required for core functionality.
     * @property extract      Whether the downloaded file should be ZIP-extracted.
     * @property description  Short user-facing description of the model.
     */
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

    /** The single model to download: Vosk Arabic MGB2 */
    private val models: List<ModelDefinition> = listOf(
        ModelDefinition(
            name = "Vosk Arabic Model",
            fileName = "vosk-model-ar-mgb2-0.4.zip",
            downloadUrl = "$GITHUB_RELEASES_BASE_URL/$MODEL_RELEASE_TAG/vosk-model-ar-mgb2-0.4.zip",
            relativePath = "models",
            sizeMB = 318,
            sha256 = "",
            required = true,
            extract = true,
            description = "Arabic speech recognition model (Vosk MGB2) – 318MB"
        )
    )

    // ================================================================== //
    //  Download state                                                     //
    // ================================================================== //

    /**
     * Sealed class representing the current state of a model download.
     */
    sealed class DownloadInfo {
        /** Download has not started yet. */
        data object Pending : DownloadInfo()

        /** Download is in progress. */
        data class Running(
            val progress: Int,       // 0–100
            val bytesDownloaded: Long,
            val bytesTotal: Long
        ) : DownloadInfo()

        /** Download finished successfully. */
        data class Completed(
            val filePath: String
        ) : DownloadInfo()

        /** Download failed. */
        data class Failed(
            val reason: String
        ) : DownloadInfo()
    }

    /**
     * Represents the overall status of all models.
     */
    data class ModelStatus(
        val readyCount: Int,
        val totalCount: Int,
        val totalSizeMB: Int,
        val allModelsReady: Boolean,
        val partialModelsReady: Boolean,
        val missingModels: List<ModelDefinition>
    )

    // ================================================================== //
    //  Public API                                                         //
    // ================================================================== //

    /**
     * Check which models are already extracted and ready to use.
     *
     * @return A [ModelStatus] summarising readiness.
     */
    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L

        for (model in models) {
            val extractDir = File(context.filesDir, model.relativePath)
            val isReady = extractDir.exists() &&
                    extractDir.isDirectory &&
                    extractDir.list()?.isNotEmpty() == true

            if (isReady) {
                readyCount++
                totalSize += model.sizeMB.toLong() * 1024 * 1024
            }
        }

        ModelStatus(
            readyCount = readyCount,
            totalCount = models.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == models.size,
            partialModelsReady = readyCount > 0,
            missingModels = models.filter { model ->
                val extractDir = File(context.filesDir, model.relativePath)
                !(extractDir.exists() &&
                        extractDir.isDirectory &&
                        extractDir.list()?.isNotEmpty() == true)
            }
        )
    }

    /**
     * Start downloading all missing models using [DownloadManager].
     *
     * Each model is enqueued as a separate download. Progress is polled
     * every [PROGRESS_POLL_INTERVAL_MS] and reported via callbacks.
     * After a download completes, the ZIP is extracted (if [ModelDefinition.extract]
     * is true).
     *
     * @param onProgress   Called with (overallProgress 0–100, modelName).
     * @param onComplete   Called when all models have been downloaded and extracted.
     * @param onError      Called with an error message on failure.
     */
    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
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
                Log.i(TAG, "📥 Starting download: ${model.name} (${model.sizeMB}MB)")

                // Enqueue download via DownloadManager
                val downloadId = enqueueDownload(model)

                if (downloadId == -1L) {
                    val msg = "Failed to enqueue download for ${model.name}"
                    Log.e(TAG, msg)
                    if (model.required) {
                        onError(msg)
                        return@withContext
                    }
                    continue
                }

                // Store the download ID for later reference
                prefs.edit().putLong("dl_${model.fileName}", downloadId).apply()

                // Poll progress
                val file = pollDownloadProgress(
                    downloadId = downloadId,
                    modelName = model.name,
                    modelIndex = index,
                    totalModels = missingModels.size,
                    onProgress = onProgress
                )

                if (file != null) {
                    // Download succeeded
                    if (model.extract) {
                        Log.d(TAG, "Extracting ${model.fileName}…")
                        extractModel(model, file)
                        file.delete() // Remove ZIP after extraction
                    }
                    // Clean up download ID from prefs
                    prefs.edit().remove("dl_${model.fileName}").apply()
                } else {
                    // Download failed
                    val msg = "Download failed for ${model.name}"
                    Log.e(TAG, msg)
                    if (model.required) {
                        onError(msg)
                        return@withContext
                    }
                }
            }

            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Model download error", e)
            onError(e.message ?: "Unknown error during model download")
        }
    }

    /**
     * Query the current download state for a specific model.
     *
     * @param model The [ModelDefinition] to query.
     * @return The current [DownloadInfo] state.
     */
    fun getDownloadState(model: ModelDefinition): DownloadInfo {
        val downloadId = prefs.getLong("dl_${model.fileName}", -1L)
        if (downloadId == -1L) return DownloadInfo.Pending

        return queryDownloadInfo(downloadId, model.name)
    }

    // ================================================================== //
    //  DownloadManager helpers                                            //
    // ================================================================== //

    /**
     * Enqueue a model download via [DownloadManager].
     *
     * @return The enqueue download ID, or -1 on failure.
     */
    private fun enqueueDownload(model: ModelDefinition): Long {
        return try {
            val uri = Uri.parse(model.downloadUrl)
            val destinationFile = File(externalDownloadsDir, model.fileName)

            // Delete any previous partial download
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(uri).apply {
                setTitle("Binti – ${model.name}")
                setDescription("جَارِي تَحْمِيل: ${model.description}")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    model.fileName
                )
                // Allow download over any network type
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                            DownloadManager.Request.NETWORK_MOBILE
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

    /**
     * Poll the download progress until it finishes or fails.
     *
     * Checks the [DownloadManager] query every [PROGRESS_POLL_INTERVAL_MS].
     *
     * @param downloadId   The download ID returned by [DownloadManager.enqueue].
     * @param modelName    Name of the model (for logging and callback).
     * @param modelIndex   Index of this model in the overall download batch.
     * @param totalModels  Total number of models in the batch.
     * @param onProgress   Progress callback.
     * @return The downloaded [File] on success, or `null` on failure.
     */
    private suspend fun pollDownloadProgress(
        downloadId: Long,
        modelName: String,
        modelIndex: Int,
        totalModels: Int,
        onProgress: (Int, String) -> Unit
    ): File? {
        while (true) {
            val info = queryDownloadInfo(downloadId, modelName)

            when (info) {
                is DownloadInfo.Pending -> {
                    // Still pending, keep polling
                }
                is DownloadInfo.Running -> {
                    val perModelProgress = info.progress
                    val overallProgress = ((modelIndex * 100) + perModelProgress) / totalModels
                    onProgress(overallProgress, modelName)
                }
                is DownloadInfo.Completed -> {
                    return File(info.filePath)
                }
                is DownloadInfo.Failed -> {
                    Log.e(TAG, "Download failed: ${info.reason}")
                    return null
                }
            }

            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }

    /**
     * Query the [DownloadManager] for the current status of a download.
     */
    private fun queryDownloadInfo(downloadId: Long, modelName: String): DownloadInfo {
        val query = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null

        return try {
            cursor = downloadManager.query(query)
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "DownloadManager query returned empty cursor for $downloadId")
                return DownloadInfo.Pending
            }

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            if (statusIndex == -1) return DownloadInfo.Pending

            val status = cursor.getInt(statusIndex)
            val bytesDownloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
            val bytesTotal = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
            val localUri = if (localUriIndex >= 0) cursor.getString(localUriIndex) else null

            when (status) {
                DownloadManager.STATUS_PENDING -> DownloadInfo.Pending

                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (bytesTotal > 0) {
                        ((bytesDownloaded * 100) / bytesTotal).toInt()
                    } else {
                        0
                    }
                    DownloadInfo.Running(
                        progress = progress,
                        bytesDownloaded = bytesDownloaded,
                        bytesTotal = bytesTotal
                    )
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val filePath = localUri?.let { Uri.parse(it).path }
                    if (filePath != null) {
                        Log.i(TAG, "✅ Download complete: $modelName → $filePath")
                        DownloadInfo.Completed(filePath = filePath)
                    } else {
                        // Fallback: derive path from known destination
                        val fallbackFile = File(externalDownloadsDir, modelName)
                        if (fallbackFile.exists()) {
                            DownloadInfo.Completed(filePath = fallbackFile.absolutePath)
                        } else {
                            DownloadInfo.Failed(reason = "Download completed but file path unknown")
                        }
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = if (reasonIndex >= 0) {
                        when (cursor.getInt(reasonIndex)) {
                            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
                            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
                            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                            DownloadManager.ERROR_FILE_ERROR -> "File error"
                            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
                            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                            else -> "Error code: ${cursor.getInt(reasonIndex)}"
                        }
                    } else {
                        "Unknown failure reason"
                    }
                    Log.e(TAG, "❌ Download failed ($modelName): $reason")
                    DownloadInfo.Failed(reason = reason)
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

    // ================================================================== //
    //  ZIP extraction                                                     //
    // ================================================================== //

    /**
     * Extract a ZIP archive to the model's target directory.
     *
     * The target directory is derived from [ModelDefinition.relativePath]
     * inside the app's internal files directory.
     *
     * @param model   The model definition.
     * @param zipFile The downloaded ZIP file.
     */
    private fun extractModel(model: ModelDefinition, zipFile: File) {
        val targetDir = File(context.filesDir, model.relativePath)
        targetDir.mkdirs()

        Log.d(TAG, "Extracting ${zipFile.name} → ${targetDir.absolutePath}")

        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry: java.util.zip.ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry!!
                    val file = File(targetDir, currentEntry.name)

                    // Security: prevent zip-slip
                    if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        Log.w(TAG, "Skipping potentially malicious entry: ${currentEntry.name}")
                        continue
                    }

                    if (currentEntry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { out ->
                            zis.copyTo(out, bufferSize = 8192)
                        }
                    }
                    zis.closeEntry()
                }
            }
            Log.i(TAG, "✅ Extraction complete: ${model.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Extraction failed for ${model.name}", e)
            throw e
        }
    }

    // ================================================================== //
    //  Utility                                                            //
    // ================================================================== //

    /**
     * Cancel all active model downloads.
     */
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

    /**
     * Get the list of all model definitions.
     */
    fun getModels(): List<ModelDefinition> = models.toList()
}
