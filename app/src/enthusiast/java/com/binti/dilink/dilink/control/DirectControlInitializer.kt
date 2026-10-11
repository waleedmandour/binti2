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

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.control.ControlLoader

/**
 * Enthusiast-flavor initializer for the DirectControlBridge.
 *
 * Registers the [DirectControlBridge] with [ControlLoader] so it's
 * available as a fallback when the accessibility service can't be enabled
 * (the common case on BYD DiLink 3 head units where the OEM settings app
 * blocks accessibility enablement).
 *
 * Phase 3: also initializes the [BintiDiLinkBridge] which provides
 * Tier 2 vehicle data via ADB + `service call autoservice`. This is the
 * ADB substrate ported from DiPlay — it enables reading speed, SoC,
 * range, gear, and tyre data WITHOUT Accessibility scraping.
 *
 * Called by `BintiApplication.onCreate` only in the enthusiast flavor.
 *
 * @author Dr. Waleed Mandour
 */
object DirectControlInitializer {
    private const val TAG = "DirectControlInit"

    @Volatile private var instance: DirectControlBridge? = null

    fun init(context: Context) {
        if (instance != null) return

        // Phase 3: Initialize the DiLink ADB substrate bridge
        try {
            com.binti.dilink.dilink.vehicle.BintiDiLinkBridge
                .getInstance(context.applicationContext)
                .init()
            Log.i(TAG, "✅ BintiDiLinkBridge initialized (ADB substrate from DiPlay)")
        } catch (e: Throwable) {
            Log.w(TAG, "BintiDiLinkBridge init failed — Tier 2 ADB vehicle data unavailable", e)
        }

        // Register the existing DirectControlBridge (reflection-based fallback)
        val bridge = DirectControlBridge(context.applicationContext)
        instance = bridge
        ControlLoader.registerDirect(bridge)
        Log.i(TAG, "DirectControlBridge registered — fallback for when accessibility " +
                   "is unavailable on DiLink 3 head units")
    }
}
