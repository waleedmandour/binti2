// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.vehicle

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Recovery journal for vendor side-effects.
 *
 * Ported from DiPlay's recovery-journal pattern. Every vendor command that
 * cannot be atomically reverted is journaled in SharedPreferences BEFORE
 * the command is issued, and unjournaled only after confirmed revert.
 *
 * On [BintiApplication.onCreate] (or the first time the reader is used),
 * [recoverPending] should be called to check for and re-apply any pending
 * cleanups from a previous crash.
 *
 * **Pattern:**
 * ```kotlin
 * // Before issuing a command:
 * journal.markPending("cluster_mode_switch")
 * val success = issueClusterModeSwitch(newMode)
 * if (success) {
 *     journal.clearPending("cluster_mode_switch")
 * } else {
 *     // On next launch, recoverPending() will revert it
 * }
 * ```
 *
 * @author Dr. Waleed Mandour (pattern from DiPlay by shihabal3amri)
 */
class RecoveryJournal(context: Context) {

    companion object {
        private const val TAG = "RecoveryJournal"
        private const val PREFS_NAME = "binti_recovery_journal"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Mark a side-effect as pending. Called BEFORE the command is issued.
     * The [value] is an optional string describing what needs to be reverted
     * (e.g. "restore_stock_mode", "re-enable_bt").
     */
    fun markPending(key: String, value: String = "pending") {
        prefs.edit().putString(key, value).apply()
        Log.i(TAG, "Marked pending: $key = $value")
    }

    /**
     * Clear a pending side-effect after confirmed revert.
     */
    fun clearPending(key: String) {
        prefs.edit().remove(key).apply()
        Log.i(TAG, "Cleared pending: $key")
    }

    /**
     * Check if a side-effect is pending.
     */
    fun isPending(key: String): Boolean = prefs.contains(key)

    /**
     * Get the value of a pending side-effect, or null if not pending.
     */
    fun getPendingValue(key: String): String? = prefs.getString(key, null)

    /**
     * Get all pending side-effects. Called on app launch to recover
     * from a crash.
     */
    fun getAllPending(): Map<String, String> {
        val all = prefs.all
        return all.mapValues { it.value?.toString() ?: "" }
    }

    /**
     * Recover all pending side-effects. Called on [BintiApplication.onCreate].
     * Each pending entry's value is logged; the caller is responsible for
     * actually reverting the side-effect based on the key.
     *
     * Returns the number of pending entries found.
     */
    fun recoverPending(): Int {
        val pending = getAllPending()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending side-effects to recover")
            return 0
        }
        Log.w(TAG, "⚠️ Found ${pending.size} pending side-effects from a previous crash:")
        for ((key, value) in pending) {
            Log.w(TAG, "  - $key = $value")
        }
        return pending.size
    }

    /**
     * Clear all pending entries. Called after all recoveries are confirmed.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Cleared all pending side-effects")
    }
}
