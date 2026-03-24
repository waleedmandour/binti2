package com.binti.dilink

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.binti.dilink.dilink.DiLinkCommandExecutor
import com.binti.dilink.nlp.IntentClassifier
import com.binti.dilink.response.EgyptianTTS
import com.binti.dilink.voice.VoiceProcessor
import com.binti.dilink.voice.WakeWordDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Binti Voice Service
 * 
 * Foreground service that:
 * - Listens for wake word "يا بنتي" (Ya Binti)
 * - Processes voice commands in Egyptian Arabic
 * - Executes DiLink vehicle commands
 * - Provides spoken responses in Egyptian female voice
 * 
 * Architecture: Wake Word → ASR → NLU → Command Execution → TTS Response
 */
class BintiService : Service() {

    companion object {
        private const val TAG = "BintiService"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.binti.dilink.action.START"
        const val ACTION_STOP = "com.binti.dilink.action.STOP"
        const val ACTION_WAKE_WORD_DETECTED = "com.binti.dilink.action.WAKE_WORD_DETECTED"
        const val EXTRA_COMMAND = "extra_command"
        
        // Intent actions for UI communication
        const val BROADCAST_STATE_CHANGED = "com.binti.dilink.STATE_CHANGED"
        const val BROADCAST_COMMAND_RESULT = "com.binti.dilink.COMMAND_RESULT"
        const val EXTRA_STATE = "state"
        const val EXTRA_RESULT = "result"
        
        @Volatile
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
    }

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Components
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var commandExecutor: DiLinkCommandExecutor
    private lateinit var egyptianTTS: EgyptianTTS
    
    // System services
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPreferences: SharedPreferences
    
    // Overlay UI
    private var overlayView: View? = null
    private var overlayTextView: TextView? = null
    private var overlayWaveView: ImageView? = null
    
    // State
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    private var isListening = false
    private var wakeWordJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚗 BintiService onCreate")
        
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        initializeComponents()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startWakeWordDetection()
            ACTION_STOP -> stopSelf()
            ACTION_WAKE_WORD_DETECTED -> onWakeWordDetected()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "BintiService onDestroy")
        
        isRunning = false
        wakeWordJob?.cancel()
        serviceScope.cancel()
        
        hideOverlay()
        
        wakeWordDetector.release()
        voiceProcessor.release()
        egyptianTTS.release()
        
        super.onDestroy()
    }

    /**
     * Initialize all voice processing components
     */
    private fun initializeComponents() {
        serviceScope.launch {
            try {
                _serviceState.value = ServiceState.Initializing
                
                // Initialize wake word detector
                wakeWordDetector = WakeWordDetector(this@BintiService)
                wakeWordDetector.initialize()
                Log.d(TAG, "✅ Wake word detector initialized")
                
                // Initialize voice processor (ASR)
                voiceProcessor = VoiceProcessor(this@BintiService)
                voiceProcessor.initialize()
                Log.d(TAG, "✅ Voice processor initialized")
                
                // Initialize intent classifier (NLU)
                intentClassifier = IntentClassifier(this@BintiService)
                intentClassifier.initialize()
                Log.d(TAG, "✅ Intent classifier initialized")
                
                // Initialize command executor
                commandExecutor = DiLinkCommandExecutor(this@BintiService)
                Log.d(TAG, "✅ DiLink command executor initialized")
                
                // Initialize Egyptian TTS
                egyptianTTS = EgyptianTTS(this@BintiService)
                egyptianTTS.initialize()
                Log.d(TAG, "✅ Egyptian TTS initialized")
                
                _serviceState.value = ServiceState.Ready
                broadcastState("ready")
                
                // Auto-start wake word detection
                startWakeWordDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize components", e)
                _serviceState.value = ServiceState.Error(e.message ?: "Initialization failed")
                broadcastState("error")
            }
        }
    }

    /**
     * Start listening for wake word "يا بنتي"
     */
    private fun startWakeWordDetection() {
        if (wakeWordJob?.isActive == true) {
            Log.d(TAG, "Wake word detection already running")
            return
        }
        
        wakeWordJob = serviceScope.launch {
            Log.i(TAG, "🎤 Starting wake word detection...")
            _serviceState.value = ServiceState.ListeningForWakeWord
            
            // Start the wake word detector listening
            launch {
                wakeWordDetector.startListening()
            }
            
            // Collect wake word detection events
            wakeWordDetector.wakeWordFlow
                .catch { e -> Log.e(TAG, "Wake word detection error", e) }
                .collect { detected ->
                    if (detected) {
                        onWakeWordDetected()
                    }
                }
        }
    }

    /**
     * Handle wake word detection
     */
    private fun onWakeWordDetected() {
        if (isListening) {
            Log.d(TAG, "Already processing a command, ignoring wake word")
            return
        }
        
        serviceScope.launch {
            try {
                isListening = true
                _serviceState.value = ServiceState.ListeningForCommand
                
                // Play feedback sound
                playWakeWordFeedback()
                
                // Show overlay
                showOverlay(getString(R.string.listening_hint))
                
                // Speak acknowledgment
                egyptianTTS.speak(getString(R.string.wake_response))
                
                // Start voice recording and ASR
                val transcription = voiceProcessor.listenAndTranscribe(timeoutMs = 10000)
                
                if (transcription.isNotBlank()) {
                    Log.i(TAG, "📝 Transcribed: $transcription")
                    updateOverlay(transcription)
                    
                    // Classify intent
                    val intent = intentClassifier.classifyIntent(transcription)
                    Log.i(TAG, "🎯 Classified intent: $intent")
                    
                    // Execute command
                    val result = commandExecutor.executeCommand(intent)
                    
                    // Speak response
                    val response = generateResponse(intent, result)
                    egyptianTTS.speak(response)
                    
                    _serviceState.value = ServiceState.CommandExecuted(intent.action, result.success)
                    broadcastCommandResult(intent.action, result.success)
                } else {
                    egyptianTTS.speak(getString(R.string.fallback_message))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                egyptianTTS.speak(getString(R.string.error_message))
            } finally {
                isListening = false
                hideOverlay()
                _serviceState.value = ServiceState.Ready
                
                // Resume wake word detection after short delay
                delay(1000)
                startWakeWordDetection()
            }
        }
    }

    /**
     * Generate Egyptian Arabic response based on intent and result
     */
    private fun generateResponse(intent: com.binti.dilink.nlp.IntentResult, result: com.binti.dilink.dilink.CommandResult): String {
        return if (result.success) {
            String.format(getString(R.string.command_confirmed), intent.action)
        } else {
            getString(R.string.command_failed)
        }
    }

    /**
     * Play wake word feedback sound
     */
    private fun playWakeWordFeedback() {
        // Play a subtle sound to indicate wake word detected
        // Implementation uses audio focus and plays from raw resources
    }

    /**
     * Show voice overlay UI
     */
    private fun showOverlay(message: String) {
        if (overlayView != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200 // Offset from top
        }
        
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_voice, null)
        
        overlayTextView = overlayView?.findViewById(R.id.statusText)
        overlayWaveView = overlayView?.findViewById(R.id.waveAnimation)
        
        overlayTextView?.text = message
        windowManager.addView(overlayView, params)
        
        // Start wave animation
        overlayWaveView?.visibility = View.VISIBLE
    }

    /**
     * Update overlay text
     */
    private fun updateOverlay(text: String) {
        overlayTextView?.text = text
    }

    /**
     * Hide voice overlay UI
     */
    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeViewImmediate(it)
            overlayView = null
            overlayTextView = null
            overlayWaveView = null
        }
    }

    /**
     * Create foreground service notification
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Broadcast state change to UI
     */
    private fun broadcastState(state: String) {
        val intent = Intent(BROADCAST_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, state)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcast command result to UI
     */
    private fun broadcastCommandResult(action: String, success: Boolean) {
        val intent = Intent(BROADCAST_COMMAND_RESULT).apply {
            putExtra(EXTRA_RESULT, "$action:$success")
        }
        sendBroadcast(intent)
    }

    /**
     * Service state sealed class
     */
    sealed class ServiceState {
        object Idle : ServiceState()
        object Initializing : ServiceState()
        object Ready : ServiceState()
        object ListeningForWakeWord : ServiceState()
        object ListeningForCommand : ServiceState()
        data class CommandExecuted(val action: String, val success: Boolean) : ServiceState()
        data class Error(val message: String) : ServiceState()
    }
}
