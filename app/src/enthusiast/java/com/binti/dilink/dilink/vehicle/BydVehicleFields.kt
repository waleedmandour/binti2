// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Vehicle field addresses verified against BYD SDK 1.0.5.
// Ported from DiPlay's BydVehicleFields.kt with gratitude.

package com.binti.dilink.dilink.vehicle

/**
 * BYD vehicle field addresses for `service call autoservice`.
 *
 * Each field is identified by a (device, fid, transaction) tuple:
 * - `device` — the BYD service device ID (e.g. 1013 for speed, 1014 for battery)
 * - `fid` — the field ID within that device (a signed 32-bit int, often negative)
 * - `tx` — the `service call` transaction code (5 = GET_INT, 7 = GET_FLOAT)
 *
 * The CAN vs CANFD address tables are selected at runtime by reading
 * `ro.car.protocol` / `sys.car.protocol` system properties.
 *
 * Ported from DiPlay's `BydVehicleFields.kt`. Values verified against
 * BYD SDK 1.0.5 by the DiPlay project.
 *
 * @author Dr. Waleed Mandour (constants from DiPlay by shihabal3amri)
 */
object BydVehicleFields {

    /**
     * Build the `service call autoservice` command for a GET_INT transaction.
     * Transaction code 5 reads an integer property.
     */
    fun getIntCommand(device: Int, fid: Int): String =
        "service call autoservice 5 i32 $device i32 $fid"

    /**
     * Build the `service call autoservice` command for a GET_FLOAT transaction.
     * Transaction code 7 reads a float (returned as intBits, then Float.intBitsToFloat).
     */
    fun getFloatCommand(device: Int, fid: Int): String =
        "service call autoservice 7 i32 $device i32 $fid"

    // ──────────────────────────────────────────────────────────────────────────
    // Speed
    // ──────────────────────────────────────────────────────────────────────────

    object Speed {
        // BYDAutoFeatureIds.Speed.SPEED_AUTO_SPEED
        // Device 1013, fid -1807745016, transaction 7 (float)
        const val DEVICE = 1013
        const val FID = -1807745016
        const val TX = 7

        val COMMAND = getFloatCommand(DEVICE, FID)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gear
    // ──────────────────────────────────────────────────────────────────────────

    object Gear {
        // BYDAutoFeatureIds.Gearbox.GEARBOX_AUTO_MODE_TYPE
        // Device 1011, fid 555745336, transaction 5 (int)
        const val DEVICE = 1011
        const val FID = 555745336
        const val TX = 5

        val COMMAND = getIntCommand(DEVICE, FID)

        // Gear codes (decoded from the int return value)
        // 1 = PARK, 2 = REVERSE, 3 = NEUTRAL, 4/5/6 = DRIVE
        const val GEAR_PARK = 1
        const val GEAR_REVERSE = 2
        const val GEAR_NEUTRAL = 3
        const val GEAR_DRIVE = 4

        fun gearName(code: Int): String = when (code) {
            GEAR_PARK -> "P"
            GEAR_REVERSE -> "R"
            GEAR_NEUTRAL -> "N"
            in 4..6 -> "D"
            else -> "?"
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Battery State of Charge + Range
    // ──────────────────────────────────────────────────────────────────────────

    object Battery {
        // BYDAutoFeatureIds.Statistic.STATISTIC_ELEC_PERCENTAGE
        // Device 1014. CANFD fid = 1246777400, CAN fid = 1033543720. Transaction 7 (float).
        const val DEVICE = 1014
        const val SOC_FID_CANFD = 1246777400
        const val SOC_FID_CAN = 1033543720
        const val SOC_TX = 7

        // BYDAutoFeatureIds.Statistic.STATISTIC_ELEC_DRIVING_RANGE
        // CANFD fid = 1246765118, CAN fid = 1033203771. Transaction 5 (int).
        const val RANGE_FID_CANFD = 1246765118
        const val RANGE_FID_CAN = 1033203771
        const val RANGE_TX = 5

        fun socCommand(isCanfd: Boolean): String =
            if (isCanfd) getFloatCommand(DEVICE, SOC_FID_CANFD)
            else getFloatCommand(DEVICE, SOC_FID_CAN)

        fun rangeCommand(isCanfd: Boolean): String =
            if (isCanfd) getIntCommand(DEVICE, RANGE_FID_CANFD)
            else getIntCommand(DEVICE, RANGE_FID_CAN)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Remaining battery energy (kWh)
    // ──────────────────────────────────────────────────────────────────────────

    object Power {
        // BYDAutoFeatureIds.Power.POWER_BATTERY_REMAIN_ELECTRICITY
        // Device 1005, fid 882901008, transaction 7 (float)
        const val DEVICE = 1005
        const val FID = 882901008
        const val TX = 7

        val COMMAND = getFloatCommand(DEVICE, FID)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BMS charging state
    // ──────────────────────────────────────────────────────────────────────────

    object Charging {
        // BYDAutoFeatureIds.Charging.CHARGING_BATTERRY_DEVICE_STATE
        // Device 1009. CANFD fid = 876609560, CAN fid = 876611608. Transaction 5 (int).
        const val DEVICE = 1009
        const val STATE_FID_CANFD = 876609560
        const val STATE_FID_CAN = 876611608
        const val STATE_TX = 5

        fun stateCommand(isCanfd: Boolean): String =
            if (isCanfd) getIntCommand(DEVICE, STATE_FID_CANFD)
            else getIntCommand(DEVICE, STATE_FID_CAN)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tyre pressure (4 corners)
    // ──────────────────────────────────────────────────────────────────────────

    object Tyres {
        // Device 1016, transaction 5 (int). 4 corners: FL, FR, RL, RR.
        const val DEVICE = 1016
        const val TX = 5

        val PRESSURE_FIDS = listOf(
            -1728052956,  // Front-left
            -1728052952,  // Front-right
            -1728052948,  // Rear-left
            -1728052944,  // Rear-right
        )

        fun pressureCommands(): List<String> =
            PRESSURE_FIDS.map { fid -> getIntCommand(DEVICE, fid) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tyre temperature (4 corners)
    // ──────────────────────────────────────────────────────────────────────────

    object TyreTemperature {
        // Device 1007, transaction 5 (int). 4 corners: FL, FR, RL, RR.
        const val DEVICE = 1007
        const val TX = 5

        val TEMP_FIDS = listOf(
            1246797848,  // Front-left
            1246797860,  // Front-right
            1246797872,  // Rear-left
            1246797884,  // Rear-right
        )

        fun temperatureCommands(): List<String> =
            TEMP_FIDS.map { fid -> getIntCommand(DEVICE, fid) }
    }
}
