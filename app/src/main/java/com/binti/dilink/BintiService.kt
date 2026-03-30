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
import com.binti.dilink.nlp.IntentResult
import com.binti.dilink.response.EgyptianTTS
import com.binti.dilink.voice.VoiceProcessor
import com.binti.dilink.voice.WakeWordDetectorVosk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Calendar

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
        const val ACTION_START_LISTENING = "com.binti.dilink.action.START_LISTENING"
        const val ACTION_EXECUTE_COMMAND = "com.binti.dilink.action.EXECUTE_COMMAND"
        
        // Intent actions for UI communication
        const val BROADCAST_STATE_CHANGED = "com.binti.dilink.STATE_CHANGED"
        const val BROADCAST_COMMAND_RESULT = "com.binti.dilink.COMMAND_RESULT"
        const val EXTRA_STATE = "state"
        const val EXTRA_RESULT = "result"
        
        // Preferences keys
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_TONE_FORMAL = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
        
        @Volatile
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
    }

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Components
    private lateinit var wakeWordDetector: WakeWordDetectorVosk
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
    private var lastGreetingTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚗 BintiService onCreate")
        
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
        
        initializeComponents()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                startWakeWordDetection()
                checkProactiveGreeting()
            }
            ACTION_STOP -> stopSelf()
            ACTION_START_LISTENING -> onWakeWordDetected()
            ACTION_EXECUTE_COMMAND -> {
                val commandJson = intent.getStringExtra("command")
                if (commandJson != null) executeDirectCommand(commandJson)
            }
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
        super.onDestroy()
    }

    private fun initializeComponents() {
        serviceScope.launch {
            try {
                _serviceState.value = ServiceState.Initializing
                
                wakeWordDetector = WakeWordDetectorVosk(this@BintiService)
                wakeWordDetector.initialize()
                
                voiceProcessor = VoiceProcessor(this@BintiService)
                voiceProcessor.initialize()
                
                intentClassifier = IntentClassifier(this@BintiService)
                intentClassifier.initialize()
                
                commandExecutor = DiLinkCommandExecutor(this@BintiService)
                
                egyptianTTS = EgyptianTTS(this@BintiService)
                egyptianTTS.initialize()
                
                _serviceState.value = ServiceState.Ready
                broadcastState("ready")
                startWakeWordDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Initialization failed", e)
                _serviceState.value = ServiceState.Error(e.message ?: "Init error")
            }
        }
    }

    private fun checkProactiveGreeting() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGreetingTime > 3600000) { // Once per hour
            serviceScope.launch {
                val greeting = getContextualGreeting()
                egyptianTTS.speak(greeting)
                lastGreetingTime = currentTime
            }
        }
    }

    private fun getContextualGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val userName = sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
        val isFormal = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)
        
        val greeting = when {
            hour in 5..11 -> if (isFormal) "صباح الخير يا فندم" else "يا صباح الفل"
            hour in 12..16 -> if (isFormal) "مساء الخير يا فندم" else "يا مساء القشطة"
            else -> if (isFormal) "مساء الخير يا فندم" else "يا مساء الجمال"
        }
        
        return if (userName.isNotEmpty()) "$greeting يا $userName" else greeting
    }

    private fun startWakeWordDetection() {
        if (wakeWordJob?.isActive == true) return
        
        wakeWordJob = serviceScope.launch {
            _serviceState.value = ServiceState.ListeningForWakeWord
            wakeWordDetector.startListening()
            wakeWordDetector.wakeWordFlow.collect { detected ->
                if (detected) onWakeWordDetected()
            }
        }
    }

    private fun onWakeWordDetected() {
        if (isListening) return
        
        serviceScope.launch {
            try {
                isListening = true
                _serviceState.value = ServiceState.ListeningForCommand
                broadcastState("listening")
                showOverlay(getString(R.string.listening_hint))
                
                val isFormal = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)
                val wakeResponse = if (isFormal) getString(R.string.wake_response_formal) else getString(R.string.wake_response)
                egyptianTTS.speak(wakeResponse)
                
                val transcription = voiceProcessor.listenAndTranscribe(timeoutMs = 10000)
                if (transcription.isNotBlank()) {
                    updateOverlay(transcription)
                    broadcastState("processing")
                    val result = intentClassifier.classifyIntent(transcription)
                    val executionResult = commandExecutor.executeCommand(result)
                    
                    val response = if (executionResult.success) {
                        result.matchedPatternResponse.takeIf { it.isNotBlank() } ?: getString(R.string.command_success)
                    } else getString(R.string.command_failed)
                    
                    egyptianTTS.speak(response)
                } else {
                    egyptianTTS.speak(getString(R.string.fallback_message))
                }
                
            } finally {
                isListening = false
                hideOverlay()
                _serviceState.value = ServiceState.Ready
                broadcastState("ready")
                delay(1000)
                // restartWakeWordDetection() is not needed if wakeWordFlow is handled correctly
            }
        }
    }

    private fun executeDirectCommand(commandJson: String) {
        serviceScope.launch {
            try {
                val json = JSONObject(commandJson)
                val actionStr = json.optString("action")
                val patternStr = json.optString("pattern")
                
                val mockResult = IntentResult(
                    action = actionStr,
                    entities = emptyMap(),
                    confidence = 1.0f,
                    originalText = patternStr
                )
                
                val executionResult = commandExecutor.executeCommand(mockResult)
                if (executionResult.success) {
                    egyptianTTS.speak(getString(R.string.command_confirmed, patternStr.ifBlank { actionStr }))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute direct command", e)
            }
        }
    }

    private fun showOverlay(message: String) {
        if (overlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 
        }
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_voice, null)
        overlayTextView = overlayView?.findViewById(R.id.statusText)
        overlayTextView?.text = message
        windowManager.addView(overlayView, params)
    }

    private fun updateOverlay(text: String) { overlayTextView?.text = text }

    private fun hideOverlay() {
        overlayView?.let { try { windowManager.removeViewImmediate(it) } catch (e: Exception) {}; overlayView = null }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun broadcastState(state: String) {
        sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply { putExtra(EXTRA_STATE, state) })
    }

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
