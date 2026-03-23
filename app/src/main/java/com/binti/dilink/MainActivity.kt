package com.binti.dilink

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.databinding.ActivityMainBinding
import com.binti.dilink.utils.ModelDownloadManager
import com.binti.dilink.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MainActivity - Setup Wizard and Main Entry Point
 * 
 * Handles:
 * - Permission requests (Microphone, Overlay, Accessibility)
 * - Initial model download flow
 * - Service startup
 * - Settings access
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modelDownloadManager: ModelDownloadManager

    // Permission request launchers
    private val requestMicrophonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.i("Microphone permission granted")
            checkAndRequestOverlayPermission()
        } else {
            showPermissionRationale(
                getString(R.string.permission_microphone_required),
                ::requestMicrophonePermissionAgain
            )
        }
    }

    private val requestOverlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Settings.canDrawOverlays(this)) {
            Timber.i("Overlay permission granted")
            checkAndRequestAccessibilityPermission()
        } else {
            showPermissionRationale(
                getString(R.string.permission_overlay_required),
                ::requestOverlayPermissionAgain
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelDownloadManager = ModelDownloadManager(this)

        setupUI()
        checkPermissionsAndStartSetup()
    }

    private fun setupUI() {
        // Setup welcome text with Egyptian Arabic
        binding.welcomeText.text = getString(R.string.welcome_message)
        
        // Setup buttons
        binding.btnStartSetup.setOnClickListener {
            startSetupWizard()
        }

        binding.btnSettings.setOnClickListener {
            // Open settings
        }

        // Observe download progress
        lifecycleScope.launch {
            modelDownloadManager.downloadProgress.collect { progress ->
                updateDownloadProgress(progress)
            }
        }

        // Observe setup state
        lifecycleScope.launch {
            viewModel.setupState.collect { state ->
                updateSetupState(state)
            }
        }
    }

    /**
     * Check existing permissions and determine setup flow
     */
    private fun checkPermissionsAndStartSetup() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlayPermission = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        when {
            !hasMicPermission -> {
                showPermissionRequestCard(
                    getString(R.string.permission_microphone_title),
                    getString(R.string.permission_microphone_desc)
                )
            }
            !hasOverlayPermission -> {
                showPermissionRequestCard(
                    getString(R.string.permission_overlay_title),
                    getString(R.string.permission_overlay_desc)
                )
            }
            !hasAccessibility -> {
                showPermissionRequestCard(
                    getString(R.string.permission_accessibility_title),
                    getString(R.string.permission_accessibility_desc)
                )
            }
            !PreferenceManager.isModelsDownloaded() -> {
                showModelDownloadCard()
            }
            else -> {
                // All permissions granted and models downloaded
                showReadyState()
                startBintiService()
            }
        }
    }

    private fun startSetupWizard() {
        requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermission.launch(intent)
        } else {
            checkAndRequestAccessibilityPermission()
        }
    }

    private fun checkAndRequestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilitySetupDialog()
        } else {
            checkModelsAndStart()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val serviceName = "$packageName/${DiLinkAccessibilityService::class.java.canonicalName}"
        return enabledServices.contains(serviceName)
    }

    private fun showAccessibilitySetupDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_setup_title)
            .setMessage(R.string.accessibility_setup_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(R.string.skip) { dialog, _ ->
                dialog.dismiss()
                checkModelsAndStart()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkModelsAndStart() {
        if (!PreferenceManager.isModelsDownloaded()) {
            showModelDownloadDialog()
        } else {
            showReadyState()
            startBintiService()
        }
    }

    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_models_title)
            .setMessage(R.string.download_models_message)
            .setPositiveButton(R.string.download) { _, _ ->
                startModelDownload()
            }
            .setNegativeButton(R.string.later) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun startModelDownload() {
        binding.downloadProgressCard.visibility = android.view.View.VISIBLE
        binding.permissionRequestCard.visibility = android.view.View.GONE

        lifecycleScope.launch {
            val result = modelDownloadManager.downloadRequiredModels()
            result.fold(
                onSuccess = {
                    PreferenceManager.setModelsDownloaded(true)
                    showReadyState()
                    startBintiService()
                },
                onFailure = { error ->
                    Timber.e(error, "Model download failed")
                    showDownloadError(error.message ?: "Unknown error")
                }
            )
        }
    }

    private fun updateDownloadProgress(progress: ModelDownloadManager.DownloadProgress) {
        binding.downloadProgressBar.progress = progress.percentage
        binding.downloadProgressText.text = getString(
            R.string.download_progress,
            progress.percentage,
            formatFileSize(progress.downloadedBytes),
            formatFileSize(progress.totalBytes)
        )
    }

    private fun updateSetupState(state: MainViewModel.SetupState) {
        when (state) {
            is MainViewModel.SetupState.Ready -> {
                showReadyState()
            }
            is MainViewModel.SetupState.Error -> {
                showSetupError(state.message)
            }
            else -> {}
        }
    }

    private fun showPermissionRequestCard(title: String, description: String) {
        binding.permissionRequestCard.visibility = android.view.View.VISIBLE
        binding.permissionTitle.text = title
        binding.permissionDescription.text = description
        binding.downloadProgressCard.visibility = android.view.View.GONE
        binding.readyStateCard.visibility = android.view.View.GONE
    }

    private fun showModelDownloadCard() {
        binding.downloadProgressCard.visibility = android.view.View.VISIBLE
        binding.permissionRequestCard.visibility = android.view.View.GONE
        binding.readyStateCard.visibility = android.view.View.GONE
    }

    private fun showReadyState() {
        binding.readyStateCard.visibility = android.view.View.VISIBLE
        binding.permissionRequestCard.visibility = android.view.View.GONE
        binding.downloadProgressCard.visibility = android.view.View.GONE
        
        binding.statusText.text = getString(R.string.status_ready)
    }

    private fun showPermissionRationale(message: String, retryAction: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_required)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ -> retryAction() }
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showDownloadError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_error)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ -> startModelDownload() }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSetupError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setup_error)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ -> checkPermissionsAndStartSetup() }
            .show()
    }

    private fun startBintiService() {
        val serviceIntent = Intent(this, BintiService::class.java).apply {
            action = BintiService.ACTION_START_LISTENING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        Timber.i("Binti service started")
    }

    private fun requestMicrophonePermissionAgain() {
        requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestOverlayPermissionAgain() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        requestOverlayPermission.launch(intent)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        }
    }
}
