package com.binti.dilink

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.databinding.ActivityMainBinding
import com.binti.dilink.dilink.DiLinkAccessibilityService
import com.binti.dilink.utils.HMSUtils
import com.binti.dilink.utils.ModelManager
import com.binti.dilink.utils.ModelStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Main Activity - Setup Wizard & Dashboard
 * 
 * Handles:
 * - Permission requests (Microphone, Overlay, Accessibility, Notifications)
 * - Model download prompts
 * - Service start/stop controls
 * - Status display
 * 
 * @author Dr. Waleed Mandour
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    
    // Permission states
    private var hasMicPermission = false
    private var hasOverlayPermission = false
    private var hasAccessibilityPermission = false
    private var hasNotificationPermission = false

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            hasNotificationPermission = true
        }
        
        checkAllPermissions()
    }

    // State change receiver
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BintiService.BROADCAST_STATE_CHANGED -> {
                    val state = intent.getStringExtra(BintiService.EXTRA_STATE) ?: "unknown"
                    updateServiceState(state)
                }
                BintiService.BROADCAST_COMMAND_RESULT -> {
                    val result = intent.getStringExtra(BintiService.EXTRA_RESULT) ?: ""
                    showCommandResult(result)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚗 MainActivity onCreate")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        modelManager = ModelManager(this)
        
        setupUI()
        checkPermissions()
        registerReceivers()
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityPermission()
        checkBatteryOptimization()
        updatePermissionCards()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
    }

    /**
     * Setup UI elements and click listeners
     */
    private fun setupUI() {
        binding.apply {
            // Start/Stop service button
            btnStartService.setOnClickListener {
                if (BintiService.isServiceRunning()) {
                    stopBintiService()
                } else {
                    if (checkAllPermissions()) {
                        checkModelsAndStart()
                    }
                }
            }
            
            // Permission request buttons
            btnGrantMic.setOnClickListener {
                requestMicPermission()
            }
            
            btnGrantOverlay.setOnClickListener {
                requestOverlayPermission()
            }
            
            btnGrantAccessibility.setOnClickListener {
                requestAccessibilityPermission()
            }
            
            btnGrantNotifications.setOnClickListener {
                requestNotificationPermission()
            }
            
            // Model download button
            btnDownloadModels.setOnClickListener {
                showModelDownloadDialog()
            }
            
            // Settings button
            btnSettings.setOnClickListener {
                // Open settings activity/fragment
            }
        }
        
        // Show Huawei device info
        if (HMSUtils.isHuaweiDevice()) {
            binding.tvDeviceInfo.text = getString(R.string.huawei_device_detected)
            binding.tvDeviceInfo.setTextColor(ContextCompat.getColor(this, R.color.huawei_red))
        }
    }

    /**
     * Check and request necessary permissions
     */
    private fun checkPermissions() {
        hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        hasOverlayPermission = Settings.canDrawOverlays(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasNotificationPermission = true
        }
        
        updatePermissionCards()
    }

    /**
     * Check all permissions and return status
     */
    private fun checkAllPermissions(): Boolean {
        val allGranted = hasMicPermission && hasOverlayPermission && 
                         hasAccessibilityPermission && hasNotificationPermission
        
        if (!allGranted) {
            showPermissionRequiredDialog()
        }
        
        return allGranted
    }

    /**
     * Update permission card visibility based on current state
     */
    private fun updatePermissionCards() {
        binding.apply {
            // Mic permission
            if (hasMicPermission) {
                cardMicPermission.setCardBackgroundColor(getColor(R.color.permission_granted))
                btnGrantMic.text = getString(R.string.permission_granted)
                btnGrantMic.isEnabled = false
            }
            
            // Overlay permission
            if (hasOverlayPermission) {
                cardOverlayPermission.setCardBackgroundColor(getColor(R.color.permission_granted))
                btnGrantOverlay.text = getString(R.string.permission_granted)
                btnGrantOverlay.isEnabled = false
            }
            
            // Accessibility permission
            if (hasAccessibilityPermission) {
                cardAccessibilityPermission.setCardBackgroundColor(getColor(R.color.permission_granted))
                btnGrantAccessibility.text = getString(R.string.permission_granted)
                btnGrantAccessibility.isEnabled = false
            }
            
            // Notification permission
            if (hasNotificationPermission) {
                cardNotificationPermission.setCardBackgroundColor(getColor(R.color.permission_granted))
                btnGrantNotifications.text = getString(R.string.permission_granted)
                btnGrantNotifications.isEnabled = false
            }
            
            // Update main button state
            updateServiceButton()
        }
    }

    /**
     * Check accessibility service enabled
     */
    private fun checkAccessibilityPermission() {
        val accessibilityEnabled = try {
            val service = packageName + "/" + DiLinkAccessibilityService::class.java.canonicalName
            val enabledServices = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(service) == true
        } catch (e: Exception) {
            false
        }
        
        hasAccessibilityPermission = accessibilityEnabled
    }

    /**
     * Check and request battery optimization exemption
     */
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            
            if (!isIgnoring && sharedPreferences.getBoolean("prompted_battery", false).not()) {
                showBatteryOptimizationDialog()
            }
        }
    }

    /**
     * Request microphone permission
     */
    private fun requestMicPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            ))
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    /**
     * Request overlay permission
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    /**
     * Request accessibility permission
     */
    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        
        // Show guide dialog
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_guide_title)
            .setMessage(R.string.accessibility_guide_message)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    /**
     * Request notification permission (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    /**
     * Check models and start service
     */
    private fun checkModelsAndStart() {
        lifecycleScope.launch {
            val status = modelManager.checkModelsStatus()
            
            if (status.allModelsReady) {
                startBintiService()
            } else {
                showModelDownloadDialog()
            }
        }
    }

    /**
     * Show model download dialog
     */
    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_models_title)
            .setMessage(getString(R.string.download_models_message))
            .setPositiveButton(R.string.download_now) { _, _ ->
                startModelDownload()
            }
            .setNegativeButton(R.string.use_cloud_only) { _, _ ->
                // Use cloud-only mode
                sharedPreferences.edit().putBoolean("cloud_only_mode", true).apply()
                startBintiService()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Start model download
     */
    private fun startModelDownload() {
        lifecycleScope.launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.tvDownloadStatus.visibility = android.view.View.VISIBLE
            
            modelManager.downloadModels(
                onProgress = { progress, currentFile ->
                    runOnUiThread {
                        binding.progressBar.progress = progress
                        binding.tvDownloadStatus.text = getString(
                            R.string.downloading_file, currentFile, progress
                        )
                    }
                },
                onComplete = {
                    runOnUiThread {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.tvDownloadStatus.text = getString(R.string.download_complete)
                        startBintiService()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.tvDownloadStatus.text = getString(R.string.download_failed, error)
                    }
                }
            )
        }
    }

    /**
     * Start Binti voice service
     */
    private fun startBintiService() {
        val intent = Intent(this, BintiService::class.java).apply {
            action = BintiService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        updateServiceButton()
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
    }

    /**
     * Stop Binti voice service
     */
    private fun stopBintiService() {
        val intent = Intent(this, BintiService::class.java).apply {
            action = BintiService.ACTION_STOP
        }
        startService(intent)
        
        updateServiceButton()
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
    }

    /**
     * Update service start/stop button state
     */
    private fun updateServiceButton() {
        binding.btnStartService.text = if (BintiService.isServiceRunning()) {
            getString(R.string.stop_service)
        } else {
            getString(R.string.start_service)
        }
    }

    /**
     * Update service state display
     */
    private fun updateServiceState(state: String) {
        binding.tvServiceState.text = when (state) {
            "ready" -> getString(R.string.state_ready)
            "listening" -> getString(R.string.state_listening)
            "processing" -> getString(R.string.state_processing)
            "error" -> getString(R.string.state_error)
            else -> getString(R.string.state_unknown)
        }
    }

    /**
     * Show command execution result
     */
    private fun showCommandResult(result: String) {
        val parts = result.split(":")
        if (parts.size == 2) {
            val action = parts[0]
            val success = parts[1].toBoolean()
            
            val message = if (success) {
                getString(R.string.command_success, action)
            } else {
                getString(R.string.command_failed_generic)
            }
            
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show permission required dialog
     */
    private fun showPermissionRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_required_title)
            .setMessage(R.string.permissions_required_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Show battery optimization dialog
     */
    private fun showBatteryOptimizationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.allow) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.skip, null)
            .show()
        
        sharedPreferences.edit().putBoolean("prompted_battery", true).apply()
    }

    /**
     * Register broadcast receivers
     */
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BintiService.BROADCAST_STATE_CHANGED)
            addAction(BintiService.BROADCAST_COMMAND_RESULT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }
    
    private val sharedPreferences by lazy {
        getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
    }
}
