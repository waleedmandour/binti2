// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink

/**
 * P0 FIX (speed unit): pure decoder for the `service call autoservice` speed
 * field. Lives in the main source set so it is unit-testable in every flavor.
 *
 * **The raw autoservice speed value is ALREADY km/h.**
 *
 * Evidence (DiPlay, GPL-3.0, BydWheelSpeed.kt / BydWheelSpeedTest.kt):
 *  - the field is declared as `service call autoservice 7 i32 1013
 *    i32 -1807745016 // float km/h`
 *  - the test vector `Parcel(00000000 42100000)` — float bits 0x42100000 —
 *    decodes to 36.0 and DiPlay treats it as **36 km/h** (= 10 m/s).
 *
 * The previous Binti implementation treated the raw value as m/s and
 * multiplied by 3.6, so a real 36 km/h reading was announced as ~130 km/h.
 *
 * Ported from DiPlay's `BydWheelSpeed.metersPerSecond()` range check
 * (0.0..300.0), inverted to return km/h instead of m/s.
 *
 * @author Dr. Waleed Mandour (range-check pattern from DiPlay by shihabal3amri)
 */
object BydSpeedDecoder {

    /** Physically plausible speed ceiling for the sanity check (km/h). */
    const val MAX_PLAUSIBLE_KMH = 300f

    /**
     * Decode raw float bits from the autoservice parcel into km/h.
     *
     * @param rawBits the integer returned inside the service-call parcel,
     *                interpreted as IEEE-754 float bits.
     * @return speed in km/h, or null when the value is NaN/negative/absurd
     *         (an unread field on the wrong firmware usually returns garbage).
     */
    fun decodeKmh(rawBits: Int): Float? {
        val kmh = Float.fromBits(rawBits)
        if (!kmh.isFinite()) return null
        if (kmh < 0f || kmh > MAX_PLAUSIBLE_KMH) return null
        return kmh
    }

    /**
     * Convenience overload for already-decoded floats (e.g. values that went
     * through [com.binti.dilink.dilink.BydParcel] as floats).
     */
    fun sanitizeKmh(kmh: Float): Float? {
        if (!kmh.isFinite()) return null
        if (kmh < 0f || kmh > MAX_PLAUSIBLE_KMH) return null
        return kmh
    }
}
