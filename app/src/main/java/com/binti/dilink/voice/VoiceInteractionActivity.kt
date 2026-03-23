package com.binti.dilink.voice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.binti.dilink.R
import com.binti.dilink.databinding.ActivityVoiceInteractionBinding
import com.binti.dilink.nlp.Intent
import com.binti.dilink.response.EgyptianTTS
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * VoiceInteractionActivity - Full-screen voice interaction UI
 * 
 * Displayed when:
 * - Wake word is detected
 * - User initiates voice command
 * - Command confirmation is needed
 */
class VoiceInteractionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceInteractionBinding
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var tts: EgyptianTTS

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROMPT = "prompt"
        
        const val MODE_LISTEN = "listen"
        const val MODE_CONFIRM = "confirm"
        const val MODE_RESPONSE = "response"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceInteractionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceProcessor = VoiceProcessor(this)
        tts = EgyptianTTS(this)
        tts.initialize()

        setupUI()
        
        // Start listening immediately
        startListening()
    }

    private fun setupUI() {
        binding.apply {
            // Set up animation
            voiceAnimation.setAnimation(R.raw.voice_listening)
            voiceAnimation.playAnimation()
            
            // Cancel button
            cancelButton.setOnClickListener {
                finish()
            }
        }
    }

    private fun startListening() {
        lifecycleScope.launch {
            try {
                updateState(State.LISTENING)
                
                // Record voice command
                val audioData = voiceProcessor.recordCommand()
                
                if (audioData.isNotEmpty()) {
                    updateState(State.PROCESSING)
                    
                    // Transcribe
                    val text = voiceProcessor.transcribe(audioData)
                    binding.statusText.text = text.ifEmpty { "لم أسمع شيئاً" }
                    
                    if (text.isNotEmpty()) {
                        // Process command (would connect to BintiService)
                        processCommand(text)
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Voice interaction error")
                updateState(State.ERROR)
            }
        }
    }

    private suspend fun processCommand(text: String) {
        updateState(State.PROCESSING)
        
        // Placeholder - actual processing would be done by BintiService
        // This would send the text to the service and wait for response
        
        // Simulate processing
        kotlinx.coroutines.delay(500)
        
        // Speak response
        val response = "تمام، نفذت الأمر!"
        speakResponse(response)
    }

    private suspend fun speakResponse(text: String) {
        updateState(State.SPEAKING)
        binding.statusText.text = text
        
        tts.speak(text) {
            finish()
        }
    }

    private fun updateState(state: State) {
        binding.apply {
            when (state) {
                State.LISTENING -> {
                    voiceAnimation.setAnimation(R.raw.voice_listening)
                    statusText.text = getString(R.string.voice_listening)
                }
                State.PROCESSING -> {
                    voiceAnimation.setAnimation(R.raw.voice_processing)
                    statusText.text = getString(R.string.voice_processing)
                }
                State.SPEAKING -> {
                    voiceAnimation.setAnimation(R.raw.voice_speaking)
                }
                State.ERROR -> {
                    voiceAnimation.setAnimation(R.raw.voice_error)
                    statusText.text = getString(R.string.voice_error)
                }
                State.SUCCESS -> {
                    voiceAnimation.setAnimation(R.raw.voice_success)
                }
            }
            voiceAnimation.playAnimation()
        }
    }

    enum class State {
        LISTENING,
        PROCESSING,
        SPEAKING,
        SUCCESS,
        ERROR
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.release()
        voiceProcessor.release()
    }
}
