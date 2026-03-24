package com.binti.dilink.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * HMS Utilities - Huawei Mobile Services Helper
 * 
 * Provides utilities for detecting and working with Huawei devices
 * and HMS Core services. HMS features are optional and gracefully
 * degrade on non-Huawei devices.
 * 
 * @author Dr. Waleed Mandour
 */
object HMSUtils {
    
    private const val TAG = "HMSUtils"
    
    // Cached availability result
    private var hmsAvailable: Boolean? = null
    
    /**
     * Check if the current device is a Huawei device
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer == "huawei" || 
               manufacturer == "honor" ||
               manufacturer == "byd"
    }
    
    /**
     * Check if Huawei Mobile Services are available
     * Returns false gracefully if HMS is not installed
     */
    fun isHuaweiServicesAvailable(context: Context): Boolean {
        // Return cached result if available
        hmsAvailable?.let { return it }
        
        return try {
            // Try to check HMS availability via reflection
            val hmsApiClass = Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            val getInstance = hmsApiClass.getMethod("getInstance")
            val apiInstance = getInstance.invoke(null)
            val isAvailableMethod = hmsApiClass.getMethod("isHuaweiMobileServicesAvailable", Context::class.java)
            val result = isAvailableMethod.invoke(apiInstance, context) as Int
            
            // ConnectionResult.SUCCESS = 0
            val available = result == 0
            hmsAvailable = available
            Log.d(TAG, "HMS availability: $available (result=$result)")
            available
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "HMS not available - HuaweiApiAvailability not found")
            hmsAvailable = false
            false
        } catch (e: Exception) {
            Log.w(TAG, "HMS availability check failed: ${e.message}")
            hmsAvailable = false
            false
        }
    }
    
    /**
     * Get HMS Core version
     */
    fun getHMSVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager
                .getPackageInfo("com.huawei.hwid", 0)
            "HMS Core ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            "HMS Core not installed"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Check if this is a BYD vehicle head unit
     */
    fun isBYDHeadUnit(): Boolean {
        return Build.MANUFACTURER.equals("byd", ignoreCase = true) ||
               Build.MODEL.contains("BYD", ignoreCase = true) ||
               Build.PRODUCT.contains("dilink", ignoreCase = true)
    }
    
    /**
     * Get device info string for logging/debugging
     */
    fun getDeviceInfo(): String {
        return """
            |Device: ${Build.DEVICE}
            |Model: ${Build.MODEL}
            |Manufacturer: ${Build.MANUFACTURER}
            |Product: ${Build.PRODUCT}
            |Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            |Is Huawei: ${isHuaweiDevice()}
        """.trimMargin()
    }
    
    /**
     * Get the preferred ASR provider based on device capabilities
     */
    fun getPreferredASRProvider(): ASRProvider {
        return when {
            isHuaweiDevice() -> ASRProvider.HUAWEI_ML_KIT
            else -> ASRProvider.VOSK_OFFLINE
        }
    }
    
    /**
     * Get the preferred TTS provider based on device capabilities
     */
    fun getPreferredTTSProvider(): TTSProvider {
        return when {
            isHuaweiDevice() -> TTSProvider.HUAWEI_ML_KIT
            else -> TTSProvider.ANDROID_TTS
        }
    }
    
    /**
     * ASR Provider enum
     */
    enum class ASRProvider {
        VOSK_OFFLINE,       // Vosk Egyptian Arabic model
        HUAWEI_ML_KIT,      // Huawei ML Kit Speech-to-Text
        GOOGLE_CLOUD        // Google Cloud Speech (future)
    }
    
    /**
     * TTS Provider enum
     */
    enum class TTSProvider {
        COQUI_OFFLINE,      // Coqui TTS Egyptian Female
        HUAWEI_ML_KIT,      // Huawei ML Kit Text-to-Speech
        ANDROID_TTS         // Android TTS with Arabic locale
    }
}
