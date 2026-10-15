// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MicRetryPolicy] — the v3.0.2 dead-microphone backoff used
 * by BintiService's wake-word loop.
 */
class MicRetryPolicyTest {

    @Test
    fun `first failure waits one second`() {
        assertEquals(1_000L, MicRetryPolicy.backoffMs(1))
    }

    @Test
    fun `backoff doubles each consecutive failure`() {
        assertEquals(1_000L, MicRetryPolicy.backoffMs(1))
        assertEquals(2_000L, MicRetryPolicy.backoffMs(2))
        assertEquals(4_000L, MicRetryPolicy.backoffMs(3))
        assertEquals(8_000L, MicRetryPolicy.backoffMs(4))
        assertEquals(16_000L, MicRetryPolicy.backoffMs(5))
    }

    @Test
    fun `backoff is capped at thirty seconds`() {
        assertEquals(30_000L, MicRetryPolicy.backoffMs(6))
        assertEquals(30_000L, MicRetryPolicy.backoffMs(7))
        assertEquals(30_000L, MicRetryPolicy.backoffMs(100))
    }

    @Test
    fun `exponent is clamped so shifts cannot overflow negative`() {
        // A huge failure count must never produce a negative / overflowed delay.
        repeat(1000) { n ->
            val delay = MicRetryPolicy.backoffMs(n + 1)
            assertTrue("delay for $n failures must be positive", delay > 0)
            assertTrue(
                "delay for $n failures must not exceed the cap",
                delay <= MicRetryPolicy.MAX_BACKOFF_MS
            )
        }
    }

    @Test
    fun `zero and negative failure counts are treated as first failure`() {
        assertEquals(1_000L, MicRetryPolicy.backoffMs(0))
        assertEquals(1_000L, MicRetryPolicy.backoffMs(-3))
    }

    @Test
    fun `policy constants are sane`() {
        assertTrue(MicRetryPolicy.MIN_HEALTHY_SESSION_MS > 0)
        assertTrue(MicRetryPolicy.BASE_BACKOFF_MS >= 1_000L)
        assertTrue(MicRetryPolicy.MAX_BACKOFF_MS >= MicRetryPolicy.BASE_BACKOFF_MS)
    }
}
