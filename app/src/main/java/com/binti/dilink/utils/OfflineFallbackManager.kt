package com.binti.dilink.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline Fallback Manager
 *
 * Enhanced offline mode management for Binti voice assistant.
 * Handles graceful degradation when network is unavailable.
 *
 * Features:
 * - Network state monitoring
 * - Offline-capable command execution
 * - Local command cache
 * - Queued commands for later execution
 * - Automatic sync when online
 * - Fallback responses for unavailable features
 *
 * @author Dr. Waleed Mandour
 */
class OfflineFallbackManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineFallbackManager"
        private const val CACHE_DIR = "offline_cache"
        private const val QUEUE_FILE = "command_queue.json"

        // Offline-capable commands
        val OFFLINE_COMMANDS = setOf(
            "AC_CONTROL",
            "MEDIA",
            "SYSTEM",
            "PHONE",
            "INFO_TIME",
            "INFO_DATE",
            "INFO_BATTERY",
            "INFO_RANGE"
        )

        // Commands requiring internet
        val ONLINE_ONLY_COMMANDS = setOf(
            "NAVIGATION_SEARCH",
            "INFO_WEATHER",
            "CLOUD_ASR",
            "CLOUD_TTS"
        )
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _connectionType = MutableStateFlow(getConnectionType())
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            _isOnline.value = true
            _connectionType.value = getConnectionType()
            processQueuedCommands()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            _isOnline.value = isNetworkAvailable()
            _connectionType.value = getConnectionType()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _connectionType.value = getConnectionType()
        }
    }

    private val cacheDir = File(context.filesDir, CACHE_DIR)
    private val queueFile = File(cacheDir, QUEUE_FILE)
    private val isProcessingQueue = AtomicBoolean(false)

    // Callbacks
    private var onOnlineCallback: (() -> Unit)? = null
    private var onOfflineCallback: (() -> Unit)? = null

    init {
        cacheDir.mkdirs()
        registerNetworkCallback()
    }

    /**
     * Register network callback for monitoring
     */
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.i(TAG, "Network monitoring registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Unregister network callback
     */
    fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    /**
     * Set online/offline callbacks
     */
    fun setCallbacks(
        onOnline: (() -> Unit)? = null,
        onOffline: (() -> Unit)? = null
    ) {
        onOnlineCallback = onOnline
        onOfflineCallback = onOffline
    }

    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }

    /**
     * Get current connection type
     */
    private fun getConnectionType(): ConnectionType {
        return try {
            val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
                else -> ConnectionType.OTHER
            }
        } catch (e: Exception) {
            ConnectionType.NONE
        }
    }

    /**
     * Check if a command can be executed offline
     */
    fun canExecuteOffline(commandAction: String): Boolean {
        return commandAction in OFFLINE_COMMANDS
    }

    /**
     * Check if a command requires internet
     */
    fun requiresInternet(commandAction: String): Boolean {
        return commandAction in ONLINE_ONLY_COMMANDS
    }

    /**
     * Get offline fallback response for a command
     */
    fun getOfflineFallback(commandAction: String): String {
        return when (commandAction) {
            "NAVIGATION_SEARCH" -> "التنقل مش متاح أوفلاين. حاول تاني لما يكون في نت."
            "INFO_WEATHER" -> "معلومات الطقس مش متاحة أوفلاين."
            "CLOUD_ASR" -> "التعرف على الصوت السحابي مش متاح. جرب الأوامر المحلية."
            else -> "الأمر ده محتاج نت. حاول تاني لما الاتصال يرجع."
        }
    }

    /**
     * Queue a command for later execution when online
     */
    fun queueCommand(command: QueuedCommand): Boolean {
        return try {
            val queue = loadCommandQueue().toMutableList()
            queue.add(command)
            saveCommandQueue(queue)
            Log.i(TAG, "Queued command: ${command.action} (ID: ${command.id})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue command", e)
            false
        }
    }

    /**
     * Load queued commands
     */
    private fun loadCommandQueue(): List<QueuedCommand> {
        if (!queueFile.exists()) return emptyList()

        return try {
            val json = queueFile.readText()
            val jsonArray = org.json.JSONArray(json)

            (0 until jsonArray.length()).map { i ->
                val jsonObj = jsonArray.getJSONObject(i)
                QueuedCommand(
                    id = jsonObj.getString("id"),
                    action = jsonObj.getString("action"),
                    entities = parseEntities(jsonObj.getJSONObject("entities")),
                    timestamp = jsonObj.getLong("timestamp"),
                    retries = jsonObj.getInt("retries")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load command queue", e)
            emptyList()
        }
    }

    /**
     * Save command queue
     */
    private fun saveCommandQueue(queue: List<QueuedCommand>) {
        try {
            val jsonArray = org.json.JSONArray()
            queue.forEach { cmd ->
                jsonArray.put(org.json.JSONObject().apply {
                    put("id", cmd.id)
                    put("action", cmd.action)
                    put("entities", org.json.JSONObject(cmd.entities))
                    put("timestamp", cmd.timestamp)
                    put("retries", cmd.retries)
                })
            }
            queueFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save command queue", e)
        }
    }

    /**
     * Process queued commands when online
     */
    private fun processQueuedCommands() {
        if (!isProcessingQueue.compareAndSet(false, true)) {
            return // Already processing
        }

        try {
            val queue = loadCommandQueue().toMutableList()
            if (queue.isEmpty()) {
                isProcessingQueue.set(false)
                return
            }

            Log.i(TAG, "Processing ${queue.size} queued commands")

            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val cmd = iterator.next()

                // Skip commands that are too old (older than 1 hour)
                if (System.currentTimeMillis() - cmd.timestamp > 3600000) {
                    iterator.remove()
                    Log.d(TAG, "Removed expired command: ${cmd.id}")
                    continue
                }

                // Process the command
                // This would be implemented with a callback to the main service
                // For now, just remove from queue
                iterator.remove()
                Log.d(TAG, "Processed queued command: ${cmd.action}")
            }

            saveCommandQueue(queue)
            onOnlineCallback?.invoke()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued commands", e)
        } finally {
            isProcessingQueue.set(false)
        }
    }

    /**
     * Get number of queued commands
     */
    fun getQueuedCommandCount(): Int {
        return loadCommandQueue().size
    }

    /**
     * Clear all queued commands
     */
    fun clearQueuedCommands() {
        saveCommandQueue(emptyList())
        Log.i(TAG, "Cleared command queue")
    }

    /**
     * Cache data for offline use
     */
    suspend fun cacheData(key: String, data: String, ttlMs: Long = 3600000): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "$key.json")
                val cacheEntry = JSONObject().apply {
                    put("data", data)
                    put("timestamp", System.currentTimeMillis())
                    put("ttl", ttlMs)
                }
                cacheFile.writeText(cacheEntry.toString())
                Log.d(TAG, "Cached data: $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache data: $key", e)
                false
            }
        }
    }

    /**
     * Get cached data
     */
    suspend fun getCachedData(key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "$key.json")
                if (!cacheFile.exists()) return@withContext null

                val json = JSONObject(cacheFile.readText())
                val timestamp = json.getLong("timestamp")
                val ttl = json.getLong("ttl")

                // Check if cache is expired
                if (System.currentTimeMillis() - timestamp > ttl) {
                    cacheFile.delete()
                    Log.d(TAG, "Cache expired: $key")
                    return@withContext null
                }

                json.getString("data")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cached data: $key", e)
                null
            }
        }
    }

    /**
     * Clear expired cache entries
     */
    fun clearExpiredCache() {
        try {
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    try {
                        val json = JSONObject(file.readText())
                        val timestamp = json.optLong("timestamp", 0)
                        val ttl = json.optLong("ttl", Long.MAX_VALUE)

                        if (now - timestamp > ttl) {
                            file.delete()
                            Log.d(TAG, "Deleted expired cache: ${file.name}")
                        }
                    } catch (e: Exception) {
                        // Invalid cache file, delete it
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear expired cache", e)
        }
    }

    /**
     * Get cache size in bytes
     */
    fun getCacheSize(): Long {
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * Clear all cache
     */
    fun clearAllCache() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        cacheDir.mkdirs()
        Log.i(TAG, "Cleared all cache")
    }

    /**
     * Get offline mode status
     */
    fun getOfflineStatus(): OfflineStatus {
        return OfflineStatus(
            isOnline = _isOnline.value,
            connectionType = _connectionType.value,
            queuedCommands = getQueuedCommandCount(),
            cacheSizeBytes = getCacheSize(),
            canUseOfflineASR = checkOfflineASRAvailable(),
            canUseOfflineTTS = checkOfflineTTSAvailable(),
            canUseOfflineNLU = checkOfflineNLUAvailable()
        )
    }

    /**
     * Check if offline ASR is available
     */
    private fun checkOfflineASRAvailable(): Boolean {
        val asrModel = File(context.filesDir, "models/vosk-model-ar-mgb2")
        return asrModel.exists() && asrModel.isDirectory
    }

    /**
     * Check if offline TTS is available
     */
    private fun checkOfflineTTSAvailable(): Boolean {
        val ttsVoice = File(context.filesDir, "voices/ar-eg-female")
        return ttsVoice.exists() && ttsVoice.isDirectory
    }

    /**
     * Check if offline NLU is available
     */
    private fun checkOfflineNLUAvailable(): Boolean {
        val nluModel = File(context.filesDir, "models/nlu/egybert_tiny_int8.onnx")
        return nluModel.exists()
    }

    /**
     * Parse entities from JSON
     */
    private fun parseEntities(json: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            map[key] = json.getString(key)
        }
        return map
    }
}

/**
 * Connection type enum
 */
enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    OTHER,
    NONE
}

/**
 * Queued command data class
 */
data class QueuedCommand(
    val id: String,
    val action: String,
    val entities: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis(),
    val retries: Int = 0
)

/**
 * Offline status data class
 */
data class OfflineStatus(
    val isOnline: Boolean,
    val connectionType: ConnectionType,
    val queuedCommands: Int,
    val cacheSizeBytes: Long,
    val canUseOfflineASR: Boolean,
    val canUseOfflineTTS: Boolean,
    val canUseOfflineNLU: Boolean
) {
    val canOperateOffline: Boolean
        get() = canUseOfflineASR && canUseOfflineNLU

    val cacheSizeMB: Float
        get() = cacheSizeBytes / (1024f * 1024f)
}
