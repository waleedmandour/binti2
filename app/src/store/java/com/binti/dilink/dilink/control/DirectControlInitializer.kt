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

/**
 * Store-flavor initializer for DirectControl — intentionally a no-op.
 *
 * The store flavor (Huawei AppGallery) does NOT include the
 * DirectControlBridge (which uses reflection against bydauto.* services).
 * Only the enthusiast flavor registers it.
 *
 * @author Dr. Waleed Mandour
 */
object DirectControlInitializer {
    private const val TAG = "DirectControlInit"

    fun init(context: Context) {
        Log.i(TAG, "Store flavor active — DirectControlBridge NOT registered " +
                   "(accessibility-only mode; use enthusiast flavor for direct control)")
    }
}
