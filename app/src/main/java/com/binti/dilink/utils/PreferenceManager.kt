package com.binti.dilink.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    
    private const val PREFS_NAME = "binti_preferences"
    private const val KEY_MODELS_DOWNLOADED = "models_downloaded"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isModelsDownloaded(): Boolean = prefs.getBoolean(KEY_MODELS_DOWNLOADED, false)
    
    fun setModelsDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean(KEY_MODELS_DOWNLOADED, downloaded).apply()
    }
    
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    
    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }
}
