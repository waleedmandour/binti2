package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class StationManager(private val context: Context) {

    companion object {
        private const val TAG = "StationManager"

        const val REMOTE_STATIONS_URL  = "https://waleedmandour.github.io/binti2/data/stations.json"
        const val ASSET_PATH           = "data/stations.json"
        const val CACHE_FILE           = "stations_remote.json"
        const val PREFS_NAME           = "binti_prefs"
        const val KEY_LAST_FETCH       = "stations_last_fetch"
        const val CACHE_MAX_AGE_MS     = 24 * 60 * 60 * 1000L
        const val DEFAULT_SEARCH_RADIUS_KM = 50.0
        const val EARTH_RADIUS_KM      = 6371.0
    }

    // FIX #1 — was a mutable var backed by mutableListOf(); concurrent reads
    // during a background loadStations() call could see a half-replaced list.
    // Use @Volatile so reads always see the latest reference.
    @Volatile
    private var stations: List<ChargingStation> = emptyList()

    // ──────────────────────────────────────────────────────────────────────────
    // Data classes
    // ──────────────────────────────────────────────────────────────────────────

    data class ChargingStation(
        val id:          String,
        val name:        String,
        val nameEn:      String,
        val address:     String,
        val city:        String,
        val latitude:    Double,
        val longitude:   Double,
        val operator:    String,
        val connectors:  List<String>,
        val powerKw:     Int,
        val status:      String,
        val amenities:   List<String>,
        val hours:       String,
        val costPerKwh:  Double
    )

    data class StationWithDistance(
        val station:    ChargingStation,
        val distanceKm: Double
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    // FIX #2 — loadStations() calls fetchRemoteJson() which does network I/O,
    // but the function was not suspend and had no thread annotation. Callers on
    // the main thread would cause a NetworkOnMainThreadException. Made suspend
    // and dispatched to IO. (Callers must be updated to use lifecycleScope /
    // a coroutine scope accordingly.)
    suspend fun loadStations(forceRefresh: Boolean = false): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

        val prefs      = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch  = prefs.getLong(KEY_LAST_FETCH, 0)
        val cacheFresh = (System.currentTimeMillis() - lastFetch) < CACHE_MAX_AGE_MS

        if (forceRefresh || !cacheFresh) {
            val remoteJson = fetchRemoteJson()
            if (remoteJson != null) {
                val parsed = parseStationJson(remoteJson)
                if (parsed.isNotEmpty()) {
                    stations = parsed
                    saveCache(remoteJson)
                    prefs.edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply()
                    Log.i(TAG, "✅ Loaded ${stations.size} stations from remote")
                    return@withContext true
                }
            } else {
                Log.w(TAG, "Remote fetch failed, falling back to cache/asset")
            }
        }

        val cacheJson = readCache()
        if (cacheJson != null) {
            val parsed = parseStationJson(cacheJson)
            if (parsed.isNotEmpty()) {
                stations = parsed
                Log.i(TAG, "✅ Loaded ${stations.size} stations from cache")
                return@withContext true
            }
        }

        val assetJson = readAsset()
        if (assetJson != null) {
            val parsed = parseStationJson(assetJson)
            if (parsed.isNotEmpty()) {
                stations = parsed
                Log.i(TAG, "✅ Loaded ${stations.size} stations from asset")
                return@withContext true
            }
        }

        Log.e(TAG, "❌ No station data available")
        false
    }

    fun findNearestStation(
        lat: Double, lon: Double,
        maxKm: Double = DEFAULT_SEARCH_RADIUS_KM
    ): StationWithDistance? = findStationsNearby(lat, lon, maxKm, limit = 1).firstOrNull()

    fun findStationsNearby(
        lat: Double, lon: Double,
        maxKm: Double = DEFAULT_SEARCH_RADIUS_KM,
        limit: Int = 10
    ): List<StationWithDistance> {
        // FIX #3 — no guard against invalid coordinates; lat/lon of 0.0,0.0 (Gulf
        // of Guinea) would return wrong results silently. Validate range.
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Log.w(TAG, "findStationsNearby called with invalid coordinates ($lat, $lon)")
            return emptyList()
        }

        return stations
            .filter { it.status == "active" }
            .map { StationWithDistance(it, calculateDistance(lat, lon, it.latitude, it.longitude)) }
            .filter { it.distanceKm <= maxKm }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    fun searchStations(query: String): List<ChargingStation> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return stations.filter { s ->
            s.name.lowercase().contains(q)     ||
            s.nameEn.lowercase().contains(q)   ||
            s.city.lowercase().contains(q)     ||
            s.operator.lowercase().contains(q) ||
            s.address.lowercase().contains(q)
        }
    }

    fun getActiveStationCount(): Int = stations.count { it.status == "active" }

    fun getAvailableCities(): List<String> = stations.map { it.city }.distinct().sorted()

    fun clearCache() {
        File(context.filesDir, CACHE_FILE).takeIf { it.exists() }?.delete()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LAST_FETCH).apply()
        Log.i(TAG, "Cache cleared")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Network
    // ──────────────────────────────────────────────────────────────────────────

    private fun fetchRemoteJson(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(REMOTE_STATIONS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout         = 10_000
                readTimeout            = 15_000
                instanceFollowRedirects = true
                requestMethod          = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Binti-DiLink/2.2.0 (Android; EV Station Manager)")
            }

            // FIX #4 — original created a BufferedReader but only called readText()
            // without explicitly closing it if an exception was thrown mid-read.
            // Wrapped in use{} for guaranteed closure.
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Remote fetch returned HTTP ${connection.responseCode}")
                return null
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                .also { Log.d(TAG, "Remote JSON fetched (${it.length} chars)") }

        } catch (e: Exception) {
            Log.e(TAG, "Remote fetch failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Parsing
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseStationJson(json: String): List<ChargingStation> {
        return try {
            val root           = JSONObject(json)
            val stationsArray  = root.getJSONArray("stations")
            (0 until stationsArray.length()).map { parseSingleStation(stationsArray.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse station JSON", e)
            emptyList()
        }
    }

    private fun parseSingleStation(obj: JSONObject): ChargingStation {
        fun JSONObject.strings(key: String) =
            optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()

        return ChargingStation(
            id         = obj.getString("id"),
            name       = obj.getString("name"),
            // FIX #5 — getString() throws if key is missing; use optString() with
            // a fallback for non-mandatory fields so one bad record doesn't abort
            // parsing the entire array.
            nameEn     = obj.optString("name_en", ""),
            address    = obj.optString("address", ""),
            city       = obj.optString("city", ""),
            latitude   = obj.getDouble("latitude"),
            longitude  = obj.getDouble("longitude"),
            operator   = obj.optString("operator", ""),
            connectors = obj.strings("connectors"),
            powerKw    = obj.optInt("power_kw", 50),
            status     = obj.optString("status", "active"),
            amenities  = obj.strings("amenities"),
            hours      = obj.optString("hours", "N/A"),
            costPerKwh = obj.optDouble("cost_per_kwh", 0.0)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Local storage
    // ──────────────────────────────────────────────────────────────────────────

    private fun readAsset(): String? = try {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    } catch (e: Exception) { Log.e(TAG, "Failed to read asset", e); null }

    private fun readCache(): String? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return null
        return try { file.readText(Charsets.UTF_8) }
        catch (e: Exception) { Log.w(TAG, "Failed to read cache", e); null }
    }

    private fun saveCache(json: String) {
        try {
            FileOutputStream(File(context.filesDir, CACHE_FILE)).use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) { Log.w(TAG, "Failed to save cache", e) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Haversine
    // ──────────────────────────────────────────────────────────────────────────

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                   sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
