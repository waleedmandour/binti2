package com.binti.dilink.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.binti.dilink.R
import com.binti.dilink.dilink.telemetry.TelemetryConsentManager

/**
 * Tier 2 — Advanced Telemetry Onboarding (enthusiast flavor only).
 *
 * The single entry point through which a user grants consent for the
 * `AdvancedTelemetryProbe` to reflect against `android.hardware.bydauto.*`.
 * The screen is deliberately verbose — users MUST read and explicitly
 * acknowledge the warning before the toggle flips to GRANTED.
 *
 * Flow:
 *  1. User opens the screen (from the Developer Settings screen).
 *  2. Reads the warning text.
 * 3. Checks the "I have read and accept" checkbox.
 *  4. Taps "Enable Advanced Telemetry".
 *  5. Consent is recorded via [TelemetryConsentManager.grant].
 *
 * Reversibility: the user can revoke at any time from the same screen —
 * the toggle becomes "Disable Advanced Telemetry" once consent is GRANTED.
 *
 * @author Dr. Waleed Mandour
 */
class AdvancedTelemetryOnboardingActivity : AppCompatActivity() {

    private lateinit var consent: TelemetryConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_telemetry_onboarding)

        consent = TelemetryConsentManager.get(this)

        val tvWarning       = findViewById<TextView>(R.id.tvTelemetryWarning)
        val cbAcknowledge   = findViewById<CheckBox>(R.id.cbAcknowledge)
        val btnToggle       = findViewById<Button>(R.id.btnToggleTelemetry)
        val tvStatus        = findViewById<TextView>(R.id.tvConsentStatus)

        tvWarning.text = getString(R.string.advanced_telemetry_warning_full)

        updateStatusText(tvStatus, btnToggle, cbAcknowledge)

        cbAcknowledge.setOnCheckedChangeListener { _, isChecked ->
            btnToggle.isEnabled = isChecked || consent.isGranted
        }

        btnToggle.setOnClickListener {
            if (consent.isGranted) {
                consent.revoke()
                TelemetryConsentManager.get(this).let { /* refresh */ }
                // Also clear the snapshot's Tier 2 fields — falling back to Tier 1 only.
                com.binti.dilink.dilink.DiLinkAccessibilityService.vehicleDataRepository.reset()
            } else {
                if (!cbAcknowledge.isChecked) {
                    android.widget.Toast.makeText(this,
                        getString(R.string.advanced_telemetry_must_acknowledge),
                        android.widget.Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                consent.grant()
            }
            updateStatusText(tvStatus, btnToggle, cbAcknowledge)

            // Broadcast back to the caller so the Developer screen can refresh.
            val resultIntent = Intent("com.binti.dilink.TELEMETRY_CONSENT_CHANGED")
            resultIntent.putExtra("granted", consent.isGranted)
            setResult(if (consent.isGranted) RESULT_OK else RESULT_CANCELED, resultIntent)
        }
    }

    private fun updateStatusText(
        tvStatus: TextView,
        btnToggle: Button,
        cbAcknowledge: CheckBox
    ) {
        val state = consent.state
        tvStatus.text = when (state) {
            TelemetryConsentManager.ConsentState.NOT_SHOWN ->
                getString(R.string.advanced_telemetry_status_not_shown)
            TelemetryConsentManager.ConsentState.GRANTED ->
                getString(R.string.advanced_telemetry_status_granted,
                          java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                              java.util.Locale.getDefault())
                              .format(java.util.Date(consent.grantedAtMillis)))
            TelemetryConsentManager.ConsentState.REVOKED ->
                getString(R.string.advanced_telemetry_status_revoked)
        }
        btnToggle.text = if (state == TelemetryConsentManager.ConsentState.GRANTED)
            getString(R.string.advanced_telemetry_disable)
        else
            getString(R.string.advanced_telemetry_enable)
        btnToggle.isEnabled = state == TelemetryConsentManager.ConsentState.GRANTED ||
                              cbAcknowledge.isChecked
    }
}
