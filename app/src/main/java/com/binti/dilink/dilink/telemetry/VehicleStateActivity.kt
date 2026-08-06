

package com.binti.dilink.dilink.telemetry

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.BuildConfig
import com.binti.dilink.R
import com.binti.dilink.dilink.DiLinkAccessibilityService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tier 1 + Tier 2 — Developer Debug Screen.
 *
 * Shows the current [VehicleSnapshot] in real time (collects from
 * [DiLinkAccessibilityService.vehicleDataRepository]) and provides three
 * controls:
 *
 *  1. **Refresh now** — triggers [DiLinkAccessibilityService.forceRefreshSnapshot]
 *     for an immediate Accessibility scrape.
 *  2. **Probe Advanced Telemetry** — calls the registered [AdvancedTelemetryBridge]
 *     (no-op in the `store` flavor, real reflection probe in `enthusiast`).
 *     Disabled when consent is not granted.
 *  3. **Advanced Telemetry settings** — opens the consent / onboarding screen.
 *     Hidden in the `store` flavor (no point showing it — there's no probe
 *     to enable).
 *
 * This screen exists primarily for QA across BYD models. The acceptance
 * criterion is: "battery/range/charging/AC state visible in a simple debug
 * screen for QA across models" (per the project brief, Tier 1 task #5).
 *
 * @author Dr. Waleed Mandour
 */
class VehicleStateActivity : AppCompatActivity() {

    private lateinit var tvSnapshot: TextView
    private lateinit var tvFlavor: TextView
    private lateinit var tvConsent: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnProbe: Button
    private lateinit var btnTelemetrySettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_state)

        tvSnapshot          = findViewById(R.id.tvSnapshot)
        tvFlavor            = findViewById(R.id.tvFlavor)
        tvConsent           = findViewById(R.id.tvConsent)
        btnRefresh          = findViewById(R.id.btnRefresh)
        btnProbe            = findViewById(R.id.btnProbe)
        btnTelemetrySettings = findViewById(R.id.btnTelemetrySettings)

        // Flavor banner.
        tvFlavor.text = "Build flavor: ${BuildConfig.FLAVOR}\n" +
                        "Advanced Telemetry available: ${AdvancedTelemetryLoader.get().isAvailable}"

        // Hide Tier 2 controls entirely in the store flavor.
        val isEnthusiast = BuildConfig.ENTHUSIAST_FLAVOR
        btnProbe.visibility          = if (isEnthusiast) View.VISIBLE else View.GONE
        btnTelemetrySettings.visibility = if (isEnthusiast) View.VISIBLE else View.GONE
        tvConsent.visibility         = if (isEnthusiast) View.VISIBLE else View.GONE

        // Observe the shared snapshot.
        lifecycleScope.launch {
            DiLinkAccessibilityService.vehicleDataRepository.snapshot.collectLatest { snap ->
                tvSnapshot.text = snap.toDebugString()
            }
        }

        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                val service = DiLinkAccessibilityService.getInstance()
                if (service != null) {
                    service.forceRefreshSnapshot()
                } else {
                    android.widget.Toast.makeText(
                        this@VehicleStateActivity,
                        "Accessibility service not connected",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnProbe.setOnClickListener {
            lifecycleScope.launch {
                val bridge = AdvancedTelemetryLoader.get()
                val partial = bridge.probe()
                DiLinkAccessibilityService.vehicleDataRepository.mergeAdvanced(partial)
            }
        }

        btnTelemetrySettings.setOnClickListener {
            // Launch the enthusiast-flavor onboarding activity by class name
            // so main source never imports it directly.
            try {
                val className = "com.binti.dilink.ui.AdvancedTelemetryOnboardingActivity"
                val cls = Class.forName(className)
                startActivity(Intent(this, cls))
            } catch (e: ClassNotFoundException) {
                android.widget.Toast.makeText(
                    this,
                    "Onboarding activity not found in this build",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Refresh consent label whenever we return to the foreground.
        refreshConsentLabel()
        refreshA11yDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        refreshConsentLabel()
        refreshA11yDiagnostics()
    }

    private fun refreshConsentLabel() {
        val consent = TelemetryConsentManager.get(this)
        tvConsent.text = "Consent state: ${consent.state}" +
                         (if (consent.isGranted)
                             "\nGranted at: ${java.text.SimpleDateFormat(
                                 "yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                 .format(java.util.Date(consent.grantedAtMillis))}"
                          else "")
        btnProbe.isEnabled = consent.isGranted
    }

    /**
     * Populates the Accessibility Diagnostics section — helps debug the
     * crash-on-enable issue on DiLink 3. Shows:
     *  - Whether the service is enabled in system Settings.Secure
     *  - Whether the service instance is connected (onServiceConnected fired)
     *  - The list of all enabled accessibility services
     *  - The service's AccessibilityServiceInfo flags + packageNames
     *
     * If the service crashes on enable, this section will show "instance: null"
     * even after the user toggles it on in Settings, confirming the crash.
     */
    private fun refreshA11yDiagnostics() {
        val tvA11y = findViewById<android.widget.TextView>(R.id.tvA11yDiagnostics)
        val sb = StringBuilder()

        // 1. Is the service enabled in system settings?
        val canonicalName = com.binti.dilink.dilink.DiLinkAccessibilityService::class.java.canonicalName
        val serviceName = if (canonicalName != null) "$packageName/$canonicalName" else "null"
        val enabledServices = try {
            android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (e: Exception) { "ERROR: ${e.message}" }
        val isEnabledInSettings = enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
        sb.appendLine("Enabled in settings: $isEnabledInSettings")
        sb.appendLine("Service name: $serviceName")

        // 2. Is the service instance connected?
        val instance = com.binti.dilink.dilink.DiLinkAccessibilityService.getInstance()
        sb.appendLine("Instance connected: ${instance != null}")

        // 3. AccessibilityServiceInfo (only if instance is connected)
        if (instance != null) {
            try {
                val info = instance.serviceInfo
                sb.appendLine("Event types: ${info?.eventTypes}")
                sb.appendLine("Flags: ${info?.flags}")
                sb.appendLine("Feedback type: ${info?.feedbackType}")
                sb.appendLine("Notification timeout: ${info?.notificationTimeout}")
                sb.appendLine("Can retrieve window content: ${info?.canRetrieveWindowContent}")
                // canPerformGestures is not on AccessibilityServiceInfo;
                // it's declared in the XML config and readable via the service.
                val pkgs = info?.packageNames
                sb.appendLine("Monitored packages: ${pkgs?.joinToString(", ") ?: "(all)"}")
            } catch (e: Exception) {
                sb.appendLine("ERROR reading serviceInfo: ${e.message}")
            }
        }

        // 4. All enabled accessibility services (for debugging conflicts)
        sb.appendLine()
        sb.appendLine("All enabled services:")
        if (enabledServices.isBlank()) {
            sb.appendLine("  (none)")
        } else {
            enabledServices.split(":").forEachIndexed { i, svc ->
                sb.appendLine("  [$i] $svc")
            }
        }

        // 5. Detected BYD model
        sb.appendLine()
        sb.appendLine("Detected model: ${instance?.getDetectedModel() ?: "service not connected"}")

        tvA11y.text = sb.toString()
    }
}
