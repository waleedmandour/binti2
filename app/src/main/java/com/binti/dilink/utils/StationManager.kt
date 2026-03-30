package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * EV Charging Station Manager
 *
 * Manages EV charging station data for BYD vehicles.
 * Loads bundled stations from assets, can fetch updated list from GitHub Pages.
 * Provides nearest-station search and text-based station lookup.
 *
 * @author Dr. Waleed Mandour
 */
class StationManager(private val context: Context) {

    companion object {
        private const val TAG = "StationManager"
        private const val REMOTE_STATIONS_URL =
            "https://waleedmandour.github.io/binti2/data/stations.json"
        private const val ASSET_PATH = "data/stations.json"
        private const val CACHE_FILE = "stations_remote.json"
        private const val PREFS_NAME = "binti_prefs"
        private const val KEY_LAST_FETCH = "stations_last_fetch"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val DEFAULT_SEARCH_RADIUS_KM = 50.0
        private const val EARTH_RADIUS_KM = 6371.0
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val cacheFile by lazy { File(context.filesDir, CACHE_FILE) }
    private var cachedStations: List<ChargingStation>? = null

    suspend fun loadStations(forceRefresh: Boolean = false): List<ChargingStation> = withContext(Dispatchers.IO) {
        cachedStations?.let { return@withContext it }
        val stations = when {
            forceRefresh || shouldFetchRemote() -> {
                val remote = fetchRemoteStations()
                if (remote.isNotEmpty()) {
                    saveCache(remote)
                    prefs.edit { putLong(KEY_LAST_FETCH, System.currentTimeMillis()) }
                    remote
                } else {
                    loadFromCache() ?: loadFromAssets()
                }
            }
            else -> loadFromCache() ?: loadFromAssets()
        }
        cachedStations = stations
        Log.i(TAG, "Loaded ${stations.size} charging stations")
        stations
    }

    private fun shouldFetchRemote(): Boolean {
        if (!prefs.contains(KEY_LAST_FETCH)) return true
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
        return (System.currentTimeMillis() - lastFetch) > CACHE_MAX_AGE_MS
    }

    private fun fetchRemoteStations(): List<ChargingStation> {
        return try {
            val url = URL(REMOTE_STATIONS_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.connect()
            if (conn.responseCode != 200) {
                Log.w(TAG, "Remote fetch failed: HTTP ${conn.responseCode}")
                return emptyList()
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parseStationsJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Remote fetch error: ${e.message}")
            emptyList()
        }
    }

    private fun saveCache(stations: List<ChargingStation>) {
        try {
            val json = buildStationsJson(stations)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache: ${e.message}")
        }
    }

    private fun loadFromCache(): List<ChargingStation>? {
        return try {
            if (!cacheFile.exists()) return null
            parseStationsJson(cacheFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Cache load error: ${e.message}")
            null
        }
    }

    private fun loadFromAssets(): List<ChargingStation> {
        return try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            parseStationsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Asset load error: ${e.message}")
            emptyList()
        }
    }

    private fun parseStationsJson(json: String): List<ChargingStation> {
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("stations")
        val stations = mutableListOf<ChargingStation>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val connectors = mutableListOf<String>()
            val connArr = s.optJSONArray("connectors")
            connArr?.let { for (j in 0 until it.length()) connectors.add(it.getString(j)) }
            val amenities = mutableListOf<String>()
            val amenArr = s.optJSONArray("amenities")
            amenArr?.let { for (j in 0 until it.length()) amenities.add(it.getString(j)) }
            stations.add(
                ChargingStation(
                    id = s.getString("id"),
                    name = s.getString("name"),
                    nameEn = s.optString("name_en", ""),
                    address = s.optString("address", ""),
                    city = s.optString("city", ""),
                    latitude = s.getDouble("latitude"),
                    longitude = s.getDouble("longitude"),
                    operator = s.optString("operator", ""),
                    connectors = connectors,
                    powerKw = s.optInt("power_kw", 50),
                    status = s.optString("status", "unknown"),
                    amenities = amenities,
                    hours = s.optString("hours", ""),
                    costPerKwh = s.optDouble("cost_per_kwh", 0.0)
                )
            )
        }
        return stations
    }

    private fun buildStationsJson(stations: List<ChargingStation>): String {
        val arr = org.json.JSONArray()
        for (s in stations) {
            val obj = JSONObject()
            obj.put("id", s.id); obj.put("name", s.name); obj.put("name_en", s.nameEn)
            obj.put("address", s.address); obj.put("city", s.city)
            obj.put("latitude", s.latitude); obj.put("longitude", s.longitude)
            obj.put("operator", s.operator); obj.put("connectors", org.json.JSONArray(s.connectors))
            obj.put("power_kw", s.powerKw); obj.put("status", s.status)
            obj.put("amenities", org.json.JSONArray(s.amenities))
            obj.put("hours", s.hours); obj.put("cost_per_kwh", s.costPerKwh)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("version", "1.0.0")
        root.put("last_updated", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
        root.put("total_stations", stations.size)
        root.put("stations", arr)
        return root.toString(2)
    }

    suspend fun findNearestStation(lat: Double, lon: Double, maxKm: Double = DEFAULT_SEARCH_RADIUS_KM): ChargingStation? {
        val stations = loadStations()
        var nearest: ChargingStation? = null
        var minDist = maxKm
        for (station in stations) {
            if (station.status != "active") continue
            val d = haversineKm(lat, lon, station.latitude, station.longitude)
            if (d < minDist) { minDist = d; nearest = station }
        }
        return nearest
    }

    suspend fun findStationsNearby(lat: Double, lon: Double, maxKm: Double = DEFAULT_SEARCH_RADIUS_KM, limit: Int = 5): List<StationWithDistance> {
        val stations = loadStations()
        return stations
            .filter { it.status == "active" }
            .map { Pair(it, haversineKm(lat, lon, it.latitude, it.longitude)) }
            .filter { it.second <= maxKm }
            .sortedBy { it.second }
            .take(limit)
            .map { StationWithDistance(it.first, it.second) }
    }

    suspend fun searchStations(query: String): List<ChargingStation> {
        val stations = loadStations()
        val q = query.lowercase().trim()
        return stations.filter { s ->
            s.name.contains(q, ignoreCase = true) ||
            s.city.contains(q, ignoreCase = true) ||
            s.operator.contains(q, ignoreCase = true) ||
            s.nameEn.contains(q, ignoreCase = true)
        }
    }

    suspend fun getActiveStationCount(): Int = loadStations().count { it.status == "active" }

    suspend fun getAvailableCities(): List<String> = loadStations().map { it.city }.distinct().sorted()

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun clearCache() {
        cachedStations = null
        cacheFile.delete()
        prefs.edit { remove(KEY_LAST_FETCH) }
    }
}

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

data class StationWithDistance(
    val station: ChargingStation,
    val distanceKm: Double
)
