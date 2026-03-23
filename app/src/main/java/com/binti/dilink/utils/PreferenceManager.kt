package com.binti.dilink.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    
    private const val PREFS_NAME = "binti_preferences"
    private const val KEY_MODELS_DOWNLOADED = "models_downloaded"
    private const val KEY_MODEL_VERSION = "model_version"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isModelsDownloaded(): Boolean = prefs.getBoolean(KEY_MODELS_DOWNLOADED, false)
    
    fun setModelsDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean(KEY_MODELS_DOWNLOADED, downloaded).apply()
    }
    
    fun getModelVersion(): String = prefs.getString(KEY_MODEL_VERSION, "") ?: ""
    
    fun setModelVersion(version: String) {
        prefs.edit().putString(KEY_MODEL_VERSION, version).apply()
    }
    
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    
    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }
    
    fun getWakeWordSensitivity(): Float = prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.85f)
    
    fun setWakeWordSensitivity(sensitivity: Float) {
        prefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, sensitivity.coerceIn(0.5f, 1.0f)).apply()
    }
    
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    
    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
    
    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    
    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
