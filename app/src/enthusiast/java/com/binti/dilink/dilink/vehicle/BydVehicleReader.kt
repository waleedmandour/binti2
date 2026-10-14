// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.vehicle

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.BydSpeedDecoder
import com.binti.dilink.dilink.adb.BydAdbShell
import com.binti.dilink.dilink.adb.BydParcel

/**
 * Reads BYD vehicle data via `service call autoservice` over ADB.
 *
 * Ported from DiPlay's `BydBattery.kt` + `BydWheelSpeed.kt` + `BydTyres.kt`.
 * Consolidated into one reader to share a single [BydAdbShell] instance
 * (each reader opening its own shell wastes connections).
 *
 * **CAN vs CANFD:**
 * BYD vehicles use either CAN or CANFD bus protocol. The field addresses
 * differ between the two. We detect which by reading `ro.car.protocol` or
 * `sys.car.protocol` system properties.
 *
 * **Safety:**
 * This reader is READ-ONLY. It never writes to any vehicle system. The
 * enthusiast flavor's [com.binti.dilink.dilink.telemetry.AdvancedTelemetryProbe]
 * has a block-list that prevents reading safety-critical systems (steer,
 * brake, abs, esp, torque, airbag, srs, adas, cruise, control, actuator, drive).
 *
 * @author Dr. Waleed Mandour (ported from DiPlay by shihabal3amri)
 */
class BydVehicleReader(private val context: Context) {

    companion object {
        private const val TAG = "BydVehicleReader"

        // System properties that indicate CAN vs CANFD
        private const val PROP_RO_CAR_PROTOCOL = "ro.car.protocol"
        private const val PROP_SYS_CAR_PROTOCOL = "sys.car.protocol"
        private const val PROTOCOL_CAN = "CAN"
        private const val PROTOCOL_CANFD = "CANFD"
    }

    private val shell = BydAdbShell(TAG)

    // ──────────────────────────────────────────────────────────────────────────
    // CAN / CANFD detection
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Detect whether the vehicle uses CAN or CANFD protocol.
     * Cached after first detection.
     */
    @Volatile
    private var cachedIsCanfd: Boolean? = null

    private fun isCanfd(): Boolean {
        cachedIsCanfd?.let { return it }
        val protocol = readSystemProperty(PROP_RO_CAR_PROTOCOL)
            ?: readSystemProperty(PROP_SYS_CAR_PROTOCOL)
            ?: ""
        val canfd = protocol.equals(PROTOCOL_CANFD, ignoreCase = true)
        cachedIsCanfd = canfd
        Log.d(TAG, "Detected vehicle protocol: ${if (canfd) "CANFD" else "CAN"} (raw: '$protocol')")
        return canfd
    }

    private fun readSystemProperty(name: String): String? {
        val output = shell.run(context, "getprop $name") ?: return null
        return output.trim().ifBlank { null }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Battery: State of Charge + Range
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Read the battery state of charge (0-100 percent).
     * Returns null if ADB is unavailable or the read failed.
     */
    fun readBatteryPercent(): Float? {
        val canfd = isCanfd()
        val command = BydVehicleFields.Battery.socCommand(canfd)
        val output = shell.run(context, command) ?: return null
        val rawBits = BydParcel.value(output) ?: return null
        val percent = Float.fromBits(rawBits)
        // Sanity check like the speed path — an SoC of -3% or 900% is garbage.
        val valid = BydSpeedDecoder.sanitizeKmh(percent)?.let { it in 0f..100f } == true
        if (!valid) {
            Log.w(TAG, "Battery SoC out of range: $percent% — treating as unread")
            return null
        }
        Log.v(TAG, "Battery SoC: $percent% (raw bits: $rawBits)")
        return percent
    }

    /**
     * Read the estimated driving range in kilometers.
     * Returns null if ADB is unavailable or the read failed.
     */
    fun readEstimatedRangeKm(): Int? {
        val canfd = isCanfd()
        val command = BydVehicleFields.Battery.rangeCommand(canfd)
        val output = shell.run(context, command) ?: return null
        val range = BydParcel.value(output) ?: return null
        Log.v(TAG, "Range: $range km")
        return range
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Speed + Gear
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Speed-field command that answered with a sane reading last time we
     * probed. Null until the first successful read. See [readSpeedKmh].
     */
    @Volatile
    private var cachedSpeedCommand: String? = null

    /** Invalidate the speed-address probe cache (e.g. when reads go stale). */
    fun invalidateSpeedProbe() {
        cachedSpeedCommand = null
    }

    /**
     * Read the current vehicle speed in km/h.
     *
     * P0 FIX (unit): the raw autoservice speed value is ALREADY km/h —
     * DiPlay's test vector 0x42100000 → 36.0 means 36 km/h. The previous code
     * treated it as m/s and multiplied by 3.6, announcing real 36 km/h as
     * ~130 km/h. Decoding is delegated to [BydSpeedDecoder], which also
     * rejects NaN/negative/absurd readings.
     *
     * P0 FIX (DiLink 3): the SDK 1.0.5 speed address reads garbage on
     * controller-13 / DiLink 3 head units, so we probe the SDK address first
     * and fall back to the DiLink 3 legacy address recorded by DiPlay. The
     * first address that returns a sane value is cached for subsequent reads.
     *
     * NOTE: while PARKED both addresses legitimately read 0.0, so the probe
     * cannot distinguish firmware generations at standstill — verify the
     * cached choice during the first drive (see docs/REBUILD_PLAN.md).
     *
     * Returns null if ADB is unavailable or the read failed.
     */
    fun readSpeedKmh(): Float? {
        cachedSpeedCommand?.let { return readSpeedVia(it) }

        for (command in BydVehicleFields.Speed.COMMAND_CANDIDATES) {
            val kmh = readSpeedVia(command)
            if (kmh != null) {
                cachedSpeedCommand = command
                Log.d(TAG, "Speed address probed OK: $command")
                return kmh
            }
        }
        return null
    }

    private fun readSpeedVia(command: String): Float? {
        val output = shell.run(context, command) ?: return null
        val rawBits = BydParcel.value(output) ?: return null
        return BydSpeedDecoder.decodeKmh(rawBits)
    }

    /**
     * Read the current gear position.
     * Returns the gear code (1=P, 2=R, 3=N, 4-6=D) or null if unavailable.
     */
    fun readGear(): Int? {
        val output = shell.run(context, BydVehicleFields.Gear.COMMAND) ?: return null
        val gear = BydParcel.value(output) ?: return null
        Log.v(TAG, "Gear: ${BydVehicleFields.Gear.gearName(gear)} ($gear)")
        return gear
    }

    /**
     * Read the remaining battery energy in kWh.
     * Returns null if ADB is unavailable or the read failed.
     */
    fun readRemainingKwh(): Float? {
        val output = shell.run(context, BydVehicleFields.Power.COMMAND) ?: return null
        val rawBits = BydParcel.value(output) ?: return null
        val kwh = Float.fromBits(rawBits)
        Log.v(TAG, "Remaining energy: $kwh kWh")
        return kwh
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tyres: pressure + temperature (4 corners each)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Read tyre pressure for all 4 corners.
     * Returns a list of 4 Int values (kPa) [FL, FR, RL, RR], or null if unavailable.
     */
    fun readTyrePressures(): List<Int>? {
        val commands = BydVehicleFields.Tyres.pressureCommands()
        val results = commands.mapNotNull { cmd ->
            val output = shell.run(context, cmd) ?: return null
            BydParcel.value(output)
        }
        if (results.size != 4) {
            Log.w(TAG, "Expected 4 tyre pressure readings, got ${results.size}")
            return null
        }
        Log.v(TAG, "Tyre pressures (kPa): $results")
        return results
    }

    /**
     * Read tyre temperature for all 4 corners.
     * Returns a list of 4 Int values (°C) [FL, FR, RL, RR], or null if unavailable.
     */
    fun readTyreTemperatures(): List<Int>? {
        val commands = BydVehicleFields.TyreTemperature.temperatureCommands()
        val results = commands.mapNotNull { cmd ->
            val output = shell.run(context, cmd) ?: return null
            BydParcel.value(output)
        }
        if (results.size != 4) {
            Log.w(TAG, "Expected 4 tyre temperature readings, got ${results.size}")
            return null
        }
        Log.v(TAG, "Tyre temperatures (°C): $results")
        return results
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ADB access status (for diagnostics)
    // ──────────────────────────────────────────────────────────────────────────

    /** The latest ADB access state. null if no attempt has been made yet. */
    fun adbAccessStatus() = shell.lastAccess

    /** Release the ADB connection. Safe to call multiple times. */
    fun close() {
        shell.close()
    }
}
