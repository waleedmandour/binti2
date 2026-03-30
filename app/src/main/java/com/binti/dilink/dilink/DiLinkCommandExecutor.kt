package com.binti.dilink.dilink

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import com.binti.dilink.nlp.IntentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiLink Command Executor - Reverted to Accessibility Method
 *
 * Executes vehicle commands on BYD DiLink via Accessibility Service.
 *
 * @author Dr. Waleed Mandour
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DiLinkExecutor"
    }

    private var accessibilityService: DiLinkAccessibilityService? = null
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun setAccessibilityService(service: DiLinkAccessibilityService) {
        this.accessibilityService = service
    }

    /**
     * Execute a classified intent
     */
    suspend fun executeCommand(intent: IntentResult): CommandResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "🎯 Executing command: ${intent.action}")
        try {
            when (intent.action) {
                "AC_CONTROL" -> executeACCommand(intent)
                "NAVIGATION" -> executeNavigationCommand(intent)
                "MEDIA" -> executeMediaCommand(intent)
                "PHONE" -> executePhoneCommand(intent)
                "INFO" -> executeInfoCommand(intent)
                "SYSTEM" -> executeSystemCommand(intent)
                else -> CommandResult(false, "Unknown command: ${intent.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution failed", e)
            CommandResult(false, "Error: ${e.message}")
        }
    }

    // ========== AC CONTROL (Accessibility Driven) ==========

    private suspend fun executeACCommand(intent: IntentResult): CommandResult {
        val service = accessibilityService ?: return CommandResult(false, "خِدْمِة الْوُصُول غِير مِفَعَّلَة")
        val pattern = intent.matchedPattern?.lowercase() ?: ""

        // Ensure AC app is open
        if (!service.isACAppActive()) {
            service.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_AC)
            Thread.sleep(1500) // Wait for app to open
        }

        return when {
            pattern.contains("شغل") || pattern.contains("افتح") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_POWER)
                CommandResult(true, "تَمّ تَشْغِيل الْمُكَيِّف")
            }
            pattern.contains("طفي") || pattern.contains("قفل") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_POWER)
                CommandResult(true, "تَمّ إِطْفَاء الْمُكَيِّف")
            }
            pattern.contains("زود") || pattern.contains("علي") || pattern.contains("ارفع") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_TEMP_UP)
                CommandResult(true, "تَمّ رَفْع الْحَرَّارَة")
            }
            pattern.contains("قلل") || pattern.contains("وطي") || pattern.contains("خفض") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_TEMP_DOWN)
                CommandResult(true, "تَمّ خَفْض الْحَرَّارَة")
            }
            else -> CommandResult(true, "تَمّ تَنْفِيذ الْأَمْر")
        }
    }

    // ========== NAVIGATION ==========

    private suspend fun executeNavigationCommand(intent: IntentResult): CommandResult {
        val dest = intent.entities["destination"]
        return if (dest != null) startNavigation(dest)
        else {
            accessibilityService?.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_NAV)
            CommandResult(true, "تَمّ فَتْح الْخَرَائِط")
        }
    }

    private fun startNavigation(destination: String): CommandResult {
        val uri = Uri.parse("geo:0,0?q=$destination")
        val navIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(BYDModels.YuanPlus2023.PACKAGE_NAV)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(navIntent)
        return CommandResult(true, "جَارِي التَّوْجِيه إِلَى $destination")
    }

    // ========== MEDIA ==========

    private fun executeMediaCommand(intent: IntentResult): CommandResult {
        val service = accessibilityService ?: return CommandResult(false, "خِدْمِة الْوُصُول غِير مِفَعَّلَة")
        val action = intent.entities["media_action"] ?: "play"
        
        if (!service.isMediaAppActive()) {
            service.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_MEDIA)
            Thread.sleep(1000)
        }

        val success = when (action) {
            "play" -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PLAY)
            "pause" -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PAUSE)
            "next" -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_NEXT)
            "previous" -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PREV)
            else -> false
        }
        
        return CommandResult(success, if (success) "تَمّ تَنْفِيذ أَمْر الْوَسَائِط" else "فِشِل فِي التَّحَكُّم بِالْوَسَائِط")
    }

    // ========== PHONE ==========

    private fun executePhoneCommand(intent: IntentResult): CommandResult {
        val number = intent.entities["phone_number"] ?: intent.entities["contact_name"]
        return if (number != null) {
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            CommandResult(true, "جَارِي الِاتِّصَال")
        } else {
            accessibilityService?.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_PHONE)
            CommandResult(true, "تَمّ فَتْح الْهَاتِف")
        }
    }

    // ========== INFO & SYSTEM ==========

    private fun executeInfoCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("ساعة") -> {
                val time = SimpleDateFormat("h:mm a", Locale("ar", "EG")).format(Date())
                CommandResult(true, "السَّاعَة دِلْوَقْت هِيَّ $time", isInfo = true)
            }
            pattern.contains("بطارية") -> {
                val battery = accessibilityService?.getBatteryPercentage()
                val response = if (battery != null) "نِسْبِة الْبَطَّارِيَّة هِيَّ $battery%" else "الْبَطَّارِيَّة كُوَيِّسَة حَالِيًّا"
                CommandResult(true, response, isInfo = true)
            }
            else -> CommandResult(true, "كُلّ حَاجَة شَغَّالَة تَمَام", isInfo = true)
        }
    }

    private fun executeSystemCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("صوت") -> {
                if (pattern.contains("علي") || pattern.contains("ارفع")) {
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                } else {
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                CommandResult(true, "تَمّ تَعْدِيل مُسْتَوَى الصُّوت")
            }
            else -> CommandResult(true, "تَمّ")
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val message: String,
    val isInfo: Boolean = false
)
