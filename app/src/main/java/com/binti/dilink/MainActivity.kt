

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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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
import com.binti.dilink.voice.LaunchGreeting
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "binti_prefs"
        private const val DOWNLOAD_NOTIFICATION_ID = 2001

        /** Minimum time away from the app before the greeting replays (60 s). */
        private const val GREETING_REPLAY_MIN_GAP_MS = 60_000L

        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_TONE_FORMAL       = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
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

    // v3.0.2 FIX (greeting only once per process): the old process-wide guard
    // never reset while the foreground service kept the process alive, so
    // closing and reopening the app never greeted again. The policy is now:
    //  - a FRESH launch (onCreate with savedInstanceState == null) greets,
    //  - returning to the app after >= GREETING_REPLAY_MIN_GAP_MS away greets
    //    again (covers reopening from the launcher while the process/task is
    //    still alive — no onCreate happens on that path),
    //  - rotations and quick app switches (< 60 s away) do NOT.
    private var lastStoppedAtMs: Long = 0L

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
        }

        // Launch greeting — plays IMMEDIATELY from the bundled clip, without
        // waiting for any TTS engine to warm up (see playLaunchGreeting).
        // v3.0.2 FIX: only on a FRESH launch — an activity recreation
        // (rotation, locale change…) restores state and must stay silent.
        if (savedInstanceState == null) {
            playLaunchGreeting()
        }

        setupUI()
        loadSettings()
        registerReceivers()
        createDownloadNotificationChannel()
    }

    override fun onResume() {
        super.onResume()
        // v3.0.2 FIX — replay the greeting when the user comes back to the app
        // after a real absence. This covers the common in-car path where the
        // process and task are still alive (foreground service!), so tapping
        // the launcher icon brings the EXISTING activity forward without any
        // onCreate — the old code never greeted on that path either.
        val awayMs = if (lastStoppedAtMs > 0) System.currentTimeMillis() - lastStoppedAtMs else 0L
        if (lastStoppedAtMs > 0 && awayMs >= GREETING_REPLAY_MIN_GAP_MS) {
            playLaunchGreeting()
        }
        lastStoppedAtMs = 0L
        checkPermissions()
        updateServiceState(if (BintiService.isServiceRunning()) "ready" else "off")
    }

    override fun onStop() {
        super.onStop()
        lastStoppedAtMs = System.currentTimeMillis()
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

            // Developer / QA shortcut — open the Vehicle State debug screen.
            // (Settings → Developer → Vehicle State per the project brief.)
            btnVehicleState.setOnClickListener {
                startActivity(Intent(this@MainActivity,
                    com.binti.dilink.dilink.telemetry.VehicleStateActivity::class.java))
            }

            btnVocalizeGuide.setOnClickListener {
                // Use speakNow() which works even if the async TTS init hasn't
                // completed yet — it creates a temporary TTS instance as fallback.
                val instruction = getString(R.string.usage_instruction_egyptian)
                egyptianTTS.speakNow(instruction, this@MainActivity)
                Toast.makeText(this@MainActivity,
                    "▶️ جاري تشغيل التعليمات...",
                    Toast.LENGTH_SHORT).show()
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
                        openAccessibilitySettingsSafely()
                    }
                    .show()
            }

            layoutOverlayPermission.btnGrantOverlay.setOnClickListener {
                openOverlaySettingsSafely()
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

    // ──────────────────────────────────────────────────────────────────────────
    // Defensive intent launchers — never crash on DiLink 3's custom Settings
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to open [intent]; returns true if it launched. Never throws.
     * On BYD DiLink 3 (Android 10), the OEM Settings app may not implement
     * all standard Android intent actions (e.g. ACCESSIBILITY_SETTINGS),
     * causing ActivityNotFoundException. This helper catches that and falls
     * back gracefully.
     */
    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                true
            } else {
                Log.w(TAG, "No activity found for ${intent.action}")
                false
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "ActivityNotFoundException for ${intent.action}", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch ${intent.action}", e)
            false
        }
    }

    /**
     * Open Accessibility Settings with a multi-step fallback chain:
     * 1. Android 13+ deep link to this service's own detail page (nicer UX)
     * 2. Standard ACCESSIBILITY_SETTINGS (works on most devices)
     * 3. Generic ACTION_SETTINGS (last resort)
     * 4. If all fail: show manual instructions with ADB commands
     */
    private fun openAccessibilitySettingsSafely() {
        // Step 1: Android 13+ deep link to this service's detail page
        // ACTION_ACCESSIBILITY_DETAILS_SETTINGS was added in API 33 (Tiramisu).
        // Use the string constant directly to avoid compile issues with
        // targetSdk 33 — the constant exists at runtime on API 33+ devices.
        val detailIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                val componentName = android.content.ComponentName(
                    this@MainActivity,
                    com.binti.dilink.dilink.DiLinkAccessibilityService::class.java
                ).flattenToString()
                putExtra("android.intent.extra.ACCESSIBILITY_COMPONENT_NAME", componentName)
            }
        } else null

        // Step 2: Standard accessibility settings
        val standardIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        // Step 3: Generic settings (last resort before manual)
        val genericIntent = Intent(Settings.ACTION_SETTINGS)

        val opened = (detailIntent?.let { tryStartActivity(it) } ?: false) ||
                     tryStartActivity(standardIntent) ||
                     tryStartActivity(genericIntent)

        if (!opened) {
            showManualAccessibilityInstructions()
        }
    }

    /**
     * Open Overlay Permission Settings with fallback.
     */
    private fun openOverlaySettingsSafely() {
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        if (!tryStartActivity(overlayIntent)) {
            // Fallback to generic settings
            tryStartActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Show a dialog with manual ADB instructions when the head unit's
     * Settings app doesn't expose the Accessibility Settings screen.
     * This is common on BYD DiLink 3 where the OEM settings app is
     * heavily customized and may omit the standard accessibility page.
     *
     * The ADB target IP is auto-detected from the WiFi gateway (DhcpInfo)
     * and displayed in an editable EditText so the user can override it
     * if the detected address is wrong. This replaces the previous
     * hardcoded `192.168.10.10:5555` which only worked on one specific
     * network configuration.
     */
    private fun showManualAccessibilityInstructions() {
        val serviceName = "$packageName/com.binti.dilink.dilink.DiLinkAccessibilityService"
        val detectedIp = detectAdbTargetIp()

        // Editable IP field — defaults to the detected gateway IP, user can override
        val ipInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(detectedIp)
            hint = "IP:PORT (e.g. 192.168.1.100:5555)"
            setSingleLine(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(ipInput)
        }

        // Build the instructions with a placeholder that gets substituted on "Got it"
        fun buildInstructions(ip: String): String {
            val safeIp = if (ip.isBlank()) "<HEAD_UNIT_IP>:5555" else ip
            return """
                هذا النظام (DiLink 3) لا يعرض شاشة إعدادات الوصول القياسية.

                لتفعيل خدمة الوصول، استخدم أوامر ADB التالية من جهاز كمبيوتر متصل بنفس شبكة WiFi:

                1️⃣ adb connect $safeIp

                2️⃣ adb shell settings put secure accessibility_enabled 1

                3️⃣ adb shell settings put secure enabled_accessibility_services $serviceName

                ثم أعد فتح تطبيق "يا بنتي" وتحقق من حالة الخدمة.

                ―――――――――――――――――――――――――――――――――――

                This DiLink 3 head unit does not expose the standard Accessibility Settings screen.

                To enable the accessibility service, run these ADB commands from a computer on the same WiFi:

                1️⃣ adb connect $safeIp

                2️⃣ adb shell settings put secure accessibility_enabled 1

                3️⃣ adb shell settings put secure enabled_accessibility_services $serviceName

                Then reopen Binti and check the service status.

                💡 The IP above was auto-detected from your WiFi gateway. If it's wrong, edit it and copy the command with your correct IP.
            """.trimIndent()
        }

        // We use a TextView-like approach: show the instructions in the dialog body,
        // with the editable IP above it. On "Copy commands" we regenerate with the
        // user's edited IP and copy to clipboard.
        val messageView = android.widget.TextView(this).apply {
            text = buildInstructions(detectedIp)
            setTextIsSelectable(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            textSize = 13f
        }

        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(inputContainer)
            addView(messageView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("إعدادات الوصول غير متاحة")
            .setView(dialogContainer)
            .setPositiveButton("فهمت") { _, _ ->
                // Optionally copy the final commands to clipboard with the user's IP
                val finalIp = ipInput.text.toString().trim()
                if (finalIp.isNotBlank() && finalIp != detectedIp) {
                    copyAdbCommandsToClipboard(finalIp, serviceName)
                }
            }
            .setNeutralButton("نسخ الأوامر") { _, _ ->
                val finalIp = ipInput.text.toString().trim().ifBlank { detectedIp }
                copyAdbCommandsToClipboard(finalIp, serviceName)
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Auto-detect the most likely ADB target IP for the head unit.
     *
     * Strategy:
     * 1. Read the WiFi gateway from [WifiManager.DhcpInfo] — on a BYD head
     *    unit, the head unit itself is typically the gateway or sits at
     *    gateway+1 on the same /24 subnet.
     * 2. If DhcpInfo is unavailable (no WiFi), fall back to the loopback
     *    `127.0.0.1:5555` — this works when ADB is enabled over USB and
     *    the user connects the head unit to a laptop via USB.
     *
     * The returned string is a best-effort default; the user can always
     * edit it in the dialog.
     */
    @Suppress("DEPRECATION") // DhcpInfo is deprecated but still the only way to get the gateway on API 26+
    private fun detectAdbTargetIp(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                val gatewayIp = intToIpAddress(dhcpInfo.gateway)
                // The head unit is usually at the gateway IP or gateway+1.
                // We default to the gateway IP since on BYD head units acting
                // as WiFi hosts, the head unit IS the gateway. If the user is
                // on a phone hotspot, the head unit is a client and the user
                // will need to find its IP in the head unit's WiFi settings.
                "$gatewayIp:5555"
            } else {
                // No WiFi gateway — fall back to loopback (USB ADB)
                "127.0.0.1:5555"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not detect WiFi gateway IP: ${e.message}")
            "127.0.0.1:5555"
        }
    }

    /**
     * Convert an integer IP address (as returned by [WifiManager.DhcpInfo])
     * to a dotted-quad string.
     */
    private fun intToIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            (ip shr 8) and 0xff,
            (ip shr 16) and 0xff,
            (ip shr 24) and 0xff
        )
    }

    /**
     * Copy the three ADB commands to the system clipboard so the user can
     * paste them into a terminal on their computer.
     */
    @Suppress("DEPRECATION") // ClipboardManager#setText is deprecated but simplest for plain text
    private fun copyAdbCommandsToClipboard(ip: String, serviceName: String) {
        val safeIp = if (ip.isBlank()) "127.0.0.1:5555" else ip
        val commands = """
            adb connect $safeIp
            adb shell settings put secure accessibility_enabled 1
            adb shell settings put secure enabled_accessibility_services $serviceName
        """.trimIndent()

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ADB Commands", commands))
            Toast.makeText(this, "تم نسخ الأوامر ✅", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy to clipboard: ${e.message}")
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
        // v3.0.2 FIX (boot auto-start never fired): the BootReceiver reads the
        // "auto_start" flag but NOTHING ever wrote it, so auto-start could never
        // trigger. Persist the user's intent here: starting the service from the
        // UI means "keep me running across reboots". Storing it in BOTH the
        // default (credential-encrypted) prefs and the device-protected prefs
        // matters because BootReceiver handles ACTION_LOCKED_BOOT_COMPLETED too,
        // which runs BEFORE user unlock and can only read device-protected
        // storage — a CE-only flag would always read false there.
        persistAutoStart(true)
        updateServiceState("ready")
    }

    private fun stopBintiService() {
        startService(Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_STOP })
        // v3.0.2 FIX: the mirror of startBintiService() — if the user explicitly
        // stopped Binti, a reboot must NOT silently restart it.
        persistAutoStart(false)
        updateServiceState("off")
    }

    /**
     * v3.0.2 FIX — persist the auto-start flag to BOTH preference stores:
     *  1. the normal (credential-encrypted) "binti_prefs" — read by
     *     BootReceiver for ACTION_BOOT_COMPLETED / QUICKBOOT_POWERON,
     *  2. the device-protected "binti_prefs" — read by BootReceiver for
     *     ACTION_LOCKED_BOOT_COMPLETED (Direct Boot, pre-unlock).
     *
     * These are two physically different files; writing only one of them
     * silently breaks the other boot path.
     */
    private fun persistAutoStart(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(BintiService.KEY_AUTO_START, enabled) }
        try {
            createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { putBoolean(BintiService.KEY_AUTO_START, enabled) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist auto_start to device-protected storage", e)
        }
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
    // Launch greeting
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Launch greeting — "أهلا بيك" spoken in a clear female voice on EVERY
     * app open.
     *
     * Uses the bundled clip (res/raw/launch_greeting.wav) via [LaunchGreeting]
     * so it plays instantly without waiting for any TTS engine to warm up,
     * and sounds the same on every car. If the clip is unavailable or fails,
     * fall back to the configured EgyptianTTS engine.
     *
     * v3.0.2 FIX — WHEN it plays is decided here (see class-level comments):
     * fresh onCreate (savedInstanceState == null) and re-entry after ≥ 60 s
     * away. Rotations and quick switches stay silent. [LaunchGreeting] itself
     * only guards against two overlapping playbacks.
     */
    private fun playLaunchGreeting() {
        val started = LaunchGreeting.play(this)
        if (!started) {
            // Clip missing/failed or already playing — TTS fallback keeps the
            // behaviour present but never double-plays.
            if (!LaunchGreeting.isPlaying && !isDestroyed && !isFinishing) {
                lifecycleScope.launch {
                    egyptianTTS.speak(getString(R.string.launch_greeting))
                }
            }
        }
    }
}
