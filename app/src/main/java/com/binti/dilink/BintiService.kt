

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
import android.os.SystemClock
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
import com.binti.dilink.voice.MicRetryPolicy
import com.binti.dilink.voice.VoiceProcessor
import com.binti.dilink.voice.WakeWordDetectorVosk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Calendar

class BintiService : Service() {

    companion object {
        private const val TAG = "BintiService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START           = "com.binti.dilink.action.START"
        const val ACTION_STOP            = "com.binti.dilink.action.STOP"
        const val ACTION_WAKE_WORD_DETECTED = "com.binti.dilink.action.WAKE_WORD_DETECTED"
        const val ACTION_START_LISTENING = "com.binti.dilink.action.START_LISTENING"
        const val ACTION_EXECUTE_COMMAND = "com.binti.dilink.action.EXECUTE_COMMAND"

        const val BROADCAST_STATE_CHANGED = "com.binti.dilink.STATE_CHANGED"
        const val BROADCAST_COMMAND_RESULT = "com.binti.dilink.COMMAND_RESULT"
        const val EXTRA_STATE  = "state"
        const val EXTRA_RESULT = "result"

        private const val KEY_USER_NAME        = "user_name"
        private const val KEY_TONE_FORMAL      = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"

        /**
         * v3.0.2 FIX (boot auto-start never fired): the flag BootReceiver reads
         * — now actually persisted by MainActivity.startBintiService() /
         * stopBintiService() via [MainActivity.persistAutoStart]. Lives here so
         * both the service and the activity share ONE key name.
         */
        const val KEY_AUTO_START = "auto_start"

        private const val MIC_UNAVAILABLE_NOTIFICATION_ID = 1002

        // FIX #1 — use AtomicBoolean so cross-thread reads/writes are safe
        private val _isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        fun isServiceRunning(): Boolean = _isRunning.get()
    }

    // v3.0.2 FIX (cold-start crash hardening): without a handler, ANY uncaught
    // exception inside a serviceScope.launch{} tore the whole process down —
    // on a head unit that means Binti silently disappears until the user
    // manually restarts it. The handler logs the failure and surfaces it in
    // serviceState instead of crashing.
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "❌ Uncaught service coroutine failure", e)
        _serviceState.value = ServiceState.Error(e.message ?: "Unexpected error")
    }

    // FIX #2 — Main dispatcher for UI operations; Default for CPU work
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + serviceExceptionHandler)

    /**
     * v3.0.2 FIX (cold-start race): completed once every lateinit component
     * (wakeWordDetector / voiceProcessor / intentClassifier / commandExecutor /
     * egyptianTTS) is fully initialised. onStartCommand can fire BEFORE that
     * (startForegroundService → onCreate → onStartCommand while init still
     * runs on a background dispatcher), so any path that touches the lateinits
     * must first await this. CompletableDeferred gives the happens-before
     * guarantee that all assignments made before complete() are visible after
     * await() returns.
     */
    private val componentsReady = CompletableDeferred<Unit>()

    private lateinit var wakeWordDetector: WakeWordDetectorVosk
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var commandExecutor: DiLinkCommandExecutor
    private lateinit var egyptianTTS: EgyptianTTS

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPreferences: SharedPreferences

    private var overlayView: View? = null
    private var overlayTextView: TextView? = null
    private var overlayWaveView: ImageView? = null

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    // FIX #3 — use AtomicBoolean to guard onWakeWordDetected() across coroutines
    private val isListening = java.util.concurrent.atomic.AtomicBoolean(false)
    private var wakeWordJob: Job? = null
    private var lastGreetingTime: Long = 0

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚗 BintiService onCreate")

        _isRunning.set(true)
        windowManager     = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager      = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)

        // FIX #4 — startForeground BEFORE initializeComponents so the service
        // is promoted before Android can kill it for being background too long
        startForeground(NOTIFICATION_ID, createNotification())
        initializeComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startWakeWordDetection()
                checkProactiveGreeting()
            }
            ACTION_STOP  -> stopSelf()
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
        // FIX #5 — cancel scope before super so coroutines don't outlive the service
        wakeWordJob?.cancel()
        serviceScope.cancel()
        // P0 FIX: release the native Vosk recognizer + AudioRecord; cancelling
        // the scope alone leaves the mic handle open until GC.
        if (this::wakeWordDetector.isInitialized) {
            runCatching { wakeWordDetector.release() }
        }
        if (this::voiceProcessor.isInitialized) {
            runCatching { voiceProcessor.release() }
        }
        _isRunning.set(false)
        // v3.0.2 FIX: a lingering "mic unavailable" notification must not
        // outlive the service that posted it.
        cancelMicUnavailableNotification()
        // FIX #6 — hide overlay on main thread; windowManager calls must be on main
        hideOverlay()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────────

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

                com.binti.dilink.dilink.DiLinkAccessibilityService.getInstance()?.let { a11y ->
                    a11y.setCommandExecutor(commandExecutor)
                    Log.i(TAG, "✅ Executor connected to accessibility service")
                } ?: Log.w(TAG, "⚠ Accessibility service not running")

                egyptianTTS = EgyptianTTS(this@BintiService)
                egyptianTTS.initialize()

                _serviceState.value = ServiceState.Ready
                broadcastState("ready")

                // v3.0.2 FIX: publish readiness BEFORE arming the wake loop so
                // every lateinit assignment above is visible to any coroutine
                // waiting on componentsReady.
                componentsReady.complete(Unit)
                startWakeWordDetection()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Initialization failed", e)
                _serviceState.value = ServiceState.Error(e.message ?: "Init error")
                // FIX #7 — stop the service if init fails; otherwise it sits in a
                // broken state silently consuming resources. componentsReady is
                // deliberately left incomplete: every await() on it will be
                // cancelled by serviceScope.cancel() in onDestroy().
                stopSelf()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Proactive greeting
    // ──────────────────────────────────────────────────────────────────────────

    private fun checkProactiveGreeting() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGreetingTime > 3_600_000L) {
            serviceScope.launch {
                // FIX #8 — guard: don't greet if TTS/egyptianTTS not yet initialised
                if (!::egyptianTTS.isInitialized) return@launch
                egyptianTTS.speak(getContextualGreeting())
                lastGreetingTime = currentTime
            }
        }
    }

    private fun getContextualGreeting(): String {
        val hour     = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val userName = sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
        val isFormal = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)

        val greeting = when (hour) {
            in 5..11  -> if (isFormal) "صباح الخير يا فندم" else "يا صباح الفل"
            in 12..16 -> if (isFormal) "مساء الخير يا فندم" else "يا مساء القشطة"
            else      -> if (isFormal) "مساء الخير يا فندم" else "يا مساء الجمال"
        }

        return if (userName.isNotEmpty()) "$greeting يا $userName" else greeting
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Wake word loop
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * P0 FIX (wake-word drop): the previous code called startListening()
     * BEFORE collecting wakeWordFlow. startListening() is a blocking audio
     * loop that only returns when recording stops, so the collector below it
     * never subscribed while the loop ran — and wakeWordFlow is a
     * SharedFlow(replay = 0), meaning every tryEmit() with no subscriber is
     * dropped on the floor. The wake word could never fire.
     *
     * New structure:
     *  1. A collector is launched with CoroutineStart.UNDISPATCHED so the
     *     subscription exists before the audio loop emits anything.
     *  2. The blocking audio loop runs inside try/finally that cancels the
     *     collector.
     *  3. An outer while-loop re-arms detection after each command session,
     *     but waits while [isListening] is true so the command listener can
     *     own the microphone exclusively (two concurrent AudioRecord
     *     instances steal frames from each other on DiLink).
     */
    private fun startWakeWordDetection() {
        if (wakeWordJob?.isActive == true) return

        wakeWordJob = serviceScope.launch {
            // v3.0.2 FIX (cold-start crash): onStartCommand can deliver
            // ACTION_START while initializeComponents() is still loading the
            // Vosk model on a background dispatcher. The old code immediately
            // touched the uninitialized `wakeWordDetector` lateinit and the
            // UninitializedPropertyAccessException killed the whole process on
            // a cold start — exactly the path BootReceiver takes. Await the
            // readiness signal instead; it is only completed AFTER every
            // component is assigned, with a happens-before guarantee.
            componentsReady.await()

            // v3.0.2 FIX (dead mic): consecutive AudioRecord failures back off
            // exponentially instead of spinning every 300 ms, and the user is
            // told about the problem instead of it being invisible.
            var consecutiveMicFailures = 0

            while (isActive) {
                // Wait while a command session owns the microphone
                while (isListening.get() && isActive) delay(200)
                if (!isActive) break

                _serviceState.value = ServiceState.ListeningForWakeWord

                // Subscriber FIRST (UNDISPATCHED enters the block immediately
                // until first suspension), THEN the audio loop.
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    wakeWordDetector.wakeWordFlow.collect { detected ->
                        if (detected) onWakeWordDetected()
                    }
                }
                val sessionStart = SystemClock.elapsedRealtime()
                try {
                    wakeWordDetector.startListening()
                } finally {
                    collector.cancel()
                }
                val sessionMs = SystemClock.elapsedRealtime() - sessionStart

                // startListening() catches AudioRecord failures internally and
                // returns within milliseconds. A healthy session can only end
                // by handing the mic to a command session (isListening == true
                // while the wake loop is parked at the top of this loop). A
                // SHORT session with isListening still false therefore means
                // the mic could not be opened (dead / held / revoked).
                val micFailed = sessionMs < MicRetryPolicy.MIN_HEALTHY_SESSION_MS &&
                        !isListening.get()
                if (micFailed) {
                    consecutiveMicFailures++
                    val backoffMs = MicRetryPolicy.backoffMs(consecutiveMicFailures)
                    Log.w(
                        TAG,
                        "Mic unavailable (failure #$consecutiveMicFailures, " +
                            "session=${sessionMs}ms) — retrying in ${backoffMs}ms"
                    )
                    notifyMicUnavailable()
                    delay(backoffMs)
                } else {
                    if (consecutiveMicFailures > 0) {
                        consecutiveMicFailures = 0
                        // Mic recovered — remove the warning notification.
                        cancelMicUnavailableNotification()
                    }
                    delay(300) // brief pause before re-arming the mic
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        // FIX #3 — compareAndSet prevents two simultaneous wake events both
        // entering the command flow
        if (!isListening.compareAndSet(false, true)) return

        // P0 FIX: an external trigger (widget / binti://) can arrive before
        // initializeComponents() has finished — every field below is lateinit.
        val componentsReady = this::wakeWordDetector.isInitialized &&
                              this::voiceProcessor.isInitialized &&
                              this::commandExecutor.isInitialized &&
                              this::egyptianTTS.isInitialized
        if (!componentsReady) {
            Log.w(TAG, "Wake trigger ignored — components not initialised yet")
            isListening.set(false)
            return
        }

        serviceScope.launch {
            try {
                _serviceState.value = ServiceState.ListeningForCommand
                broadcastState("listening")

                // P0 FIX (mic ownership): release the wake-word AudioRecord
                // BEFORE the command listener opens its own, otherwise the two
                // recordings compete for mic frames. The supervised wake loop
                // in startWakeWordDetection() waits for isListening to clear
                // before re-arming.
                runCatching { wakeWordDetector.stopListening() }

                // FIX #6 — overlay must be touched on the main thread
                withContext(Dispatchers.Main) {
                    showOverlay(getString(R.string.listening_hint))
                }

                val isFormal     = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)
                val wakeResponse = if (isFormal) getString(R.string.wake_response_formal)
                                   else          getString(R.string.wake_response)
                egyptianTTS.speak(wakeResponse)

                val transcription = voiceProcessor.listenAndTranscribe(timeoutMs = 10_000)

                if (transcription.isNotBlank()) {
                    withContext(Dispatchers.Main) { updateOverlay(transcription) }
                    broadcastState("processing")

                    val intentResult    = intentClassifier.classifyIntent(transcription)
                    val executionResult = commandExecutor.executeCommand(intentResult)

                    // FIX #9 — always speak executionResult.message; it now carries
                    // honest success OR failure text from the fixed executor
                    egyptianTTS.speak(executionResult.message)

                    _serviceState.value = ServiceState.CommandExecuted(
                        intentResult.action, executionResult.success
                    )
                } else {
                    egyptianTTS.speak(getString(R.string.fallback_message))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Command flow error", e)
                egyptianTTS.speak(getString(R.string.command_failed))
            } finally {
                isListening.set(false)
                withContext(Dispatchers.Main) { hideOverlay() }
                _serviceState.value = ServiceState.Ready
                broadcastState("ready")
                // P0 FIX: the supervised wake loop re-arms itself once
                // isListening clears. This relaunch only matters when the
                // trigger came from outside (widget / binti://) and the wake
                // loop was never running at all.
                if (wakeWordJob?.isActive != true) startWakeWordDetection()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Direct command execution (from widget / intent)
    // ──────────────────────────────────────────────────────────────────────────

    private fun executeDirectCommand(commandJson: String) {
        serviceScope.launch {
            // v3.0.2 FIX: same cold-start race as onWakeWordDetected — a widget
            // / binti:// trigger can beat initializeComponents(). Waiting on
            // componentsReady is safe even if init ultimately fails: the
            // awaiting coroutine is cancelled together with the scope when the
            // service stops itself.
            componentsReady.await()
            try {
                val json      = JSONObject(commandJson)
                val actionStr = json.optString("action")
                val patternStr = json.optString("pattern")

                // FIX #11 — guard against empty action string
                if (actionStr.isBlank()) {
                    Log.w(TAG, "executeDirectCommand: empty action, ignoring")
                    return@launch
                }

                val mockResult = IntentResult(
                    action        = actionStr,
                    entities      = emptyMap(),
                    confidence    = 1.0f,
                    originalText  = patternStr
                )

                val executionResult = commandExecutor.executeCommand(mockResult)
                // FIX #9 — speak actual result message, not a generic confirmation
                egyptianTTS.speak(executionResult.message)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute direct command", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Overlay (must be called on Main thread)
    // ──────────────────────────────────────────────────────────────────────────

    private fun showOverlay(message: String) {
        if (overlayView != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        overlayView     = LayoutInflater.from(this).inflate(R.layout.overlay_voice, null)
        overlayTextView = overlayView?.findViewById(R.id.statusText)
        overlayTextView?.text = message

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            overlayView = null
        }
    }

    private fun updateOverlay(text: String) {
        overlayTextView?.text = text
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeViewImmediate(it) } catch (e: Exception) { /* ignore */ }
            overlayView     = null
            overlayTextView = null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification & broadcast
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
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
        try {
            sendBroadcast(Intent(BROADCAST_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, state)
                setPackage(packageName)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast state", e)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mic-unavailable notification (v3.0.2)
    // ────────────────────────────────────────────────────────────────────────

    @Volatile
    private var micNotificationActive = false

    /**
     * v3.0.2 FIX — make a dead microphone VISIBLE. Posted once when the wake
     * loop enters failure mode; stays until the mic recovers (or the service
     * dies). Silent so it never chirps in the cabin; the foreground-service
     * notification channel already exists.
     */
    private fun notifyMicUnavailable() {
        if (micNotificationActive) return
        micNotificationActive = true
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val contentIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val text = getString(R.string.mic_unavailable_text)
            val notification = NotificationCompat.Builder(this, BintiApplication.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.mic_unavailable_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
            manager.notify(MIC_UNAVAILABLE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post mic-unavailable notification", e)
        }
    }

    private fun cancelMicUnavailableNotification() {
        if (!micNotificationActive) return
        micNotificationActive = false
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(MIC_UNAVAILABLE_NOTIFICATION_ID)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────────────────

    sealed class ServiceState {
        object Idle                : ServiceState()
        object Initializing        : ServiceState()
        object Ready               : ServiceState()
        object ListeningForWakeWord: ServiceState()
        object ListeningForCommand : ServiceState()
        data class CommandExecuted(val action: String, val success: Boolean) : ServiceState()
        data class Error(val message: String) : ServiceState()
    }
}
