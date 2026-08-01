package com.binti.dilink.dilink.telemetry

import com.binti.dilink.dilink.VehicleSnapshot

/**
 * Tier 2 contract — what main-source code can ask of the (optional) Advanced
 * Telemetry module without ever importing the enthusiast-flavor implementation.
 *
 * The enthusiast flavor provides an implementation that uses reflection
 * against `android.hardware.bydauto.*`. The store flavor provides a no-op
 * implementation that always returns null. Main-source code picks up the
 * right implementation via `AdvancedTelemetryLoader` (a tiny registry that
 * the flavor overrides).
 *
 * All operations are READ-ONLY and confined to comfort/info data:
 * battery cell stats, tyre pressure, motor RPM, HV/12V voltage. The bridge
 * MUST refuse to read or write any safety-critical system (steering, braking,
 * propulsion torque, airbags, ADAS).
 *
 * @author Dr. Waleed Mandour
 */
interface AdvancedTelemetryBridge {

    /**
     * `true` if this bridge is backed by a real (enthusiast flavor) probe.
     * `false` if it's the no-op store-flavor stub.
     */
    val isAvailable: Boolean

    /**
     * Probe the vehicle's internal Android services and return a partial
     * [VehicleSnapshot] populated with whatever Tier 2 fields could be
     * read. Fields that can't be read stay null.
     *
     * MUST be safe to call from a background coroutine — implementations
     * are expected to do reflection on a background thread.
     *
     * MUST be a no-op (returning an empty snapshot) when consent has not
     * been granted via [TelemetryConsentManager].
     */
    suspend fun probe(): VehicleSnapshot

    /**
     * Release any underlying resources (cached service handles, broadcast
     * receivers, etc.). Called by the application on shutdown.
     */
    fun release()
}
