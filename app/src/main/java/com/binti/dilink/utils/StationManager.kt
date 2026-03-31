package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * StationManager - Egyptian EV Charging Station Database Manager
 *
 * Manages loading, caching, searching, and geo-querying of EV charging stations
 * across Egypt. Supports local asset fallback and remote JSON fetch with HTTP
 * URL connection.
 *
 * Features:
 * - Loads stations from local assets or remote URL
 * - Caches remote data with configurable TTL
 * - Haversine distance calculation for nearest-station queries
 * - Search by name (Arabic/English), city, operator
 * - Distance-sorted nearby station discovery
 *
 * @author Dr. Waleed Mandour
 */
class StationManager(private val context: Context) {

    companion object {
        private const val TAG = "StationManager"

        /** Remote JSON URL hosted on GitHub Pages */
        const val REMOTE_STATIONS_URL =
            "https://waleedmandour.github.io/binti2/data/stations.json"

        /** Local asset path inside the APK */
        const val ASSET_PATH = "data/stations.json"

        /** Cached remote JSON filename in app internal storage */
        const val CACHE_FILE = "stations_remote.json"

        /** SharedPreferences file name */
        const val PREFS_NAME = "binti_prefs"

        /** SharedPreferences key for last successful fetch timestamp */
        const val KEY_LAST_FETCH = "stations_last_fetch"

        /** Maximum cache age before a refresh is attempted (24 hours) */
        const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

        /** Default search radius for nearby queries (km) */
        const val DEFAULT_SEARCH_RADIUS_KM = 50.0

        /** Earth radius in kilometres for Haversine formula */
        const val EARTH_RADIUS_KM = 6371.0
    }

    /** In-memory station list, populated by [loadStations] */
    private var stations: MutableList<ChargingStation> = mutableListOf()

    // ------------------------------------------------------------------ //
    //  Data classes                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Represents a single EV charging station.
     */
    data class ChargingStation(
        val id: String,
        val name: String,
        val nameEn: String,
        val address: String,
        val city: String,
        val latitude: Double,
        val longitude: Double,
        val operator: String,
        val connectors: List<String>,
        val powerKw: Int,
        val status: String,
        val amenities: List<String>,
        val hours: String,
        val costPerKwh: Double
    )

    /**
     * Wraps a [ChargingStation] together with its distance from a reference point.
     */
    data class StationWithDistance(
        val station: ChargingStation,
        val distanceKm: Double
    )

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Load stations into memory.
     *
     * The loading strategy is:
     * 1. If [forceRefresh] is true **or** the cached remote data is older than
     *    [CACHE_MAX_AGE_MS], attempt a network fetch first.
     * 2. If the network fetch succeeds, parse and cache the JSON, then return.
     * 3. If the network fetch fails (or [forceRefresh] is false and cache is
     *    still fresh), try to load from the local cache file.
     * 4. If no cache exists, fall back to the bundled asset.
     *
     * @param forceRefresh When true, always attempts a remote fetch regardless of cache age.
     * @return `true` if stations were loaded successfully.
     */
    fun loadStations(forceRefresh: Boolean = false): Boolean {
        Log.d(TAG, "loadStations called (forceRefresh=$forceRefresh)")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
        val cacheAge = System.currentTimeMillis() - lastFetch
        val cacheFresh = cacheAge < CACHE_MAX_AGE_MS

        if (forceRefresh || !cacheFresh) {
            Log.d(TAG, "Attempting remote fetch…")
            val remoteJson = fetchRemoteJson()
            if (remoteJson != null) {
                val parsed = parseStationJson(remoteJson)
                if (parsed.isNotEmpty()) {
                    stations = parsed.toMutableList()
                    // Persist to cache
                    saveCache(remoteJson)
                    prefs.edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply()
                    Log.i(TAG, "✅ Loaded ${stations.size} stations from remote")
                    return true
                }
            } else {
                Log.w(TAG, "Remote fetch failed, falling back to cache/asset")
            }
        }

        // Try local cache file
        val cacheJson = readCache()
        if (cacheJson != null) {
            val parsed = parseStationJson(cacheJson)
            if (parsed.isNotEmpty()) {
                stations = parsed.toMutableList()
                Log.i(TAG, "✅ Loaded ${stations.size} stations from cache")
                return true
            }
        }

        // Final fallback: bundled asset
        val assetJson = readAsset()
        if (assetJson != null) {
            val parsed = parseStationJson(assetJson)
            if (parsed.isNotEmpty()) {
                stations = parsed.toMutableList()
                Log.i(TAG, "✅ Loaded ${stations.size} stations from asset")
                return true
            }
        }

        Log.e(TAG, "❌ No station data available")
        return false
    }

    /**
     * Find the single nearest active station within [maxKm] kilometres.
     *
     * @param lat   Reference latitude.
     * @param lon   Reference longitude.
     * @param maxKm Maximum search radius in kilometres.
     * @return A [StationWithDistance] or `null` if no station is in range.
     */
    fun findNearestStation(
        lat: Double,
        lon: Double,
        maxKm: Double = DEFAULT_SEARCH_RADIUS_KM
    ): StationWithDistance? {
        val nearby = findStationsNearby(lat, lon, maxKm, limit = 1)
        return nearby.firstOrNull()
    }

    /**
     * Find up to [limit] active stations within [maxKm] kilometres, sorted by
     * distance ascending (nearest first).
     *
     * @param lat   Reference latitude.
     * @param lon   Reference longitude.
     * @param maxKm Maximum search radius in kilometres.
     * @param limit Maximum number of results to return.
     * @return List of [StationWithDistance], may be empty.
     */
    fun findStationsNearby(
        lat: Double,
        lon: Double,
        maxKm: Double = DEFAULT_SEARCH_RADIUS_KM,
        limit: Int = 10
    ): List<StationWithDistance> {
        return stations
            .filter { it.status == "active" }
            .map { station ->
                StationWithDistance(
                    station = station,
                    distanceKm = calculateDistance(
                        lat, lon,
                        station.latitude, station.longitude
                    )
                )
            }
            .filter { it.distanceKm <= maxKm }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * Free-text search across station name (Arabic & English), city, and operator.
     * Matching is case-insensitive and accent-insensitive.
     *
     * @param query The search string (may be Arabic or English).
     * @return Matching stations, unsorted.
     */
    fun searchStations(query: String): List<ChargingStation> {
        if (query.isBlank()) return emptyList()
        val normalizedQuery = query.trim().lowercase()
        return stations.filter { station ->
            station.name.lowercase().contains(normalizedQuery) ||
                    station.nameEn.lowercase().contains(normalizedQuery) ||
                    station.city.lowercase().contains(normalizedQuery) ||
                    station.operator.lowercase().contains(normalizedQuery) ||
                    station.address.lowercase().contains(normalizedQuery)
        }
    }

    /**
     * Count of stations with status "active".
     */
    fun getActiveStationCount(): Int {
        return stations.count { it.status == "active" }
    }

    /**
     * Return the deduplicated, sorted list of city names present in the database.
     */
    fun getAvailableCities(): List<String> {
        return stations
            .map { it.city }
            .distinct()
            .sorted()
    }

    /**
     * Clear the cached remote JSON file and the last-fetch timestamp.
     * The in-memory list is NOT cleared – call [loadStations] afterwards.
     */
    fun clearCache() {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (cacheFile.exists()) {
            cacheFile.delete()
            Log.i(TAG, "Cache file deleted")
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_FETCH).apply()
        Log.i(TAG, "Cache timestamp cleared")
    }

    // ------------------------------------------------------------------ //
    //  Network                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Fetch the remote stations JSON using [HttpURLConnection].
     *
     * Connection parameters:
     * - Connect timeout: 10 000 ms
     * - Read timeout:    15 000 ms
     * - Follow redirects (HTTP 3xx)
     *
     * @return The response body as a [String], or `null` on failure.
     */
    private fun fetchRemoteJson(): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(REMOTE_STATIONS_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "User-Agent",
                "Binti-DiLink/2.2.0 (Android; EV Station Manager)"
            )

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Remote fetch returned HTTP $responseCode")
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
            val body = reader.readText()
            reader.close()
            Log.d(TAG, "Remote JSON fetched (${body.length} chars)")
            return body
        } catch (e: Exception) {
            Log.e(TAG, "Remote fetch failed", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    // ------------------------------------------------------------------ //
    //  Parsing                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Parse the top-level JSON object and extract the "stations" array.
     */
    private fun parseStationJson(json: String): List<ChargingStation> {
        return try {
            val root = JSONObject(json)
            val stationsArray = root.getJSONArray("stations")
            val result = mutableListOf<ChargingStation>()

            for (i in 0 until stationsArray.length()) {
                val obj = stationsArray.getJSONObject(i)
                result.add(parseSingleStation(obj))
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse station JSON", e)
            emptyList()
        }
    }

    /**
     * Parse a single station JSON object into a [ChargingStation].
     */
    private fun parseSingleStation(obj: JSONObject): ChargingStation {
        val connectorsList = mutableListOf<String>()
        val connectorsArray = obj.optJSONArray("connectors")
        if (connectorsArray != null) {
            for (j in 0 until connectorsArray.length()) {
                connectorsList.add(connectorsArray.getString(j))
            }
        }

        val amenitiesList = mutableListOf<String>()
        val amenitiesArray = obj.optJSONArray("amenities")
        if (amenitiesArray != null) {
            for (j in 0 until amenitiesArray.length()) {
                amenitiesList.add(amenitiesArray.getString(j))
            }
        }

        return ChargingStation(
            id = obj.getString("id"),
            name = obj.getString("name"),
            nameEn = obj.getString("name_en"),
            address = obj.optString("address", ""),
            city = obj.getString("city"),
            latitude = obj.getDouble("latitude"),
            longitude = obj.getDouble("longitude"),
            operator = obj.getString("operator"),
            connectors = connectorsList,
            powerKw = obj.optInt("power_kw", 50),
            status = obj.optString("status", "active"),
            amenities = amenitiesList,
            hours = obj.optString("hours", "N/A"),
            costPerKwh = obj.optDouble("cost_per_kwh", 0.0)
        )
    }

    // ------------------------------------------------------------------ //
    //  Local storage helpers                                               //
    // ------------------------------------------------------------------ //

    /**
     * Read the bundled asset JSON.
     */
    private fun readAsset(): String? {
        return try {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read asset", e)
            null
        }
    }

    /**
     * Read the cached remote JSON from internal storage.
     */
    private fun readCache(): String? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cache", e)
            null
        }
    }

    /**
     * Write remote JSON to the cache file.
     */
    private fun saveCache(json: String) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            FileOutputStream(file).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "Cache saved (${json.length} chars)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache", e)
        }
    }

    // ------------------------------------------------------------------ //
    //  Geography                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Calculate the great-circle distance between two points using the
     * **Haversine formula**.
     *
     * @param lat1 Latitude of point 1 (degrees).
     * @param lon1 Longitude of point 1 (degrees).
     * @param lat2 Latitude of point 2 (degrees).
     * @param lon2 Longitude of point 2 (degrees).
     * @return Distance in kilometres.
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }
}
