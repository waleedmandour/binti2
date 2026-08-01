

package com.binti.dilink.dilink

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pure, side-effect-free extractor that turns an Accessibility node tree
 * (rooted at the active DiLink window) into a partial [VehicleSnapshot].
 *
 * Extracted from `DiLinkAccessibilityService` so the parsing logic can be
 * unit-tested in isolation — see `VehicleSnapshotParserTest`.
 *
 * The parser takes an [IDLookup] abstraction that wraps
 * `root.findAccessibilityNodeInfosByViewId(id)`, so unit tests can plug in
 * a fake without touching the real Android Accessibility API.
 *
 *  - Tier 1 fields only. Tier 2 advanced telemetry lives in
 *    `AdvancedTelemetryProbe` (enthusiast flavor only).
 *  - Failures are silent: any field whose node can't be found stays null.
 *
 * @author Dr. Waleed Mandour
 */
object VehicleSnapshotParser {

    /**
     * Pure-Kotlin abstraction over `findAccessibilityNodeInfosByViewId`.
     *
     * `text(id)` returns the text or content-description of the first node
     * matching `id` (or `null`); `isChecked(id)` returns the toggle state
     * of the first node matching `id` (or `null`).
     *
     * The default implementation wraps a real [AccessibilityNodeInfo] root.
     * Tests supply their own implementation against an in-memory ID → text
     * map — see `VehicleSnapshotParserTest`.
     */
    interface IDLookup {
        fun text(id: String): String?
        fun isChecked(id: String): Boolean?
    }

    /** Wraps a real AccessibilityNodeInfo root as an [IDLookup]. */
    class AccessibilityLookup(private val root: AccessibilityNodeInfo) : IDLookup {
        override fun text(id: String): String? {
            val node = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: return null
            return node.text?.toString() ?: node.contentDescription?.toString()
        }
        override fun isChecked(id: String): Boolean? {
            val node = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: return null
            return if (node.isCheckable) node.isChecked else node.isSelected
        }
    }

    /**
     * Build a partial Tier 1 snapshot from the given lookup.
     *
     * @param ids         the model-specific ID set (YuanPlus2023, Dolphin, …)
     * @param lookup      pluggable node lookup
     * @param fallbackIds optional fallback ID lists keyed by the primary ID
     *                    (matches the runtime fallback behaviour of
     *                    `DiLinkAccessibilityService.findNodeById`)
     */
    fun parse(
        ids:         BYDModels.ModelIDs = BYDModels.YuanPlus2023,
        lookup:      IDLookup,
        fallbackIds: Map<String, List<String>> = emptyMap()
    ): VehicleSnapshot {
        val batteryText = lookup.findText(ids.ID_VEHICLE_BATTERY,  fallbackIds)
        val rangeText   = lookup.findText(ids.ID_VEHICLE_RANGE,    fallbackIds)
        val tempOutText = lookup.findText(ids.ID_VEHICLE_TEMP_OUT, fallbackIds)
        val acTempText  = lookup.findText(ids.ID_AC_TEMP_DISPLAY,  fallbackIds)
        val acOn        = lookup.findState(ids.ID_AC_POWER,        fallbackIds)

        return VehicleSnapshot(
            batteryPercent   = batteryText?.parsePercent(),
            estimatedRangeKm = rangeText?.parseRange(),
            acOn             = acOn,
            acTargetTempC    = acTempText?.trim()?.toIntOrNull(),
            outsideTempC     = tempOutText?.parseTemp(),
            source           = DataSource.ACCESSIBILITY
            // NOTE: doorsLocked and isCharging intentionally left null —
            // Tier 1 has no confirmed node IDs for these; they're populated
            // only by Tier 2 (AdvancedTelemetryProbe).
            // insideTempC is also intentionally omitted: the original brief's
            // VehicleSnapshot doesn't carry it, and no voice command surfaces
            // it. The ID_VEHICLE_TEMP_IN lookup is skipped to save a tree walk.
        )
    }

    /** Find a node's text by primary ID, then walk the fallback list. */
    private fun IDLookup.findText(
        primaryId:   String,
        fallbackIds: Map<String, List<String>>
    ): String? {
        text(primaryId)?.let { return it }
        fallbackIds[primaryId]?.forEach { fb -> text(fb)?.let { return it } }
        return null
    }

    /** Find a node's toggle state by primary ID, then walk the fallback list. */
    private fun IDLookup.findState(
        primaryId:   String,
        fallbackIds: Map<String, List<String>>
    ): Boolean? {
        isChecked(primaryId)?.let { return it }
        fallbackIds[primaryId]?.forEach { fb -> isChecked(fb)?.let { return it } }
        return null
    }
}

// ──────────────────────────────────────────────────────────────────────────
// String → typed value helpers (top-level so tests can call them directly)
// ──────────────────────────────────────────────────────────────────────────

/** "82%" → 82. Tolerates Arabic percent sign and whitespace. */
fun String.parsePercent(): Int? =
    replace("%", "")
        .replace("٪", "")   // Arabic percent
        .trim()
        .toIntOrNull()

/** "350 km" / "350كم" / "350" → 350. Strips both Latin and Arabic units.
 *  Longer Arabic units must be stripped BEFORE shorter ones, otherwise
 *  `.replace("كم", "")` would chew up the `كم` inside `كيلومتر`. */
fun String.parseRange(): Int? =
    replace("km",  "", ignoreCase = true)
        .replace("كيلومتر", "")   // "kilometre" (Arabic) — must come before كم and كيلو
        .replace("كيلو", "")      // "kilo" (Arabic)
        .replace("كم", "")        // "km" (Arabic)
        .trim()
        .toIntOrNull()

/** "30°" / "30 °C" / "30" → 30. Strips degree symbol and unit. */
fun String.parseTemp(): Int? =
    replace("°", "")
        .replace("℃", "")
        .replace("C",  "", ignoreCase = true)
        .trim()
        .toIntOrNull()
