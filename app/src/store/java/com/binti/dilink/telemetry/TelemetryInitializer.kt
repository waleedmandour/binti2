package com.binti.dilink.telemetry

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.telemetry.AdvancedTelemetryLoader

/**
 * Store-flavor initializer — explicitly logs that this build does NOT
 * register any Advanced Telemetry bridge, so the no-op default remains in
 * place. This guarantees that no `android.hardware.bydauto.*` references
 * can survive into a Huawei AppGallery-bound APK.
 *
 * Called by `BintiApplication.onCreate` via a flavor-polymorphic
 * `TelemetryInitializer.init(this)` call. The store flavor's implementation
 * is intentionally a no-op (the no-op default is correct for AppGallery).
 *
 * @author Dr. Waleed Mandour
 */
object TelemetryInitializer {
    private const val TAG = "TelemetryInit"

    @JvmOverloads
    fun init(context: Context? = null) {
        Log.i(TAG, "Store flavor active — Advanced Telemetry NOT registered " +
                   "(NoOpAdvancedTelemetryBridge remains in place)")
        // Explicitly do NOT call AdvancedTelemetryLoader.register(...) —
        // the no-op default is correct for the AppGallery build.
        // A defensive assertion: confirm the loader is still pointing at the no-op.
        check(!AdvancedTelemetryLoader.get().isAvailable) {
            "Store flavor must NOT have an Advanced Telemetry bridge registered"
        }
    }
}
