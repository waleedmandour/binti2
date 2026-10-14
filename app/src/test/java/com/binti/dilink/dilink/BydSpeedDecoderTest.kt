// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [BydSpeedDecoder] — the P0 speed-unit fix.
 *
 * Test vectors are taken from DiPlay's `BydWheelSpeedTest.kt` (GPL-3.0,
 * recorded from a real BYD on 2026-09-29), which prove the autoservice
 * speed field is ALREADY km/h:
 *
 *   raw bits 0x00000000 → 0.0  ("parked")
 *   raw bits 0x42100000 → 36.0 → 36 km/h (= 10 m/s)
 *
 * The old Binti implementation multiplied by 3.6, so a real 36 km/h drive
 * was announced as ~130 km/h. These tests lock the corrected behaviour in.
 */
class BydSpeedDecoderTest {

    private fun bits(f: Float): Int = java.lang.Float.floatToIntBits(f)

    // ── Known car recordings (DiPlay BydWheelSpeedTest) ────────────────────

    @Test
    fun `parked raw zero decodes to 0 kmh`() {
        assertEquals(0.0f, BydSpeedDecoder.decodeKmh(0x00000000)!!, 1e-9f)
    }

    @Test
    fun `real 36 kmh reading decodes unchanged`() {
        // DiPlay: Parcel(00000000 42100000) = 36.0 = "36 km/h"
        val kmh = BydSpeedDecoder.decodeKmh(bits(36.0f))
        assertNotNull(kmh)
        assertEquals(36.0f, kmh!!, 1e-6f)
    }

    @Test
    fun `float 10 point 0 is 10 kmh NOT 36 kmh`() {
        // Regression guard for the ×3.6 bug: raw 10.0 must stay 10.0 km/h.
        assertEquals(10.0f, BydSpeedDecoder.decodeKmh(bits(10.0f))!!, 1e-6f)
    }

    // ── Rejection of garbage (wrong firmware / unread fields) ──────────────

    @Test
    fun `null input bits rejected`() {
        assertNull(BydSpeedDecoder.decodeKmh(bits(Float.NaN)))
    }

    @Test
    fun `negative speed rejected`() {
        // DiPlay recorded -1.0 replies from a wrong field read.
        assertNull(BydSpeedDecoder.decodeKmh(bits(-1.0f)))
    }

    @Test
    fun `absurd speed rejected`() {
        assertNull(BydSpeedDecoder.decodeKmh(bits(301.0f)))
        assertNull(BydSpeedDecoder.decodeKmh(bits(5000.0f)))
    }

    @Test
    fun `infinite rejected`() {
        assertNull(BydSpeedDecoder.decodeKmh(bits(Float.POSITIVE_INFINITY)))
    }

    // ── Boundary values ────────────────────────────────────────────────────

    @Test
    fun `max plausible speed accepted`() {
        assertEquals(300.0f, BydSpeedDecoder.decodeKmh(bits(300.0f))!!, 1e-6f)
    }

    @Test
    fun `highway speed accepted unchanged`() {
        // 120 km/h motorway cruise must read 120, not 432.
        assertEquals(120.0f, BydSpeedDecoder.decodeKmh(bits(120.0f))!!, 1e-6f)
    }

    // ── Sanitize overload ──────────────────────────────────────────────────

    @Test
    fun `sanitize rejects out of range SoC values`() {
        assertNull(BydSpeedDecoder.sanitizeKmh(-3.0f))
        assertNull(BydSpeedDecoder.sanitizeKmh(900.0f))
        assertEquals(55.0f, BydSpeedDecoder.sanitizeKmh(55.0f)!!, 1e-6f)
    }
}
