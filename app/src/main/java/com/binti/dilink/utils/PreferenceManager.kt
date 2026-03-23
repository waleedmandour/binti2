package com.binti.dilink.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * PreferenceManager - Manages app preferences using DataStore
 * 
 * Preferences stored:
 * - Model download status
 * - Wake word settings
 * - Voice configuration
 * - User preferences
 */
object PreferenceManager {

    private const val PREFS_NAME = "binti_preferences"
    
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_NAME)
    
    // Preference keys
    private object Keys {
        val MODELS_DOWNLOADED = booleanPreferencesKey("models_downloaded")
        val MODEL_VERSION = stringPreferencesKey("model_version")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_FORMALITY = stringPreferencesKey("voice_formality")
        val DILINK_ADB_ENABLED = booleanPreferencesKey("dilink_adb_enabled")
        val DILINK_ADB_HOST = stringPreferencesKey("dilink_adb_host")
        val DILINK_ADB_PORT = intPreferencesKey("dilink_adb_port")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val USER_NAME = stringPreferencesKey("user_name")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
    }
    
    // Need to create long preferences key
    private fun longPreferencesKey(name: String): Preferences.Key<Long> {
        return Preferences.Key(name)
    }
    
    private lateinit var appContext: Context
    
    fun init(context: Context) {
        appContext = context.applicationContext
        Timber.d("PreferenceManager initialized")
    }

    // Model related preferences
    fun isModelsDownloaded(): Boolean = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.MODELS_DOWNLOADED] ?: false
        }.first
    }
    
    suspend fun setModelsDownloaded(downloaded: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.MODELS_DOWNLOADED] = downloaded
        }
    }
    
    fun getModelVersion(): String = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.MODEL_VERSION] ?: ""
        }.first
    }
    
    suspend fun setModelVersion(version: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.MODEL_VERSION] = version
        }
    }

    // Wake word preferences
    fun isWakeWordEnabled(): Boolean = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] ?: true
        }.first
    }
    
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }
    
    fun getWakeWordSensitivity(): Float = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.WAKE_WORD_SENSITIVITY] ?: 0.85f
        }.first
    }
    
    suspend fun setWakeWordSensitivity(sensitivity: Float) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_SENSITIVITY] = sensitivity.coerceIn(0.5f, 1.0f)
        }
    }

    // Voice preferences
    fun getVoiceSpeed(): Float = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.VOICE_SPEED] ?: 0.9f
        }.first
    }
    
    suspend fun setVoiceSpeed(speed: Float) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.VOICE_SPEED] = speed.coerceIn(0.5f, 1.5f)
        }
    }
    
    fun getVoicePitch(): Float = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.VOICE_PITCH] ?: 1.0f
        }.first
    }
    
    suspend fun setVoicePitch(pitch: Float) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.VOICE_PITCH] = pitch.coerceIn(0.5f, 1.5f)
        }
    }
    
    fun getVoiceFormality(): String = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.VOICE_FORMALITY] ?: "casual"
        }.first
    }
    
    suspend fun setVoiceFormality(formality: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.VOICE_FORMALITY] = formality
        }
    }

    // DiLink connection preferences
    fun isADBEnabled(): Boolean = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.DILINK_ADB_ENABLED] ?: false
        }.first
    }
    
    suspend fun setADBEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DILINK_ADB_ENABLED] = enabled
        }
    }
    
    fun getADBHost(): String = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.DILINK_ADB_HOST] ?: "localhost"
        }.first
    }
    
    suspend fun setADBHost(host: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DILINK_ADB_HOST] = host
        }
    }
    
    fun getADBPort(): Int = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.DILINK_ADB_PORT] ?: 5555
        }.first
    }
    
    suspend fun setADBPort(port: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DILINK_ADB_PORT] = port
        }
    }

    // Update check
    fun getLastUpdateCheck(): Long = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK] ?: 0L
        }.first
    }
    
    suspend fun setLastUpdateCheck(timestamp: Long) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK] = timestamp
        }
    }

    // User preferences
    fun getUserName(): String? = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.USER_NAME]
        }.first
    }
    
    suspend fun setUserName(name: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
        }
    }

    // Onboarding
    fun isOnboardingComplete(): Boolean = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] ?: false
        }.first
    }
    
    suspend fun setOnboardingComplete(complete: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = complete
        }
    }

    // Accessibility service
    fun isAccessibilityEnabled(): Boolean = runBlocking {
        appContext.dataStore.data.map { prefs ->
            prefs[Keys.ACCESSIBILITY_ENABLED] ?: false
        }.first
    }
    
    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ACCESSIBILITY_ENABLED] = enabled
        }
    }

    // Clear all preferences
    suspend fun clearAll() {
        appContext.dataStore.edit { prefs ->
            prefs.clear()
        }
        Timber.i("All preferences cleared")
    }
}
