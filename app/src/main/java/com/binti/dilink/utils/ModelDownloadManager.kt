package com.binti.dilink.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DownloadProgress(
    val currentFile: String,
    val percentage: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

class ModelDownloadManager(private val context: Context) {
    private val _progress = MutableStateFlow(DownloadProgress("", 0, 0, 0))
    val downloadProgress: StateFlow<DownloadProgress> = _progress
    
    suspend fun downloadRequiredModels(): Result<Unit> = Result.success(Unit)
}
