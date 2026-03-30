package com.binti.dilink.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Voice Profile Manager
 *
 * Manages voice profiles for different drivers, allowing personalized
 * voice recognition and settings for each user.
 *
 * Features:
 * - Multiple voice profiles per device
 * - Personalized wake word sensitivity
 * - Custom commands per profile
 * - Preferred settings (AC temp, media, navigation favorites)
 * - Voice imprint for speaker identification
 *
 * @author Dr. Waleed Mandour
 */
class VoiceProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceProfileManager"
        private const val PREFS_NAME = "binti_voice_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        private const val KEY_LAST_SPEAKER = "last_speaker_id"

        // Default profiles
        val DEFAULT_PROFILES = listOf(
            VoiceProfile(
                id = "default",
                name = "المستخدم الافتراضي",
                nameEn = "Default User",
                wakeWordSensitivity = 0.7f,
                preferredACTemp = 22,
                preferredVolume = 50,
                preferredBrightness = 70,
                isDefault = true
            )
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profilesDir = File(context.filesDir, "voice_profiles")

    init {
        profilesDir.mkdirs()
        initializeDefaultProfile()
    }

    /**
     * Initialize default profile if none exists
     */
    private fun initializeDefaultProfile() {
        val profiles = getProfiles()
        if (profiles.isEmpty()) {
            saveProfile(DEFAULT_PROFILES.first())
            setActiveProfile(DEFAULT_PROFILES.first().id)
            Log.i(TAG, "Initialized default voice profile")
        }
    }

    /**
     * Create a new voice profile
     */
    fun createProfile(
        name: String,
        nameEn: String = "",
        wakeWordSensitivity: Float = 0.7f,
        preferredACTemp: Int = 22,
        preferredVolume: Int = 50,
        preferredBrightness: Int = 70
    ): VoiceProfile {
        val profile = VoiceProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            nameEn = nameEn,
            wakeWordSensitivity = wakeWordSensitivity,
            preferredACTemp = preferredACTemp,
            preferredVolume = preferredVolume,
            preferredBrightness = preferredBrightness,
            createdAt = System.currentTimeMillis(),
            isDefault = false
        )

        saveProfile(profile)
        Log.i(TAG, "Created new voice profile: ${profile.name}")

        return profile
    }

    /**
     * Save a voice profile
     */
    fun saveProfile(profile: VoiceProfile) {
        val profiles = getProfiles().toMutableList()

        // Update existing or add new
        val existingIndex = profiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }

        saveProfiles(profiles)
        Log.d(TAG, "Saved profile: ${profile.name}")
    }

    /**
     * Delete a voice profile
     */
    fun deleteProfile(profileId: String): Boolean {
        val profiles = getProfiles().toMutableList()
        val profile = profiles.find { it.id == profileId }

        if (profile == null) {
            Log.w(TAG, "Profile not found: $profileId")
            return false
        }

        if (profile.isDefault) {
            Log.w(TAG, "Cannot delete default profile")
            return false
        }

        profiles.removeAll { it.id == profileId }
        saveProfiles(profiles)

        // Delete profile data directory
        val profileDir = File(profilesDir, profileId)
        if (profileDir.exists()) {
            profileDir.deleteRecursively()
        }

        // Switch to default if deleted profile was active
        if (getActiveProfile()?.id == profileId) {
            setActiveProfile("default")
        }

        Log.i(TAG, "Deleted profile: ${profile.name}")
        return true
    }

    /**
     * Get all voice profiles
     */
    fun getProfiles(): List<VoiceProfile> {
        val profilesJson = prefs.getString(KEY_PROFILES, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(profilesJson)
            (0 until jsonArray.length()).map { i ->
                jsonToProfile(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse profiles", e)
            emptyList()
        }
    }

    /**
     * Save profiles list
     */
    private fun saveProfiles(profiles: List<VoiceProfile>) {
        val jsonArray = JSONArray()
        profiles.forEach { profile ->
            jsonArray.put(profileToJson(profile))
        }

        prefs.edit().putString(KEY_PROFILES, jsonArray.toString()).apply()
    }

    /**
     * Get a specific profile by ID
     */
    fun getProfile(profileId: String): VoiceProfile? {
        return getProfiles().find { it.id == profileId }
    }

    /**
     * Set the active profile
     */
    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
        Log.i(TAG, "Set active profile: $profileId")
    }

    /**
     * Get the active profile
     */
    fun getActiveProfile(): VoiceProfile? {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE, "default") ?: "default"
        return getProfile(activeId) ?: getProfiles().firstOrNull()
    }

    /**
     * Identify speaker from voice features
     * Returns matching profile ID or null if no match
     */
    suspend fun identifySpeaker(voiceFeatures: FloatArray): VoiceProfile? = withContext(Dispatchers.Default) {
        // Compare voice features with stored profiles
        val profiles = getProfiles()

        for (profile in profiles) {
            val storedFeatures = loadVoiceFeatures(profile.id)
            if (storedFeatures != null && storedFeatures.size == voiceFeatures.size) {
                val similarity = calculateSimilarity(voiceFeatures, storedFeatures)
                if (similarity > 0.85f) { // 85% similarity threshold
                    Log.i(TAG, "Identified speaker: ${profile.name} (${similarity * 100}%)")
                    setLastSpeaker(profile.id)
                    return@withContext profile
                }
            }
        }

        Log.d(TAG, "No matching speaker found")
        null
    }

    /**
     * Train voice profile with voice samples
     */
    suspend fun trainVoiceProfile(profileId: String, voiceSamples: List<FloatArray>): Boolean =
        withContext(Dispatchers.Default) {
            try {
                if (voiceSamples.size < 3) {
                    Log.w(TAG, "Need at least 3 voice samples for training")
                    return@withContext false
                }

                // Average the voice features
                val avgFeatures = FloatArray(voiceSamples.first().size)
                voiceSamples.forEach { sample ->
                    sample.forEachIndexed { i, value ->
                        avgFeatures[i] += value
                    }
                }
                avgFeatures.indices.forEach { i ->
                    avgFeatures[i] = avgFeatures[i] / voiceSamples.size
                }

                // Save the averaged features
                saveVoiceFeatures(profileId, avgFeatures)

                // Mark profile as trained
                val profile = getProfile(profileId)
                if (profile != null) {
                    saveProfile(profile.copy(isTrained = true))
                }

                Log.i(TAG, "Trained voice profile: $profileId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to train voice profile", e)
                false
            }
        }

    /**
     * Save voice features for a profile
     */
    private fun saveVoiceFeatures(profileId: String, features: FloatArray) {
        val profileDir = File(profilesDir, profileId)
        profileDir.mkdirs()

        val featuresFile = File(profileDir, "voice_features.bin")
        featuresFile.outputStream().use { os ->
            val sizeBytes = java.nio.ByteBuffer.allocate(4).putInt(features.size).array()
            os.write(sizeBytes) // Write array size as 4-byte int
            features.forEach { value ->
                os.write(java.nio.ByteBuffer.allocate(4).putFloat(value).array())
            }
        }
    }

    /**
     * Load voice features for a profile
     */
    private fun loadVoiceFeatures(profileId: String): FloatArray? {
        val featuresFile = File(profilesDir, "$profileId/voice_features.bin")
        if (!featuresFile.exists()) return null

        return try {
            featuresFile.inputStream().use { ios ->
                val sizeBuffer = ByteArray(4)
                ios.read(sizeBuffer)
                val size = java.nio.ByteBuffer.wrap(sizeBuffer).int
                if (size <= 0 || size > 100000) return null // Sanity check
                val features = FloatArray(size)
                val buffer = ByteArray(4)
                repeat(size) { i ->
                    ios.read(buffer)
                    features[i] = java.nio.ByteBuffer.wrap(buffer).float
                }
                features
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load voice features", e)
            null
        }
    }

    /**
     * Calculate cosine similarity between two feature vectors
     */
    private fun calculateSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        a.indices.forEach { i ->
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA > 0 && normB > 0) {
            dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
        } else {
            0f
        }
    }

    /**
     * Set last identified speaker
     */
    private fun setLastSpeaker(profileId: String) {
        prefs.edit().putString(KEY_LAST_SPEAKER, profileId).apply()
    }

    /**
     * Get last identified speaker
     */
    fun getLastSpeaker(): VoiceProfile? {
        val lastSpeakerId = prefs.getString(KEY_LAST_SPEAKER, null) ?: return null
        return getProfile(lastSpeakerId)
    }

    /**
     * Add favorite destination to profile
     */
    fun addFavoriteDestination(profileId: String, name: String, address: String, lat: Double, lng: Double) {
        val profile = getProfile(profileId) ?: return
        val favorites = profile.favoriteDestinations.toMutableList()

        favorites.add(FavoriteDestination(
            id = UUID.randomUUID().toString(),
            name = name,
            address = address,
            latitude = lat,
            longitude = lng
        ))

        saveProfile(profile.copy(favoriteDestinations = favorites))
        Log.d(TAG, "Added favorite destination: $name")
    }

    /**
     * Remove favorite destination
     */
    fun removeFavoriteDestination(profileId: String, destinationId: String) {
        val profile = getProfile(profileId) ?: return
        val favorites = profile.favoriteDestinations.filter { it.id != destinationId }

        saveProfile(profile.copy(favoriteDestinations = favorites))
        Log.d(TAG, "Removed favorite destination: $destinationId")
    }

    /**
     * Add custom command to profile
     */
    fun addCustomCommand(profileId: String, trigger: String, action: String, params: Map<String, String> = emptyMap()) {
        val profile = getProfile(profileId) ?: return
        val commands = profile.customCommands.toMutableList()

        commands.add(CustomCommand(
            id = UUID.randomUUID().toString(),
            trigger = trigger,
            action = action,
            parameters = params
        ))

        saveProfile(profile.copy(customCommands = commands))
        Log.d(TAG, "Added custom command: $trigger")
    }

    /**
     * Remove custom command
     */
    fun removeCustomCommand(profileId: String, commandId: String) {
        val profile = getProfile(profileId) ?: return
        val commands = profile.customCommands.filter { it.id != commandId }

        saveProfile(profile.copy(customCommands = commands))
        Log.d(TAG, "Removed custom command: $commandId")
    }

    /**
     * Match a phrase against custom commands
     */
    fun matchCustomCommand(phrase: String): Pair<CustomCommand, VoiceProfile>? {
        for (profile in getProfiles()) {
            for (command in profile.customCommands) {
                if (phrase.contains(command.trigger, ignoreCase = true)) {
                    return command to profile
                }
            }
        }
        return null
    }

    /**
     * Export profiles to JSON string
     */
    fun exportProfiles(): String {
        val profiles = getProfiles()
        val jsonArray = JSONArray()
        profiles.forEach { profile ->
            jsonArray.put(profileToJson(profile))
        }
        return jsonArray.toString(2)
    }

    /**
     * Import profiles from JSON string
     */
    fun importProfiles(json: String): Boolean {
        return try {
            val jsonArray = JSONArray(json)
            val profiles = mutableListOf<VoiceProfile>()

            (0 until jsonArray.length()).forEach { i ->
                profiles.add(jsonToProfile(jsonArray.getJSONObject(i)))
            }

            // Merge with existing profiles
            val existingProfiles = getProfiles().toMutableList()
            profiles.forEach { newProfile ->
                val existingIndex = existingProfiles.indexOfFirst { it.id == newProfile.id }
                if (existingIndex >= 0) {
                    // Update existing, but preserve isDefault flag
                    val existing = existingProfiles[existingIndex]
                    existingProfiles[existingIndex] = newProfile.copy(
                        isDefault = existing.isDefault
                    )
                } else {
                    existingProfiles.add(newProfile)
                }
            }

            saveProfiles(existingProfiles)
            Log.i(TAG, "Imported ${profiles.size} profiles")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import profiles", e)
            false
        }
    }

    // ===== JSON Serialization =====

    private fun profileToJson(profile: VoiceProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("nameEn", profile.nameEn)
            put("wakeWordSensitivity", profile.wakeWordSensitivity)
            put("preferredACTemp", profile.preferredACTemp)
            put("preferredVolume", profile.preferredVolume)
            put("preferredBrightness", profile.preferredBrightness)
            put("createdAt", profile.createdAt)
            put("isDefault", profile.isDefault)
            put("isTrained", profile.isTrained)

            // Favorite destinations
            val destArray = JSONArray()
            profile.favoriteDestinations.forEach { dest ->
                destArray.put(JSONObject().apply {
                    put("id", dest.id)
                    put("name", dest.name)
                    put("address", dest.address)
                    put("latitude", dest.latitude)
                    put("longitude", dest.longitude)
                })
            }
            put("favoriteDestinations", destArray)

            // Custom commands
            val cmdArray = JSONArray()
            profile.customCommands.forEach { cmd ->
                cmdArray.put(JSONObject().apply {
                    put("id", cmd.id)
                    put("trigger", cmd.trigger)
                    put("action", cmd.action)
                    put("parameters", JSONObject(cmd.parameters))
                })
            }
            put("customCommands", cmdArray)
        }
    }

    private fun jsonToProfile(json: JSONObject): VoiceProfile {
        val destArray = json.optJSONArray("favoriteDestinations") ?: JSONArray()
        val destinations = (0 until destArray.length()).map { i ->
            val destJson = destArray.getJSONObject(i)
            FavoriteDestination(
                id = destJson.getString("id"),
                name = destJson.getString("name"),
                address = destJson.getString("address"),
                latitude = destJson.getDouble("latitude"),
                longitude = destJson.getDouble("longitude")
            )
        }

        val cmdArray = json.optJSONArray("customCommands") ?: JSONArray()
        val commands = (0 until cmdArray.length()).map { i ->
            val cmdJson = cmdArray.getJSONObject(i)
            val paramsJson = cmdJson.optJSONObject("parameters") ?: JSONObject()
            val params = mutableMapOf<String, String>()
            paramsJson.keys().forEach { key ->
                params[key] = paramsJson.getString(key)
            }
            CustomCommand(
                id = cmdJson.getString("id"),
                trigger = cmdJson.getString("trigger"),
                action = cmdJson.getString("action"),
                parameters = params
            )
        }

        return VoiceProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            nameEn = json.optString("nameEn", ""),
            wakeWordSensitivity = json.getDouble("wakeWordSensitivity").toFloat(),
            preferredACTemp = json.getInt("preferredACTemp"),
            preferredVolume = json.getInt("preferredVolume"),
            preferredBrightness = json.getInt("preferredBrightness"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            isDefault = json.optBoolean("isDefault", false),
            isTrained = json.optBoolean("isTrained", false),
            favoriteDestinations = destinations,
            customCommands = commands
        )
    }
}

/**
 * Voice profile data class
 */
data class VoiceProfile(
    val id: String,
    val name: String,
    val nameEn: String = "",
    val wakeWordSensitivity: Float = 0.7f,
    val preferredACTemp: Int = 22,
    val preferredVolume: Int = 50,
    val preferredBrightness: Int = 70,
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false,
    val isTrained: Boolean = false,
    val favoriteDestinations: List<FavoriteDestination> = emptyList(),
    val customCommands: List<CustomCommand> = emptyList()
)

/**
 * Favorite destination
 */
data class FavoriteDestination(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Custom command
 */
data class CustomCommand(
    val id: String,
    val trigger: String,
    val action: String,
    val parameters: Map<String, String> = emptyMap()
)
