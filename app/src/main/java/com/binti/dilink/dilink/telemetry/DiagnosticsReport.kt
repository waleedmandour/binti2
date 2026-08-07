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

package com.binti.dilink.dilink.telemetry

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.binti.dilink.BuildConfig
import com.binti.dilink.dilink.DiLinkAccessibilityService
import com.binti.dilink.dilink.control.ControlLoader
import com.binti.dilink.dilink.telemetry.AdvancedTelemetryLoader
import com.binti.dilink.dilink.telemetry.TelemetryConsentManager
import com.binti.dilink.response.EgyptianTTS
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comprehensive diagnostics report generator — collects the state of EVERY
 * feature, permission, and system capability on the DiLink head unit.
 *
 * The generated report can be shared via email/Gmail/WhatsApp to
 * waleedmandour@gmail.com for troubleshooting.
 *
 * @author Dr. Waleed Mandour
 */
object DiagnosticsReport {

    private const val TAG = "DiagnosticsReport"
    const val SUPPORT_EMAIL = "waleedmandour@gmail.com"

    /**
     * Generate a full diagnostics report as a formatted string.
     * Covers: app info, build flavor, Android version, device model,
     * all permissions, accessibility service state, TTS engine state,
     * control mode, telemetry consent, vehicle snapshot, BYD model detection.
     */
    fun generate(context: Context, egyptianTTS: EgyptianTTS?): String {
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date())

        sb.appendLine("═══════════════════════════════════════════════════════════════")
        sb.appendLine("  Binti Diagnostics Report")
        sb.appendLine("  Generated: $timestamp")
        sb.appendLine("═══════════════════════════════════════════════════════════════")
        sb.appendLine()

        // ── App Info ──
        sb.appendLine("── APP INFO ──")
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.appendLine("  Package: ${pkgInfo.packageName}")
            sb.appendLine("  Version: ${pkgInfo.versionName} (code ${pkgInfo.longVersionCode})")
        } catch (e: Exception) {
            sb.appendLine("  Package: ERROR — ${e.message}")
        }
        sb.appendLine("  Build flavor: ${BuildConfig.FLAVOR}")
        sb.appendLine("  Enthusiast flavor: ${BuildConfig.ENTHUSIAST_FLAVOR}")
        sb.appendLine()

        // ── Device Info ──
        sb.appendLine("── DEVICE INFO ──")
        sb.appendLine("  Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("  Device: ${Build.DEVICE}")
        sb.appendLine("  Model: ${Build.MODEL}")
        sb.appendLine("  Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("  Brand: ${Build.BRAND}")
        sb.appendLine("  Product: ${Build.PRODUCT}")
        sb.appendLine("  Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine()

        // ── BYD Model Detection ──
        sb.appendLine("── BYD MODEL DETECTION ──")
        val a11yService = DiLinkAccessibilityService.getInstance()
        sb.appendLine("  Detected model: ${a11yService?.getDetectedModel() ?: "service not connected"}")
        sb.appendLine("  System properties:")
        for (prop in listOf("ro.product.model", "ro.byd.model", "ro.byd.platform", "ro.build.fingerprint")) {
            val value = getSystemProperty(prop)
            sb.appendLine("    $prop = $value")
        }
        sb.appendLine()

        // ── Permissions ──
        sb.appendLine("── PERMISSIONS ──")
        val permissions = listOf(
            "android.permission.RECORD_AUDIO" to "Microphone",
            "android.permission.ACCESS_FINE_LOCATION" to "Location (fine)",
            "android.permission.ACCESS_COARSE_LOCATION" to "Location (coarse)",
            "android.permission.CALL_PHONE" to "Phone calls",
            "android.permission.READ_CONTACTS" to "Contacts",
            "android.permission.READ_PHONE_STATE" to "Phone state",
            "android.permission.POST_NOTIFICATIONS" to "Notifications",
            "android.permission.SYSTEM_ALERT_WINDOW" to "Display over apps",
            "android.permission.WRITE_SETTINGS" to "Write settings (enthusiast only)"
        )
        for ((perm, label) in permissions) {
            val granted = context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            // WRITE_SETTINGS is special — check via Settings.System.canWrite
            if (perm == "android.permission.WRITE_SETTINGS") {
                val canWrite = Settings.System.canWrite(context)
                sb.appendLine("  $label: ${if (canWrite) "GRANTED" else "not granted"} ($perm)")
            } else {
                sb.appendLine("  $label: ${if (granted) "GRANTED" else "NOT GRANTED"} ($perm)")
            }
        }
        sb.appendLine()

        // ── Accessibility Service ──
        sb.appendLine("── ACCESSIBILITY SERVICE ──")
        val canonicalName = DiLinkAccessibilityService::class.java.canonicalName
        val serviceName = if (canonicalName != null) "${context.packageName}/$canonicalName" else "null"
        val enabledServices = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        } catch (e: Exception) { "ERROR: ${e.message}" }
        val isEnabledInSettings = enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
        sb.appendLine("  Service name: $serviceName")
        sb.appendLine("  Enabled in settings: $isEnabledInSettings")
        sb.appendLine("  Instance connected: ${a11yService != null}")
        sb.appendLine("  All enabled services: ${if (enabledServices.isBlank()) "(none)" else enabledServices}")
        if (a11yService != null) {
            try {
                val info = a11yService.serviceInfo
                sb.appendLine("  Event types: ${info?.eventTypes}")
                sb.appendLine("  Flags: ${info?.flags}")
                sb.appendLine("  Notification timeout: ${info?.notificationTimeout}")
                sb.appendLine("  Can retrieve window content: ${info?.canRetrieveWindowContent}")
                sb.appendLine("  Monitored packages: ${info?.packageNames?.joinToString(", ") ?: "(all)"}")
            } catch (e: Exception) {
                sb.appendLine("  ServiceInfo error: ${e.message}")
            }
        }
        sb.appendLine()

        // ── TTS Engine ──
        sb.appendLine("── TTS ENGINE ──")
        if (egyptianTTS != null) {
            sb.appendLine("  Selected engine: ${egyptianTTS.getTTSEngine()}")
            sb.appendLine("  Sherpa model available: ${egyptianTTS.isSherpaModelAvailable()}")
        } else {
            sb.appendLine("  TTS not initialized")
        }
        sb.appendLine("  HMS Core available: ${com.binti.dilink.utils.HMSUtils.isHuaweiServicesAvailable(context)}")
        sb.appendLine("  HMS version: ${com.binti.dilink.utils.HMSUtils.getHMSVersion(context)}")
        sb.appendLine()

        // ── Control Mode ──
        sb.appendLine("── CONTROL MODE ──")
        val controlBridge = ControlLoader.get()
        sb.appendLine("  Active mode: ${controlBridge.modeName}")
        sb.appendLine("  Available: ${controlBridge.isAvailable}")
        sb.appendLine("  Ready: ${controlBridge.isReady()}")
        sb.appendLine()

        // ── Telemetry Consent ──
        sb.appendLine("── TELEMETRY CONSENT ──")
        val consent = TelemetryConsentManager.get(context)
        sb.appendLine("  State: ${consent.state}")
        sb.appendLine("  Advanced telemetry available: ${AdvancedTelemetryLoader.get().isAvailable}")
        if (consent.isGranted) {
            sb.appendLine("  Granted at: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(consent.grantedAtMillis))}")
        }
        sb.appendLine()

        // ── Vehicle Snapshot ──
        sb.appendLine("── VEHICLE SNAPSHOT ──")
        val snapshot = DiLinkAccessibilityService.vehicleDataRepository.current()
        sb.appendLine(snapshot.toDebugString())
        sb.appendLine()

        // ── Model Download Status ──
        sb.appendLine("── MODEL DOWNLOAD STATUS ──")
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            val modelFiles = modelsDir.listFiles()
            if (modelFiles != null && modelFiles.isNotEmpty()) {
                for (f in modelFiles.sortedBy { it.name }) {
                    sb.appendLine("  ${f.name} (${f.length() / 1024 / 1024} MB)")
                }
            } else {
                sb.appendLine("  (no model files found)")
            }
        } else {
            sb.appendLine("  (models directory not created yet)")
        }
        sb.appendLine()

        sb.appendLine("═══════════════════════════════════════════════════════════════")
        sb.appendLine("  End of report")
        sb.appendLine("═══════════════════════════════════════════════════════════════")

        return sb.toString()
    }

    /**
     * Create a share intent for the diagnostics report.
     * Offers email (Gmail), WhatsApp, and other sharing apps if available.
     */
    fun createShareIntent(context: Context, reportText: String): Intent {
        // Write report to a temp file for attachment
        val reportFile = File(context.cacheDir, "binti_diagnostics_report.txt")
        reportFile.writeText(reportText)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Binti Diagnostics Report — ${Build.MODEL}")
            putExtra(Intent.EXTRA_TEXT, reportText)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "مشاركة تقرير التشخيص")
    }

    private fun getSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    } catch (e: Exception) { null }
}
