// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.vehicle

import android.content.Context
import android.util.Log
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
     * Read the current vehicle speed in km/h.
     * The autoservice returns speed in meters/second as a float; we convert to km/h.
     * Returns null if ADB is unavailable or the read failed.
     */
    fun readSpeedKmh(): Float? {
        val output = shell.run(context, BydVehicleFields.Speed.COMMAND) ?: return null
        val rawBits = BydParcel.value(output) ?: return null
        val mps = Float.fromBits(rawBits)
        val kmh = mps * 3.6f
        Log.v(TAG, "Speed: $kmh km/h ($mps m/s)")
        return kmh
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
