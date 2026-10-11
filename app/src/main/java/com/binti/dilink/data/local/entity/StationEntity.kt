// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for an EV charging station.
 *
 * Mirrors [com.binti.dilink.utils.StationManager.ChargingStation] but
 * with Room annotations. This is the database representation; the
 * existing [StationManager.ChargingStation] data class is the runtime
 * representation used by the voice pipeline.
 *
 * The [id] field is the primary key. [latitude] and [longitude] are
 * indexed together for efficient spatial queries (nearest-station search).
 *
 * @author Dr. Waleed Mandour
 */
@Entity(
    tableName = "charging_stations",
    indices = [
        Index(value = ["latitude", "longitude"]),
        Index(value = ["city"]),
        Index(value = ["status"])
    ]
)
data class StationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val nameEn: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val operator: String,
    val connectorsJson: String, // JSON-encoded list of connector types
    val powerKw: Int,
    val status: String,
    val amenitiesJson: String, // JSON-encoded list of amenities
    val hours: String,
    val costPerKwh: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)
