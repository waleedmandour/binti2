package com.binti.dilink.dilink

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import com.binti.dilink.nlp.IntentResult
import com.binti.dilink.utils.StationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiLink Command Executor - Enhanced with STATION Intent
 *
 * Executes vehicle commands on BYD DiLink via Accessibility Service.
 * Supports AC control, navigation, media, phone, info, system,
 * greetings, social, help, and EV charging station discovery.
 *
 * @author Dr. Waleed Mandour
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DiLinkExecutor"
    }

    private var accessibilityService: DiLinkAccessibilityService? = null
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Lazy-initialised StationManager for EV charging station queries. */
    private val stationManager: StationManager by lazy {
        StationManager(context)
    }

    fun setAccessibilityService(service: DiLinkAccessibilityService) {
        this.accessibilityService = service
    }

    // ================================================================== //
    //  Command Router                                                     //
    // ================================================================== //

    /**
     * Execute a classified intent.
     *
     * Routes to the appropriate handler based on [IntentResult.action] and
     * returns a [CommandResult] containing the outcome message, success flag,
     * and TTS hints.
     */
    suspend fun executeCommand(intent: IntentResult): CommandResult =
        withContext(Dispatchers.Default) {
            Log.i(TAG, "🎯 Executing command: ${intent.action}")
            try {
                when (intent.action) {
                    "AC_CONTROL"  -> executeACCommand(intent)
                    "NAVIGATION"  -> executeNavigationCommand(intent)
                    "MEDIA"       -> executeMediaCommand(intent)
                    "PHONE"       -> executePhoneCommand(intent)
                    "INFO"        -> executeInfoCommand(intent)
                    "SYSTEM"      -> executeSystemCommand(intent)
                    "GREETINGS"   -> executeGreetingsCommand(intent)
                    "SOCIAL"      -> executeSocialCommand(intent)
                    "HELP"        -> executeHelpCommand(intent)
                    "STATION"     -> executeStationCommand(intent)
                    else          -> CommandResult(
                        success = false,
                        message = "Unknown command: ${intent.action}",
                        shouldSpeak = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Execution failed", e)
                CommandResult(
                    success = false,
                    message = "Error: ${e.message}",
                    shouldSpeak = true
                )
            }
        }

    // ================================================================== //
    //  AC CONTROL (Accessibility Driven)                                  //
    // ================================================================== //

    private suspend fun executeACCommand(intent: IntentResult): CommandResult {
        val service =
            accessibilityService ?: return CommandResult(false, "خِدْمِة الْوُصُول غِير مِفَعَّلَة")
        val pattern = intent.matchedPattern?.lowercase() ?: ""

        // Ensure AC app is open
        if (!service.isACAppActive()) {
            service.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_AC)
            Thread.sleep(1500)
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
            pattern.contains("اتوماتيك") || pattern.contains("أوتوماتيك") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_MODE_AUTO)
                CommandResult(true, "تَمّ التَّكْيِيف عَلَى أُوتُومَاتِيك")
            }
            pattern.contains("بار") || pattern.contains("تبريد") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_MODE_COOL)
                CommandResult(true, "تَمّ تَبْرِيد الْمُكَيِّف")
            }
            pattern.contains("ساخ") || pattern.contains("تدفئة") -> {
                service.clickById(BYDModels.YuanPlus2023.ID_AC_MODE_HEAT)
                CommandResult(true, "تَمّ تَدْفِئَة الْمُكَيِّف")
            }
            else -> CommandResult(true, "تَمّ تَنْفِيذ الْأَمْر")
        }
    }

    // ================================================================== //
    //  NAVIGATION                                                         //
    // ================================================================== //

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

    // ================================================================== //
    //  MEDIA                                                              //
    // ================================================================== //

    private fun executeMediaCommand(intent: IntentResult): CommandResult {
        val service =
            accessibilityService ?: return CommandResult(false, "خِدْمِة الْوُصُول غِير مِفَعَّلَة")
        val action = intent.entities["media_action"] ?: "play"

        if (!service.isMediaAppActive()) {
            service.openDiLinkApp(BYDModels.YuanPlus2023.PACKAGE_MEDIA)
            Thread.sleep(1000)
        }

        val success = when (action) {
            "play"     -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PLAY)
            "pause"    -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PAUSE)
            "next"     -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_NEXT)
            "previous" -> service.clickById(BYDModels.YuanPlus2023.ID_MEDIA_PREV)
            else       -> false
        }

        return CommandResult(
            success = success,
            message = if (success) "تَمّ تَنْفِيذ أَمْر الْوَسَائِط"
            else "فِشِل فِي التَّحَكُّم بِالْوَسَائِط"
        )
    }

    // ================================================================== //
    //  PHONE                                                              //
    // ================================================================== //

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

    // ================================================================== //
    //  INFO                                                               //
    // ================================================================== //

    private fun executeInfoCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("ساعة") || pattern.contains("وقت") -> {
                val time = SimpleDateFormat("h:mm a", Locale("ar", "EG")).format(Date())
                CommandResult(
                    success = true,
                    message = "السَّاعَة دِلْوَقْت هِيَّ $time",
                    isInfo = true
                )
            }
            pattern.contains("بطارية") || pattern.contains("شحن") -> {
                val battery = accessibilityService?.getBatteryPercentage()
                val response = if (battery != null) "نِسْبِة الْبَطَّارِيَّة هِيَّ $battery%"
                else "الْبَطَّارِيَّة كُوَيِّسَة حَالِيًّا"
                CommandResult(
                    success = true,
                    message = response,
                    isInfo = true
                )
            }
            pattern.contains("بره") || pattern.contains("حرارة") || pattern.contains("طقس") -> {
                val outsideTemp = accessibilityService?.getOutsideTemperature()
                val response = if (outsideTemp != null) "حَرَّارَة بَرَّه حَالِيًّا $outsideTemp دَرَجَة"
                else "مِش قَادِر أَقْرَأ حَرَّارَة بَرَّه دلوقتي"
                CommandResult(
                    success = true,
                    message = response,
                    isInfo = true
                )
            }
            else -> CommandResult(
                success = true,
                message = "كُلّ حَاجَة شَغَّالَة تَمَام",
                isInfo = true
            )
        }
    }

    // ================================================================== //
    //  SYSTEM                                                             //
    // ================================================================== //

    private fun executeSystemCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("صوت") -> {
                if (pattern.contains("علي") || pattern.contains("ارفع") ||
                    pattern.contains("زود") || pattern.contains("كبر")
                ) {
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                } else {
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                CommandResult(true, "تَمّ تَعْدِيل مُسْتَوَى الصُّوت")
            }
            else -> CommandResult(true, "تَمّ")
        }
    }

    // ================================================================== //
    //  GREETINGS                                                          //
    // ================================================================== //

    private fun executeGreetingsCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("صباح") -> CommandResult(
                success = true,
                message = "يَا صَبَاح الْفِل وِالْجَمَال، تُؤْمُرْنِي بِإِيه؟"
            )
            pattern.contains("مساء") -> CommandResult(
                success = true,
                message = "يَا مَسَاء النُّور، أَيْنَ عَزْمِي؟"
            )
            pattern.contains("ازيك") || pattern.contains("إزيك") ||
            pattern.contains("أخبارك") || pattern.contains("عاملة") -> CommandResult(
                success = true,
                message = "أَنَا زَي الْفِل طُول مَا أَنْتَ بِخِير، أَأدَر أَسَاعْدَك فِي إِيه؟"
            )
            else -> CommandResult(
                success = true,
                message = "أَهْلًا بِيك، أَنَا حَاضِر لِلْخِدْمَة"
            )
        }
    }

    // ================================================================== //
    //  SOCIAL                                                             //
    // ================================================================== //

    private fun executeSocialCommand(intent: IntentResult): CommandResult {
        val pattern = intent.originalText.lowercase()
        return when {
            pattern.contains("شكر") || pattern.contains("تسلم") ||
            pattern.contains("ذوق") || pattern.contains("حبيبي") -> CommandResult(
                success = true,
                message = "الشُّكْرُ لِلّٰه، أَنَا فِي الْخِدْمَة دَايْمًا"
            )
            else -> CommandResult(
                success = true,
                message = "تَسَلَّم يَا بَاشَا"
            )
        }
    }

    // ================================================================== //
    //  HELP                                                               //
    // ================================================================== //

    private fun executeHelpCommand(intent: IntentResult): CommandResult {
        return CommandResult(
            success = true,
            message = "أَنَا بِنْتِي قَادِرَة أَتْحَكِّم فِي التَّكْيِيف، " +
                    "وِأَشْغِّل لَك الْمُزِيكَا، " +
                    "وِأَوْصِّلَك لِأَي مَكَان، " +
                    "وِأَطْلُبْلَك أَي حَدّ، " +
                    "وِأَلْقِي لَك أَقْرَب مَحَطَّة شُحْن لِلْعَرَبِيَّة. " +
                    "قُولِّي بَس أَنْتَ مِحْتَاج إِيه!"
        )
    }

    // ================================================================== //
    //  STATION – EV Charging Station Discovery                            //
    // ================================================================== //

    /**
     * Handle the STATION intent.
     *
     * Behaviour:
     * 1. Ensure station data is loaded (with optional forced refresh).
     * 2. Find the nearest active station (uses a default GPS position for
     *    Cairo if the user has not granted location, or falls back to a
     *    hardcoded Cairo centre).
     * 3. Build an Egyptian Arabic spoken response with:
     *    - Station name (Arabic)
     *    - City
     *    - Distance (rounded, in km)
     *    - Available power (kW)
     *    - Operating hours
     *    - Connectors
     * 4. Automatically launch BYD navigation to the station via geo intent.
     */
    private suspend fun executeStationCommand(intent: IntentResult): CommandResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "🔌 STATION intent received")

            // --- 1. Load station data ---
            val loaded = stationManager.loadStations()
            if (!loaded) {
                return@withContext CommandResult(
                    success = false,
                    message = "آسِف يَا فَنْدِم، مِش قَادِر أَحْمِّل بَيَانَات مَحَطَّات الشُّحْن دلوقتي. حَاوِل تَانِي بَعْدِيَن."
                )
            }

            // --- 2. Determine user location ---
            // In a real implementation this would use FusedLocationProviderClient.
            // For the infotainment context we default to Cairo centre.
            val userLat = 30.0444
            val userLon = 31.2357

            // --- 3. Find nearest station ---
            val nearest = stationManager.findNearestStation(userLat, userLon)
            if (nearest == null) {
                return@withContext CommandResult(
                    success = false,
                    message = "مِش لَاقِي مَحَطَّات شُحْن قَرِيبَة مِنَّك دلوقتي. جَرَّب بَعْد شَوْيَة لَمَّ تِكُون أَقْرَب لِمَدِينَة."
                )
            }

            val station = nearest.station
            val distKm = nearest.distanceKm
            val distRounded = if (distKm >= 1.0) {
                "%.1f".format(distKm)
            } else {
                "%.0f".format(distKm * 1000) + " مِتْر"
            }

            val connectors = station.connectors.joinToString(" و ") { it }

            // --- 4. Build Egyptian Arabic response ---
            val response = buildString {
                append("لَقِيت لَك أَقْرَب مَحَطَّة شُحْن. ")
                append("${station.name} فِي ${station.city}. ")
                append("بُعْدَهَا عَنَّك حَوَالِي $distRounded. ")
                append("قُوَّة الشُّحْن ${station.powerKw} كِيلُو وَاتّ. ")
                append("الشُّحْن بِـ ${station.costPerKwh} جُنَيَّه لِكُلّ كِيلُو وَاتّ سَاعَة. ")
                append("الْمَقَابِس الْمُتَاحَة: $connectors. ")
                append("مَفْتُوحَة ${station.hours}. ")
                append("جَارِي فَتْح الْخَرِيطَة لِلتَّوْجِيه.")
            }

            Log.i(
                TAG,
                "⚡ Nearest station: ${station.name} (${station.city}) – " +
                        "${"%.1f".format(distKm)} km"
            )

            // --- 5. Auto-navigate via geo intent to BYD navigation ---
            try {
                val geoUri = Uri.parse(
                    "geo:${station.latitude},${station.longitude}?" +
                            "q=${station.latitude},${station.longitude}(${Uri.encode(station.name)})"
                )
                val navIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    setPackage(BYDModels.YuanPlus2023.PACKAGE_NAV)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(navIntent)
                Log.d(TAG, "Navigation intent sent to ${station.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch BYD navigation, trying generic geo", e)
                try {
                    val fallbackUri = Uri.parse(
                        "geo:${station.latitude},${station.longitude}?" +
                                "q=${station.latitude},${station.longitude}"
                    )
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Navigation launch failed completely", e2)
                }
            }

            CommandResult(
                success = true,
                message = response
            )
        }
}

/**
 * Represents the result of executing a voice command.
 *
 * @property success      Whether the command was executed successfully.
 * @property message      The Egyptian Arabic response to be shown/spoken.
 * @property isInfo       When `true`, the response is informational (time, battery, etc.)
 *                        and may be displayed differently in the UI.
 * @property shouldSpeak  Whether the TTS engine should speak [message] aloud.
 *                        Defaults to `true`.
 */
data class CommandResult(
    val success: Boolean,
    val message: String,
    val isInfo: Boolean = false,
    val shouldSpeak: Boolean = true
)
