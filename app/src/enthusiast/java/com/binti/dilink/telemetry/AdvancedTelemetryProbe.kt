package com.binti.dilink.telemetry

import android.util.Log
import com.binti.dilink.dilink.DataSource
import com.binti.dilink.dilink.VehicleSnapshot
import com.binti.dilink.dilink.telemetry.AdvancedTelemetryBridge
import com.binti.dilink.dilink.telemetry.AdvancedTelemetryLoader
import com.binti.dilink.dilink.telemetry.TelemetryConsentManager

/**
 * Tier 2 — Advanced Telemetry Probe (enthusiast flavor only).
 *
 * Reads richer read-only telemetry — motor RPM, tyre pressure, HV/12V bus
 * voltage, battery-cell voltage spread — by reflecting against BYD's
 * undocumented internal Android framework services that run locally on the
 * DiLink head unit (`android.hardware.bydauto.*`).
 *
 * **Reference and attribution:**
 * The enumeration approach used below — walking `ServiceManager.listServices()`,
 * fetching the `IBinder` for each `bydauto.*` service, creating a proxy via
 * `ServiceManager.asBinder()` and method-name lookup — is modelled on the
 * public MIT-licensed `BYD Probe` diagnostic tool published in
 * `github.com/wheregoes/byd-apps`. See `docs/ADVANCED_TELEMETRY.md` and the
 * `NOTICE` file at the repository root for the full MIT attribution.
 *
 * **Scope is strictly comfort/info:**
 *  - Reads ONLY: battery cell voltages, tyre pressure, motor RPM, HV/12V
 *    voltage.
 *  - NEVER reads or writes: steering, braking, propulsion torque, airbags,
 *    ADAS / driving-assist systems. The bridge refuses any service whose
 *    name suggests a safety-critical domain.
 *
 * **Consent:**
 * `probe()` is a no-op when `TelemetryConsentManager.state` is not GRANTED.
 *
 * **Robustness:**
 * Every reflection call is wrapped — a firmware update that renames a
 * service or changes a method signature produces a `null` field, not a
 * crash. The probe is best-effort; the repository's merge semantics ensure
 * Tier 1 values are always preserved.
 *
 * @author Dr. Waleed Mandour
 */
class AdvancedTelemetryProbe(
    private val consent: TelemetryConsentManager
) : AdvancedTelemetryBridge {

    companion object {
        private const val TAG = "AdvTelemetryProbe"

        /**
         * Allow-list of `android.hardware.bydauto.*` service prefixes that
         * the probe is allowed to talk to. Any service outside this list is
         * skipped. This is the primary safety boundary — anything suggesting
         * steering / braking / propulsion / airbag / ADAS is excluded.
         */
        private val ALLOWED_SERVICE_PREFIXES = setOf(
            "bydauto.battery",       // battery cell stats, pack voltage
            "bydauto.tyre",          // tyre pressure (TPMS)
            "bydauto.motor",         // traction motor RPM (read-only)
            "bydauto.hvbus",         // HV bus voltage
            "bydauto.auxbattery",    // 12V auxiliary battery
            "bydauto.energy",        // energy management (read-only)
            "bydauto.vehicle"        // general vehicle info (read-only)
        )

        /**
         * Hard-blocked service name substrings. Even if a service matches an
         * allowed prefix, this deny-list catches anything that touches a
         * safety-critical domain.
         */
        private val BLOCKED_SERVICE_SUBSTRINGS = setOf(
            "steer", "brake", "abs", "esp", "torque",
            "airbag", "srs", "adas", "adasassist",
            "cruise", "control", "actuator", "drive"
        )
    }

    override val isAvailable: Boolean = true

    override suspend fun probe(): VehicleSnapshot {
        // Gate #1 — explicit user consent.
        if (!consent.isGranted) {
            Log.d(TAG, "Probe skipped — consent not granted")
            return VehicleSnapshot()
        }

        return try {
            val services = enumerateBydServices()
            if (services.isEmpty()) {
                Log.w(TAG, "No android.hardware.bydauto.* services found on this device — " +
                           "either not a DiLink head unit or services were renamed by a firmware update")
                return VehicleSnapshot()
            }

            val partial = VehicleSnapshot(
                tyrePressureFrontLeftKpa  = readTyrePressure(services, corner = "FL"),
                tyrePressureFrontRightKpa = readTyrePressure(services, corner = "FR"),
                tyrePressureRearLeftKpa   = readTyrePressure(services, corner = "RL"),
                tyrePressureRearRightKpa  = readTyrePressure(services, corner = "RR"),
                hvBusVoltageV             = readHvBusVoltage(services),
                aux12vVoltageV            = readAux12vVoltage(services),
                motorRpm                  = readMotorRpm(services),
                cellVoltageMaxMv          = readCellVoltage(services, max = true),
                cellVoltageMinMv          = readCellVoltage(services, max = false),
                timestamp                 = System.currentTimeMillis(),
                source                    = DataSource.ADVANCED_TELEMETRY
            )
            // Spread is derived from min/max if both are present.
            val spread = if (partial.cellVoltageMaxMv != null && partial.cellVoltageMinMv != null) {
                partial.cellVoltageMaxMv - partial.cellVoltageMinMv
            } else null
            partial.copy(cellVoltageSpreadMv = spread)
        } catch (e: Throwable) {
            // Catch Throwable, not Exception: reflection can throw
            // NoSuchMethodError / UnsupportedClassVersionError / etc.
            Log.w(TAG, "Advanced telemetry probe failed — returning empty snapshot", e)
            VehicleSnapshot()
        }
    }

    override fun release() {
        // No persistent resources held in this implementation; future versions
        // may cache service proxies that would be released here.
    }

    // ──────────────────────────────────────────────────────────────────────
    // Service enumeration
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Enumerates the names of `android.hardware.bydauto.*` services currently
     * registered with `ServiceManager`. Mirrors the enumeration pattern in
     * the MIT-licensed `BYD Probe` reference tool.
     *
     * Returns an empty list if ServiceManager isn't accessible (e.g. on a
     * non-BYD device) — the caller treats that as "advanced telemetry not
     * available on this build/device".
     */
    private fun enumerateBydServices(): List<String> = try {
        val smClass = Class.forName("android.os.ServiceManager")
        val listMethod = smClass.getMethod("listServices")
        @Suppress("UNCHECKED_CAST")
        val all = listMethod.invoke(null) as? Array<String> ?: emptyArray()
        all.filter { name ->
            name.startsWith("bydauto.") &&
            ALLOWED_SERVICE_PREFIXES.any { name.startsWith(it) } &&
            BLOCKED_SERVICE_SUBSTRINGS.none { name.contains(it, ignoreCase = true) }
        }.also {
            Log.v(TAG, "Allowed BYD services: $it")
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ServiceManager.listServices() unavailable — advanced telemetry disabled", e)
        emptyList()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Individual readers
    //
    // Each reader is independent: if one fails, the others still run.
    // The `serviceHint` parameter names the expected service prefix; the
    // reader walks the enumerated services and tries each one whose name
    // matches the hint until a method call succeeds.
    // ──────────────────────────────────────────────────────────────────────

    private fun readTyrePressure(services: List<String>, corner: String): Int? {
        val candidates = services.filter { it.startsWith("bydauto.tyre") }
        for (svc in candidates) {
            val value = callIntMethod(svc, "getTyrePressure", corner.lowercase())
            if (value != null) return value
            // Fallback method name seen in some firmware versions:
            val alt = callIntMethod(svc, "getTyrePressure${corner}", null)
            if (alt != null) return alt
        }
        return null
    }

    private fun readHvBusVoltage(services: List<String>): Int? {
        val candidates = services.filter { it.startsWith("bydauto.hvbus") }
        for (svc in candidates) {
            callIntMethod(svc, "getHvBusVoltage", null)?.let { return it }
            callIntMethod(svc, "getBusVoltage",   null)?.let { return it }
        }
        // Fall back to battery service if a dedicated hvbus service isn't present.
        val batteryCandidates = services.filter { it.startsWith("bydauto.battery") }
        for (svc in batteryCandidates) {
            callIntMethod(svc, "getPackVoltage", null)?.let { return it }
        }
        return null
    }

    private fun readAux12vVoltage(services: List<String>): Double? {
        val candidates = services.filter { it.startsWith("bydauto.auxbattery") }
        for (svc in candidates) {
            callDoubleMethod(svc, "getAuxVoltage",   null)?.let { return it }
            callDoubleMethod(svc, "get12vVoltage",   null)?.let { return it }
        }
        return null
    }

    private fun readMotorRpm(services: List<String>): Int? {
        val candidates = services.filter { it.startsWith("bydauto.motor") }
        for (svc in candidates) {
            callIntMethod(svc, "getMotorRpm",  null)?.let { return it }
            callIntMethod(svc, "getRpm",       null)?.let { return it }
        }
        return null
    }

    private fun readCellVoltage(services: List<String>, max: Boolean): Int? {
        val candidates = services.filter { it.startsWith("bydauto.battery") }
        for (svc in candidates) {
            val method = if (max) "getMaxCellVoltageMv" else "getMinCellVoltageMv"
            callIntMethod(svc, method, null)?.let { return it }
            // Fallbacks seen in alternate firmware:
            val alt = if (max) "getHighestCellVoltage" else "getLowestCellVoltage"
            callIntMethod(svc, alt, null)?.let { return it }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Low-level reflection helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Calls `int methodName(arg)` on the given BYD service. Returns null on
     * any reflection failure — never throws.
     *
     * The actual call path on Android is `ServiceManager.getService(name)`
     * → `IBinder` → `IInterface` as a proxy via `ServiceManager.asBinder()`.
     * We approximate that here by looking up a stub class
     * `android.hardware.bydauto.IBydAuto${Name}` and invoking the method.
     */
    private fun callIntMethod(serviceName: String, methodName: String, arg: String?): Int? = try {
        val proxy = acquireServiceProxy(serviceName) ?: return null
        val args = if (arg != null) arrayOf<Any>(arg) else emptyArray<Any>()
        val argTypes = if (arg != null) arrayOf<Class<*>>(String::class.java) else emptyArray()
        val method = proxy.javaClass.getMethod(methodName, *argTypes)
        method.invoke(proxy, *args) as? Int
    } catch (e: Throwable) {
        Log.v(TAG, "callIntMethod($serviceName, $methodName, $arg) failed: ${e.message}")
        null
    }

    private fun callDoubleMethod(serviceName: String, methodName: String, arg: String?): Double? = try {
        val proxy = acquireServiceProxy(serviceName) ?: return null
        val args = if (arg != null) arrayOf<Any>(arg) else emptyArray<Any>()
        val argTypes = if (arg != null) arrayOf<Class<*>>(String::class.java) else emptyArray()
        val method = proxy.javaClass.getMethod(methodName, *argTypes)
        method.invoke(proxy, *args) as? Double
    } catch (e: Throwable) {
        Log.v(TAG, "callDoubleMethod($serviceName, $methodName, $arg) failed: ${e.message}")
        null
    }

    /**
     * Acquires a proxy for the named BYD service. Mirrors the
     * `ServiceManager.getService(name)` pattern used by the BYD Probe
     * reference tool.
     */
    private fun acquireServiceProxy(serviceName: String): Any? {
        return try {
            val smClass   = Class.forName("android.os.ServiceManager")
            val getMethod = smClass.getMethod("getService", String::class.java)
            val binder    = getMethod.invoke(null, serviceName) ?: return null

            // Try a few well-known stub class name patterns. The exact class
            // hierarchy varies by DiLink firmware version; the reference tool
            // tries several in turn.
            val stubCandidates = listOf(
                "android.hardware.bydauto.IBydAuto${serviceName.removePrefix("bydauto.")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}",
                "android.hardware.bydauto.IBydAutoService",
                "android.hardware.bydauto.IBydService"
            )
            for (stubClassName in stubCandidates) {
                try {
                    val stubClass = Class.forName(stubClassName)
                    val asInterface = stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
                    val proxy = asInterface.invoke(null, binder)
                    if (proxy != null) return proxy
                } catch (_: ClassNotFoundException) { /* try next */ }
            }
            null
        } catch (e: Throwable) {
            Log.v(TAG, "acquireServiceProxy($serviceName) failed: ${e.message}")
            null
        }
    }
}

/**
 * Enthusiast-flavor initializer — registers the real [AdvancedTelemetryProbe]
 * via [AdvancedTelemetryLoader]. Called by `BintiApplication.onCreate` only
 * when `BuildConfig.ENTHUSIAST_FLAVOR` is true.
 *
 * @author Dr. Waleed Mandour
 */
object TelemetryInitializer {
    private const val TAG = "TelemetryInit"

    @Volatile private var instance: AdvancedTelemetryProbe? = null

    fun init(context: android.content.Context) {
        if (instance != null) return
        val consent = TelemetryConsentManager.get(context)
        val probe = AdvancedTelemetryProbe(consent)
        instance = probe
        AdvancedTelemetryLoader.register(probe)
        Log.i(TAG, "Enthusiast flavor active — Advanced Telemetry bridge registered " +
                   "(consent state: ${consent.state})")
    }
}
