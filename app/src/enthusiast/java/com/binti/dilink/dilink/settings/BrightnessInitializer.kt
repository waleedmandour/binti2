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
 * Enthusiast-flavor initializer — registers the real [RealBrightnessBridge]
 * via [BrightnessLoader]. Called by `BintiApplication.onCreate` only when
 * `BuildConfig.ENTHUSIAST_FLAVOR` is true.
 *
 * The enthusiast manifest declares `WRITE_SETTINGS`, so the bridge can
 * actually modify system brightness. The store flavor never calls this.
 *
 * @author Dr. Waleed Mandour
 */
object BrightnessInitializer {
    private const val TAG = "BrightnessInit"

    @Volatile private var instance: RealBrightnessBridge? = null

    fun init(context: Context) {
        if (instance != null) return
        val bridge = RealBrightnessBridge(context.applicationContext)
        instance = bridge
        BrightnessLoader.register(bridge)
        Log.i(TAG, "Enthusiast flavor active — RealBrightnessBridge registered " +
                   "(WRITE_SETTINGS declared in enthusiast manifest)")
    }
}
