package com.binti.dilink

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.voice.WakeWordDetector
import com.binti.dilink.voice.VoiceProcessor
import com.binti.dilink.nlp.IntentClassifier
import com.binti.dilink.dilink.DiLinkCommandExecutor
import com.binti.dilink.response.EgyptianTTS
import com.binti.dilink.utils.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BintiService - Foreground Service for Voice Assistant
 * 
 * Core service that manages:
 * - Wake word detection ("يا بنتي")
 * - Voice activity detection
 * - Audio processing pipeline
 * - Command execution
 * - Response generation
 */
class BintiService : LifecycleService() {

    companion object {
        const val ACTION_START_LISTENING = "com.binti.dilink.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.binti.dilink.action.STOP_LISTENING"
        const val ACTION_EXECUTE_COMMAND = "com.binti.dilink.action.EXECUTE_COMMAND"
        const val ACTION_WAKE_WORD_DETECTED = "com.binti.dilink.action.WAKE_WORD_DETECTED"
        
        const val EXTRA_COMMAND_TEXT = "command_text"
        const val EXTRA_COMMAND_INTENT = "command_intent"
        
        const val NOTIFICATION_ID = 1001
        
        // Audio configuration
        const val SAMPLE_RATE = 16000
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val BUFFER_SIZE_FACTOR = 2
        
        @Volatile
        private var isServiceRunning = false
        
        fun isRunning(): Boolean = isServiceRunning
    }

    private val binder = LocalBinder()
    
    // Service state
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _currentState = MutableStateFlow<State>(State.Idle)
    val currentState: StateFlow<State> = _currentState.asStateFlow()
    
    // Components
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var commandExecutor: DiLinkCommandExecutor
    private lateinit var tts: EgyptianTTS
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    
    // Power management
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Service states
    sealed class State {
        object Idle : State()
        object ListeningForWakeWord : State()
        object ProcessingVoice : State()
        object ExecutingCommand : State()
        object Speaking : State()
        data class Error(val message: String) : State()
    }

    inner class LocalBinder : Binder() {
        fun getService(): BintiService = this@BintiService
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        createNotificationChannel()
        acquireWakeLock()
        isServiceRunning = true
        Timber.i("BintiService created")
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createServiceNotification())
        
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_EXECUTE_COMMAND -> executeVoiceCommand(intent)
            ACTION_WAKE_WORD_DETECTED -> onWakeWordDetected()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        releaseWakeLock()
        isServiceRunning = false
        Timber.i("BintiService destroyed")
    }

    /**
     * Initialize all voice assistant components
     */
    private fun initializeComponents() {
        try {
            // Initialize wake word detector
            wakeWordDetector = WakeWordDetector(this)
            wakeWordDetector.loadModel()
            
            // Initialize voice processor (ASR + preprocessing)
            voiceProcessor = VoiceProcessor(this)
            
            // Initialize intent classifier (EgyBERT)
            intentClassifier = IntentClassifier(this)
            intentClassifier.loadModel()
            
            // Initialize DiLink command executor
            commandExecutor = DiLinkCommandExecutor(this)
            
            // Initialize TTS
            tts = EgyptianTTS(this)
            tts.initialize()
            
            Timber.i("All components initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize components")
            _currentState.value = State.Error("فشل تحميل المكونات: ${e.message}")
        }
    }

    /**
     * Start listening for wake word
     */
    private fun startListening() {
        if (isRecording.get()) {
            Timber.w("Already recording, ignoring start request")
            return
        }
        
        if (!PreferenceManager.isWakeWordEnabled()) {
            Timber.d("Wake word disabled in preferences")
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                isRecording.set(true)
                _isListening.value = true
                _currentState.value = State.ListeningForWakeWord
                
                // Initialize audio record
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
                ) * BUFFER_SIZE_FACTOR
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                ).apply {
                    startRecording()
                }
                
                Timber.i("Started listening for wake word")
                
                // Start recording loop
                val buffer = ShortArray(bufferSize / 2)
                
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (read > 0) {
                        // Process audio chunk for wake word detection
                        val audioChunk = buffer.copyOf(read)
                        val wakeWordDetected = wakeWordDetector.processAudioChunk(audioChunk)
                        
                        if (wakeWordDetected) {
                            onWakeWordDetected()
                        }
                    }
                    
                    // Small delay to prevent CPU overload
                    delay(10)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in recording loop")
                _currentState.value = State.Error("خطأ في التسجيل: ${e.message}")
            }
        }
    }

    /**
     * Stop listening for wake word
     */
    private fun stopListening() {
        isRecording.set(false)
        _isListening.value = false
        _currentState.value = State.Idle
        
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        Timber.i("Stopped listening")
    }

    /**
     * Handle wake word detection
     */
    private fun onWakeWordDetected() {
        Timber.i("Wake word 'يا بنتي' detected!")
        
        lifecycleScope.launch {
            try {
                _currentState.value = State.ProcessingVoice
                
                // Play wake sound / haptic feedback
                provideWakeFeedback()
                
                // Speak greeting
                val greeting = getEgyptianGreeting()
                speak(greeting)
                
                // Record voice command
                val voiceCommand = recordVoiceCommand()
                
                if (voiceCommand.isNotEmpty()) {
                    processVoiceCommand(voiceCommand)
                }
                
                // Return to listening state
                _currentState.value = State.ListeningForWakeWord
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing wake word")
                _currentState.value = State.Error("خطأ: ${e.message}")
                delay(2000)
                _currentState.value = State.ListeningForWakeWord
            }
        }
    }

    /**
     * Record voice command after wake word
     */
    private suspend fun recordVoiceCommand(): String {
        return withContext(Dispatchers.IO) {
            // Record for up to 5 seconds with VAD
            voiceProcessor.recordCommand(maxDurationMs = 5000)
        }
    }

    /**
     * Process voice command through NLP pipeline
     */
    private suspend fun processVoiceCommand(audioData: ShortArray) {
        _currentState.value = State.ProcessingVoice
        
        // Convert audio to text (ASR)
        val transcription = voiceProcessor.transcribe(audioData)
        Timber.i("Transcription: $transcription")
        
        if (transcription.isEmpty()) {
            speak("معرفتش أسمع كويس، ممكن تعيد؟")
            return
        }
        
        // Classify intent
        val intent = intentClassifier.classify(transcription)
        Timber.i("Classified intent: $intent")
        
        // Execute command
        _currentState.value = State.ExecutingCommand
        val result = commandExecutor.execute(intent)
        
        // Generate and speak response
        val response = generateResponse(intent, result)
        speak(response)
    }

    /**
     * Execute voice command from intent
     */
    private fun executeVoiceCommand(intent: Intent) {
        val commandText = intent.getStringExtra(EXTRA_COMMAND_TEXT)
        val commandIntent = intent.getStringExtra(EXTRA_COMMAND_INTENT)
        
        lifecycleScope.launch {
            if (commandText != null) {
                // Process as text command
                val classifiedIntent = intentClassifier.classify(commandText)
                val result = commandExecutor.execute(classifiedIntent)
                val response = generateResponse(classifiedIntent, result)
                speak(response)
            }
        }
    }

    /**
     * Speak text using Egyptian TTS
     */
    private suspend fun speak(text: String) {
        _currentState.value = State.Speaking
        tts.speak(text)
        delay(text.length * 50L) // Approximate speech duration
        _currentState.value = State.Idle
    }

    /**
     * Generate Egyptian Arabic response based on intent and result
     */
    private fun generateResponse(intent: com.binti.dilink.nlp.Intent, result: com.binti.dilink.dilink.CommandResult): String {
        return when {
            !result.success -> "معلش، حصل خطأ. جرب تاني؟"
            intent.action == "NAVIGATE" -> "تمام! رايحين ${intent.parameters["destination"] ?: "المكان اللي طلبته"} 🗺️"
            intent.action == "CLIMATE_ON" -> "شغلت التكييف يا حبيبي! 🌬️"
            intent.action == "CLIMATE_OFF" -> "قفلت التكييف تمام"
            intent.action == "CLIMATE_TEMP" -> "ضبطت الحرارة على ${intent.parameters["temperature"]} درجة"
            intent.action == "MUSIC_PLAY" -> "أوكيه، شغلت المزيكا 🎵"
            intent.action == "MUSIC_PAUSE" -> "وقفت المزيكا"
            intent.action == "VOLUME_UP" -> "رفعت الصوت"
            intent.action == "VOLUME_DOWN" -> "خفضت الصوت"
            else -> "تمام، نفذت الأمر! ✅"
        }
    }

    /**
     * Get random Egyptian greeting
     */
    private fun getEgyptianGreeting(): String {
        val greetings = listOf(
            "أهلاً وسهلاً يا حبيبي! أقدر أساعدك إزاي؟",
            "نورتني يا باشا! قولي عايز إيه؟",
            "يا هلا! أنا هنا، قولي اللي في بالك!",
            "أهلاً يا نور العين! أسمعك!"
        )
        return greetings.random()
    }

    /**
     * Provide haptic feedback on wake word detection
     */
    private fun provideWakeFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    100, 
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    /**
     * Create notification channel for service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BintiApplication.NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_service_description)
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground service notification
     */
    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_SERVICE)
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
     * Update notification with current state
     */
    private fun updateNotification(state: State) {
        val text = when (state) {
            is State.Idle -> getString(R.string.notification_idle)
            is State.ListeningForWakeWord -> getString(R.string.notification_listening)
            is State.ProcessingVoice -> getString(R.string.notification_processing)
            is State.ExecutingCommand -> getString(R.string.notification_executing)
            is State.Speaking -> getString(R.string.notification_speaking)
            is State.Error -> getString(R.string.notification_error)
        }

        val notification = NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Acquire partial wake lock for continuous listening
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Binti::VoiceListening"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}
