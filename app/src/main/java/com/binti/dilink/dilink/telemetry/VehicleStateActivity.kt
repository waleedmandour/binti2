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
import com.binti.dilink.dilink.VehicleSnapshot
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
    }

    override fun onResume() {
        super.onResume()
        refreshConsentLabel()
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
}
