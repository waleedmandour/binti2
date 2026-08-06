

package com.binti.dilink.dilink

/**
 * Tier 1 + Tier 2 vehicle data model.
 *
 * A single, immutable snapshot of every vehicle field Binti can surface.
 *
 *  - Tier 1 fields (battery, range, AC, doors, outside temp) are populated from
 *    the Accessibility layer — the same screen-scraping mechanism the rest of
 *    Binti already uses. These fields are always safe to expose and are the
 *    only ones populated in the [DataSource.ACCESSIBILITY] case.
 *  - Tier 2 fields (motor RPM, cell voltages, tyre pressure, HV/12V bus) are
 *    nullable advanced-telemetry fields. They are ONLY populated when the
 *    opt-in `enthusiast` build flavor is active, the user has explicitly
 *    consented to Advanced Telemetry, and the reflection-based probe has
 *    successfully read a value. Otherwise they remain `null` and callers must
 *    transparently fall back to Tier 1.
 *
 * Design notes:
 *  - Every field is nullable so a partial scrape (e.g. AC app open but vehicle
 *    info screen never visited) doesn't corrupt the rest of the snapshot.
 *  - `timestamp` lets the repository implement a staleness check so voice
 *    queries like "الرينج فيه كام؟" can return a cached value if fresh, or
 *    trigger a re-scrape if older than [VehicleDataRepository.STALE_AFTER_MS].
 *  - `source` records where the snapshot came from. UI code MUST surface an
 *    "Experimental" badge anywhere a non-null Tier 2 field is shown to the
 *    user, per the Advanced Telemetry consent contract.
 *
 * @author Dr. Waleed Mandour
 */
data class VehicleSnapshot(

    // ──────────────────────────────────────────────────────────────────────
    // Tier 1 — Accessibility-layer fields (always available)
    // ──────────────────────────────────────────────────────────────────────

    /** Battery state-of-charge, 0–100. */
    val batteryPercent: Int? = null,

    /** Estimated driving range in kilometres. */
    val estimatedRangeKm: Int? = null,

    /** True when the charger is currently plugged in and drawing power. */
    val isCharging: Boolean? = null,

    /** AC compressor / climate master switch. */
    val acOn: Boolean? = null,

    /** AC target temperature in degrees Celsius (16–32 typical). */
    val acTargetTempC: Int? = null,

    /** Door-lock state as observed from the vehicle info screen. */
    val doorsLocked: Boolean? = null,

    /** Exterior (ambient) temperature in degrees Celsius. */
    val outsideTempC: Int? = null,

    // ──────────────────────────────────────────────────────────────────────
    // Tier 2 — Advanced Telemetry fields (opt-in, enthusiast flavor only)
    //
    // These are populated exclusively by `AdvancedTelemetryProbe` via
    // reflection against `android.hardware.bydauto.*`. The default `store`
    // flavor never touches them and they stay null.
    // ──────────────────────────────────────────────────────────────────────

    /** Front-left tyre pressure in kPa. */
    val tyrePressureFrontLeftKpa: Int? = null,

    /** Front-right tyre pressure in kPa. */
    val tyrePressureFrontRightKpa: Int? = null,

    /** Rear-left tyre pressure in kPa. */
    val tyrePressureRearLeftKpa: Int? = null,

    /** Rear-right tyre pressure in kPa. */
    val tyrePressureRearRightKpa: Int? = null,

    /** High-voltage bus voltage in volts (typical EV: 300–400 V nominal). */
    val hvBusVoltageV: Int? = null,

    /** 12 V auxiliary battery voltage in volts. */
    val aux12vVoltageV: Double? = null,

    /** Traction motor RPM (signed; negative = reverse / regen). */
    val motorRpm: Int? = null,

    /** Spread between highest and lowest battery-cell voltage in millivolts.
     *  A widening spread is an early indicator of cell imbalance. */
    val cellVoltageSpreadMv: Int? = null,

    /** Highest individual cell voltage in millivolts. */
    val cellVoltageMaxMv: Int? = null,

    /** Lowest individual cell voltage in millivolts. */
    val cellVoltageMinMv: Int? = null,

    // ──────────────────────────────────────────────────────────────────────
    // Bookkeeping
    // ──────────────────────────────────────────────────────────────────────

    /** Wall-clock millis when this snapshot was captured. */
    val timestamp: Long = System.currentTimeMillis(),

    /** Where the data came from — Accessibility (Tier 1) or Advanced (Tier 2). */
    val source: DataSource = DataSource.ACCESSIBILITY
) {

    /**
     * `true` when at least one Tier 2 field is populated. Callers that surface
     * vehicle data to the user MUST check this and show the "Experimental"
     * badge if it returns true.
     */
    val hasAdvancedTelemetry: Boolean
        get() = tyrePressureFrontLeftKpa  != null ||
                tyrePressureFrontRightKpa != null ||
                tyrePressureRearLeftKpa   != null ||
                tyrePressureRearRightKpa  != null ||
                hvBusVoltageV             != null ||
                aux12vVoltageV            != null ||
                motorRpm                  != null ||
                cellVoltageSpreadMv       != null ||
                cellVoltageMaxMv          != null ||
                cellVoltageMinMv          != null

    /**
     * Returns a new snapshot that merges [other] into this one.
     * Non-null fields in [other] win; null fields fall back to this snapshot.
     * The merged `timestamp` is the newer of the two; `source` is the
     * higher-tier source if either side has advanced telemetry, otherwise
     * Accessibility.
     */
    fun merge(other: VehicleSnapshot): VehicleSnapshot = VehicleSnapshot(
        batteryPercent            = other.batteryPercent            ?: batteryPercent,
        estimatedRangeKm          = other.estimatedRangeKm          ?: estimatedRangeKm,
        isCharging                = other.isCharging                ?: isCharging,
        acOn                      = other.acOn                      ?: acOn,
        acTargetTempC             = other.acTargetTempC             ?: acTargetTempC,
        doorsLocked               = other.doorsLocked               ?: doorsLocked,
        outsideTempC              = other.outsideTempC              ?: outsideTempC,
        tyrePressureFrontLeftKpa  = other.tyrePressureFrontLeftKpa  ?: tyrePressureFrontLeftKpa,
        tyrePressureFrontRightKpa = other.tyrePressureFrontRightKpa ?: tyrePressureFrontRightKpa,
        tyrePressureRearLeftKpa   = other.tyrePressureRearLeftKpa   ?: tyrePressureRearLeftKpa,
        tyrePressureRearRightKpa  = other.tyrePressureRearRightKpa  ?: tyrePressureRearRightKpa,
        hvBusVoltageV             = other.hvBusVoltageV             ?: hvBusVoltageV,
        aux12vVoltageV            = other.aux12vVoltageV            ?: aux12vVoltageV,
        motorRpm                  = other.motorRpm                  ?: motorRpm,
        cellVoltageSpreadMv       = other.cellVoltageSpreadMv       ?: cellVoltageSpreadMv,
        cellVoltageMaxMv          = other.cellVoltageMaxMv          ?: cellVoltageMaxMv,
        cellVoltageMinMv          = other.cellVoltageMinMv          ?: cellVoltageMinMv,
        timestamp                 = maxOf(timestamp, other.timestamp),
        source                    = if (other.hasAdvancedTelemetry || hasAdvancedTelemetry)
                                       DataSource.ADVANCED_TELEMETRY
                                     else DataSource.ACCESSIBILITY
    )

    /** Human-readable summary used by the developer debug screen and tests. */
    fun toDebugString(): String = buildString {
        appendLine("VehicleSnapshot @ $timestamp (source=$source)")
        appendLine("  Tier 1:")
        appendLine("    batteryPercent     = $batteryPercent")
        appendLine("    estimatedRangeKm   = $estimatedRangeKm")
        appendLine("    isCharging         = $isCharging")
        appendLine("    acOn               = $acOn")
        appendLine("    acTargetTempC      = $acTargetTempC")
        appendLine("    doorsLocked        = $doorsLocked")
        appendLine("    outsideTempC       = $outsideTempC")
        appendLine("  Tier 2:")
        appendLine("    tyrePressure FL/FR/RL/RR = $tyrePressureFrontLeftKpa / $tyrePressureFrontRightKpa / $tyrePressureRearLeftKpa / $tyrePressureRearRightKpa kPa")
        appendLine("    hvBusVoltageV      = $hvBusVoltageV")
        appendLine("    aux12vVoltageV     = $aux12vVoltageV")
        appendLine("    motorRpm           = $motorRpm")
        appendLine("    cellVoltage max/min/spread = $cellVoltageMaxMv / $cellVoltageMinMv / $cellVoltageSpreadMv mV")
        appendLine("  hasAdvancedTelemetry = $hasAdvancedTelemetry")
    }
}

/**
 * Provenance tag for a [VehicleSnapshot].
 *
 *  - [ACCESSIBILITY]        — Tier 1, scraped from DiLink UI via the
 *                             Accessibility Service. Always safe; default.
 *  - [ADVANCED_TELEMETRY]   — Tier 2, populated by reflection against
 *                             `android.hardware.bydauto.*` on the head unit.
 *                             Experimental, opt-in, enthusiast flavor only.
 */
enum class DataSource {
    ACCESSIBILITY,
    ADVANCED_TELEMETRY
}
