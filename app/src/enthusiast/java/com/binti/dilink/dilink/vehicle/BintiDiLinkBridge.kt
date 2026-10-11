// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.vehicle

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.DataSource
import com.binti.dilink.dilink.VehicleSnapshot
import com.binti.dilink.dilink.VehicleDataRepository
/**
 * Binti DiLink Bridge — the enthusiast-flavor entry point for Tier 2
 * vehicle data via ADB + `service call autoservice`.
 *
 * This class ties together:
 * - [BydVehicleReader] — reads speed, SoC, range, gear, tyres via ADB shell
 * - [RecoveryJournal] — journals vendor side-effects for crash recovery
 * - [FirmwareGate] — gates privileged operations by firmware fingerprint
 *
 * **Lifecycle:**
 * - [init] is called by `DirectControlInitializer` at app startup.
 * - [recoverPendingSideEffects] is called on first use to clean up any
 *   side-effects left from a previous crash.
 * - [readVehicleSnapshot] reads all available fields and publishes them
 *   into the shared [VehicleDataRepository].
 * - [close] releases the ADB connection.
 *
 * **Safety:**
 * This bridge is READ-ONLY. It never writes to any vehicle system.
 * The [FirmwareGate] is checked before any privileged operation.
 *
 * @author Dr. Waleed Mandour
 */
class BintiDiLinkBridge(private val context: Context) {

    companion object {
        private const val TAG = "BintiDiLinkBridge"

        @Volatile
        private var instance: BintiDiLinkBridge? = null

        fun getInstance(context: Context): BintiDiLinkBridge {
            return instance ?: synchronized(this) {
                instance ?: BintiDiLinkBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val vehicleReader = BydVehicleReader(context)
    private val recoveryJournal = RecoveryJournal(context)
    // NOTE: VehicleDataRepository is a plain class (no singleton getInstance()).
    // The enthusiast bridge creates its own instance for merging Tier 2 data.
    // Phase 2's Hilt AppModule also provides a singleton instance — these
    // will be unified when the accessibility service is migrated to Hilt.
    private val vehicleDataRepository = VehicleDataRepository()

    @Volatile
    private var initialized = false

    /**
     * Initialize the bridge. Called by `DirectControlInitializer` at app startup.
     * Recovers any pending side-effects from a previous crash.
     */
    fun init() {
        if (initialized) return
        initialized = true

        Log.i(TAG, "🚗 Initializing Binti DiLink Bridge (enthusiast flavor)")

        // Recover any pending side-effects from a previous crash
        val pendingCount = recoveryJournal.recoverPending()
        if (pendingCount > 0) {
            Log.w(TAG, "⚠️ $pendingCount pending side-effects need manual recovery — see logs above")
        }

        // Check if the privileged path (firmware-gated operations) is available
        val privilegedAvailable = FirmwareGate.isPrivilegedPathAvailable(context)
        Log.i(TAG, "Privileged path available: $privilegedAvailable")

        Log.i(TAG, "✅ Binti DiLink Bridge initialized")
    }

    /**
     * Read all available vehicle data via ADB and publish into the shared
     * [VehicleDataRepository]. This is the Tier 2 data path — it works
     * without Accessibility scraping.
     *
     * Returns the partial [VehicleSnapshot] that was published, or null
     * if ADB is unavailable.
     */
    suspend fun readAndPublishVehicleSnapshot(): VehicleSnapshot? {
        val batteryPercent = vehicleReader.readBatteryPercent()
        val rangeKm = vehicleReader.readEstimatedRangeKm()
        val speedKmh = vehicleReader.readSpeedKmh()
        val gear = vehicleReader.readGear()
        val remainingKwh = vehicleReader.readRemainingKwh()
        val tyrePressures = vehicleReader.readTyrePressures()
        val tyreTemperatures = vehicleReader.readTyreTemperatures()

        // If we couldn't read anything, ADB is likely unavailable
        if (batteryPercent == null && rangeKm == null && speedKmh == null && gear == null) {
            Log.d(TAG, "No vehicle data could be read — ADB may be unavailable")
            return null
        }

        // Build a partial snapshot and publish it
        val partial = VehicleSnapshot(
            timestamp = System.currentTimeMillis(),
            batteryPercent = batteryPercent?.toInt(),
            estimatedRangeKm = rangeKm,
            // Tier 2 fields would go here when VehicleSnapshot is extended
            source = DataSource.ADVANCED_TELEMETRY
        )

        vehicleDataRepository.mergeAdvanced(partial)
        Log.d(TAG, "📊 Published Tier 2 snapshot: battery=${partial.batteryPercent}% range=${partial.estimatedRangeKm}km")
        return partial
    }

    /**
     * Get the latest ADB access status for diagnostics.
     */
    fun adbAccessStatus() = vehicleReader.adbAccessStatus()

    /**
     * Check if the privileged path (firmware-gated operations) is available.
     */
    fun isPrivilegedPathAvailable(): Boolean =
        FirmwareGate.isPrivilegedPathAvailable(context)

    /**
     * Release the ADB connection. Safe to call multiple times.
     */
    fun close() {
        vehicleReader.close()
        Log.i(TAG, "Binti DiLink Bridge closed")
    }
}
