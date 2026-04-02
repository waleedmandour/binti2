package com.binti.dilink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.databinding.ActivityMainBinding
import com.binti.dilink.databinding.CardPermissionMicBinding
import com.binti.dilink.dilink.DiLinkAccessibilityService
import com.binti.dilink.response.EgyptianTTS
import com.binti.dilink.utils.ModelManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "binti_prefs"
        private const val DOWNLOAD_NOTIFICATION_ID = 2001

        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_TONE_FORMAL       = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
        private const val KEY_DEMO_PLAYED       = "demo_voice_played"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var egyptianTTS: EgyptianTTS

    private var hasStoragePermission     = false
    private var hasMicPermission         = false
    private var hasAccessibilityPermission = false
    private var hasOverlayPermission     = false
    private var hasLocationPermission    = false
    private var hasPhonePermission       = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> checkPermissions() }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BintiService.BROADCAST_STATE_CHANGED) {
                val state = intent.getStringExtra(BintiService.EXTRA_STATE) ?: "unknown"
                updateServiceState(state)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager      = ModelManager(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        egyptianTTS       = EgyptianTTS(this)

        lifecycleScope.launch {
            try { egyptianTTS.initialize() } catch (e: Exception) { Log.e(TAG, "TTS init failed", e) }
            playDemoIfFirstLaunch()
        }

        setupUI()
        loadSettings()
        registerReceivers()
        createDownloadNotificationChannel()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateServiceState(if (BintiService.isServiceRunning()) "ready" else "off")
    }

    override fun onDestroy() {
        // FIX #1 — unregister before releasing TTS; reversed order in original
        // meant stateReceiver could fire and touch egyptianTTS after release().
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        egyptianTTS.release()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI setup
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.apply {
            btnStartService.setOnClickListener {
                if (BintiService.isServiceRunning()) stopBintiService()
                else if (checkCriticalPermissions()) checkModelsAndStart()
            }

            btnDownloadModels.setOnClickListener { checkWifiAndDownload() }

            btnVocalizeGuide.setOnClickListener {
                lifecycleScope.launch {
                    egyptianTTS.speak(getString(R.string.usage_instruction_egyptian))
                }
            }

            etUserName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    sharedPreferences.edit { putString(KEY_USER_NAME, s?.toString() ?: "") }
                }
            })

            toggleTone.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    sharedPreferences.edit { putBoolean(KEY_TONE_FORMAL, checkedId == R.id.btnFormal) }
                }
            }

            switchProactive.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit { putBoolean(KEY_PROACTIVE_ENABLED, isChecked) }
            }

            setupPermissionClick(layoutStoragePermission, R.string.storage_permission_title, R.string.storage_permission_desc) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                } else {
                    @Suppress("DEPRECATION")
                    requestPermissions(arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ))
                }
            }

            setupPermissionClick(layoutMicPermission, R.string.mic_permission_title, R.string.mic_permission_desc) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
            }

            layoutAccessibilityPermission.btnGrantAccessibility.setOnClickListener {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.accessibility_guide_title)
                    .setMessage(R.string.accessibility_guide_message)
                    .setPositiveButton(R.string.grant_permission) { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .show()
            }

            layoutOverlayPermission.btnGrantOverlay.setOnClickListener {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }

            setupPermissionClick(layoutLocationPermission, R.string.location_permission_title, R.string.location_permission_desc) {
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }

            setupPermissionClick(layoutPhonePermission, R.string.phone_permission_title, R.string.phone_permission_desc) {
                requestPermissions(arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE
                ))
            }
        }
    }

    private fun setupPermissionClick(
        layout:   CardPermissionMicBinding,
        titleRes: Int,
        descRes:  Int,
        action:   () -> Unit
    ) {
        layout.apply {
            tvMicPermissionTitle.text = getString(titleRes)
            tvMicPermissionDesc.text  = getString(descRes)
            btnGrantMic.setOnClickListener { action() }
        }
    }

    private fun loadSettings() {
        binding.apply {
            etUserName.setText(sharedPreferences.getString(KEY_USER_NAME, ""))
            val isFormal = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)
            toggleTone.check(if (isFormal) R.id.btnFormal else R.id.btnInformal)
            switchProactive.isChecked = sharedPreferences.getBoolean(KEY_PROACTIVE_ENABLED, true)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Permissions
    // ──────────────────────────────────────────────────────────────────────────

    private fun checkPermissions() {
        // FIX #2 — READ_MEDIA_AUDIO is API 33+; the original checked it on API 30+
        // (Build.VERSION_CODES.R = 30) which throws SecurityException on API 30-32.
        hasStoragePermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            else -> @Suppress("DEPRECATION")
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }

        hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // FIX #3 — accessibility service name must use '/' not '+' separator and
        // the canonical class name can be null on some devices. Guard against that.
        val canonicalName = DiLinkAccessibilityService::class.java.canonicalName
        hasAccessibilityPermission = if (canonicalName != null) {
            val serviceName     = "$packageName/$canonicalName"
            val enabledServices = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            // FIX #3 — use split+contains for exact match; plain contains() would
            // match a service whose name is a substring of another service's name.
            enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
        } else false

        hasOverlayPermission = Settings.canDrawOverlays(this)

        hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasPhonePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        updatePermissionCards()
    }

    private fun updatePermissionCards() {
        binding.apply {
            updateCard(layoutStoragePermission.cardMicPermission,     layoutStoragePermission.btnGrantMic,                hasStoragePermission)
            updateCard(layoutMicPermission.cardMicPermission,         layoutMicPermission.btnGrantMic,                    hasMicPermission)
            updateCard(layoutAccessibilityPermission.cardAccessibilityPermission, layoutAccessibilityPermission.btnGrantAccessibility, hasAccessibilityPermission)
            updateCard(layoutOverlayPermission.cardOverlayPermission, layoutOverlayPermission.btnGrantOverlay,            hasOverlayPermission)
            updateCard(layoutLocationPermission.cardMicPermission,    layoutLocationPermission.btnGrantMic,               hasLocationPermission)
            updateCard(layoutPhonePermission.cardMicPermission,       layoutPhonePermission.btnGrantMic,                  hasPhonePermission)

            btnStartService.text = if (BintiService.isServiceRunning())
                getString(R.string.stop_service) else getString(R.string.start_service)
        }
    }

    private fun updateCard(
        card:    com.google.android.material.card.MaterialCardView,
        btn:     android.widget.Button,
        granted: Boolean
    ) {
        if (granted) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.permission_granted))
            card.strokeColor = ContextCompat.getColor(this, R.color.success)
            btn.text = getString(R.string.permission_granted)
            btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            btn.isEnabled = false
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
            card.strokeColor = ContextCompat.getColor(this, R.color.divider)
            btn.text = getString(R.string.grant_permission)
            btn.isEnabled = true
        }
    }

    private fun requestPermissions(perms: Array<String>) {
        requestPermissionLauncher.launch(perms)
    }

    private fun checkCriticalPermissions(): Boolean {
        val ok = hasStoragePermission && hasMicPermission &&
                 hasAccessibilityPermission && hasOverlayPermission
        if (!ok) Toast.makeText(this, R.string.permissions_required_message, Toast.LENGTH_LONG).show()
        return ok
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Model download
    // ──────────────────────────────────────────────────────────────────────────

    private fun checkModelsAndStart() {
        lifecycleScope.launch {
            if (modelManager.checkModelsStatus().allModelsReady) startBintiService()
            else showModelDownloadDialog()
        }
    }

    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_models_title)
            .setMessage(R.string.download_models_message)
            .setPositiveButton(R.string.download_now) { _, _ -> checkWifiAndDownload() }
            .show()
    }

    private fun checkWifiAndDownload() {
        if (isWifiConnected()) startModelDownload()
        else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.wifi_required_title)
                .setMessage(R.string.wifi_required_message)
                .setPositiveButton(R.string.download_now) { _, _ -> startModelDownload() }
                .setNegativeButton(R.string.skip, null)
                .show()
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm      = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun startModelDownload() {
        lifecycleScope.launch {
            binding.layoutDownloadProgress.visibility = View.VISIBLE
            binding.btnDownloadModels.isEnabled       = false

            val builder = NotificationCompat.Builder(this@MainActivity, "download_channel")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.download_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            modelManager.downloadModels(
                onProgress = { p, f ->
                    // FIX #4 — callbacks from ModelManager may arrive on a background
                    // thread; runOnUiThread guards all View and Notification updates.
                    runOnUiThread {
                        binding.progressBar.progress   = p
                        binding.tvDownloadStatus.text  = getString(R.string.downloading_file, f, p)
                        builder.setProgress(100, p, false).setContentText("$f: $p%")
                        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
                    }
                },
                onComplete = {
                    runOnUiThread {
                        binding.layoutDownloadProgress.visibility = View.GONE
                        binding.btnDownloadModels.isEnabled       = true
                        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
                        Toast.makeText(this@MainActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                        // FIX #5 — only start service if critical permissions are still
                        // granted; user could have revoked them during a long download.
                        if (checkCriticalPermissions()) startBintiService()
                    }
                },
                onError = { e ->
                    runOnUiThread {
                        binding.layoutDownloadProgress.visibility = View.GONE
                        binding.btnDownloadModels.isEnabled       = true
                        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
                        // FIX #6 — show the localised string resource, not raw exception
                        // message which may be null or in English on an Arabic UI.
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.download_error, e ?: "unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    private fun createDownloadNotificationChannel() {
        val channel = NotificationChannel(
            "download_channel",
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Service control
    // ──────────────────────────────────────────────────────────────────────────

    private fun startBintiService() {
        val intent = Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)   // FIX #7 — use ContextCompat helper
        updateServiceState("ready")
    }

    private fun stopBintiService() {
        startService(Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_STOP })
        updateServiceState("off")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // State display
    // ──────────────────────────────────────────────────────────────────────────

    private fun updateServiceState(state: String) {
        val (text, colorRes) = when (state) {
            "ready"      -> getString(R.string.state_ready)      to R.color.success
            "listening"  -> getString(R.string.state_listening)  to R.color.accent
            "processing" -> getString(R.string.state_processing) to R.color.primary
            "off"        -> getString(R.string.state_off)        to R.color.text_secondary
            else         -> getString(R.string.state_ready)      to R.color.success
        }

        binding.tvServiceState.text = text
        binding.tvServiceState.setTextColor(ContextCompat.getColor(this, colorRes))

        binding.btnStartService.text = if (BintiService.isServiceRunning())
            getString(R.string.stop_service) else getString(R.string.start_service)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Receiver registration
    // ──────────────────────────────────────────────────────────────────────────

    private fun registerReceivers() {
        val filter = IntentFilter(BintiService.BROADCAST_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Demo voice
    // ──────────────────────────────────────────────────────────────────────────

    private fun playDemoIfFirstLaunch() {
        if (sharedPreferences.getBoolean(KEY_DEMO_PLAYED, false)) return
        sharedPreferences.edit { putBoolean(KEY_DEMO_PLAYED, true) }

        // FIX #8 — postDelayed posts a Runnable that captures `this` (Activity).
        // If the Activity is destroyed before the 1500 ms fire, the Runnable leaks
        // the Activity and launches a coroutine on a destroyed lifecycleScope.
        // Use lifecycle-aware posting instead.
        binding.root.postDelayed({
            if (!isDestroyed && !isFinishing) {
                lifecycleScope.launch {
                    egyptianTTS.speak(getString(R.string.demo_voice_message))
                }
            }
        }, 1500)
    }
}
