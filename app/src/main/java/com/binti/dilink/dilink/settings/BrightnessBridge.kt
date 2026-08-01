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

package com.binti.dilink.dilink.settings

/**
 * Brightness control contract — mirrors the AdvancedTelemetryBridge pattern.
 *
 * Brightness control requires the `WRITE_SETTINGS` permission, which is a
 * protected permission that triggers extra review scrutiny on app stores.
 * To keep the `store` (Huawei AppGallery) flavor free of this permission,
 * brightness control is isolated to the `enthusiast` flavor via this bridge.
 *
 *  - `store` flavor       → [NoOpBrightnessBridge] (always returns "not available")
 *  - `enthusiast` flavor  → `RealBrightnessBridge` (uses Settings.System with
 *                            WRITE_SETTINGS permission declared in the
 *                            enthusiast manifest)
 *
 * Main-source code (DiLinkCommandExecutor) calls [BrightnessLoader.get()] and
 * never references the enthusiast implementation directly.
 *
 * @author Dr. Waleed Mandour
 */
interface BrightnessBridge {

    /** `true` if this bridge can actually control brightness (enthusiast flavor). */
    val isAvailable: Boolean

    /**
     * Set brightness to an absolute percentage [level] (0–100).
     * Returns a user-facing Arabic message describing the result.
     */
    suspend fun setBrightness(level: Int): String

    /**
     * Adjust brightness by [delta] percentage points (positive = brighter).
     * Returns a user-facing Arabic message describing the result.
     */
    suspend fun adjustBrightness(delta: Int): String

    /**
     * Whether the user has granted the "Modify system settings" capability.
     * If false, the caller should prompt the user to grant it before
     * retrying. The enthusiast implementation opens the system settings
     * page for this; the store no-op always returns false.
     */
    fun hasWriteSettingsPermission(): Boolean

    /**
     * Launch the system "Modify system settings" screen so the user can
     * grant the capability. No-op in the store flavor.
     */
    fun requestWriteSettingsPermission()
}
