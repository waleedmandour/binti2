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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Enthusiast-flavor implementation of [BrightnessBridge].
 *
 * Uses `Settings.System.putInt` to control screen brightness, which requires
 * the `WRITE_SETTINGS` permission — declared in the enthusiast-flavor manifest
 * (`app/src/enthusiast/AndroidManifest.xml`) ONLY. The store flavor never
 * includes this permission or this class.
 *
 * Before any write, checks [Settings.System.canWrite]; if not granted, launches
 * the ACTION_MANAGE_WRITE_SETTINGS intent so the user can grant it. This matches
 * the original brightness code that lived in DiLinkCommandExecutor, now isolated
 * behind the bridge contract.
 *
 * @author Dr. Waleed Mandour
 */
class RealBrightnessBridge(private val context: Context) : BrightnessBridge {

    companion object {
        private const val TAG = "BrightnessBridge"
    }

    override val isAvailable: Boolean = true

    override suspend fun setBrightness(level: Int): String {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettingsPermission()
            return "اسمح بتعديل الإعدادات أولاً"
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val brightness = level.coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            "تم ضبط الإضاءة على $level%"
        } catch (e: Exception) {
            Log.e(TAG, "setBrightness failed", e)
            "فشل ضبط الإضاءة: ${e.message}"
        }
    }

    override suspend fun adjustBrightness(delta: Int): String {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettingsPermission()
            return "اسمح بتعديل الإعدادات أولاً"
        }
        return try {
            val current = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
            )
            val newVal = (current + delta * 255 / 100).coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
            "الإضاءة ${newVal * 100 / 255}%"
        } catch (e: Exception) {
            Log.e(TAG, "adjustBrightness failed", e)
            "فشل تعديل الإضاءة: ${e.message}"
        }
    }

    override fun hasWriteSettingsPermission(): Boolean = Settings.System.canWrite(context)

    override fun requestWriteSettingsPermission() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch MANAGE_WRITE_SETTINGS intent", e)
        }
    }
}
