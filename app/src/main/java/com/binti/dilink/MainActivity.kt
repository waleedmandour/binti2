package com.binti.dilink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var welcomeText: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        welcomeText = findViewById(R.id.welcome_text)
        startButton = findViewById(R.id.btn_start_setup)

        startButton.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
        } else {
            startBintiService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBintiService()
            } else {
                Toast.makeText(this, R.string.permission_microphone_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBintiService() {
        val intent = Intent(this, BintiService::class.java).apply {
            action = BintiService.ACTION_START_LISTENING
        }
        startService(intent)
        Toast.makeText(this, R.string.status_ready, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_MICROPHONE = 100
    }
}
