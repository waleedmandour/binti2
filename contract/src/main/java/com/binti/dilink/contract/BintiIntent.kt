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

package com.binti.dilink.contract

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Public intent contract for Binti voice assistant.
 *
 * This module is the stable API that external automation apps (Tasker,
 * MacroDroid, ADB shell scripts, other BYD head-unit apps) can depend on
 * to trigger Binti actions without depending on the full `:app` module.
 *
 * **Usage from an external app:**
 * ```kotlin
 * // Start voice listening
 * val intent = BintiIntent.StartVoice.create(packageManager)
 * context.sendBroadcast(intent, BintiIntent.PERMISSION)
 *
 * // Or via deep link
 * val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("binti://start-voice"))
 * context.startActivity(deepLink)
 * ```
 *
 * **Usage via ADB:**
 * ```bash
 * adb shell am start -a android.intent.action.VIEW -d "binti://start-voice"
 * adb shell am start -a android.intent.action.VIEW -d "binti://toggle-ac"
 * adb shell am start -a android.intent.action.VIEW -d "binti://navigate-home"
 * ```
 *
 * **Security:** All broadcasts require the signature-level permission
 * [PERMISSION]. Only apps signed with the same key as Binti can send
 * or receive these broadcasts. Deep links go through [BintiAutomationActivity]
 * which is a transparent no-history trampoline.
 *
 * @author Dr. Waleed Mandour
 */
object BintiIntent {

    /**
     * The signature-level permission that guards Binti's widget and
     * automation broadcasts. Only apps signed with the same key as Binti
     * can send or receive these broadcasts.
     */
    const val PERMISSION = "com.binti.dilink.permission.WIDGET_ACTION"

    /**
     * The deep-link scheme for Binti automation. External apps can trigger
     * Binti actions by starting an activity with a `binti://` URI.
     */
    const val DEEP_LINK_SCHEME = "binti"

    /**
     * The host component for deep links. The full URI format is:
     * `binti://<host>` where host is one of the `HOST_*` constants below.
     */
    const val DEEP_LINK_AUTHORITY = "com.binti.dilink"

    // ──────────────────────────────────────────────────────────────────────────
    // Deep-link hosts
    // ──────────────────────────────────────────────────────────────────────────

    /** `binti://start-voice` — start voice listening (equivalent to wake word). */
    const val HOST_START_VOICE = "start-voice"

    /** `binti://stop` — stop the voice service. */
    const val HOST_STOP = "stop"

    /** `binti://toggle-ac` — toggle AC power. */
    const val HOST_TOGGLE_AC = "toggle-ac"

    /** `binti://navigate-home` — navigate to saved home address. */
    const val HOST_NAVIGATE_HOME = "navigate-home"

    /** `binti://media-play` — play/pause media. */
    const val HOST_MEDIA_PLAY = "media-play"

    /** `binti://next-track` — skip to next track. */
    const val HOST_NEXT_TRACK = "next-track"

    /** `binti://find-station` — find nearest EV charging station. */
    const val HOST_FIND_STATION = "find-station"

    // ──────────────────────────────────────────────────────────────────────────
    // Broadcast actions (for PendingIntent / programmatic triggers)
    // ──────────────────────────────────────────────────────────────────────────

    /** Broadcast action: start voice listening. */
    const val ACTION_START_VOICE = "com.binti.dilink.action.START_VOICE"

    /** Broadcast action: toggle AC power. */
    const val ACTION_TOGGLE_AC = "com.binti.dilink.action.TOGGLE_AC"

    /** Broadcast action: navigate to saved home. */
    const val ACTION_NAVIGATE_HOME = "com.binti.dilink.action.NAVIGATE_HOME"

    /** Broadcast action: play/pause media. */
    const val ACTION_MEDIA_PLAY = "com.binti.dilink.action.MEDIA_PLAY"

    /** Broadcast action: skip to next track. */
    const val ACTION_NEXT_TRACK = "com.binti.dilink.action.NEXT_TRACK"

    /** Broadcast action: find nearest EV charging station. */
    const val ACTION_FIND_STATION = "com.binti.dilink.action.FIND_STATION"

    // ──────────────────────────────────────────────────────────────────────────
    // Intent builders
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Build a deep-link intent for the given host. The intent targets
     * [BintiAutomationActivity] via `ACTION_VIEW` + the `binti://` scheme.
     * External apps can `startActivity()` this intent.
     */
    fun deepLink(host: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.Builder()
                .scheme(DEEP_LINK_SCHEME)
                .authority(host)
                .build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent wrappers for each automation action. Each creates a broadcast
     * intent targeting Binti's widget receiver (guarded by [PERMISSION]).
     */
    object StartVoice {
        val action = ACTION_START_VOICE
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }

    object ToggleAc {
        val action = ACTION_TOGGLE_AC
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }

    object NavigateHome {
        val action = ACTION_NAVIGATE_HOME
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }

    object MediaPlay {
        val action = ACTION_MEDIA_PLAY
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }

    object NextTrack {
        val action = ACTION_NEXT_TRACK
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }

    object FindStation {
        val action = ACTION_FIND_STATION
        fun create(context: Context): Intent =
            Intent(action).apply {
                component = ComponentName(
                    context.packageName,
                    "com.binti.dilink.ui.QuickActionsWidget"
                )
            }
    }
}
