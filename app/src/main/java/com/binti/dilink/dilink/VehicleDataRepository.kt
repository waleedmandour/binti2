package com.binti.dilink.dilink

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tier 1 single-source-of-truth for vehicle state.
 *
 * `DiLinkAccessibilityService` publishes into this repository whenever it
 * scrapes a relevant DiLink screen (vehicle info, energy, climate). Voice
 * commands and UI consumers read from the [snapshot] StateFlow instead of
 * re-scraping the screen on every query — with a staleness check
 * ([isStale] / [freshOrAwait]) so callers can decide whether a fresh scrape
 * is needed.
 *
 * Tier 2 (Advanced Telemetry, enthusiast flavor only) also publishes into
 * the same repository via [mergeAdvanced]. The merged snapshot keeps Tier 1
 * values as the baseline and overlays Tier 2 fields on top — so if Tier 2
 * ever fails, the snapshot still carries the last-known Tier 1 values.
 *
 * Thread-safety: all mutations go through `MutableStateFlow.update`, which
 * is atomic. Safe to call from any thread.
 *
 * @author Dr. Waleed Mandour
 */
class VehicleDataRepository {

    companion object {
        private const val TAG = "VehicleRepo"

        /**
         * A snapshot older than this is considered stale and callers should
         * trigger a fresh Accessibility scrape before responding to a voice
         * query. 15 seconds is a reasonable trade-off: short enough that
         * "الرينج فيه كام؟" doesn't return yesterday's number after a long
         * drive, long enough that rapid-fire queries don't re-scrape the
         * screen on every utterance.
         */
        const val STALE_AFTER_MS: Long = 15_000L
    }

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    /** The current vehicle snapshot. Always non-null; fields are nullable. */
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    /**
     * Publish a Tier 1 (Accessibility-layer) update.
     *
     * Only non-null fields in [partial] are applied; nulls are preserved
     * from the previous snapshot. This lets the AC screen update `acOn`
     * without clobbering `batteryPercent` that was read from the vehicle
     * info screen.
     */
    fun publishAccessibility(partial: VehicleSnapshot) {
        _snapshot.update { current ->
            val merged = current.merge(partial.copy(source = DataSource.ACCESSIBILITY))
            Log.v(TAG, "Published Tier 1: $merged")
            merged
        }
    }

    /**
     * Publish a Tier 2 (Advanced Telemetry) update.
     *
     * Merges advanced fields on top of the current Tier 1 baseline. If
     * [partial] has no advanced fields (everything null), this is a no-op
     * apart from refreshing the timestamp.
     */
    fun mergeAdvanced(partial: VehicleSnapshot) {
        if (!partial.hasAdvancedTelemetry) {
            Log.v(TAG, "mergeAdvanced called with no advanced fields — skipping")
            return
        }
        _snapshot.update { current ->
            val merged = current.merge(partial.copy(source = DataSource.ADVANCED_TELEMETRY))
            Log.v(TAG, "Merged Tier 2: $merged")
            merged
        }
    }

    /** Current snapshot value (convenience for synchronous callers). */
    fun current(): VehicleSnapshot = _snapshot.value

    /** `true` when the current snapshot is older than [STALE_AFTER_MS]. */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        now - _snapshot.value.timestamp > STALE_AFTER_MS

    /**
     * Returns the current snapshot if it is fresh, or `null` if it is stale.
     * Voice-command handlers use this to decide whether to re-scrape:
     *
     * ```
     * val snap = repo.freshOrNull()
     * if (snap?.batteryPercent != null) { /* respond immediately */ }
     * else { /* trigger Accessibility scrape, await, retry */ }
     * ```
     */
    fun freshOrNull(now: Long = System.currentTimeMillis()): VehicleSnapshot? =
        if (isStale(now)) null else _snapshot.value

    /** Wipe all fields. Used by tests and by the Advanced Telemetry opt-out flow. */
    fun reset() {
        _snapshot.value = VehicleSnapshot()
    }
}
