package com.binti.dilink.dilink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the resource-ID constants defined in [BYDModels] match the
 * expected patterns for each supported BYD model, and that the fallback ID
 * lists cover every primary ID referenced by the executor.
 *
 * These tests guard against regressions when IDs are added or renamed — the
 * runtime AccessibilityNodeInfo API itself is mocked at the integration-test
 * level (out of scope for this unit test module).
 *
 * @author Dr. Waleed Mandour
 */
class BYDModelsTest {

    // ──────────────────────────────────────────────────────────────────────
    // Yuan Plus / Atto 3 (PRIMARY — IDs verified on real hardware)
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `Yuan Plus vehicle info IDs follow expected package and prefix`() {
        val ids = BYDModels.YuanPlus2023
        assertEquals("com.byd.auto.vehicleinfo:id/tv_battery_percent", ids.ID_VEHICLE_BATTERY)
        assertEquals("com.byd.auto.vehicleinfo:id/tv_range",           ids.ID_VEHICLE_RANGE)
        assertEquals("com.byd.auto.vehicleinfo:id/tv_outside_temp",    ids.ID_VEHICLE_TEMP_OUT)
        assertEquals("com.byd.auto.vehicleinfo:id/tv_inside_temp",     ids.ID_VEHICLE_TEMP_IN)
    }

    @Test fun `Yuan Plus AC IDs follow expected package and prefix`() {
        val ids = BYDModels.YuanPlus2023
        assertEquals("com.byd.auto.ac:id/btn_power",        ids.ID_AC_POWER)
        assertEquals("com.byd.auto.ac:id/btn_temp_up",      ids.ID_AC_TEMP_UP)
        assertEquals("com.byd.auto.ac:id/btn_temp_down",    ids.ID_AC_TEMP_DOWN)
        assertEquals("com.byd.auto.ac:id/tv_temperature",   ids.ID_AC_TEMP_DISPLAY)
        assertEquals("com.byd.auto.ac:id/btn_fan_up",       ids.ID_AC_FAN_UP)
        assertEquals("com.byd.auto.ac:id/btn_fan_down",     ids.ID_AC_FAN_DOWN)
    }

    @Test fun `Yuan Plus media IDs follow expected package and prefix`() {
        val ids = BYDModels.YuanPlus2023
        assertEquals("com.byd.auto.media:id/btn_play",     ids.ID_MEDIA_PLAY)
        assertEquals("com.byd.auto.media:id/btn_pause",    ids.ID_MEDIA_PAUSE)
        assertEquals("com.byd.auto.media:id/btn_next",     ids.ID_MEDIA_NEXT)
        assertEquals("com.byd.auto.media:id/btn_previous", ids.ID_MEDIA_PREV)
    }

    // ──────────────────────────────────────────────────────────────────────
    // forModel() factory
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `forModel resolves Yuan and Atto variants to the verified Yuan Plus ID set`() {
        val cases = listOf("Yuan Plus", "Atto 3", "YuanPlus", "ATTO3")
        cases.forEach { model ->
            val cfg = BYDModels.forModel(model)
            assertTrue("'$model' should be confirmed", cfg.isConfirmed)
            assertEquals("Yuan Plus / Atto 3", cfg.modelName)
            // Verified IDs come from the YuanPlus2023 object
            assertEquals(BYDModels.YuanPlus2023.ID_VEHICLE_BATTERY, cfg.ids.ID_VEHICLE_BATTERY)
        }
    }

    @Test fun `forModel resolves Dolphin, Seal, Han, Tang to unconfirmed ID sets`() {
        listOf("Dolphin" to "Dolphin",
               "Seal"    to "Seal",
               "Han"     to "Han",
               "Tang"    to "Tang").forEach { (input, expected) ->
            val cfg = BYDModels.forModel(input)
            assertEquals(expected, cfg.modelName)
            assertTrue("'$input' should be unconfirmed", !cfg.isConfirmed)
        }
    }

    @Test fun `forModel falls back to Yuan Plus for unknown model strings`() {
        val cfg = BYDModels.forModel("SomeUnknownModel XYZ")
        assertEquals("Unknown (using Yuan Plus fallback)", cfg.modelName)
        assertFalse(cfg.isConfirmed)
        // Falls back to Yuan Plus IDs so the executor still has something to try
        assertEquals(BYDModels.YuanPlus2023.ID_AC_POWER, cfg.ids.ID_AC_POWER)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Fallback ID lists
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `AC_POWER fallback list includes primary IDs for every supported model`() {
        val fallbacks = BYDModels.FallbackIDs.AC_POWER
        // Primary IDs that the executor will look up first, then fall back if not found
        assertTrue(fallbacks.contains(BYDModels.YuanPlus2023.ID_AC_POWER))
        assertTrue(fallbacks.contains(BYDModels.Dolphin.ID_AC_POWER))
        assertTrue(fallbacks.contains(BYDModels.Seal.ID_AC_POWER))
        assertTrue(fallbacks.contains(BYDModels.Han.ID_AC_POWER))
        assertTrue(fallbacks.contains(BYDModels.Tang.ID_AC_POWER))
    }

    @Test fun `AC_TEMP_UP and AC_TEMP_DOWN fallback lists are non-empty`() {
        assertTrue(BYDModels.FallbackIDs.AC_TEMP_UP.isNotEmpty())
        assertTrue(BYDModels.FallbackIDs.AC_TEMP_DOWN.isNotEmpty())
    }

    @Test fun `PHONE_ANSWER and PHONE_REJECT fallback lists cover accept and decline variants`() {
        val answer = BYDModels.FallbackIDs.PHONE_ANSWER
        val reject = BYDModels.FallbackIDs.PHONE_REJECT
        assertTrue(answer.any { it.contains("answer", ignoreCase = true) || it.contains("accept", ignoreCase = true) })
        assertTrue(reject.any { it.contains("reject", ignoreCase = true) || it.contains("decline", ignoreCase = true) })
    }

    @Test fun `MEDIA_PLAY and MEDIA_NEXT fallback lists cover primary IDs`() {
        assertTrue(BYDModels.FallbackIDs.MEDIA_PLAY.contains(BYDModels.YuanPlus2023.ID_MEDIA_PLAY))
        assertTrue(BYDModels.FallbackIDs.MEDIA_NEXT.contains(BYDModels.YuanPlus2023.ID_MEDIA_NEXT))
    }

    @Test fun `NAV_CANCEL fallback list covers primary ID`() {
        assertTrue(BYDModels.FallbackIDs.NAV_CANCEL.contains(BYDModels.YuanPlus2023.ID_NAV_CANCEL))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Packages object
    // ──────────────────────────────────────────────────────────────────────

    @Test fun `Packages constants match the DiLink package names`() {
        assertEquals("com.byd.auto.ac",          BYDModels.Packages.AC)
        assertEquals("com.byd.auto.navigation",  BYDModels.Packages.NAV)
        assertEquals("com.byd.auto.media",       BYDModels.Packages.MEDIA)
        assertEquals("com.byd.auto.settings",    BYDModels.Packages.SETTINGS)
        assertEquals("com.byd.auto.phone",       BYDModels.Packages.PHONE)
        assertEquals("com.byd.auto.launcher",    BYDModels.Packages.LAUNCHER)
        assertEquals("com.byd.auto.climate",     BYDModels.Packages.CLIMATE)
        assertEquals("com.byd.auto.vehicleinfo", BYDModels.Packages.VEHICLE)
        assertEquals("com.byd.auto.energy",      BYDModels.Packages.ENERGY)
        assertEquals("com.byd.auto.camera",      BYDModels.Packages.CAMERA)
    }
}
