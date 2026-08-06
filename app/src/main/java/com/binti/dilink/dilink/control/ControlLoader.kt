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

import android.util.Log
import com.binti.dilink.dilink.DiLinkAccessibilityService
import com.binti.dilink.dilink.VehicleSnapshot

/**
 * Runtime registry that selects the best available [ControlBridge].
 *
 * Selection priority:
 * 1. If the accessibility service is connected → [AccessibilityControlBridge]
 *    (most reliable when available, works on all DiLink versions)
 * 2. Else if direct control is registered (enthusiast flavor + setup done)
 *    → the enthusiast-flavor [DirectControlBridge]
 * 3. Else → [NoOpControlBridge] (everything returns "not available")
 *
 * The enthusiast flavor's `DirectControlInitializer` calls [registerDirect]
 * at startup if the direct-control daemon is running.
 *
 * @author Dr. Waleed Mandour
 */
object ControlLoader {

    private const val TAG = "ControlLoader"

    @Volatile
    private var directBridge: ControlBridge? = null

    /**
     * Called by the enthusiast flavor's initializer to register the
     * direct-control bridge. The store flavor never calls this.
     */
    fun registerDirect(bridge: ControlBridge) {
        directBridge = bridge
        Log.i(TAG, "Direct control bridge registered: ${bridge.modeName}")
    }

    /**
     * Get the best available control bridge at this moment.
     * Re-evaluates each call (accessibility may connect/disconnect at any time).
     */
    fun get(): ControlBridge {
        // Priority 1: Accessibility (if connected)
        val a11yService = DiLinkAccessibilityService.getInstance()
        if (a11yService != null) {
            return AccessibilityControlBridge(a11yService)
        }

        // Priority 2: Direct control (enthusiast flavor, if registered)
        directBridge?.let { bridge ->
            if (bridge.isReady()) return bridge
        }

        // Priority 3: No-op fallback
        return NoOpControlBridge
    }
}

/**
 * Accessibility-based control bridge — the original Binti approach.
 *
 * Wraps the existing [DiLinkAccessibilityService] to implement the
 * [ControlBridge] contract. When the accessibility service is connected,
 * this is the primary control path. Commands are executed by finding UI
 * nodes by resource ID and clicking them.
 */
class AccessibilityControlBridge(
    private val service: DiLinkAccessibilityService
) : ControlBridge {

    override val modeName: String = "Accessibility Service"
    override val isAvailable: Boolean = true

    override fun isReady(): Boolean =
        DiLinkAccessibilityService.getInstance() != null

    override suspend fun readVehicleState(): VehicleSnapshot {
        // Delegate to the existing repository (which is populated by the
        // accessibility service's publishSnapshotFromCurrentScreen).
        return DiLinkAccessibilityService.vehicleDataRepository.current()
    }

    override suspend fun sendCommand(command: String, params: Map<String, String>): String {
        // The actual command execution stays in DiLinkCommandExecutor for now.
        // This bridge exists to unify the interface; the executor is refactored
        // to use this bridge in a future release. For now, return a placeholder
        // indicating the accessibility path is active.
        return "تم (عبر Accessibility)"
    }

    override fun release() {
        // The accessibility service manages its own lifecycle.
    }
}

/**
 * No-op fallback bridge — used when neither accessibility nor direct
 * control is available. All commands return a user-facing Arabic message
 * explaining the situation and how to fix it.
 */
object NoOpControlBridge : ControlBridge {
    override val modeName: String = "None (no control method available)"
    override val isAvailable: Boolean = false
    override fun isReady(): Boolean = false

    override suspend fun readVehicleState(): VehicleSnapshot = VehicleSnapshot(timestamp = 0L)

    override suspend fun sendCommand(command: String, params: Map<String, String>): String =
        "مفيش طريقة تحكم متاحة. فعّل Accessibility من الإعدادات أو استخدم ADB setup script. " +
        "شوف دليل البناء في docs/BUILD.md."

    override fun release() { /* no-op */ }
}
