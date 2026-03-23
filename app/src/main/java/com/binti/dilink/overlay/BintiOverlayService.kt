package com.binti.dilink.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.binti.dilink.R
import com.binti.dilink.databinding.OverlayVoiceCardBinding
import com.airbnb.lottie.LottieAnimationView
import timber.log.Timber

/**
 * BintiOverlayService - Floating overlay UI for voice assistant
 * 
 * Provides:
 * - Floating voice card with animations
 * - Wake word indicator
 * - Command status display
 * - Dragging and positioning
 */
class BintiOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.binti.dilink.overlay.SHOW"
        const val ACTION_HIDE_OVERLAY = "com.binti.dilink.overlay.HIDE"
        const val ACTION_UPDATE_STATE = "com.binti.dilink.overlay.UPDATE_STATE"
        
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        
        @Volatile
        private var isShowing = false
        
        fun isOverlayShowing(): Boolean = isShowing
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var binding: OverlayVoiceCardBinding
    
    // Overlay state
    private var currentState: OverlayState = OverlayState.HIDDEN
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    enum class OverlayState {
        HIDDEN,
        LISTENING,
        PROCESSING,
        SPEAKING,
        SUCCESS,
        ERROR
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Timber.i("Overlay service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_UPDATE_STATE -> {
                val stateName = intent.getStringExtra(EXTRA_STATE) ?: return START_STICKY
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                val state = try {
                    OverlayState.valueOf(stateName)
                } catch (e: Exception) {
                    OverlayState.LISTENING
                }
                updateState(state, message)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        Timber.i("Overlay service destroyed")
    }

    /**
     * Show the overlay
     */
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showOverlay() {
        if (overlayView != null) return
        
        val layoutParams = createLayoutParams()
        
        // Inflate overlay view
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_voice_card, null)
        binding = OverlayVoiceCardBinding.bind(overlayView!!)
        
        // Setup touch listener for dragging
        setupDragging()
        
        // Add to window
        try {
            windowManager.addView(overlayView, layoutParams)
            isShowing = true
            
            // Show with animation
            showWithAnimation()
            
            Timber.i("Overlay shown")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show overlay")
        }
    }

    /**
     * Hide the overlay
     */
    private fun hideOverlay() {
        overlayView?.let { view ->
            // Hide with animation
            hideWithAnimation {
                windowManager.removeView(view)
                overlayView = null
                isShowing = false
                Timber.i("Overlay hidden")
            }
        }
    }

    /**
     * Update overlay state
     */
    fun updateState(state: OverlayState, message: String? = null) {
        if (overlayView == null) return
        
        currentState = state
        
        // Update UI based on state
        when (state) {
            OverlayState.LISTENING -> showListeningState()
            OverlayState.PROCESSING -> showProcessingState()
            OverlayState.SPEAKING -> showSpeakingState()
            OverlayState.SUCCESS -> showSuccessState(message)
            OverlayState.ERROR -> showErrorState(message)
            OverlayState.HIDDEN -> hideOverlay()
        }
    }

    /**
     * Show listening state
     */
    private fun showListeningState() {
        binding.apply {
            // Show Lottie animation
            voiceAnimation.setAnimation(R.raw.voice_listening)
            voiceAnimation.playAnimation()
            
            // Update text
            statusText.text = context.getString(R.string.overlay_listening)
            
            // Update colors
            updateCardColor("#2196F3") // Blue
            
            // Show pulse animation
            showPulseAnimation()
        }
    }

    /**
     * Show processing state
     */
    private fun showProcessingState() {
        binding.apply {
            // Show processing animation
            voiceAnimation.setAnimation(R.raw.voice_processing)
            voiceAnimation.playAnimation()
            
            // Update text
            statusText.text = context.getString(R.string.overlay_processing)
            
            // Update colors
            updateCardColor("#FF9800") // Orange
        }
    }

    /**
     * Show speaking state
     */
    private fun showSpeakingState() {
        binding.apply {
            // Show speaking animation
            voiceAnimation.setAnimation(R.raw.voice_speaking)
            voiceAnimation.playAnimation()
            
            // Update text
            statusText.text = context.getString(R.string.overlay_speaking)
            
            // Update colors
            updateCardColor("#4CAF50") // Green
        }
    }

    /**
     * Show success state
     */
    private fun showSuccessState(message: String?) {
        binding.apply {
            // Show success animation
            voiceAnimation.setAnimation(R.raw.voice_success)
            voiceAnimation.playAnimation()
            
            // Update text
            statusText.text = message ?: context.getString(R.string.overlay_success)
            
            // Update colors
            updateCardColor("#4CAF50") // Green
        }
        
        // Auto-hide after delay
        overlayView?.postDelayed({
            hideOverlay()
        }, 2000)
    }

    /**
     * Show error state
     */
    private fun showErrorState(message: String?) {
        binding.apply {
            // Show error animation
            voiceAnimation.setAnimation(R.raw.voice_error)
            voiceAnimation.playAnimation()
            
            // Update text
            statusText.text = message ?: context.getString(R.string.overlay_error)
            
            // Update colors
            updateCardColor("#F44336") // Red
        }
        
        // Auto-hide after delay
        overlayView?.postDelayed({
            hideOverlay()
        }, 3000)
    }

    /**
     * Update card background color
     */
    private fun updateCardColor(color: String) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(android.graphics.Color.parseColor(color))
        }
        binding.cardBackground.background = drawable
    }

    /**
     * Show pulse animation on listening
     */
    private fun showPulseAnimation() {
        val pulseAnimation = ScaleAnimation(
            1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        binding.voiceAnimation.startAnimation(pulseAnimation)
    }

    /**
     * Show overlay with animation
     */
    private fun showWithAnimation() {
        overlayView?.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    /**
     * Hide overlay with animation
     */
    private fun hideWithAnimation(onComplete: () -> Unit) {
        overlayView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(150)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.withEndAction(onComplete)
            ?.start() ?: onComplete()
    }

    /**
     * Setup dragging functionality
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragging() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        overlayView?.setOnTouchListener { _, event ->
            val params = overlayView?.layoutParams as WindowManager.LayoutParams
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Tap detected - could toggle state
                        Timber.d("Overlay tapped")
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Create window layout parameters
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 200 // Position near top
        }
    }
}

// Extension property for context in binding
private val OverlayVoiceCardBinding.context: Context
    get() = root.context
