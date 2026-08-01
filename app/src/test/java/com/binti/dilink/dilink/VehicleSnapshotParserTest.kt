package com.binti.dilink.dilink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Top-level extension functions from VehicleSnapshotParser.kt — imported so the
// tests can call "82%".parsePercent() directly.
import com.binti.dilink.dilink.parsePercent
import com.binti.dilink.dilink.parseRange
import com.binti.dilink.dilink.parseTemp

/**
 * Unit tests for [VehicleSnapshotParser].
 *
 * Uses an in-memory fake [VehicleSnapshotParser.IDLookup] so the parser can
 * be exercised without the real Android Accessibility framework. Covers:
 *  - happy-path parsing for Yuan Plus / Atto 3 primary IDs
 *  - fallback ID walking when the primary ID misses
 *  - the various string → typed value helpers (percent, range, temp)
 *  - Arabic unit suffixes ("كم", "٪") which DiLink actually displays
 *
 * @author Dr. Waleed Mandour
 */
class VehicleSnapshotParserTest {

    /** In-memory ID → (text, isChecked) map — stands in for the Accessibility tree. */
    private class FakeLookup(
        private val texts:     Map<String, String>   = emptyMap(),
        private val checkable: Map<String, Boolean>  = emptyMap()
    ) : VehicleSnapshotParser.IDLookup {
        override fun text(id: String): String?      = texts[id]
        override fun isChecked(id: String): Boolean? = checkable[id]
    }

    // ──────────────────────────────────────────────────────────────────────
    // Happy path — Yuan Plus / Atto 3 primary IDs
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `parse extracts all Tier 1 fields when all primary IDs are present`() {
        val ids = BYDModels.YuanPlus2023
        val lookup = FakeLookup(
            texts = mapOf(
                ids.ID_VEHICLE_BATTERY  to "82%",
                ids.ID_VEHICLE_RANGE    to "350 km",
                ids.ID_VEHICLE_TEMP_OUT to "30°",
                ids.ID_VEHICLE_TEMP_IN  to "24°",
                ids.ID_AC_TEMP_DISPLAY  to "22"
            ),
            checkable = mapOf(ids.ID_AC_POWER to true)
        )
        val snap = VehicleSnapshotParser.parse(lookup = lookup)

        assertEquals(82,  snap.batteryPercent)
        assertEquals(350, snap.estimatedRangeKm)
        assertEquals(30,  snap.outsideTempC)
        assertEquals(true, snap.acOn)
        assertEquals(22,  snap.acTargetTempC)
        assertNull(snap.doorsLocked)
        assertNull(snap.isCharging)
        assertEquals(DataSource.ACCESSIBILITY, snap.source)
    }

    @Test fun `parse handles Arabic unit suffixes displayed on real DiLink UIs`() {
        val ids = BYDModels.YuanPlus2023
        val lookup = FakeLookup(
            texts = mapOf(
                ids.ID_VEHICLE_BATTERY  to "٨٢٪",   // not used — toIntOrNull returns null
                ids.ID_VEHICLE_RANGE    to "٣٥٠كم",
                ids.ID_VEHICLE_TEMP_OUT to "٣٠°"
            )
        )
        val snap = VehicleSnapshotParser.parse(lookup = lookup)
        // Arabic-Indic digits are NOT auto-converted (intentional — caller should
        // normalize first). The percent/range/temp suffix strippers do work.
        assertNull(snap.batteryPercent)             // Arabic-Indic digits stay null
        assertNull(snap.estimatedRangeKm)           // Arabic-Indic digits stay null
        assertNull(snap.outsideTempC)               // Arabic-Indic digits stay null
    }

    @Test fun `parse handles Latin digits with Arabic units`() {
        val ids = BYDModels.YuanPlus2023
        val lookup = FakeLookup(
            texts = mapOf(
                ids.ID_VEHICLE_BATTERY  to "82٪",     // Latin digit + Arabic percent
                ids.ID_VEHICLE_RANGE    to "350كم",   // Latin digit + Arabic km
                ids.ID_VEHICLE_TEMP_OUT to "30°"
            )
        )
        val snap = VehicleSnapshotParser.parse(lookup = lookup)
        assertEquals(82,  snap.batteryPercent)
        assertEquals(350, snap.estimatedRangeKm)
        assertEquals(30,  snap.outsideTempC)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Fallback ID walking
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `parse falls back to alternate IDs when primary misses`() {
        val ids = BYDModels.YuanPlus2023
        // Primary ID_AC_POWER missing; fallback list contains "com.byd.auto.climate:id/btn_power"
        val fallbacks = mapOf(ids.ID_AC_POWER to BYDModels.FallbackIDs.AC_POWER)
        val climateFallback = "com.byd.auto.climate:id/btn_power"
        val lookup = FakeLookup(
            texts = mapOf(ids.ID_VEHICLE_BATTERY to "82%"),
            checkable = mapOf(climateFallback to true)   // AC power found via fallback
        )
        val snap = VehicleSnapshotParser.parse(lookup = lookup, fallbackIds = fallbacks)
        assertEquals(82, snap.batteryPercent)
        assertEquals(true, snap.acOn)
    }

    @Test fun `parse returns nulls for missing fields without throwing`() {
        val lookup = FakeLookup()  // empty
        val snap = VehicleSnapshotParser.parse(lookup = lookup)
        assertNull(snap.batteryPercent)
        assertNull(snap.estimatedRangeKm)
        assertNull(snap.acOn)
        assertNull(snap.acTargetTempC)
        assertNull(snap.outsideTempC)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Dolphin / Seal / Han / Tang ID sets
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `parse works with Dolphin ID set`() {
        val ids = BYDModels.Dolphin
        val lookup = FakeLookup(
            texts = mapOf(
                ids.ID_VEHICLE_BATTERY to "65%",
                ids.ID_VEHICLE_RANGE   to "280 km",
                ids.ID_AC_POWER        to "off"      // text, not state — should be ignored
            )
        )
        val snap = VehicleSnapshotParser.parse(ids = ids, lookup = lookup)
        assertEquals(65, snap.batteryPercent)
        assertEquals(280, snap.estimatedRangeKm)
        assertNull(snap.acOn)   // text on a checkable ID is irrelevant; state lookup returns null
    }

    @Test fun `parse works with Seal ID set (which delegates to Yuan Plus)`() {
        val ids = BYDModels.Seal
        val lookup = FakeLookup(
            texts = mapOf(
                ids.ID_VEHICLE_BATTERY to "90%",
                ids.ID_VEHICLE_RANGE   to "420 km"
            )
        )
        val snap = VehicleSnapshotParser.parse(ids = ids, lookup = lookup)
        assertEquals(90, snap.batteryPercent)
        assertEquals(420, snap.estimatedRangeKm)
    }

    // ──────────────────────────────────────────────────────────────────────
    // String-parsing helpers
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `parsePercent strips Latin and Arabic percent signs`() {
        assertEquals(82, "82%".parsePercent())
        assertEquals(82, "82٪".parsePercent())
        assertEquals(82, " 82 % ".parsePercent())
        assertNull("abc".parsePercent())
    }

    @Test fun `parseRange strips Latin km, Arabic كم, and standalone digits`() {
        assertEquals(350, "350 km".parseRange())
        assertEquals(350, "350كم".parseRange())
        assertEquals(350, "350".parseRange())
        assertEquals(350, " 350 كيلومتر ".parseRange())
        assertNull("abc".parseRange())
    }

    @Test fun `parseTemp strips degree symbol and Celsius suffix`() {
        assertEquals(30, "30°".parseTemp())
        assertEquals(30, "30 °C".parseTemp())
        assertEquals(30, "30℃".parseTemp())
        assertEquals(30, "30".parseTemp())
        assertNull("hot".parseTemp())
    }
}
