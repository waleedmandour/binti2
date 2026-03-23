package com.binti.dilink.voice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.binti.dilink.R
import com.binti.dilink.response.EgyptianTTS
import timber.log.Timber

class VoiceInteractionActivity : AppCompatActivity() {
    
    private lateinit var voiceAnimation: LottieAnimationView
    private lateinit var tts: EgyptianTTS
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_interaction)
        
        voiceAnimation = findViewById(R.id.voice_animation)
        tts = EgyptianTTS(this)
        tts.initialize()
        
        startListening()
    }
    
    private fun startListening() {
        voiceAnimation.setAnimation(R.raw.voice_listening)
        voiceAnimation.playAnimation()
        Timber.i("Voice interaction started")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tts.release()
    }
}
