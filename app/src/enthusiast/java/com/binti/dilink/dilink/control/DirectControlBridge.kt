// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.binti.dilink.dilink.control

import android.content.Context
import android.util.Log
import com.binti.dilink.dilink.DataSource
import com.binti.dilink.dilink.VehicleSnapshot
import com.binti.dilink.dilink.telemetry.AdvancedTelemetryLoader
import com.binti.dilink.dilink.telemetry.TelemetryConsentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enthusiast-flavor direct control bridge — uses `app_process` + reflection
 * against `android.hardware.bydauto.*` services. Does NOT need accessibility.
 *
 * This is the approach documented by the MIT-licensed
 * `wheregoes/byd-dolphin-hacking` project for BYD Dolphin (DiLink 3 / Android 10):
 *
 * > "BYDAUTO_* permissions client-side using app_process + reflection
 * >   — 75+ BYD remote control commands"
 *
 * The bridge can both READ vehicle data AND WRITE control commands directly
 * to BYD's internal services. No UI interaction (accessibility node clicking)
 * is needed. This is the fallback when accessibility cannot be enabled.
 *
 * **Safety boundaries** (same as AdvancedTelemetryProbe):
 *  - READ: battery, tyre pressure, motor RPM, HV/12V voltage, temperatures
 *  - WRITE: AC on/off, temp up/down, media play/pause/next/prev, volume,
 *    navigation launch
 *  - BLOCKED: steering, braking, propulsion torque, airbags, ADAS
 *
 * @author Dr. Waleed Mandour
 */
class DirectControlBridge(private val context: Context) : ControlBridge {

    companion object {
        private const val TAG = "DirectControl"

        /**
         * Write-command service prefixes. Only comfort/convenience services
         * are allowed — same safety boundary as the telemetry probe.
         */
        private val WRITABLE_SERVICE_PREFIXES = setOf(
            "bydauto.ac",           // AC / climate control
            "bydauto.media",        // media playback control
            "bydauto.navigation",   // navigation launch
            "bydauto.audio",        // volume control
            "bydauto.phone"         // phone call control
        )

        /** Hard-blocked service name substrings (safety-critical). */
        private val BLOCKED_SUBSTRINGS = setOf(
            "steer", "brake", "abs", "esp", "torque",
            "airbag", "srs", "adas", "cruise", "control",
            "actuator", "drive", "propulsion"
        )
    }

    override val modeName: String = "Direct Control (app_process + reflection)"
    override val isAvailable: Boolean = true

    /**
     * Direct control is "ready" if:
     * 1. The AdvancedTelemetryProbe is available (enthusiast flavor)
     * 2. Consent has been granted (same consent covers read + write)
     */
    override fun isReady(): Boolean {
        val consent = TelemetryConsentManager.get(context)
        return AdvancedTelemetryLoader.get().isAvailable && consent.isGranted
    }

    /**
     * Read vehicle state via the AdvancedTelemetryProbe (same reflection
     * mechanism, read-only bydauto.* service calls).
     */
    override suspend fun readVehicleState(): VehicleSnapshot {
        val bridge = AdvancedTelemetryLoader.get()
        if (!bridge.isAvailable) return VehicleSnapshot(timestamp = 0L)
        return bridge.probe()
    }

    /**
     * Send a control command via reflection against bydauto.* services.
     *
     * This is a BEST-EFFORT implementation — the exact method names vary
     * by DiLink firmware version, so we try multiple known method names
     * for each command and return the first success.
     *
     * All reflection calls are wrapped in try/catch — a failure produces
     * a user-facing Arabic error message, not a crash.
     */
    override suspend fun sendCommand(command: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            if (!isReady()) {
                return@withContext "التحكم المباشر مش جاهز. فعّل القياس المتقدم الأول من إعدادات التطبيق."
            }

            try {
                val result = when (command) {
                    // AC control
                    ControlCommand.AC_ON        -> callBydAutoService("bydauto.ac", "setACPower", true)
                    ControlCommand.AC_OFF       -> callBydAutoService("bydauto.ac", "setACPower", false)
                    ControlCommand.AC_TEMP_UP   -> callBydAutoService("bydauto.ac", "increaseTemperature", null)
                    ControlCommand.AC_TEMP_DOWN -> callBydAutoService("bydauto.ac", "decreaseTemperature", null)
                    ControlCommand.AC_TEMP_SET  -> {
                        val temp = params["temperature"]?.toIntOrNull() ?: 22
                        callBydAutoService("bydauto.ac", "setTemperature", temp)
                    }
                    ControlCommand.AC_MODE_AUTO -> callBydAutoService("bydauto.ac", "setMode", "auto")
                    ControlCommand.AC_FAN_UP    -> callBydAutoService("bydauto.ac", "increaseFanSpeed", null)
                    ControlCommand.AC_FAN_DOWN  -> callBydAutoService("bydauto.ac", "decreaseFanSpeed", null)

                    // Media control
                    ControlCommand.MEDIA_PLAY   -> callBydAutoService("bydauto.media", "play", null)
                    ControlCommand.MEDIA_PAUSE  -> callBydAutoService("bydauto.media", "pause", null)
                    ControlCommand.MEDIA_NEXT   -> callBydAutoService("bydauto.media", "next", null)
                    ControlCommand.MEDIA_PREV   -> callBydAutoService("bydauto.media", "previous", null)

                    // Navigation
                    ControlCommand.NAVIGATE_HOME -> callBydAutoService("bydauto.navigation", "navigateHome", null)
                    ControlCommand.NAVIGATE_WORK -> callBydAutoService("bydauto.navigation", "navigateWork", null)
                    ControlCommand.NAVIGATE_TO   -> {
                        val dest = params["destination"] ?: ""
                        callBydAutoService("bydauto.navigation", "navigateTo", dest)
                    }

                    // Phone
                    ControlCommand.PHONE_CALL   -> {
                        val number = params["number"] ?: params["contact"] ?: ""
                        callBydAutoService("bydauto.phone", "dial", number)
                    }
                    ControlCommand.PHONE_ANSWER -> callBydAutoService("bydauto.phone", "answerCall", null)
                    ControlCommand.PHONE_END    -> callBydAutoService("bydauto.phone", "endCall", null)

                    // Volume (via AudioManager, not bydauto — but unified here)
                    ControlCommand.VOLUME_UP    -> {
                        adjustVolume(1); "تم رفع الصوت"
                    }
                    ControlCommand.VOLUME_DOWN  -> {
                        adjustVolume(-1); "تم خفض الصوت"
                    }
                    ControlCommand.VOLUME_MUTE  -> {
                        toggleMute(); "تم كتم الصوت"
                    }

                    else -> "أمر غير معروف: $command"
                }
                result
            } catch (e: Throwable) {
                Log.e(TAG, "Direct control command '$command' failed", e)
                "فشل الأمر: ${e.message}"
            }
        }

    override fun release() {
        // No persistent resources held.
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reflection helpers — mirror the AdvancedTelemetryProbe pattern but
    // for WRITE operations. All calls are safety-checked against the
    // allow-list and block-list.
    // ──────────────────────────────────────────────────────────────────────

    private fun callBydAutoService(
        servicePrefix: String,
        methodName: String,
        arg: Any?
    ): String {
        // Safety check: verify the service prefix is in the writable allow-list
        // and doesn't contain any blocked substring.
        if (servicePrefix !in WRITABLE_SERVICE_PREFIXES) {
            return "❌ خدمة '$servicePrefix' مش مسموح بيها للتحكم المباشر"
        }
        if (BLOCKED_SUBSTRINGS.any { servicePrefix.contains(it, ignoreCase = true) }) {
            return "❌ خدمة '$servicePrefix' محظورة (نظام حرج)"
        }

        // Enumerate matching bydauto.* services
        val services = enumerateBydServices(servicePrefix)
        if (services.isEmpty()) {
            return "❌ مفيش خدمات BYD من النوع '$servicePrefix' على الجهاز ده"
        }

        // Try each service until one responds
        for (svc in services) {
            val success = tryServiceCall(svc, methodName, arg)
            if (success) {
                Log.i(TAG, "✅ $servicePrefix.$methodName(arg=$arg) -> success")
                return "تم (عبر التحكم المباشر)"
            }
        }
        return "❌ مفيش خدممة استجابت للأمر '$methodName' — ممكن اسم الميثود اختلف في الفيرموير ده"
    }

    private fun enumerateBydServices(prefix: String): List<String> = try {
        val smClass = Class.forName("android.os.ServiceManager")
        val listMethod = smClass.getMethod("listServices")
        @Suppress("UNCHECKED_CAST")
        val all = listMethod.invoke(null) as? Array<String> ?: emptyArray()
        all.filter { name ->
            name.startsWith("bydauto.") &&
            name.startsWith(prefix) &&
            BLOCKED_SUBSTRINGS.none { name.contains(it, ignoreCase = true) }
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ServiceManager.listServices() unavailable", e)
        emptyList()
    }

    private fun tryServiceCall(serviceName: String, methodName: String, arg: Any?): Boolean = try {
        val proxy = acquireServiceProxy(serviceName) ?: return false
        val method = when (arg) {
            null -> proxy.javaClass.getMethod(methodName)
            is Boolean -> proxy.javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
            is Int -> proxy.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
            is String -> proxy.javaClass.getMethod(methodName, String::class.java)
            else -> proxy.javaClass.getMethod(methodName, arg.javaClass)
        }
        val args = if (arg != null) arrayOf(arg) else emptyArray()
        method.invoke(proxy, *args)
        true
    } catch (e: Throwable) {
        Log.v(TAG, "tryServiceCall($serviceName, $methodName) failed: ${e.message}")
        false
    }

    private fun acquireServiceProxy(serviceName: String): Any? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getMethod = smClass.getMethod("getService", String::class.java)
            val binder = getMethod.invoke(null, serviceName) ?: return null

            // Try several stub class name patterns (varies by firmware)
            val stubCandidates = listOf(
                "android.hardware.bydauto.IBydAuto${serviceName.removePrefix("bydauto.")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}",
                "android.hardware.bydauto.IBydAutoService",
                "android.hardware.bydauto.IBydService"
            )
            for (stubClassName in stubCandidates) {
                try {
                    val stubClass = Class.forName(stubClassName)
                    val asInterface = stubClass.getMethod("asInterface",
                        Class.forName("android.os.IBinder"))
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

    // ──────────────────────────────────────────────────────────────────────
    // Volume control (uses Android AudioManager, not bydauto.* — but
    // unified here so the command interface is consistent)
    // ──────────────────────────────────────────────────────────────────────

    private fun adjustVolume(direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.adjustVolume(
            if (direction > 0) android.media.AudioManager.ADJUST_RAISE
            else android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleMute() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.adjustVolume(android.media.AudioManager.ADJUST_TOGGLE_MUTE,
            android.media.AudioManager.FLAG_SHOW_UI)
    }
}
