

package com.binti.dilink.dilink

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [VehicleDataRepository].
 *
 * Verifies publish/merge semantics, staleness checks, and the freshOrNull
 * contract used by voice-query handlers in [DiLinkCommandExecutor].
 *
 * @author Dr. Waleed Mandour
 */
class VehicleDataRepositoryTest {

    private lateinit var repo: VehicleDataRepository

    @Before fun setUp() {
        repo = VehicleDataRepository()
    }

    @After fun tearDown() {
        repo.reset()
    }

    @Test fun `freshOrNull returns null on a never-published repository`() {
        assertNull(repo.freshOrNull())
    }

    @Test fun `freshOrNull returns the snapshot immediately after publish`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80, estimatedRangeKm = 350))
        val fresh = repo.freshOrNull()
        assertNotNull(fresh)
        assertEquals(80, fresh!!.batteryPercent)
        assertEquals(350, fresh.estimatedRangeKm)
        assertFalse(fresh.hasAdvancedTelemetry)
    }

    @Test fun `isStale returns true after STALE_AFTER_MS has elapsed`() {
        // Publish at t=1000
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80, timestamp = 1_000L))
        // Check at t = 1000 + STALE_AFTER_MS + 1
        val staleNow = 1_000L + VehicleDataRepository.STALE_AFTER_MS + 1L
        assertTrue(repo.isStale(staleNow))
        assertNull(repo.freshOrNull(staleNow))
    }

    @Test fun `isStale returns false within the freshness window`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80, timestamp = 1_000L))
        val freshNow = 1_000L + VehicleDataRepository.STALE_AFTER_MS - 1L
        assertFalse(repo.isStale(freshNow))
        val fresh = repo.freshOrNull(freshNow)
        assertNotNull(fresh)
        assertEquals(80, fresh!!.batteryPercent)
    }

    @Test fun `publishAccessibility preserves previously known fields when partial has nulls`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80, estimatedRangeKm = 350))
        repo.publishAccessibility(VehicleSnapshot(acOn = true))   // AC scrape from a different screen
        val snap = repo.current()
        assertEquals(80,  snap.batteryPercent)   // preserved
        assertEquals(350, snap.estimatedRangeKm) // preserved
        assertEquals(true, snap.acOn)            // updated
    }

    @Test fun `mergeAdvanced overlays Tier 2 fields on top of Tier 1 baseline`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80, estimatedRangeKm = 350))
        repo.mergeAdvanced(VehicleSnapshot(
            hvBusVoltageV = 396,
            motorRpm      = 4_200
        ))
        val snap = repo.current()
        assertEquals(80,  snap.batteryPercent)         // Tier 1 preserved
        assertEquals(350, snap.estimatedRangeKm)        // Tier 1 preserved
        assertEquals(396, snap.hvBusVoltageV)           // Tier 2 added
        assertEquals(4_200, snap.motorRpm)              // Tier 2 added
        assertTrue(snap.hasAdvancedTelemetry)
        assertEquals(DataSource.ADVANCED_TELEMETRY, snap.source)
    }

    @Test fun `mergeAdvanced is a no-op when partial has no advanced fields`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80))
        // mergeAdvanced with a Tier-1-only snapshot is a no-op: the repository
        // keeps the previously published Tier 1 values unchanged. Tier 1 data
        // MUST flow through publishAccessibility, never through mergeAdvanced.
        repo.mergeAdvanced(VehicleSnapshot(batteryPercent = 90))   // no advanced fields
        val snap = repo.current()
        assertEquals(80, snap.batteryPercent)   // unchanged — mergeAdvanced was a no-op
        assertFalse(snap.hasAdvancedTelemetry)
        assertEquals(DataSource.ACCESSIBILITY, snap.source)
    }

    @Test fun `reset wipes all fields`() {
        repo.publishAccessibility(VehicleSnapshot(batteryPercent = 80))
        repo.mergeAdvanced(VehicleSnapshot(hvBusVoltageV = 396))
        repo.reset()
        assertNull(repo.current().batteryPercent)
        assertNull(repo.current().hvBusVoltageV)
        assertFalse(repo.current().hasAdvancedTelemetry)
        assertNull(repo.freshOrNull())
    }
}
