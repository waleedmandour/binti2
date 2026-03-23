package com.binti.dilink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binti.dilink.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MainViewModel - Handles setup state and service communication
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    sealed class SetupState {
        object Idle : SetupState()
        object CheckingPermissions : SetupState()
        object DownloadingModels : SetupState()
        object Ready : SetupState()
        data class Error(val message: String) : SetupState()
    }

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    init {
        checkSetupState()
    }

    private fun checkSetupState() {
        viewModelScope.launch {
            _setupState.value = SetupState.CheckingPermissions
            
            val modelsDownloaded = PreferenceManager.isModelsDownloaded()
            val wakeWordEnabled = PreferenceManager.isWakeWordEnabled()
            
            Timber.d("Setup state check - models: $modelsDownloaded, wakeWord: $wakeWordEnabled")
            
            _setupState.value = if (modelsDownloaded) {
                SetupState.Ready
            } else {
                SetupState.Idle
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun resetError() {
        _setupState.value = SetupState.Idle
    }
}
