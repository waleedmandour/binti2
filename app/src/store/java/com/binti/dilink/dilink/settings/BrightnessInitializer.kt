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

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.settings.BrightnessLoader

/**
 * Store-flavor initializer for brightness control — explicitly logs that this
 * build does NOT register a real brightness bridge, so the NoOpBrightnessBridge
 * default remains in place. This guarantees that the store flavor (Huawei
 * AppGallery) never declares WRITE_SETTINGS or attempts to modify system
 * brightness settings.
 *
 * Called by `BintiApplication.onCreate` via the same flavor-polymorphic
 * `BrightnessInitializer.init(this)` pattern used for telemetry.
 *
 * @author Dr. Waleed Mandour
 */
object BrightnessInitializer {
    private const val TAG = "BrightnessInit"

    @JvmOverloads
    fun init(context: Context? = null) {
        Log.i(TAG, "Store flavor active — BrightnessBridge NOT registered " +
                   "(NoOpBrightnessBridge remains in place; WRITE_SETTINGS " +
                   "not declared in store manifest)")
        // Explicitly do NOT call BrightnessLoader.register(...) — the no-op
        // default is correct for the AppGallery build.
        check(!BrightnessLoader.get().isAvailable) {
            "Store flavor must NOT have a real BrightnessBridge registered"
        }
    }
}
