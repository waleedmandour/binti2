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
import android.os.Environment
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

/**
 * Main Activity - Professional Automotive Dashboard for ya Binti (يا بنتي)
 * 
 * Optimized for DiLink systems with Egyptian Dialect support.
 * Settings are now integrated directly into the main dashboard for easier access.
 * 
 * @author Dr. Waleed Mandour
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "binti_prefs"
        private const val DOWNLOAD_NOTIFICATION_ID = 2001
        
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_TONE_FORMAL = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
        private const val KEY_DEMO_PLAYED = "demo_voice_played"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var egyptianTTS: EgyptianTTS
    
    // Permission states
    private var hasStoragePermission = false
    private var hasMicPermission = false
    private var hasAccessibilityPermission = false
    private var hasOverlayPermission = false
    private var hasLocationPermission = false
    private var hasPhonePermission = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        modelManager = ModelManager(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize Egyptian TTS with natural vocalization rules
        egyptianTTS = EgyptianTTS(this)
        lifecycleScope.launch {
            try { egyptianTTS.initialize() } catch (e: Exception) { Log.e(TAG, "TTS Init failed", e) }
            // Auto-play demo voice message on first app launch
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
        super.onDestroy()
        egyptianTTS.release()
        try { unregisterReceiver(stateReceiver) } catch (e: Exception) {}
    }

    private fun setupUI() {
        binding.apply {
            btnStartService.setOnClickListener {
                if (BintiService.isServiceRunning()) stopBintiService()
                else if (checkCriticalPermissions()) checkModelsAndStart()
            }
            
            btnDownloadModels.setOnClickListener { checkWifiAndDownload() }
            
            // Vocalize Usage Guide in natural Egyptian Arabic
            btnVocalizeGuide.setOnClickListener {
                lifecycleScope.launch {
                    egyptianTTS.speak(getString(R.string.usage_instruction_egyptian))
                }
            }

            // Real-time Settings Saving
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
            
            // Permissions UI Initialization
            setupPermissionClick(layoutStoragePermission, R.string.storage_permission_title, R.string.storage_permission_desc) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11-12: request legacy storage or app-specific
                    @Suppress("DEPRECATION")
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                } else {
                    @Suppress("DEPRECATION")
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
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
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) 
            }
            
            setupPermissionClick(layoutLocationPermission, R.string.location_permission_title, R.string.location_permission_desc) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) 
            }
            
            setupPermissionClick(layoutPhonePermission, R.string.phone_permission_title, R.string.phone_permission_desc) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE)) 
            }
        }
    }

    private fun setupPermissionClick(layout: CardPermissionMicBinding, titleRes: Int, descRes: Int, action: () -> Unit) {
        layout.apply {
            tvMicPermissionTitle.text = getString(titleRes)
            tvMicPermissionDesc.text = getString(descRes)
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

    private fun checkPermissions() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Check granular media permission or app-specific storage access
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED ||
                    Environment.isExternalStorageManager()
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        val accessibilityService = packageName + "/" + DiLinkAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        hasAccessibilityPermission = enabledServices?.contains(accessibilityService) == true
        
        hasOverlayPermission = Settings.canDrawOverlays(this)
        
        hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        hasPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        
        updatePermissionCards()
    }

    private fun updatePermissionCards() {
        binding.apply {
            updateCard(layoutStoragePermission.cardMicPermission, layoutStoragePermission.btnGrantMic, hasStoragePermission)
            updateCard(layoutMicPermission.cardMicPermission, layoutMicPermission.btnGrantMic, hasMicPermission)
            updateCard(layoutAccessibilityPermission.cardAccessibilityPermission, layoutAccessibilityPermission.btnGrantAccessibility, hasAccessibilityPermission)
            updateCard(layoutOverlayPermission.cardOverlayPermission, layoutOverlayPermission.btnGrantOverlay, hasOverlayPermission)
            updateCard(layoutLocationPermission.cardMicPermission, layoutLocationPermission.btnGrantMic, hasLocationPermission)
            updateCard(layoutPhonePermission.cardMicPermission, layoutPhonePermission.btnGrantMic, hasPhonePermission)
            
            btnStartService.text = if (BintiService.isServiceRunning()) getString(R.string.stop_service) else getString(R.string.start_service)
        }
    }

    private fun updateCard(card: com.google.android.material.card.MaterialCardView, btn: android.widget.Button, granted: Boolean) {
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

    private fun requestPermissions(perms: Array<String>) { requestPermissionLauncher.launch(perms) }

    private fun checkCriticalPermissions(): Boolean {
        val essential = hasStoragePermission && hasMicPermission && hasAccessibilityPermission && hasOverlayPermission
        if (!essential) Toast.makeText(this, R.string.permissions_required_message, Toast.LENGTH_LONG).show()
        return essential
    }

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
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun startModelDownload() {
        lifecycleScope.launch {
            binding.layoutDownloadProgress.visibility = View.VISIBLE
            binding.btnDownloadModels.isEnabled = false

            val builder = NotificationCompat.Builder(this@MainActivity, "download_channel")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.download_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            modelManager.downloadModels(
                onProgress = { p, f -> 
                    runOnUiThread { 
                        binding.progressBar.progress = p
                        binding.tvDownloadStatus.text = getString(R.string.downloading_file, f, p)
                        builder.setProgress(100, p, false).setContentText("$f: $p%")
                        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
                    } 
                },
                onComplete = { 
                    runOnUiThread { 
                        binding.layoutDownloadProgress.visibility = View.GONE
                        binding.btnDownloadModels.isEnabled = true
                        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
                        Toast.makeText(this@MainActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                        startBintiService() 
                    } 
                },
                onError = { e -> 
                    runOnUiThread { 
                        binding.layoutDownloadProgress.visibility = View.GONE
                        binding.btnDownloadModels.isEnabled = true
                        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
                        Toast.makeText(this@MainActivity, "Error: $e", Toast.LENGTH_LONG).show()
                    } 
                }
            )
        }
    }

    private fun createDownloadNotificationChannel() {
        val channel = NotificationChannel("download_channel", "Model Downloads", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startBintiService() {
        val intent = Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_START }
        startForegroundService(intent)
        updateServiceState("ready")
    }

    private fun stopBintiService() {
        startService(Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_STOP })
        updateServiceState("off")
    }

    private fun updateServiceState(state: String) {
        val text: String
        val colorRes: Int
        val iconRes: Int
        
        when (state) {
            "ready" -> {
                text = getString(R.string.state_ready)
                colorRes = R.color.success
                iconRes = R.drawable.ic_mic
            }
            "listening" -> {
                text = getString(R.string.state_listening)
                colorRes = R.color.accent
                iconRes = R.drawable.ic_mic
            }
            "processing" -> {
                text = getString(R.string.state_processing)
                colorRes = R.color.primary
                iconRes = android.R.drawable.stat_sys_download
            }
            "off" -> {
                text = getString(R.string.state_ready)
                colorRes = R.color.text_secondary
                iconRes = R.drawable.ic_mic
            }
            else -> {
                text = getString(R.string.state_ready)
                colorRes = R.color.success
                iconRes = R.drawable.ic_mic
            }
        }
        
        binding.tvServiceState.text = text
        binding.tvServiceState.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.ivAppLogo.imageTintList = null
        
        binding.btnStartService.text = if (BintiService.isServiceRunning()) getString(R.string.stop_service) else getString(R.string.start_service)
    }

    private fun registerReceivers() {
        val filter = IntentFilter(BintiService.BROADCAST_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
    }

    /**
     * Plays a demo voice message on first app launch only.
     * Uses the existing TTS engine to speak a natural Egyptian Arabic welcome.
     */
    private fun playDemoIfFirstLaunch() {
        val hasPlayed = sharedPreferences.getBoolean(KEY_DEMO_PLAYED, false)
        if (!hasPlayed) {
            sharedPreferences.edit { putBoolean(KEY_DEMO_PLAYED, true) }
            // Small delay so the UI is fully rendered before speaking
            binding.root.postDelayed({
                lifecycleScope.launch {
                    egyptianTTS.speak(getString(R.string.demo_voice_message))
                }
            }, 1500)
        }
    }
}
