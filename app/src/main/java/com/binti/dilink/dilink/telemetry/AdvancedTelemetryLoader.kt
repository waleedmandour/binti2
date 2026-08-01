

package com.binti.dilink.dilink.telemetry

import com.binti.dilink.dilink.VehicleSnapshot

/**
 * Loader pattern — main source declares a registry hook that the active
 * flavor overrides with a concrete [AdvancedTelemetryBridge] instance.
 *
 *  - `store` flavor       → returns a [NoOpAdvancedTelemetryBridge]
 *                           (always returns an empty snapshot).
 *  - `enthusiast` flavor  → returns an `AdvancedTelemetryManager` that
 *                           wraps `AdvancedTelemetryProbe` and gates calls
 *                           behind [TelemetryConsentManager].
 *
 * This indirection means main-source code (the developer debug screen, the
 * command executor, the application startup hook) never references the
 * enthusiast `AdvancedTelemetryProbe` directly — so the `store` flavor can
 * never accidentally include reflection-based probing code.
 *
 * @author Dr. Waleed Mandour
 */
object AdvancedTelemetryLoader {

    @Volatile
    private var registered: AdvancedTelemetryBridge = NoOpAdvancedTelemetryBridge

    /**
     * Called by the enthusiast flavor's `BintiApplication` (or
     * `TelemetryInitializer`) at startup to register the real bridge.
     * The store flavor never calls this — the no-op default stays in place.
     */
    fun register(bridge: AdvancedTelemetryBridge) {
        registered = bridge
    }

    /** The active bridge. Always non-null; never throws. */
    fun get(): AdvancedTelemetryBridge = registered
}

/**
 * Default bridge used by the `store` flavor.
 *
 * Every Tier 2 field stays null. The repository's `mergeAdvanced` is a no-op
 * when the snapshot has no advanced fields, so the rest of the app works
 * exactly the same as before — Tier 1 only.
 */
object NoOpAdvancedTelemetryBridge : AdvancedTelemetryBridge {
    override val isAvailable: Boolean = false
    override suspend fun probe(): VehicleSnapshot = VehicleSnapshot()
    override fun release() { /* no-op */ }
}
