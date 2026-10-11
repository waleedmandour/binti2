// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.data.repository

import android.content.Context
import android.util.Log
import com.binti.dilink.data.local.dao.StationDao
import com.binti.dilink.data.local.entity.StationEntity
import com.binti.dilink.utils.StationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository for EV charging station data.
 *
 * Abstracts the data source — new code should use this instead of
 * [StationManager] directly. On first access, it populates the Room database
 * from the bundled `stations.json` asset (and optionally from the remote
 * GitHub Pages source). Subsequent reads come from Room.
 *
 * **Data flow:**
 * 1. First call to [ensurePopulated] checks if Room has stations.
 * 2. If empty, reads the asset via [StationManager] and inserts into Room.
 * 3. All subsequent reads (nearest, search, count) come from Room.
 *
 * The existing [StationManager] remains the source of truth for the voice
 * pipeline (DiLinkCommandExecutor). This repository is the source of truth
 * for new Compose UI screens (Settings, Diagnostics, Station List).
 * The two will converge when StationManager is refactored to use Room
 * internally (Phase 3).
 *
 * @author Dr. Waleed Mandour
 */
class StationRepository(
    private val stationDao: StationDao,
    private val context: Context
) {
    companion object {
        private const val TAG = "StationRepository"
        private const val EARTH_RADIUS_KM = 6371.0
    }

    /**
     * Ensures the Room database is populated from the asset on first access.
     * Safe to call multiple times — only populates if the DB is empty.
     */
    suspend fun ensurePopulated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = stationDao.getActiveStationCount()
            if (count > 0) {
                Log.d(TAG, "DB already has $count stations — skip population")
                return@withContext true
            }

            // DB is empty — populate from the legacy StationManager (which reads
            // the asset / cache / remote). This bridges the old and new data paths.
            val legacyManager = StationManager(context)
            val loaded = legacyManager.loadStations()
            if (!loaded) {
                Log.w(TAG, "Could not load stations from legacy StationManager")
                return@withContext false
            }

            // We need access to the station list — StationManager doesn't expose it
            // directly, so we use findStationsNearby with a huge radius to get all
            // stations. This is a temporary bridge until StationManager is refactored.
            val allStations = legacyManager.findStationsNearby(
                lat = 0.0, lon = 0.0, maxKm = 99999.0, limit = 1000
            ).map { it.station }

            val entities = allStations.map { station ->
                StationEntity(
                    id = station.id,
                    name = station.name,
                    nameEn = station.nameEn,
                    address = station.address,
                    city = station.city,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    operator = station.operator,
                    connectorsJson = JSONArray(station.connectors).toString(),
                    powerKw = station.powerKw,
                    status = station.status,
                    amenitiesJson = JSONArray(station.amenities).toString(),
                    hours = station.hours,
                    costPerKwh = station.costPerKwh
                )
            }

            stationDao.insertAll(entities)
            Log.i(TAG, "✅ Populated Room DB with ${entities.size} stations from asset")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to populate station database", e)
            false
        }
    }

    /**
     * Observe all active stations reactively. The Flow emits whenever the
     * database changes. Call [ensurePopulated] first to trigger the initial load.
     */
    fun getActiveStations(): Flow<List<StationEntity>> =
        stationDao.getActiveStations()

    /**
     * Find the nearest station to the given coordinates within maxKm.
     *
     * Room doesn't support Haversine in SQL, so we load all active stations
     * and compute distance in Kotlin. With ~30 stations in the DB, this is
     * negligible. If the station count grows significantly, we can add
     * a bounding-box pre-filter in SQL.
     */
    suspend fun findNearestStation(
        lat: Double,
        lon: Double,
        maxKm: Double = StationManager.DEFAULT_SEARCH_RADIUS_KM
    ): StationWithDistance? = withContext(Dispatchers.IO) {
        ensurePopulated()

        val stations = stationDao.getActiveStations()
        // We can't collect a Flow synchronously, so use the DAO's count + search
        // For now, use the legacy manager's findStationsNearby for the actual
        // nearest-station logic, and use Room for the UI-facing station list.
        // This will be unified in Phase 3.
        null // Placeholder — Phase 3 will implement Room-based nearest search
    }

    /**
     * Search stations by text query. Delegates to Room.
     */
    suspend fun searchStations(query: String): List<StationEntity> = withContext(Dispatchers.IO) {
        ensurePopulated()
        stationDao.searchStations(query)
    }

    /**
     * Get the count of active stations. Ensures the DB is populated first.
     */
    suspend fun getActiveStationCount(): Int = withContext(Dispatchers.IO) {
        ensurePopulated()
        stationDao.getActiveStationCount()
    }

    /**
     * Get all distinct cities that have stations.
     */
    suspend fun getCities(): List<String> = withContext(Dispatchers.IO) {
        ensurePopulated()
        stationDao.getCities()
    }

    /**
     * Force a refresh from the remote source. Clears the DB and re-populates
     * from the legacy StationManager (which fetches from GitHub Pages).
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            val legacyManager = StationManager(context)
            val loaded = legacyManager.loadStations(forceRefresh = true)
            if (!loaded) return@withContext false

            val allStations = legacyManager.findStationsNearby(
                lat = 0.0, lon = 0.0, maxKm = 99999.0, limit = 1000
            ).map { it.station }

            val entities = allStations.map { station ->
                StationEntity(
                    id = station.id,
                    name = station.name,
                    nameEn = station.nameEn,
                    address = station.address,
                    city = station.city,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    operator = station.operator,
                    connectorsJson = JSONArray(station.connectors).toString(),
                    powerKw = station.powerKw,
                    status = station.status,
                    amenitiesJson = JSONArray(station.amenities).toString(),
                    hours = station.hours,
                    costPerKwh = station.costPerKwh
                )
            }

            stationDao.deleteAll()
            stationDao.insertAll(entities)
            Log.i(TAG, "✅ Refreshed Room DB with ${entities.size} stations from remote")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh station database", e)
            false
        }
    }

    /**
     * Haversine distance between two coordinates.
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    data class StationWithDistance(
        val station: StationEntity,
        val distanceKm: Double
    )
}
