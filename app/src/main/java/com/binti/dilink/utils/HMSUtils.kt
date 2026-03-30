package com.binti.dilink.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * HMS Utilities - Device Detection Helper
 * 
 * Provides utilities for detecting device types and capabilities.
 * 
 * @author Dr. Waleed Mandour
 */
object HMSUtils {
    
    private const val TAG = "HMSUtils"
    
    /**
     * Check if the current device is a Huawei device
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer == "huawei" || 
               manufacturer == "honor" ||
               // Some BYD vehicles use Huawei-based systems
               manufacturer == "byd"
    }
    
    /**
     * Check if Huawei Mobile Services are available
     */
    fun isHuaweiServicesAvailable(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo("com.huawei.hwid", 0)
            packageInfo != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
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
    fun getPreferredASRProvider(context: Context): ASRProvider {
        return if (isHuaweiServicesAvailable(context)) {
            ASRProvider.HUAWEI_ML_KIT
        } else {
            ASRProvider.VOSK_OFFLINE
        }
    }
    
    /**
     * Get the preferred TTS provider based on device capabilities
     */
    fun getPreferredTTSProvider(context: Context): TTSProvider {
        return if (isHuaweiServicesAvailable(context)) {
            TTSProvider.HUAWEI_ML_KIT
        } else {
            TTSProvider.ANDROID_TTS
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
