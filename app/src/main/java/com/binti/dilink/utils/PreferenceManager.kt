package com.binti.dilink.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
    }
    
    fun isModelsDownloaded(): Boolean = prefs.getBoolean("models_downloaded", false)
    fun setModelsDownloaded(value: Boolean) = prefs.edit().putBoolean("models_downloaded", value).apply()
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean("wake_word_enabled", true)
}
