// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.voice

/**
 * v3.0.2 FIX — retry policy for the wake-word loop when the microphone
 * cannot be opened (dead / held mic on the head unit).
 *
 * Previously the wake loop re-armed every 300 ms with no user feedback when
 * AudioRecord failed to initialise, burning battery forever and hiding the
 * problem. This policy defines:
 *  - how short a listening session must be to count as a mic failure,
 *  - the exponential backoff between retries (1 s → 2 s → 4 s … capped),
 *
 * Kept dependency-free and pure so it is unit-testable without Android.
 *
 * @author Dr. Waleed Mandour
 */
object MicRetryPolicy {

    /**
     * A listening session shorter than this (with no command session holding
     * the mic) means AudioRecord failed to open. A healthy session ends only
     * when a command session takes the microphone — that handoff sets
     * isListening == true and never reaches the backoff branch.
     */
    const val MIN_HEALTHY_SESSION_MS: Long = 1_000L

    /** First backoff step. */
    const val BASE_BACKOFF_MS: Long = 1_000L

    /** Ceiling — keep retrying forever, but never faster than every 30 s. */
    const val MAX_BACKOFF_MS: Long = 30_000L

    /**
     * Exponential backoff for the Nth consecutive mic failure (1-based).
     * 1 → 1 s, 2 → 2 s, 3 → 4 s, 4 → 8 s, 5 → 16 s, 6+ → 30 s (capped).
     */
    fun backoffMs(consecutiveFailures: Int): Long {
        // Shift without overflow: clamp the exponent before shifting.
        val exponent = (consecutiveFailures - 1).coerceIn(0, 10)
        val shifted = BASE_BACKOFF_MS shl exponent
        return if (shifted < 0 || shifted > MAX_BACKOFF_MS) MAX_BACKOFF_MS else shifted
    }
}
