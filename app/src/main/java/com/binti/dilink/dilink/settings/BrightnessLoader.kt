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
 * Flavor-polymorphic registry for [BrightnessBridge] — mirrors the
 * [com.binti.dilink.dilink.telemetry.AdvancedTelemetryLoader] pattern.
 *
 *  - `store` flavor       → leaves the [NoOpBrightnessBridge] default in place
 *                           (brightness control unavailable, WRITE_SETTINGS
 *                            not declared in manifest)
 *  - `enthusiast` flavor  → `BrightnessInitializer.init(context)` registers
 *                           a `RealBrightnessBridge` that uses Settings.System
 *                           with WRITE_SETTINGS (declared in enthusiast manifest)
 *
 * Main-source code calls [get] and never imports the enthusiast class directly.
 *
 * @author Dr. Waleed Mandour
 */
object BrightnessLoader {

    @Volatile
    private var registered: BrightnessBridge = NoOpBrightnessBridge

    /**
     * Called by the enthusiast flavor's initializer at startup to register
     * the real bridge. The store flavor never calls this.
     */
    fun register(bridge: BrightnessBridge) {
        registered = bridge
    }

    /** The active bridge. Always non-null; never throws. */
    fun get(): BrightnessBridge = registered
}

/**
 * Default bridge used by the `store` flavor.
 *
 * All operations return a user-facing message explaining that brightness
 * control is not available in this build. The `WRITE_SETTINGS` permission
 * is NOT declared in the store manifest, so even if this bridge tried to
 * call Settings.System, it would fail at runtime.
 */
object NoOpBrightnessBridge : BrightnessBridge {
    override val isAvailable: Boolean = false

    override suspend fun setBrightness(level: Int): String =
        "التحكم في الإضاءة مش متاح في الإصدار ده. استخدم إصدار Enthusiast."

    override suspend fun adjustBrightness(delta: Int): String =
        "التحكم في الإضاءة مش متاح في الإصدار ده. استخدم إصدار Enthusiast."

    override fun hasWriteSettingsPermission(): Boolean = false

    override fun requestWriteSettingsPermission() {
        // No-op — store flavor does not request WRITE_SETTINGS.
    }
}
