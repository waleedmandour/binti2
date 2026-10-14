// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.binti.dilink.dilink.control

import com.binti.dilink.dilink.VehicleSnapshot

/**
 * Control mode contract — abstracts HOW Binti communicates with the
 * DiLink system. Three implementations exist:
 *
 * 1. [AccessibilityControlBridge] — the original approach. Uses Android
 *    Accessibility Service to find UI nodes and click them. Works on
 *    standard Android, but BYD DiLink 3 (Android 10) has an OEM-modified
 *    settings app that often prevents accessibility services from being
 *    enabled through the UI. When accessibility IS enabled, this is the
 *    most reliable control path.
 *
 * 2. [DirectControlBridge] (enthusiast flavor only) — uses `app_process`
 *    + reflection against `android.hardware.bydauto.*` services, the
 *    approach documented by the MIT-licensed `wheregoes/byd-dolphin-hacking`
 *    project. Does NOT need accessibility at all. Requires a one-time ADB
 *    setup to grant the shell permission. Can both READ vehicle data AND
 *    WRITE control commands (AC, media, navigation, phone) directly to
 *    BYD's internal services — no UI interaction needed.
 *
 * 3. [NoOpControlBridge] — fallback when neither accessibility nor direct
 *    control is available. All commands return "not available" messages.
 *
 * @author Dr. Waleed Mandour
 */
interface ControlBridge {

    /** Human-readable name of the control mode for diagnostics. */
    val modeName: String

    /** Whether this bridge can actually control the vehicle (not a no-op). */
    val isAvailable: Boolean

    /**
     * Read the current vehicle snapshot. Returns a partial [VehicleSnapshot]
     * with whatever fields could be read. Null fields are unknown.
     */
    suspend fun readVehicleState(): VehicleSnapshot

    /**
     * Send a control command to the vehicle. Returns a user-facing Arabic
     * message describing the result.
     *
     * @param command The command to execute (see [ControlCommand] constants).
     * @param params  Optional parameters (e.g. target temperature, destination).
     * @return Arabic result message.
     */
    suspend fun sendCommand(command: String, params: Map<String, String> = emptyMap()): String

    /**
     * Check if the bridge is ready to operate (e.g. accessibility connected,
     * or direct-control daemon running).
     */
    fun isReady(): Boolean

    /** Release any resources. Called on shutdown. */
    fun release()

    /**
     * P0 FIX (Tier 2 wiring): best-effort refresh of the shared
     * [VehicleDataRepository] from this bridge's data source BEFORE a voice
     * query answers from it. Returns true when fresh data was published.
     *
     * - AccessibilityControlBridge: no separate refresh needed (the service
     *   publishes on screen events; callers use forceRefreshSnapshot).
     * - DirectControlBridge (enthusiast): re-runs the ADB autoservice reads
     *   and merges them into the shared repository.
     * - NoOpControlBridge: always false.
     */
    suspend fun refreshVehicleData(): Boolean = false
}

/**
 * Supported control commands. These are the canonical command names that
 * [ControlBridge.sendCommand] accepts. Each maps to either an accessibility
 * action (find node + click) or a direct bydauto.* service call, depending
 * on which bridge is active.
 */
object ControlCommand {
    // AC control
    const val AC_ON           = "AC_ON"
    const val AC_OFF          = "AC_OFF"
    const val AC_TEMP_UP      = "AC_TEMP_UP"
    const val AC_TEMP_DOWN    = "AC_TEMP_DOWN"
    const val AC_TEMP_SET     = "AC_TEMP_SET"
    const val AC_MODE_AUTO    = "AC_MODE_AUTO"
    const val AC_MODE_COOL    = "AC_MODE_COOL"
    const val AC_MODE_HEAT    = "AC_MODE_HEAT"
    const val AC_FAN_UP       = "AC_FAN_UP"
    const val AC_FAN_DOWN     = "AC_FAN_DOWN"

    // Media control
    const val MEDIA_PLAY      = "MEDIA_PLAY"
    const val MEDIA_PAUSE     = "MEDIA_PAUSE"
    const val MEDIA_NEXT      = "MEDIA_NEXT"
    const val MEDIA_PREV      = "MEDIA_PREV"

    // Navigation
    const val NAVIGATE_HOME   = "NAVIGATE_HOME"
    const val NAVIGATE_WORK   = "NAVIGATE_WORK"
    const val NAVIGATE_TO     = "NAVIGATE_TO"

    // Phone
    const val PHONE_CALL      = "PHONE_CALL"
    const val PHONE_ANSWER    = "PHONE_ANSWER"
    const val PHONE_END       = "PHONE_END"

    // System
    const val VOLUME_UP       = "VOLUME_UP"
    const val VOLUME_DOWN     = "VOLUME_DOWN"
    const val VOLUME_MUTE     = "VOLUME_MUTE"
}
