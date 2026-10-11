// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binti.dilink.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for charging station queries.
 *
 * Uses Room's coroutine support — all queries are suspend functions or
 * return [Flow] for reactive observation.
 *
 * @author Dr. Waleed Mandour
 */
@Dao
interface StationDao {

    /**
     * Insert all stations, replacing existing ones with the same ID.
     * Used during the initial data load from the bundled `stations.json` asset.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    /**
     * Get all active stations. Returns a [Flow] so the UI updates reactively
     * when the database changes.
     */
    @Query("SELECT * FROM charging_stations WHERE status = 'active' ORDER BY name")
    fun getActiveStations(): Flow<List<StationEntity>>

    /**
     * Get all stations (including inactive) — used in the diagnostics screen.
     */
    @Query("SELECT * FROM charging_stations ORDER BY name")
    fun getAllStations(): Flow<List<StationEntity>>

    /**
     * Count of active stations — used to check if the DB has been populated.
     */
    @Query("SELECT COUNT(*) FROM charging_stations WHERE status = 'active'")
    suspend fun getActiveStationCount(): Int

    /**
     * Search stations by name, English name, city, or address.
     * Uses LIKE for substring matching.
     */
    @Query("""
        SELECT * FROM charging_stations
        WHERE name LIKE '%' || :query || '%'
           OR nameEn LIKE '%' || :query || '%'
           OR city LIKE '%' || :query || '%'
           OR address LIKE '%' || :query || '%'
           OR operator LIKE '%' || :query || '%'
        ORDER BY name
    """)
    suspend fun searchStations(query: String): List<StationEntity>

    /**
     * Get all distinct cities — used to populate a city filter dropdown.
     */
    @Query("SELECT DISTINCT city FROM charging_stations WHERE city != '' ORDER BY city")
    suspend fun getCities(): List<String>

    /**
     * Delete all stations — used before re-importing from the asset.
     */
    @Query("DELETE FROM charging_stations")
    suspend fun deleteAll()

    /**
     * Get a single station by ID.
     */
    @Query("SELECT * FROM charging_stations WHERE id = :id")
    suspend fun getStationById(id: String): StationEntity?
}
