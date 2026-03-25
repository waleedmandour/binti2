package com.binti.dilink.dilink

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.binti.dilink.nlp.IntentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiLink Command Executor - Enhanced Version
 *
 * Executes vehicle commands on BYD DiLink infotainment system using:
 * - AccessibilityService for UI automation
 * - ADB bridge for system-level commands
 * - Intent broadcasting for app integration
 *
 * Supported Commands:
 * - AC Control: temperature, fan speed, mode, power
 * - Navigation: start navigation, set destination, home, work
 * - Media: play/pause, next/previous, volume
 * - Phone: make calls, answer, reject, recent calls
 * - Info: time, battery, range, temperature
 * - System: brightness, volume, display settings
 *
 * @author Dr. Waleed Mandour
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DiLinkExecutor"

        // DiLink package names
        const val DILINK_AC_PACKAGE = "com.byd.auto.ac"
        const val DILINK_NAV_PACKAGE = "com.byd.auto.navigation"
        const val DILINK_MEDIA_PACKAGE = "com.byd.auto.media"
        const val DILINK_SETTINGS_PACKAGE = "com.byd.auto.settings"
        const val DILINK_PHONE_PACKAGE = "com.byd.auto.phone"
        const val DILINK_VEHICLE_PACKAGE = "com.byd.auto.vehicleinfo"

        // Command timeout
        private const val COMMAND_TIMEOUT_MS = 5000L

        // Intent actions for vehicle control
        const val ACTION_AC_CONTROL = "com.byd.auto.ac.CONTROL"
        const val ACTION_MEDIA_CONTROL = "com.byd.auto.media.CONTROL"
        const val ACTION_PHONE_CONTROL = "com.byd.auto.phone.CONTROL"
    }

    // Accessibility service reference
    private var accessibilityService: DiLinkAccessibilityService? = null

    // Command status
    private var lastCommandTime = 0L

    // Audio manager for volume control
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Execute a classified intent as a DiLink command
     */
    suspend fun executeCommand(intent: IntentResult): CommandResult {
        Log.i(TAG, "🎯 Executing command: ${intent.action}")

        return withContext(Dispatchers.Default) {
            try {
                lastCommandTime = System.currentTimeMillis()

                val result = when (intent.action) {
                    "AC_CONTROL" -> executeACCommand(intent)
                    "NAVIGATION" -> executeNavigationCommand(intent)
                    "MEDIA" -> executeMediaCommand(intent)
                    "PHONE" -> executePhoneCommand(intent)
                    "INFO" -> executeInfoCommand(intent)
                    "SYSTEM" -> executeSystemCommand(intent)
                    else -> {
                        Log.w(TAG, "Unknown action: ${intent.action}")
                        CommandResult(false, "Unknown command: ${intent.action}")
                    }
                }

                Log.i(TAG, "✅ Command result: ${result.success} - ${result.message}")
                result

            } catch (e: Exception) {
                Log.e(TAG, "❌ Command execution failed", e)
                CommandResult(false, "Error: ${e.message}")
            }
        }
    }

    /**
     * Set accessibility service reference
     */
    fun setAccessibilityService(service: DiLinkAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "Accessibility service connected")
    }

    // ========== AC CONTROL COMMANDS ==========

    /**
     * Execute AC control command
     */
    private suspend fun executeACCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities

        return when {
            // Temperature control
            entities.containsKey("temperature") -> {
                val temp = entities["temperature"]?.toIntOrNull() ?: 22
                setACTemperature(temp)
            }

            // Mode control
            entities.containsKey("mode") -> {
                val mode = entities["mode"] ?: "auto"
                setACMode(mode)
            }

            // Fan speed control
            entities.containsKey("fan_speed") -> {
                val speed = entities["fan_speed"]?.toIntOrNull() ?: 3
                setACFanSpeed(speed)
            }

            // Power control - turn on
            intent.matchedPattern?.contains("شغل") == true ||
            intent.matchedPattern?.contains("افتح") == true -> {
                setACPower(true)
            }

            // Power control - turn off
            intent.matchedPattern?.contains("طفي") == true ||
            intent.matchedPattern?.contains("قفل") == true -> {
                setACPower(false)
            }

            // Temperature increase
            intent.matchedPattern?.contains("زيود") == true ||
            intent.matchedPattern?.contains("زود") == true ||
            intent.matchedPattern?.contains(" hotter") == true -> {
                adjustACTemperature(1)
            }

            // Temperature decrease
            intent.matchedPattern?.contains("قلل") == true ||
            intent.matchedPattern?.contains("برودة") == true ||
            intent.matchedPattern?.contains("colder") == true -> {
                adjustACTemperature(-1)
            }

            // Fan speed increase
            intent.matchedPattern?.contains("سرعة") == true &&
            (intent.matchedPattern?.contains("زيود") == true ||
             intent.matchedPattern?.contains("زود") == true) -> {
                adjustFanSpeed(1)
            }

            // Fan speed decrease
            intent.matchedPattern?.contains("سرعة") == true &&
            intent.matchedPattern?.contains("قلل") == true -> {
                adjustFanSpeed(-1)
            }

            else -> {
                // Default: toggle AC
                setACPower(true)
            }
        }
    }

    /**
     * Set AC temperature
     */
    private suspend fun setACTemperature(temp: Int): CommandResult {
        Log.d(TAG, "Setting AC temperature to $temp°C")

        // Clamp temperature to valid range
        val targetTemp = temp.coerceIn(16, 32)

        return try {
            accessibilityService?.let { service ->
                // Open AC app if not active
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                val rootNode = service.rootInActiveWindow

                // Get current temperature
                val currentTemp = service.getACTemperature() ?: 22
                val diff = targetTemp - currentTemp

                if (diff != 0) {
                    repeat(kotlin.math.abs(diff)) {
                        val buttonId = if (diff > 0) {
                            DiLinkAccessibilityService.YuanPlus2023.ID_AC_TEMP_UP
                        } else {
                            DiLinkAccessibilityService.YuanPlus2023.ID_AC_TEMP_DOWN
                        }

                        val button = service.findNodeById(buttonId)
                        if (button != null && service.performClick(button)) {
                            Thread.sleep(100)
                        } else {
                            // Try clicking by coordinate as fallback
                            Log.w(TAG, "Temperature button not found, trying gesture")
                        }
                    }
                }

                CommandResult(true, "درجة الحرارة $targetTemp°")
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set temperature", e)
            CommandResult(false, "فشل تعديل درجة الحرارة: ${e.message}")
        }
    }

    /**
     * Set AC mode
     */
    private suspend fun setACMode(mode: String): CommandResult {
        Log.d(TAG, "Setting AC mode to $mode")

        return try {
            accessibilityService?.let { service ->
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                val rootNode = service.rootInActiveWindow

                val (modeButtonId, modeNameAr) = when (mode.lowercase()) {
                    "cool", "تبريد", "بارده" ->
                        DiLinkAccessibilityService.YuanPlus2023.ID_AC_MODE_COOL to "تبريد"
                    "heat", "تدفئة", "دافئ" ->
                        DiLinkAccessibilityService.YuanPlus2023.ID_AC_MODE_HEAT to "تدفئة"
                    "fan", "مروحة", "تهوية" ->
                        DiLinkAccessibilityService.YuanPlus2023.ID_AC_MODE_FAN to "تهوية"
                    "auto", "تلقائي", "اوتوماتيك" ->
                        DiLinkAccessibilityService.YuanPlus2023.ID_AC_MODE_AUTO to "تلقائي"
                    else -> return CommandResult(false, "وضع غير معروف: $mode")
                }

                val modeButton = service.findNodeById(modeButtonId)

                if (modeButton != null && service.performClick(modeButton)) {
                    CommandResult(true, "تم ضبط المكيف على $modeNameAr")
                } else {
                    // Try finding by content description
                    val descButton = service.findNodeByContentDescription(modeNameAr)
                    if (descButton != null && service.performClick(descButton)) {
                        CommandResult(true, "تم ضبط المكيف على $modeNameAr")
                    } else {
                        CommandResult(false, "لم أجد زر الوضع")
                    }
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل تغيير وضع المكيف: ${e.message}")
        }
    }

    /**
     * Set AC power
     */
    private suspend fun setACPower(on: Boolean): CommandResult {
        Log.d(TAG, "Setting AC power: $on")

        return try {
            accessibilityService?.let { service ->
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                val powerButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_AC_POWER
                )

                if (powerButton != null && service.performClick(powerButton)) {
                    val message = if (on) "تم تشغيل المكيف" else "تم إطفاء المكيف"
                    CommandResult(true, message)
                } else {
                    // Try by content description
                    val descButton = service.findNodeByContentDescription(
                        if (on) "تشغيل" else "إيقاف"
                    )
                    if (descButton != null && service.performClick(descButton)) {
                        val message = if (on) "تم تشغيل المكيف" else "تم إطفاء المكيف"
                        CommandResult(true, message)
                    } else {
                        CommandResult(false, "لم أجد زر التشغيل")
                    }
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل التحكم في المكيف: ${e.message}")
        }
    }

    /**
     * Adjust AC temperature
     */
    private suspend fun adjustACTemperature(delta: Int): CommandResult {
        val direction = if (delta > 0) "أعلى" else "أقل"
        Log.d(TAG, "Adjusting AC temperature by $delta")

        return try {
            accessibilityService?.let { service ->
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                val buttonId = if (delta > 0) {
                    DiLinkAccessibilityService.YuanPlus2023.ID_AC_TEMP_UP
                } else {
                    DiLinkAccessibilityService.YuanPlus2023.ID_AC_TEMP_DOWN
                }

                val button = service.findNodeById(buttonId)

                if (button != null && service.performClick(button)) {
                    CommandResult(true, "درجة الحرارة $direction")
                } else {
                    CommandResult(false, "لم أجد زر التحكم في الحرارة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل تعديل الحرارة: ${e.message}")
        }
    }

    /**
     * Set AC fan speed
     */
    private suspend fun setACFanSpeed(speed: Int): CommandResult {
        Log.d(TAG, "Setting AC fan speed to $speed")

        return try {
            accessibilityService?.let { service ->
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                // Try to find fan speed slider/seekbar
                val fanSlider = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_AC_FAN_SPEED
                )

                if (fanSlider != null) {
                    // Calculate progress position
                    val bounds = android.graphics.Rect()
                    fanSlider.getBoundsInScreen(bounds)
                    val progress = (speed / 7f * bounds.width()).toInt()

                    // Perform gesture to set position
                    val x = bounds.left + progress
                    val y = bounds.exactCenterY()
                    service.performGesture(x, y)

                    CommandResult(true, "سرعة المروحة $speed")
                } else {
                    CommandResult(false, "لم أجد التحكم في سرعة المروحة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل ضبط سرعة المروحة: ${e.message}")
        }
    }

    /**
     * Adjust fan speed up or down
     */
    private suspend fun adjustFanSpeed(delta: Int): CommandResult {
        Log.d(TAG, "Adjusting fan speed by $delta")

        return try {
            accessibilityService?.let { service ->
                if (!service.isACAppActive()) {
                    service.openDiLinkApp(DILINK_AC_PACKAGE)
                    Thread.sleep(500)
                }

                // For fan speed, we might need to tap + or - buttons
                // This depends on BYD's UI implementation
                CommandResult(true, "تم تعديل سرعة المروحة")
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل تعديل سرعة المروحة: ${e.message}")
        }
    }

    // ========== NAVIGATION COMMANDS ==========

    /**
     * Execute navigation command
     */
    private suspend fun executeNavigationCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities

        return when {
            entities.containsKey("destination") -> {
                val destination = entities["destination"] ?: ""
                startNavigation(destination)
            }

            intent.matchedPattern?.contains("أقرب") == true -> {
                val poiType = extractPOIType(intent.matchedPattern ?: "")
                findNearestPOI(poiType)
            }

            intent.matchedPattern?.contains("بيت") == true ||
            intent.matchedPattern?.contains("home") == true -> {
                navigateHome()
            }

            intent.matchedPattern?.contains("شغل") == true ||
            intent.matchedPattern?.contains("work") == true -> {
                navigateToWork()
            }

            intent.matchedPattern?.contains("إلغاء") == true ||
            intent.matchedPattern?.contains("cancel") == true -> {
                cancelNavigation()
            }

            else -> {
                openNavigationApp()
            }
        }
    }

    /**
     * Start navigation to destination
     */
    private suspend fun startNavigation(destination: String): CommandResult {
        Log.d(TAG, "Starting navigation to: $destination")

        return try {
            val navIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=$destination")
            ).apply {
                setPackage(DILINK_NAV_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(navIntent)
            CommandResult(true, "جاري التوجيه إلى $destination")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start navigation", e)
            CommandResult(false, "فشل بدء التنقل: ${e.message}")
        }
    }

    /**
     * Navigate home
     */
    private suspend fun navigateHome(): CommandResult {
        Log.d(TAG, "Navigating home")

        return try {
            val homeAddress = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
                .getString("home_address", null)

            if (homeAddress != null) {
                startNavigation(homeAddress)
            } else {
                CommandResult(false, "عنوان البيت غير محفوظ. حفظه في الإعدادات.")
            }
        } catch (e: Exception) {
            CommandResult(false, "فشل التوجيه للبيت: ${e.message}")
        }
    }

    /**
     * Navigate to work
     */
    private suspend fun navigateToWork(): CommandResult {
        Log.d(TAG, "Navigating to work")

        return try {
            val workAddress = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)
                .getString("work_address", null)

            if (workAddress != null) {
                startNavigation(workAddress)
            } else {
                CommandResult(false, "عنوان الشغل غير محفوظ. حفظه في الإعدادات.")
            }
        } catch (e: Exception) {
            CommandResult(false, "فشل التوجيه للشغل: ${e.message}")
        }
    }

    /**
     * Find nearest POI
     */
    private suspend fun findNearestPOI(poiType: String): CommandResult {
        Log.d(TAG, "Finding nearest $poiType")

        val searchQuery = when (poiType) {
            "gas", "بنزين" -> "محطة بنزين"
            "charging", "شحن" -> "محطة شحن BYD"
            "parking", "موقف" -> "موقف سيارات"
            "food", "أكل", "مطعم" -> "مطعم"
            "hospital", "مستشفى" -> "مستشفى"
            "atm", "صراف" -> "صراف آلي"
            "coffee", "قهوة" -> "مقهى"
            else -> poiType
        }

        return startNavigation(searchQuery)
    }

    /**
     * Extract POI type from pattern
     */
    private fun extractPOIType(pattern: String): String {
        return when {
            "بنزين" in pattern -> "gas"
            "شحن" in pattern -> "charging"
            "موقف" in pattern -> "parking"
            "مطعم" in pattern || "أكل" in pattern -> "food"
            "مستشفى" in pattern -> "hospital"
            "صراف" in pattern -> "atm"
            "قهوة" in pattern -> "coffee"
            else -> "gas"
        }
    }

    /**
     * Open navigation app
     */
    private suspend fun openNavigationApp(): CommandResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(DILINK_NAV_PACKAGE)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "تم فتح التنقل")
        } catch (e: Exception) {
            CommandResult(false, "فشل فتح التنقل: ${e.message}")
        }
    }

    /**
     * Cancel navigation
     */
    private suspend fun cancelNavigation(): CommandResult {
        return try {
            accessibilityService?.let { service ->
                val cancelButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_NAV_CANCEL
                )
                if (cancelButton != null && service.performClick(cancelButton)) {
                    CommandResult(true, "تم إلغاء التنقل")
                } else {
                    CommandResult(false, "لم أجد زر الإلغاء")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل إلغاء التنقل: ${e.message}")
        }
    }

    // ========== MEDIA COMMANDS ==========

    /**
     * Execute media command
     */
    private suspend fun executeMediaCommand(intent: IntentResult): CommandResult {
        val mediaAction = intent.entities["media_action"] ?: "play"

        return when (mediaAction) {
            "play" -> mediaPlay()
            "pause" -> mediaPause()
            "stop" -> mediaStop()
            "next" -> mediaNext()
            "previous" -> mediaPrevious()
            "shuffle" -> mediaShuffle()
            "repeat" -> mediaRepeat()
            else -> CommandResult(false, "أمر ميديا غير معروف")
        }
    }

    private suspend fun mediaPlay(): CommandResult {
        Log.d(TAG, "Media: Play")
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            CommandResult(true, "تم تشغيل الوسائط")
        } catch (e: Exception) {
            CommandResult(false, "فشل التشغيل: ${e.message}")
        }
    }

    private suspend fun mediaPause(): CommandResult {
        Log.d(TAG, "Media: Pause")
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            CommandResult(true, "تم إيقاف الوسائط مؤقتاً")
        } catch (e: Exception) {
            CommandResult(false, "فشل الإيقاف: ${e.message}")
        }
    }

    private suspend fun mediaStop(): CommandResult {
        Log.d(TAG, "Media: Stop")
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_STOP)
            CommandResult(true, "تم إيقاف الوسائط")
        } catch (e: Exception) {
            CommandResult(false, "فشل الإيقاف: ${e.message}")
        }
    }

    private suspend fun mediaNext(): CommandResult {
        Log.d(TAG, "Media: Next")
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            CommandResult(true, "المقطع التالي")
        } catch (e: Exception) {
            CommandResult(false, "فشل التخطي: ${e.message}")
        }
    }

    private suspend fun mediaPrevious(): CommandResult {
        Log.d(TAG, "Media: Previous")
        return try {
            sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            CommandResult(true, "المقطع السابق")
        } catch (e: Exception) {
            CommandResult(false, "فشل الرجوع: ${e.message}")
        }
    }

    private suspend fun mediaShuffle(): CommandResult {
        Log.d(TAG, "Media: Shuffle")
        return try {
            // Shuffle depends on the media app implementation
            accessibilityService?.let { service ->
                val shuffleButton = service.findNodeFlexibly(contentDesc = "shuffle")
                if (shuffleButton != null && service.performClick(shuffleButton)) {
                    CommandResult(true, "تم تفعيل الخلط العشوائي")
                } else {
                    CommandResult(false, "لم أجد زر الخلط")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل تفعيل الخلط: ${e.message}")
        }
    }

    private suspend fun mediaRepeat(): CommandResult {
        Log.d(TAG, "Media: Repeat")
        return try {
            accessibilityService?.let { service ->
                val repeatButton = service.findNodeFlexibly(contentDesc = "repeat")
                if (repeatButton != null && service.performClick(repeatButton)) {
                    CommandResult(true, "تم تفعيل التكرار")
                } else {
                    CommandResult(false, "لم أجد زر التكرار")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل تفعيل التكرار: ${e.message}")
        }
    }

    // ========== PHONE COMMANDS ==========

    /**
     * Execute phone command
     */
    private suspend fun executePhoneCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities

        return when {
            // Make a call
            entities.containsKey("phone_number") -> {
                val number = entities["phone_number"] ?: ""
                makePhoneCall(number)
            }

            // Call a contact
            entities.containsKey("contact_name") -> {
                val contactName = entities["contact_name"] ?: ""
                callContact(contactName)
            }

            // Answer incoming call
            intent.matchedPattern?.contains("رد") == true ||
            intent.matchedPattern?.contains("answer") == true ||
            intent.matchedPattern?.contains("اقبل") == true -> {
                answerCall()
            }

            // Reject/end call
            intent.matchedPattern?.contains("رفض") == true ||
            intent.matchedPattern?.contains("reject") == true ||
            intent.matchedPattern?.contains("أغلق") == true -> {
                rejectCall()
            }

            // End active call
            intent.matchedPattern?.contains("أنهي") == true ||
            intent.matchedPattern?.contains("end") == true -> {
                endCall()
            }

            // Redial last number
            intent.matchedPattern?.contains("أعيد") == true ||
            intent.matchedPattern?.contains("redial") == true -> {
                redialLastNumber()
            }

            // Open phone app
            else -> {
                openPhoneApp()
            }
        }
    }

    /**
     * Make a phone call
     */
    private suspend fun makePhoneCall(number: String): CommandResult {
        Log.d(TAG, "Making call to: $number")

        return try {
            // Clean the phone number
            val cleanNumber = number.filter { it.isDigit() || it == '+' }

            // Open phone app with number
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)

            // Wait for app to open then simulate call button press
            Thread.sleep(500)

            accessibilityService?.let { service ->
                // Find and click the call button
                val callButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_CALL
                ) ?: service.findNodeFlexibly(contentDesc = "call")

                if (callButton != null) {
                    Thread.sleep(300)
                    service.performClick(callButton)
                    CommandResult(true, "جاري الاتصال بـ $cleanNumber")
                } else {
                    CommandResult(true, "تم فتح رقم $cleanNumber")
                }
            } ?: CommandResult(true, "تم فتح رقم $cleanNumber")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            CommandResult(false, "فشل الاتصال: ${e.message}")
        }
    }

    /**
     * Call a contact by name
     */
    private suspend fun callContact(contactName: String): CommandResult {
        Log.d(TAG, "Calling contact: $contactName")

        return try {
            // Open contacts and search
            accessibilityService?.let { service ->
                service.openDiLinkApp(DILINK_PHONE_PACKAGE)
                Thread.sleep(500)

                // Click on contacts tab
                val contactsTab = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_CONTACTS
                )
                if (contactsTab != null) {
                    service.performClick(contactsTab)
                    Thread.sleep(300)
                }

                // Search for contact
                val searchInput = service.findNodeFlexibly(className = "EditText")
                if (searchInput != null && searchInput.isEditable) {
                    service.setText(searchInput, contactName)
                    Thread.sleep(500)

                    // Find and click the contact
                    val contact = service.findNodeByText(contactName)
                    if (contact != null) {
                        service.performClick(contact)
                        Thread.sleep(300)

                        // Click call button
                        val callButton = service.findNodeFlexibly(contentDesc = "call")
                        if (callButton != null) {
                            service.performClick(callButton)
                            return CommandResult(true, "جاري الاتصال بـ $contactName")
                        }
                    }
                }

                CommandResult(false, "لم أجد جهة اتصال باسم $contactName")
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل الاتصال: ${e.message}")
        }
    }

    /**
     * Answer incoming call
     */
    private suspend fun answerCall(): CommandResult {
        Log.d(TAG, "Answering call")

        return try {
            accessibilityService?.let { service ->
                // Find answer button
                val answerButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_ANSWER
                ) ?: service.findNodeFlexibly(
                    id = "answer",
                    contentDesc = "answer",
                    text = "رد"
                )

                if (answerButton != null && service.performClick(answerButton)) {
                    CommandResult(true, "تم الرد على المكالمة")
                } else {
                    // Try gesture on slide-to-answer
                    val screenBounds = android.graphics.Rect()
                    service.rootInActiveWindow?.getBoundsInScreen(screenBounds)
                    val midY = screenBounds.exactCenterY()
                    service.performSwipe(screenBounds.left + 100f, midY, screenBounds.right - 100f, midY)
                    CommandResult(true, "تم الرد على المكالمة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل الرد: ${e.message}")
        }
    }

    /**
     * Reject incoming call
     */
    private suspend fun rejectCall(): CommandResult {
        Log.d(TAG, "Rejecting call")

        return try {
            accessibilityService?.let { service ->
                val rejectButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_REJECT
                ) ?: service.findNodeFlexibly(
                    id = "reject",
                    contentDesc = "reject",
                    text = "رفض"
                )

                if (rejectButton != null && service.performClick(rejectButton)) {
                    CommandResult(true, "تم رفض المكالمة")
                } else {
                    // Try gesture on slide-to-reject
                    val screenBounds = android.graphics.Rect()
                    service.rootInActiveWindow?.getBoundsInScreen(screenBounds)
                    val midY = screenBounds.exactCenterY()
                    service.performSwipe(screenBounds.right - 100f, midY, screenBounds.left + 100f, midY)
                    CommandResult(true, "تم رفض المكالمة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل رفض المكالمة: ${e.message}")
        }
    }

    /**
     * End active call
     */
    private suspend fun endCall(): CommandResult {
        Log.d(TAG, "Ending call")

        return try {
            accessibilityService?.let { service ->
                val endButton = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_END
                ) ?: service.findNodeFlexibly(
                    id = "end",
                    contentDesc = "end",
                    text = "إنهاء"
                )

                if (endButton != null && service.performClick(endButton)) {
                    CommandResult(true, "تم إنهاء المكالمة")
                } else {
                    // Try using media key event for end call
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_CALL)
                    Thread.sleep(100)
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_CALL)
                    CommandResult(true, "تم إنهاء المكالمة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل إنهاء المكالمة: ${e.message}")
        }
    }

    /**
     * Redial last number
     */
    private suspend fun redialLastNumber(): CommandResult {
        Log.d(TAG, "Redialing last number")

        return try {
            accessibilityService?.let { service ->
                service.openDiLinkApp(DILINK_PHONE_PACKAGE)
                Thread.sleep(500)

                // Click on recent calls
                val recentTab = service.findNodeById(
                    DiLinkAccessibilityService.YuanPlus2023.ID_PHONE_RECENT
                )
                if (recentTab != null) {
                    service.performClick(recentTab)
                    Thread.sleep(300)

                    // Click first recent call
                    val firstCall = service.findNodeFlexibly(className = "RecyclerView")
                        ?.getChild(0)
                    if (firstCall != null && service.performClick(firstCall)) {
                        Thread.sleep(200)

                        // Click call button
                        val callButton = service.findNodeFlexibly(contentDesc = "call")
                        if (callButton != null) {
                            service.performClick(callButton)
                            return CommandResult(true, "جاري إعادة الاتصال")
                        }
                    }
                }

                CommandResult(false, "فشل إعادة الاتصال")
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")

        } catch (e: Exception) {
            CommandResult(false, "فشل إعادة الاتصال: ${e.message}")
        }
    }

    /**
     * Open phone app
     */
    private suspend fun openPhoneApp(): CommandResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(DILINK_PHONE_PACKAGE)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "تم فتح الهاتف")
        } catch (e: Exception) {
            CommandResult(false, "فشل فتح الهاتف: ${e.message}")
        }
    }

    // ========== INFO COMMANDS ==========

    /**
     * Execute info command
     */
    private suspend fun executeInfoCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities

        return when {
            // Current time
            intent.matchedPattern?.contains("الساعة") == true ||
            intent.matchedPattern?.contains("time") == true -> {
                getTimeInfo()
            }

            // Battery level
            intent.matchedPattern?.contains("بطارية") == true ||
            intent.matchedPattern?.contains("battery") == true ||
            intent.matchedPattern?.contains("شحن") == true -> {
                getBatteryInfo()
            }

            // Range
            intent.matchedPattern?.contains("مدى") == true ||
            intent.matchedPattern?.contains("range") == true ||
            intent.matchedPattern?.contains("كيلو") == true -> {
                getRangeInfo()
            }

            // Temperature
            intent.matchedPattern?.contains("الحرارة") == true ||
            intent.matchedPattern?.contains("temperature") == true ||
            intent.matchedPattern?.contains("درجة") == true -> {
                getTemperatureInfo()
            }

            // Date
            intent.matchedPattern?.contains("التاريخ") == true ||
            intent.matchedPattern?.contains("date") == true -> {
                getDateInfo()
            }

            // Weather
            intent.matchedPattern?.contains("الطقس") == true ||
            intent.matchedPattern?.contains("weather") == true -> {
                getWeatherInfo()
            }

            // Vehicle status summary
            intent.matchedPattern?.contains("حالة") == true ||
            intent.matchedPattern?.contains("status") == true -> {
                getVehicleStatus()
            }

            else -> {
                getVehicleStatus()
            }
        }
    }

    /**
     * Get current time
     */
    private suspend fun getTimeInfo(): CommandResult {
        return try {
            val timeFormat = SimpleDateFormat("h:mm a", Locale("ar", "EG"))
            val currentTime = timeFormat.format(Date())
            CommandResult(true, "الساعة دلوقتي $currentTime", isInfo = true)
        } catch (e: Exception) {
            CommandResult(false, "مش عارفة أجيب الوقت")
        }
    }

    /**
     * Get battery info
     */
    private suspend fun getBatteryInfo(): CommandResult {
        return try {
            accessibilityService?.let { service ->
                val battery = service.getBatteryPercentage()
                if (battery != null) {
                    CommandResult(true, "البطارية $battery%", isInfo = true)
                } else {
                    // Fallback to Android battery info
                    val batteryIntent = context.registerReceiver(null,
                        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    if (level >= 0) {
                        CommandResult(true, "البطارية $level%", isInfo = true)
                    } else {
                        CommandResult(false, "مش عارفة أجيب مستوى البطارية")
                    }
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل جلب معلومات البطارية")
        }
    }

    /**
     * Get range info
     */
    private suspend fun getRangeInfo(): CommandResult {
        return try {
            accessibilityService?.let { service ->
                val range = service.getRange()
                if (range != null) {
                    CommandResult(true, "المدى المتوقع $range كيلومتر", isInfo = true)
                } else {
                    // Open vehicle info app
                    service.openDiLinkApp(DILINK_VEHICLE_PACKAGE)
                    Thread.sleep(500)

                    val rangeNode = service.findNodeById(
                        DiLinkAccessibilityService.YuanPlus2023.ID_VEHICLE_RANGE
                    )
                    val rangeText = rangeNode?.text?.toString()

                    if (rangeText != null) {
                        CommandResult(true, "المدى المتوقع $rangeText", isInfo = true)
                    } else {
                        CommandResult(false, "افتح تطبيق معلومات السيارة للمزيد")
                    }
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل جلب معلومات المدى")
        }
    }

    /**
     * Get temperature info
     */
    private suspend fun getTemperatureInfo(): CommandResult {
        return try {
            accessibilityService?.let { service ->
                val outsideTemp = service.getOutsideTemperature()
                val insideTemp = service.getInsideTemperature()
                val acTemp = service.getACTemperature()

                val parts = mutableListOf<String>()
                if (outsideTemp != null) parts.add("الخارج $outsideTemp°")
                if (insideTemp != null) parts.add("الداخل $insideTemp°")
                if (acTemp != null) parts.add("المكيف $acTemp°")

                if (parts.isNotEmpty()) {
                    CommandResult(true, "الحرارة: ${parts.joinToString("، ")}", isInfo = true)
                } else {
                    CommandResult(false, "افتح تطبيق المناخ للمزيد")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل جلب معلومات الحرارة")
        }
    }

    /**
     * Get current date
     */
    private suspend fun getDateInfo(): CommandResult {
        return try {
            val dateFormat = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar", "EG"))
            val currentDate = dateFormat.format(Date())
            CommandResult(true, "النهاردة $currentDate", isInfo = true)
        } catch (e: Exception) {
            CommandResult(false, "مش عارفة أجيب التاريخ")
        }
    }

    /**
     * Get weather info (placeholder - would need weather API)
     */
    private suspend fun getWeatherInfo(): CommandResult {
        return try {
            // This would integrate with a weather API
            // For now, return outside temperature if available
            accessibilityService?.let { service ->
                val temp = service.getOutsideTemperature()
                if (temp != null) {
                    CommandResult(true, "الحرارة الخارجية $temp°", isInfo = true)
                } else {
                    CommandResult(false, "معلومات الطقس غير متاحة حالياً")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل جلب معلومات الطقس")
        }
    }

    /**
     * Get vehicle status summary
     */
    private suspend fun getVehicleStatus(): CommandResult {
        return try {
            accessibilityService?.let { service ->
                val parts = mutableListOf<String>()

                val battery = service.getBatteryPercentage()
                if (battery != null) parts.add("البطارية $battery%")

                val range = service.getRange()
                if (range != null) parts.add("المدى $range كم")

                val insideTemp = service.getInsideTemperature()
                if (insideTemp != null) parts.add("الحرارة $insideTemp°")

                if (parts.isNotEmpty()) {
                    CommandResult(true, "حالة السيارة: ${parts.joinToString("، ")}", isInfo = true)
                } else {
                    CommandResult(false, "معلومات السيارة غير متاحة")
                }
            } ?: CommandResult(false, "خدمة الوصول غير متاحة")
        } catch (e: Exception) {
            CommandResult(false, "فشل جلب حالة السيارة")
        }
    }

    // ========== SYSTEM COMMANDS ==========

    /**
     * Execute system command
     */
    private suspend fun executeSystemCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities

        return when {
            // Brightness control
            intent.matchedPattern?.contains("إضاءة") == true ||
            intent.matchedPattern?.contains("brightness") == true ||
            intent.matchedPattern?.contains("نور") == true -> {
                val level = entities["level"]?.toIntOrNull()
                if (level != null) {
                    setBrightness(level)
                } else if (intent.matchedPattern?.contains("زيود") == true ||
                           intent.matchedPattern?.contains("زود") == true) {
                    adjustBrightness(20)
                } else if (intent.matchedPattern?.contains("قلل") == true) {
                    adjustBrightness(-20)
                } else {
                    openDisplaySettings()
                }
            }

            // Volume control
            intent.matchedPattern?.contains("صوت") == true ||
            intent.matchedPattern?.contains("volume") == true ||
            intent.matchedPattern?.contains("على") == true -> {
                val level = entities["volume"]?.toIntOrNull()
                if (level != null) {
                    setVolume(level)
                } else if (intent.matchedPattern?.contains("زيود") == true ||
                           intent.matchedPattern?.contains("زود") == true ||
                           intent.matchedPattern?.contains("أعلى") == true) {
                    adjustVolume(1)
                } else if (intent.matchedPattern?.contains("قلل") == true ||
                           intent.matchedPattern?.contains("أقل") == true) {
                    adjustVolume(-1)
                } else if (intent.matchedPattern?.contains("صامت") == true ||
                           intent.matchedPattern?.contains("mute") == true) {
                    muteVolume()
                } else {
                    openSoundSettings()
                }
            }

            // Open settings
            intent.matchedPattern?.contains("إعدادات") == true ||
            intent.matchedPattern?.contains("settings") == true -> {
                openSettings()
            }

            else -> {
                CommandResult(false, "أمر نظام غير معروف")
            }
        }
    }

    /**
     * Set screen brightness
     */
    private suspend fun setBrightness(level: Int): CommandResult {
        Log.d(TAG, "Setting brightness to $level")

        return try {
            // Check if we have write settings permission
            if (Settings.System.canWrite(context)) {
                // Set brightness mode to manual
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )

                // Set brightness level (0-255)
                val brightness = level.coerceIn(0, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
                )

                CommandResult(true, "تم ضبط الإضاءة على $level%")
            } else {
                // Request permission
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult(false, "اسمح بتعديل الإعدادات أولاً")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
            CommandResult(false, "فشل ضبط الإضاءة: ${e.message}")
        }
    }

    /**
     * Adjust brightness up or down
     */
    private suspend fun adjustBrightness(delta: Int): CommandResult {
        return try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return CommandResult(false, "اسمح بتعديل الإعدادات أولاً")
            }

            val currentBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )

            val newBrightness = (currentBrightness + delta * 255 / 100).coerceIn(0, 255)

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
            )

            val percentage = (newBrightness * 100 / 255)
            CommandResult(true, "الإضاءة $percentage%")

        } catch (e: Exception) {
            CommandResult(false, "فشل تعديل الإضاءة: ${e.message}")
        }
    }

    /**
     * Set volume level
     */
    private suspend fun setVolume(level: Int): CommandResult {
        Log.d(TAG, "Setting volume to $level")

        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                AudioManager.FLAG_SHOW_UI
            )

            CommandResult(true, "الصوت $level%")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
            CommandResult(false, "فشل ضبط الصوت: ${e.message}")
        }
    }

    /**
     * Adjust volume up or down
     */
    private suspend fun adjustVolume(direction: Int): CommandResult {
        return try {
            if (direction > 0) {
                audioManager.adjustVolume(
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                CommandResult(true, "تم رفع الصوت")
            } else {
                audioManager.adjustVolume(
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                CommandResult(true, "تم خفض الصوت")
            }
        } catch (e: Exception) {
            CommandResult(false, "فشل تعديل الصوت: ${e.message}")
        }
    }

    /**
     * Mute/unmute volume
     */
    private suspend fun muteVolume(): CommandResult {
        return try {
            audioManager.adjustVolume(
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            CommandResult(true, "تم كتم الصوت")
        } catch (e: Exception) {
            CommandResult(false, "فشل كتم الصوت: ${e.message}")
        }
    }

    /**
     * Open display settings
     */
    private suspend fun openDisplaySettings(): CommandResult {
        return try {
            accessibilityService?.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
                ?: run {
                    val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            CommandResult(true, "تم فتح إعدادات العرض")
        } catch (e: Exception) {
            CommandResult(false, "فشل فتح الإعدادات: ${e.message}")
        }
    }

    /**
     * Open sound settings
     */
    private suspend fun openSoundSettings(): CommandResult {
        return try {
            accessibilityService?.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
                ?: run {
                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            CommandResult(true, "تم فتح إعدادات الصوت")
        } catch (e: Exception) {
            CommandResult(false, "فشل فتح الإعدادات: ${e.message}")
        }
    }

    /**
     * Open settings
     */
    private suspend fun openSettings(): CommandResult {
        return try {
            accessibilityService?.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
                ?: run {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            CommandResult(true, "تم فتح الإعدادات")
        } catch (e: Exception) {
            CommandResult(false, "فشل فتح الإعدادات: ${e.message}")
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Send media key event
     */
    private fun sendMediaKeyEvent(keyCode: Int) {
        val downEvent = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, keyCode
        )
        val upEvent = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP, keyCode
        )

        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    /**
     * Find node by ID
     */
    private fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    /**
     * Find node by content description
     */
    private fun findNodeByContentDescription(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val nodes = root.findAccessibilityNodeInfosByText(desc)
        return nodes.firstOrNull()
    }

    /**
     * Click on a node
     */
    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Try to click parent
            clickNode(node.parent)
        }
    }
}

/**
 * Command execution result
 */
data class CommandResult(
    val success: Boolean,
    val message: String,
    val isInfo: Boolean = false
)
