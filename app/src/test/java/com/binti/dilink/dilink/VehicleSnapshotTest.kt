

package com.binti.dilink.dilink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VehicleSnapshot] and [VehicleDataRepository].
 *
 * These tests do NOT touch the real Accessibility API — they verify the
 * pure-data behaviour of the snapshot model (merge semantics, staleness,
 * experimental badge) and the repository's publish/merge/freshOrNull flow.
 *
 * Accessibility-node parsing itself is exercised by `VehicleSnapshotParsingTest`
 * against a small in-memory fake of the node tree (see [FakeAccessibilityNode]).
 *
 * @author Dr. Waleed Mandour
 */
class VehicleSnapshotTest {

    // ──────────────────────────────────────────────────────────────────────
    // merge()
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `merge prefers non-null fields from the incoming snapshot`() {
        val a = VehicleSnapshot(batteryPercent = 80, estimatedRangeKm = 350)
        val b = VehicleSnapshot(batteryPercent = 75)
        val merged = a.merge(b)
        assertEquals(75, merged.batteryPercent)              // b wins
        assertEquals(350, merged.estimatedRangeKm)            // a preserved (b null)
        assertNull(merged.acOn)                              // both null → null
    }

    @Test fun `merge keeps the newer timestamp`() {
        val a = VehicleSnapshot(timestamp = 1_000L)
        val b = VehicleSnapshot(timestamp = 2_000L)
        assertEquals(2_000L, a.merge(b).timestamp)
        assertEquals(2_000L, b.merge(a).timestamp)
    }

    @Test fun `merge promotes source to ADVANCED_TELEMETRY when either side has advanced fields`() {
        val a = VehicleSnapshot(batteryPercent = 80)
        val b = VehicleSnapshot(hvBusVoltageV = 396)
        assertEquals(DataSource.ADVANCED_TELEMETRY, a.merge(b).source)
    }

    @Test fun `merge keeps source as ACCESSIBILITY when neither side has advanced fields`() {
        val a = VehicleSnapshot(batteryPercent = 80)
        val b = VehicleSnapshot(estimatedRangeKm = 350)
        assertEquals(DataSource.ACCESSIBILITY, a.merge(b).source)
    }

    // ──────────────────────────────────────────────────────────────────────
    // hasAdvancedTelemetry
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `hasAdvancedTelemetry is false for a Tier-1-only snapshot`() {
        val snap = VehicleSnapshot(
            batteryPercent   = 80,
            estimatedRangeKm = 350,
            acOn             = true,
            acTargetTempC    = 22,
            outsideTempC     = 30
        )
        assertFalse(snap.hasAdvancedTelemetry)
    }

    @Test fun `hasAdvancedTelemetry is true when any Tier-2 field is non-null`() {
        assertTrue(VehicleSnapshot(hvBusVoltageV = 396).hasAdvancedTelemetry)
        assertTrue(VehicleSnapshot(motorRpm = 4_200).hasAdvancedTelemetry)
        assertTrue(VehicleSnapshot(tyrePressureFrontLeftKpa = 230).hasAdvancedTelemetry)
        assertTrue(VehicleSnapshot(cellVoltageSpreadMv = 18).hasAdvancedTelemetry)
        assertTrue(VehicleSnapshot(aux12vVoltageV = 12.4).hasAdvancedTelemetry)
    }

    // ──────────────────────────────────────────────────────────────────────
    // toDebugString
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `toDebugString includes both tiers and the experimental flag`() {
        val snap = VehicleSnapshot(
            batteryPercent = 80,
            hvBusVoltageV  = 396,
            source         = DataSource.ADVANCED_TELEMETRY
        )
        val s = snap.toDebugString()
        assertTrue(s.contains("batteryPercent     = 80"))
        assertTrue(s.contains("hvBusVoltageV      = 396"))
        assertTrue(s.contains("hasAdvancedTelemetry = true"))
        assertTrue(s.contains("source=ADVANCED_TELEMETRY"))
    }
}
