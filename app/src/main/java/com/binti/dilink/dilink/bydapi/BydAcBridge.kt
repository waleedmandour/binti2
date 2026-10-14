// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.dilink.bydapi

import android.content.Context
import android.util.Log

/**
 * Read-only bridge onto BYD's real framework API: the
 * `android.hardware.bydauto.ac.BYDAutoAcDevice` device class served by
 * DiCarServer on DiLink 3/5 head units.
 *
 * **Why this exists:** Binti's AC commands used to work purely by scraping
 * the AC app's UI through Accessibility. That means "turn on the AC" was a
 * BLIND TOGGLE — the app had no way to read the AC state, so "شغل" when the
 * AC was already on turned it OFF. This bridge adds the missing READ half.
 *
 * **Read-first policy (deliberate scope decision):** this class intentionally
 * exposes only READ operations. WRITE calls (start/stop/setAcTemperature) are
 * known from community reverse-engineering but are NOT wired into the command
 * flow until they are verified on a real Egyptian unit — see
 * docs/REBUILD_PLAN.md §"AC bridge". `setPowerInternal`/`setTempInternal`
 * are kept private and unused as a scaffold for that verification step.
 *
 * All reflection is fail-soft: on any head unit without the BYD framework
 * (e.g. during development, or a future firmware that renames the class)
 * every getter returns null and the executor falls back to scraping.
 *
 * Manifest: declare the `BYDAUTO_AC_GET` permission (see AndroidManifest.xml).
 * Community testing on a real Dolphin found AC state GETTERS work without
 * permission checks; we still declare the permission defensively.
 *
 * @author Dr. Waleed Mandour (method names from community DiLink 3 research)
 */
object BydAcBridge {

    private const val TAG = "BydAcBridge"

    /** Reflection candidates for the AC device class, by firmware generation. */
    private val DEVICE_CLASSES = listOf(
        "android.hardware.bydauto.ac.BYDAutoAcDevice",
        "android.hardware.bydauto.BYDAutoAcDevice"
    )

    @Volatile
    private var device: Any? = null
    @Volatile
    private var resolveAttempted = false

    /** Cached "framework API exists" flag — false when reflection fails once. */
    @Volatile
    var isPlatformSupported: Boolean = false
        private set

    private fun resolveDevice(context: Context): Any? {
        device?.let { return it }
        if (resolveAttempted) return null
        synchronized(this) {
            device?.let { return it }
            if (resolveAttempted) return null
            resolveAttempted = true
            for (className in DEVICE_CLASSES) {
                try {
                    val clazz = Class.forName(className)
                    val getInstance = clazz.getMethod("getInstance", Context::class.java)
                    val dev = getInstance.invoke(null, context.applicationContext)
                    if (dev != null) {
                        device = dev
                        isPlatformSupported = true
                        Log.i(TAG, "✅ BYD AC framework API resolved via $className")
                        return dev
                    }
                } catch (e: Throwable) {
                    Log.v(TAG, "$className unavailable: ${e.javaClass.simpleName}")
                }
            }
            Log.i(TAG, "BYD AC framework API not present on this unit — scraping fallback stays active")
            return null
        }
    }

    /** Invoke a method by name with boxed args, returning Int or null on ANY failure. */
    private fun call(dev: Any?, name: String, args: List<Pair<Class<*>, Any?>>): Int? {
        if (dev == null) return null
        return try {
            val method = dev.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.size == args.size
            } ?: return null
            val result = method.invoke(dev, *args.map { it.second }.toTypedArray())
            when (result) {
                is Int -> result
                is Number -> result.toInt()
                else -> null
            }
        } catch (e: Throwable) {
            Log.v(TAG, "$name() failed: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // READ API (wired into the executor)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * AC power state. Tries several getter names observed across firmware
     * builds. Returns true=on, false=off, null=unknown.
     */
    fun isOn(context: Context): Boolean? {
        val dev = resolveDevice(context) ?: return null
        val state = call(dev, "getAcStartState", emptyList())
            ?: call(dev, "getAcPowerState", emptyList())
            ?: call(dev, "isAcStart", emptyList())
            ?: return null
        return state == 1
    }

    /**
     * Outside temperature in °C. `getTemprature` [sic] is the real spelling
     * in the BYD API; zone 4 is the outside sensor per community research.
     */
    fun outsideTempC(context: Context): Int? {
        val dev = resolveDevice(context) ?: return null
        val raw = call(dev, "getTemprature", listOf(Int::class.java to 4)) ?: return null
        // Sanity range for Egyptian climate (-10°C winter nights .. 60°C parked in summer)
        return raw.takeIf { it in -10..60 }
    }

    /**
     * AC target temperature in °C (driver zone), for display/idempotency.
     */
    fun targetTempC(context: Context): Int? {
        val dev = resolveDevice(context) ?: return null
        val raw = call(dev, "getAcTemperature", listOf(Int::class.java to 1)) ?: return null
        return raw.takeIf { it in 15..35 }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WRITE API (scaffold — NOT wired until verified on hardware; see plan)
    // ──────────────────────────────────────────────────────────────────────────

    @Suppress("unused")
    private fun setPowerInternal(context: Context, on: Boolean): Boolean {
        val dev = resolveDevice(context) ?: return false
        val result = call(dev, if (on) "start" else "stop", listOf(Int::class.java to 0))
        // Read-back verification: trust only a confirmed state change.
        return result == 0 && isOn(context) == on
    }

    @Suppress("unused")
    private fun setTempInternal(context: Context, tempC: Int): Boolean {
        val dev = resolveDevice(context) ?: return false
        val clamped = tempC.coerceIn(17, 33)
        val result = call(
            dev, "setAcTemperature",
            listOf(Int::class.java to 1, Int::class.java to clamped,
                   Int::class.java to 1, Int::class.java to 1)
        )
        return result == 0
    }
}
