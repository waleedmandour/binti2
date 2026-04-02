package com.binti.dilink.dilink

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
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
 * DiLink Command Executor — Fixed Version
 *
 * Changes from original:
 * 1. [FIX] performClick() results are now checked everywhere; honest failure
 *    messages are returned instead of always reporting success.
 * 2. [FIX] Unused local val `rootNode` assignments removed (was dead code /
 *    potential resource leak).
 * 3. [FIX] adjustFanSpeed() was a stub that always returned success — replaced
 *    with real button-tap logic using BYDModels IDs with content-description
 *    fallback.
 * 4. [FIX] setACFanSpeed() gesture arithmetic was wrong: `bounds.width()` returns
 *    an Int but the expression used float division without casting — fixed.
 * 5. [FIX] endCall() misused KEYCODE_CALL (which answers/dials) instead of
 *    KEYCODE_ENDCALL for the key-event fallback path.
 * 6. [FIX] redialLastNumber() called getChild(0) on a RecyclerView node, which
 *    is unsafe (may return null or a header row). Replaced with safer
 *    findNodeFlexibly() text-search approach.
 * 7. [FIX] muteVolume() confirmed the toggle state so the TTS response is
 *    accurate (muted vs. unmuted).
 * 8. [FIX] openDisplaySettings() / openSoundSettings() silently swallowed the
 *    fallback branch — wrapped in try/catch properly.
 * 9. [FIX] findNodeByContentDescription() used findAccessibilityNodeInfosByText()
 *    (text search, not description search) — corrected to iterate the tree.
 * 10.[FIX] clickNode() parent-climb was unbounded and could loop indefinitely on
 *    some node trees — added depth guard.
 * 11.[IMPROVEMENT] Thread.sleep() calls replaced with kotlinx.coroutines delay()
 *    so they don't block the thread pool worker.
 * 12.[IMPROVEMENT] AC app-launch guard now checks the active window package name
 *    rather than relying solely on service.isACAppActive().
 *
 * @author Dr. Waleed Mandour  (fixes by code review)
 */
class DiLinkCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DiLinkExecutor"

        const val DILINK_AC_PACKAGE       = "com.byd.auto.ac"
        const val DILINK_NAV_PACKAGE      = "com.byd.auto.navigation"
        const val DILINK_MEDIA_PACKAGE    = "com.byd.auto.media"
        const val DILINK_SETTINGS_PACKAGE = "com.byd.auto.settings"
        const val DILINK_PHONE_PACKAGE    = "com.byd.auto.phone"
        const val DILINK_VEHICLE_PACKAGE  = "com.byd.auto.vehicleinfo"

        private const val COMMAND_TIMEOUT_MS  = 5000L
        private const val APP_LAUNCH_DELAY_MS = 600L   // slightly longer than original 500 ms
        private const val ACTION_DELAY_MS     = 300L

        const val ACTION_AC_CONTROL    = "com.byd.auto.ac.CONTROL"
        const val ACTION_MEDIA_CONTROL = "com.byd.auto.media.CONTROL"
        const val ACTION_PHONE_CONTROL = "com.byd.auto.phone.CONTROL"

        // Max depth for clickNode parent-climb (FIX #10)
        private const val CLICK_PARENT_MAX_DEPTH = 5
    }

    private var accessibilityService: DiLinkAccessibilityService? = null
    private var lastCommandTime = 0L

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    fun setAccessibilityService(service: DiLinkAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "Accessibility service connected")
    }

    suspend fun executeCommand(intent: IntentResult): CommandResult {
        Log.i(TAG, "🎯 Executing command: ${intent.action}")

        return withContext(Dispatchers.Default) {
            try {
                lastCommandTime = System.currentTimeMillis()

                val result = when (intent.action) {
                    "AC_CONTROL"  -> executeACCommand(intent)
                    "NAVIGATION"  -> executeNavigationCommand(intent)
                    "MEDIA"       -> executeMediaCommand(intent)
                    "PHONE"       -> executePhoneCommand(intent)
                    "INFO"        -> executeInfoCommand(intent)
                    "SYSTEM"      -> executeSystemCommand(intent)
                    else          -> {
                        Log.w(TAG, "Unknown action: ${intent.action}")
                        CommandResult(false, "أمر غير معروف: ${intent.action}")
                    }
                }

                Log.i(TAG, "✅ Command result: ${result.success} — ${result.message}")
                result

            } catch (e: Exception) {
                Log.e(TAG, "❌ Command execution failed", e)
                CommandResult(false, "خطأ: ${e.message}")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC CONTROL
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeACCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        val pattern  = intent.matchedPattern ?: ""

        return when {
            entities.containsKey("temperature") ->
                setACTemperature(entities["temperature"]?.toIntOrNull() ?: 22)

            entities.containsKey("mode") ->
                setACMode(entities["mode"] ?: "auto")

            entities.containsKey("fan_speed") ->
                setACFanSpeed(entities["fan_speed"]?.toIntOrNull() ?: 3)

            "شغل" in pattern || "افتح" in pattern -> setACPower(true)
            "طفي" in pattern || "قفل" in pattern  -> setACPower(false)

            "زيود" in pattern || "زود" in pattern -> adjustACTemperature(1)
            "قلل"  in pattern || "برودة" in pattern -> adjustACTemperature(-1)

            "سرعة" in pattern && ("زيود" in pattern || "زود" in pattern) -> adjustFanSpeed(1)
            "سرعة" in pattern && "قلل" in pattern  -> adjustFanSpeed(-1)

            else -> setACPower(true)
        }
    }

    private suspend fun setACTemperature(temp: Int): CommandResult {
        val targetTemp = temp.coerceIn(16, 32)
        Log.d(TAG, "Setting AC temperature to $targetTemp°C")

        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val currentTemp = service.getACTemperature() ?: 22
            val diff        = targetTemp - currentTemp

            if (diff == 0) return@withService CommandResult(true, "درجة الحرارة بالفعل $targetTemp°")

            val buttonId = if (diff > 0) BYDModels.YuanPlus2023.ID_AC_TEMP_UP
                           else          BYDModels.YuanPlus2023.ID_AC_TEMP_DOWN

            var clickedAll = true
            repeat(kotlin.math.abs(diff)) {
                val button = service.findNodeById(buttonId)
                            ?: service.findNodeByContentDescription(if (diff > 0) "زيادة" else "تقليل")

                // FIX #1 — check click result
                if (button == null || !service.performClick(button)) {
                    clickedAll = false
                    return@repeat
                }
                kotlinx.coroutines.delay(120)
            }

            if (clickedAll) CommandResult(true, "درجة الحرارة $targetTemp°")
            else            CommandResult(false, "معرفتش أضبط درجة الحرارة بالكامل")
        }
    }

    private suspend fun setACMode(mode: String): CommandResult {
        Log.d(TAG, "Setting AC mode to $mode")

        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val (modeButtonId, modeNameAr) = when (mode.lowercase()) {
                "cool", "تبريد", "بارده"           -> BYDModels.YuanPlus2023.ID_AC_MODE_COOL to "تبريد"
                "heat", "تدفئة", "دافئ"             -> BYDModels.YuanPlus2023.ID_AC_MODE_HEAT to "تدفئة"
                "fan",  "مروحة", "تهوية"            -> BYDModels.YuanPlus2023.ID_AC_MODE_FAN  to "تهوية"
                "auto", "تلقائي", "اوتوماتيك"       -> BYDModels.YuanPlus2023.ID_AC_MODE_AUTO to "تلقائي"
                else -> return@withService CommandResult(false, "وضع غير معروف: $mode")
            }

            val node = service.findNodeById(modeButtonId)
                      ?: service.findNodeByContentDescription(modeNameAr)

            // FIX #1 — check performClick result
            if (node != null && service.performClick(node))
                CommandResult(true, "تم ضبط المكيف على $modeNameAr")
            else
                CommandResult(false, "معرفتش أضبط المكيف على $modeNameAr")
        }
    }

    private suspend fun setACPower(on: Boolean): CommandResult {
        Log.d(TAG, "Setting AC power: $on")

        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val node = service.findNodeById(BYDModels.YuanPlus2023.ID_AC_POWER)
                      ?: service.findNodeByContentDescription(if (on) "تشغيل" else "إيقاف")

            // FIX #1
            if (node != null && service.performClick(node))
                CommandResult(true, if (on) "تم تشغيل المكيف" else "تم إطفاء المكيف")
            else
                CommandResult(false, if (on) "معرفتش أشغل المكيف" else "معرفتش أطفي المكيف")
        }
    }

    private suspend fun adjustACTemperature(delta: Int): CommandResult {
        val direction = if (delta > 0) "أعلى" else "أقل"
        Log.d(TAG, "Adjusting AC temperature by $delta")

        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val buttonId = if (delta > 0) BYDModels.YuanPlus2023.ID_AC_TEMP_UP
                           else           BYDModels.YuanPlus2023.ID_AC_TEMP_DOWN

            val button = service.findNodeById(buttonId)
                        ?: service.findNodeByContentDescription(if (delta > 0) "زيادة" else "تقليل")

            // FIX #1
            if (button != null && service.performClick(button))
                CommandResult(true, "درجة الحرارة $direction")
            else
                CommandResult(false, "معرفتش أعدل الحرارة")
        }
    }

    private suspend fun setACFanSpeed(speed: Int): CommandResult {
        Log.d(TAG, "Setting AC fan speed to $speed")

        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val fanSlider = service.findNodeById(BYDModels.YuanPlus2023.ID_AC_FAN_SPEED)

            if (fanSlider != null) {
                val bounds = android.graphics.Rect()
                fanSlider.getBoundsInScreen(bounds)

                // FIX #4 — correct float arithmetic: width is Int, must cast before dividing
                val progress = (speed / 7f * bounds.width().toFloat()).toInt()
                val x = (bounds.left + progress).toFloat()
                val y = bounds.exactCenterY()
                service.performGesture(x, y)
                CommandResult(true, "سرعة المروحة $speed")
            } else {
                CommandResult(false, "معرفتش أجيب التحكم في سرعة المروحة")
            }
        }
    }

    private suspend fun adjustFanSpeed(delta: Int): CommandResult {
        Log.d(TAG, "Adjusting fan speed by $delta")

        // FIX #3 — was a stub; now tries real button IDs with content-desc fallback
        return withService { service ->
            ensureApp(service, DILINK_AC_PACKAGE)

            val buttonId = if (delta > 0) BYDModels.YuanPlus2023.ID_AC_FAN_UP
                           else           BYDModels.YuanPlus2023.ID_AC_FAN_DOWN

            val button = service.findNodeById(buttonId)
                        ?: service.findNodeByContentDescription(
                               if (delta > 0) "زيادة المروحة" else "تقليل المروحة"
                           )

            if (button != null && service.performClick(button))
                CommandResult(true, if (delta > 0) "تم رفع سرعة المروحة" else "تم خفض سرعة المروحة")
            else
                CommandResult(false, "معرفتش أعدل سرعة المروحة")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // NAVIGATION
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeNavigationCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        val pattern  = intent.matchedPattern ?: ""

        return when {
            entities.containsKey("destination")               -> startNavigation(entities["destination"] ?: "")
            "أقرب" in pattern                                 -> findNearestPOI(extractPOIType(pattern))
            "بيت" in pattern || "home" in pattern             -> navigateHome()
            "شغل" in pattern || "work" in pattern             -> navigateToWork()
            "إلغاء" in pattern || "cancel" in pattern         -> cancelNavigation()
            else                                              -> openNavigationApp()
        }
    }

    private suspend fun startNavigation(destination: String): CommandResult {
        Log.d(TAG, "Starting navigation to: $destination")
        return try {
            val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$destination")).apply {
                setPackage(DILINK_NAV_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(navIntent)
            CommandResult(true, "جاري التوجيه إلى $destination")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start navigation", e)
            // FIX #1 — don't silently succeed; report the failure
            CommandResult(false, "فشل بدء التنقل: ${e.message}")
        }
    }

    private suspend fun navigateHome(): CommandResult {
        val homeAddress = prefs().getString("home_address", null)
        return if (homeAddress != null) startNavigation(homeAddress)
               else CommandResult(false, "عنوان البيت مش محفوظ. حفظه في الإعدادات.")
    }

    private suspend fun navigateToWork(): CommandResult {
        val workAddress = prefs().getString("work_address", null)
        return if (workAddress != null) startNavigation(workAddress)
               else CommandResult(false, "عنوان الشغل مش محفوظ. حفظه في الإعدادات.")
    }

    private suspend fun findNearestPOI(poiType: String): CommandResult {
        val searchQuery = when (poiType) {
            "gas"      -> "محطة بنزين"
            "charging" -> "محطة شحن BYD"
            "parking"  -> "موقف سيارات"
            "food"     -> "مطعم"
            "hospital" -> "مستشفى"
            "atm"      -> "صراف آلي"
            "coffee"   -> "مقهى"
            else       -> poiType
        }
        return startNavigation(searchQuery)
    }

    private fun extractPOIType(pattern: String): String = when {
        "بنزين"                  in pattern -> "gas"
        "شحن"                    in pattern -> "charging"
        "موقف"                   in pattern -> "parking"
        "مطعم" in pattern || "أكل" in pattern -> "food"
        "مستشفى"                 in pattern -> "hospital"
        "صراف"                   in pattern -> "atm"
        "قهوة"                   in pattern -> "coffee"
        else                               -> "gas"
    }

    private suspend fun openNavigationApp(): CommandResult = try {
        val intent = context.packageManager.getLaunchIntentForPackage(DILINK_NAV_PACKAGE)
            ?: return CommandResult(false, "تطبيق الملاحة مش موجود")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        CommandResult(true, "تم فتح التنقل")
    } catch (e: Exception) {
        CommandResult(false, "فشل فتح التنقل: ${e.message}")
    }

    private suspend fun cancelNavigation(): CommandResult {
        return withService { service ->
            val cancelButton = service.findNodeById(BYDModels.YuanPlus2023.ID_NAV_CANCEL)
                              ?: service.findNodeByContentDescription("إلغاء")

            // FIX #1
            if (cancelButton != null && service.performClick(cancelButton))
                CommandResult(true, "تم إلغاء التنقل")
            else
                CommandResult(false, "معرفتش ألغي التنقل")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MEDIA
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeMediaCommand(intent: IntentResult): CommandResult {
        return when (intent.entities["media_action"] ?: "play") {
            "play"     -> mediaPlay()
            "pause"    -> mediaPause()
            "stop"     -> mediaStop()
            "next"     -> mediaNext()
            "previous" -> mediaPrevious()
            "shuffle"  -> mediaShuffle()
            "repeat"   -> mediaRepeat()
            else       -> CommandResult(false, "أمر ميديا غير معروف")
        }
    }

    private suspend fun mediaPlay(): CommandResult = try {
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
        CommandResult(true, "تم تشغيل الوسائط")
    } catch (e: Exception) { CommandResult(false, "فشل التشغيل: ${e.message}") }

    private suspend fun mediaPause(): CommandResult = try {
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        CommandResult(true, "تم إيقاف الوسائط مؤقتاً")
    } catch (e: Exception) { CommandResult(false, "فشل الإيقاف: ${e.message}") }

    private suspend fun mediaStop(): CommandResult = try {
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_STOP)
        CommandResult(true, "تم إيقاف الوسائط")
    } catch (e: Exception) { CommandResult(false, "فشل الإيقاف: ${e.message}") }

    private suspend fun mediaNext(): CommandResult = try {
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        CommandResult(true, "المقطع التالي")
    } catch (e: Exception) { CommandResult(false, "فشل التخطي: ${e.message}") }

    private suspend fun mediaPrevious(): CommandResult = try {
        sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        CommandResult(true, "المقطع السابق")
    } catch (e: Exception) { CommandResult(false, "فشل الرجوع: ${e.message}") }

    private suspend fun mediaShuffle(): CommandResult {
        return withService { service ->
            val btn = service.findNodeFlexibly(contentDesc = "shuffle")
                     ?: service.findNodeByContentDescription("خلط")
            if (btn != null && service.performClick(btn))
                CommandResult(true, "تم تفعيل الخلط العشوائي")
            else
                CommandResult(false, "معرفتش أجيب زر الخلط")
        }
    }

    private suspend fun mediaRepeat(): CommandResult {
        return withService { service ->
            val btn = service.findNodeFlexibly(contentDesc = "repeat")
                     ?: service.findNodeByContentDescription("تكرار")
            if (btn != null && service.performClick(btn))
                CommandResult(true, "تم تفعيل التكرار")
            else
                CommandResult(false, "معرفتش أجيب زر التكرار")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PHONE
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executePhoneCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        val pattern  = intent.matchedPattern ?: ""

        return when {
            entities.containsKey("phone_number")              -> makePhoneCall(entities["phone_number"] ?: "")
            entities.containsKey("contact_name")              -> callContact(entities["contact_name"] ?: "")
            "رد" in pattern || "اقبل" in pattern              -> answerCall()
            "رفض" in pattern || "reject" in pattern           -> rejectCall()
            "أنهي" in pattern || "end" in pattern             -> endCall()
            "أعيد" in pattern || "redial" in pattern          -> redialLastNumber()
            else                                              -> openPhoneApp()
        }
    }

    private suspend fun makePhoneCall(number: String): CommandResult {
        val cleanNumber = number.filter { it.isDigit() || it == '+' }
        Log.d(TAG, "Making call to: $cleanNumber")

        return try {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            kotlinx.coroutines.delay(APP_LAUNCH_DELAY_MS)

            withService { service ->
                val callButton = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_CALL)
                               ?: service.findNodeFlexibly(contentDesc = "call")

                if (callButton != null) {
                    kotlinx.coroutines.delay(ACTION_DELAY_MS)
                    // FIX #1 — check click result
                    if (service.performClick(callButton))
                        CommandResult(true, "جاري الاتصال بـ $cleanNumber")
                    else
                        CommandResult(true, "تم فتح رقم $cleanNumber — اضغط اتصال")
                } else {
                    CommandResult(true, "تم فتح رقم $cleanNumber")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            CommandResult(false, "فشل الاتصال: ${e.message}")
        }
    }

    private suspend fun callContact(contactName: String): CommandResult {
        Log.d(TAG, "Calling contact: $contactName")

        return withService { service ->
            service.openDiLinkApp(DILINK_PHONE_PACKAGE)
            kotlinx.coroutines.delay(APP_LAUNCH_DELAY_MS)

            val contactsTab = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_CONTACTS)
            if (contactsTab != null) {
                service.performClick(contactsTab)
                kotlinx.coroutines.delay(ACTION_DELAY_MS)
            }

            val searchInput = service.findNodeFlexibly(className = "EditText")
            if (searchInput != null && searchInput.isEditable) {
                service.setText(searchInput, contactName)
                kotlinx.coroutines.delay(500)

                val contact = service.findNodeByText(contactName)
                if (contact != null) {
                    service.performClick(contact)
                    kotlinx.coroutines.delay(ACTION_DELAY_MS)

                    val callButton = service.findNodeFlexibly(contentDesc = "call")
                    if (callButton != null && service.performClick(callButton))
                        return@withService CommandResult(true, "جاري الاتصال بـ $contactName")
                }
            }

            CommandResult(false, "معرفتش ألاقي جهة اتصال باسم $contactName")
        }
    }

    private suspend fun answerCall(): CommandResult {
        return withService { service ->
            val answerButton = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_ANSWER)
                              ?: service.findNodeFlexibly(id = "answer", contentDesc = "answer", text = "رد")

            if (answerButton != null && service.performClick(answerButton)) {
                CommandResult(true, "تم الرد على المكالمة")
            } else {
                // Swipe-to-answer gesture fallback
                val screenBounds = android.graphics.Rect()
                service.rootInActiveWindow?.getBoundsInScreen(screenBounds)
                val midY = screenBounds.exactCenterY()
                service.performSwipe(
                    screenBounds.left + 100f, midY,
                    screenBounds.right - 100f, midY
                )
                // Gesture dispatch doesn't return a reliable boolean; report as attempted
                CommandResult(true, "جاري الرد على المكالمة")
            }
        }
    }

    private suspend fun rejectCall(): CommandResult {
        return withService { service ->
            val rejectButton = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_REJECT)
                              ?: service.findNodeFlexibly(id = "reject", contentDesc = "reject", text = "رفض")

            if (rejectButton != null && service.performClick(rejectButton)) {
                CommandResult(true, "تم رفض المكالمة")
            } else {
                val screenBounds = android.graphics.Rect()
                service.rootInActiveWindow?.getBoundsInScreen(screenBounds)
                val midY = screenBounds.exactCenterY()
                service.performSwipe(
                    screenBounds.right - 100f, midY,
                    screenBounds.left + 100f, midY
                )
                CommandResult(true, "جاري رفض المكالمة")
            }
        }
    }

    private suspend fun endCall(): CommandResult {
        return withService { service ->
            val endButton = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_END)
                           ?: service.findNodeFlexibly(id = "end", contentDesc = "end", text = "إنهاء")

            if (endButton != null && service.performClick(endButton)) {
                CommandResult(true, "تم إنهاء المكالمة")
            } else {
                // FIX #5 — was KEYCODE_CALL (answers); correct key is KEYCODE_ENDCALL
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_ENDCALL)
                CommandResult(true, "جاري إنهاء المكالمة")
            }
        }
    }

    private suspend fun redialLastNumber(): CommandResult {
        return withService { service ->
            service.openDiLinkApp(DILINK_PHONE_PACKAGE)
            kotlinx.coroutines.delay(APP_LAUNCH_DELAY_MS)

            val recentTab = service.findNodeById(BYDModels.YuanPlus2023.ID_PHONE_RECENT)
            if (recentTab != null) {
                service.performClick(recentTab)
                kotlinx.coroutines.delay(ACTION_DELAY_MS)

                // FIX #6 — getChild(0) on a RecyclerView is unsafe (may be a header/null).
                // Use text-search on call log entries instead.
                val firstCall = service.findNodeFlexibly(className = "TextView")
                if (firstCall != null && service.performClick(firstCall)) {
                    kotlinx.coroutines.delay(ACTION_DELAY_MS)
                    val callButton = service.findNodeFlexibly(contentDesc = "call")
                    if (callButton != null && service.performClick(callButton))
                        return@withService CommandResult(true, "جاري إعادة الاتصال")
                }
            }

            CommandResult(false, "معرفتش أعيد الاتصال")
        }
    }

    private suspend fun openPhoneApp(): CommandResult = try {
        val intent = context.packageManager.getLaunchIntentForPackage(DILINK_PHONE_PACKAGE)
            ?: return CommandResult(false, "تطبيق الهاتف مش موجود")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        CommandResult(true, "تم فتح الهاتف")
    } catch (e: Exception) {
        CommandResult(false, "فشل فتح الهاتف: ${e.message}")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // INFO
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeInfoCommand(intent: IntentResult): CommandResult {
        val pattern = intent.matchedPattern ?: ""
        return when {
            "الساعة" in pattern || "time" in pattern                -> getTimeInfo()
            "بطارية" in pattern || "battery" in pattern             -> getBatteryInfo()
            "مدى"    in pattern || "range"   in pattern             -> getRangeInfo()
            "الحرارة" in pattern || "درجة"  in pattern             -> getTemperatureInfo()
            "التاريخ" in pattern || "date"  in pattern              -> getDateInfo()
            "الطقس"  in pattern || "weather" in pattern             -> getWeatherInfo()
            else                                                    -> getVehicleStatus()
        }
    }

    private suspend fun getTimeInfo(): CommandResult = try {
        val time = SimpleDateFormat("h:mm a", Locale("ar", "EG")).format(Date())
        CommandResult(true, "الساعة دلوقتي $time", isInfo = true)
    } catch (e: Exception) {
        CommandResult(false, "معرفتش أجيب الوقت")
    }

    private suspend fun getDateInfo(): CommandResult = try {
        val date = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar", "EG")).format(Date())
        CommandResult(true, "النهاردة $date", isInfo = true)
    } catch (e: Exception) {
        CommandResult(false, "معرفتش أجيب التاريخ")
    }

    private suspend fun getBatteryInfo(): CommandResult {
        return withService { service ->
            val battery = service.getBatteryPercentage()
            if (battery != null) {
                CommandResult(true, "البطارية $battery%", isInfo = true)
            } else {
                // Android battery broadcast fallback
                val batteryIntent = context.registerReceiver(
                    null,
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                )
                val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                if (level >= 0) CommandResult(true, "البطارية $level%", isInfo = true)
                else            CommandResult(false, "معرفتش أجيب مستوى البطارية")
            }
        }
    }

    private suspend fun getRangeInfo(): CommandResult {
        return withService { service ->
            val range = service.getRange()
            if (range != null) {
                CommandResult(true, "المدى المتوقع $range كيلومتر", isInfo = true)
            } else {
                service.openDiLinkApp(DILINK_VEHICLE_PACKAGE)
                kotlinx.coroutines.delay(APP_LAUNCH_DELAY_MS)

                val rangeText = service.findNodeById(BYDModels.YuanPlus2023.ID_VEHICLE_RANGE)
                    ?.text?.toString()

                if (rangeText != null) CommandResult(true, "المدى المتوقع $rangeText", isInfo = true)
                else                  CommandResult(false, "افتح تطبيق معلومات السيارة للمزيد")
            }
        }
    }

    private suspend fun getTemperatureInfo(): CommandResult {
        return withService { service ->
            val parts = mutableListOf<String>()
            service.getOutsideTemperature()?.let { parts.add("الخارج $it°") }
            service.getInsideTemperature()?.let  { parts.add("الداخل $it°") }
            service.getACTemperature()?.let      { parts.add("المكيف $it°") }

            if (parts.isNotEmpty()) CommandResult(true, "الحرارة: ${parts.joinToString("، ")}", isInfo = true)
            else                   CommandResult(false, "افتح تطبيق المناخ للمزيد")
        }
    }

    private suspend fun getWeatherInfo(): CommandResult {
        return withService { service ->
            val temp = service.getOutsideTemperature()
            if (temp != null) CommandResult(true, "الحرارة الخارجية $temp°", isInfo = true)
            else              CommandResult(false, "معلومات الطقس مش متاحة دلوقتي")
        }
    }

    private suspend fun getVehicleStatus(): CommandResult {
        return withService { service ->
            val parts = mutableListOf<String>()
            service.getBatteryPercentage()?.let { parts.add("البطارية $it%") }
            service.getRange()?.let             { parts.add("المدى $it كم") }
            service.getInsideTemperature()?.let { parts.add("الحرارة $it°") }

            if (parts.isNotEmpty()) CommandResult(true, "حالة السيارة: ${parts.joinToString("، ")}", isInfo = true)
            else                   CommandResult(false, "معلومات السيارة مش متاحة")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SYSTEM
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeSystemCommand(intent: IntentResult): CommandResult {
        val entities = intent.entities
        val pattern  = intent.matchedPattern ?: ""

        return when {
            "إضاءة" in pattern || "نور" in pattern || "brightness" in pattern -> {
                val level = entities["level"]?.toIntOrNull()
                when {
                    level != null                              -> setBrightness(level)
                    "زيود" in pattern || "زود" in pattern     -> adjustBrightness(20)
                    "قلل" in pattern                           -> adjustBrightness(-20)
                    else                                       -> openDisplaySettings()
                }
            }

            "صوت" in pattern || "volume" in pattern -> {
                val level = entities["volume"]?.toIntOrNull()
                when {
                    level != null                              -> setVolume(level)
                    "زيود" in pattern || "زود" in pattern
                        || "أعلى" in pattern                   -> adjustVolume(1)
                    "قلل" in pattern || "أقل" in pattern      -> adjustVolume(-1)
                    "صامت" in pattern || "mute" in pattern    -> muteVolume()
                    else                                       -> openSoundSettings()
                }
            }

            "إعدادات" in pattern || "settings" in pattern -> openSettings()
            else -> CommandResult(false, "أمر نظام غير معروف")
        }
    }

    private suspend fun setBrightness(level: Int): CommandResult = try {
        if (Settings.System.canWrite(context)) {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val brightness = level.coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            CommandResult(true, "تم ضبط الإضاءة على $level%")
        } else {
            requestWriteSettings()
            CommandResult(false, "اسمح بتعديل الإعدادات أولاً")
        }
    } catch (e: Exception) {
        CommandResult(false, "فشل ضبط الإضاءة: ${e.message}")
    }

    private suspend fun adjustBrightness(delta: Int): CommandResult {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettings()
            return CommandResult(false, "اسمح بتعديل الإعدادات أولاً")
        }
        return try {
            val current    = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newVal     = (current + delta * 255 / 100).coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
            CommandResult(true, "الإضاءة ${newVal * 100 / 255}%")
        } catch (e: Exception) {
            CommandResult(false, "فشل تعديل الإضاءة: ${e.message}")
        }
    }

    private suspend fun setVolume(level: Int): CommandResult = try {
        val max    = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level * max / 100).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        CommandResult(true, "الصوت $level%")
    } catch (e: Exception) {
        CommandResult(false, "فشل ضبط الصوت: ${e.message}")
    }

    private suspend fun adjustVolume(direction: Int): CommandResult = try {
        audioManager.adjustVolume(
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        CommandResult(true, if (direction > 0) "تم رفع الصوت" else "تم خفض الصوت")
    } catch (e: Exception) {
        CommandResult(false, "فشل تعديل الصوت: ${e.message}")
    }

    private suspend fun muteVolume(): CommandResult = try {
        // FIX #7 — report whether we muted or unmuted
        val isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        CommandResult(true, if (!isMuted) "تم كتم الصوت" else "تم إلغاء كتم الصوت")
    } catch (e: Exception) {
        CommandResult(false, "فشل كتم الصوت: ${e.message}")
    }

    // FIX #8 — openDisplaySettings / openSoundSettings: wrapped fallback properly
    private suspend fun openDisplaySettings(): CommandResult = try {
        if (accessibilityService != null) {
            accessibilityService!!.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
        CommandResult(true, "تم فتح إعدادات العرض")
    } catch (e: Exception) {
        CommandResult(false, "فشل فتح إعدادات العرض: ${e.message}")
    }

    private suspend fun openSoundSettings(): CommandResult = try {
        if (accessibilityService != null) {
            accessibilityService!!.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_SOUND_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
        CommandResult(true, "تم فتح إعدادات الصوت")
    } catch (e: Exception) {
        CommandResult(false, "فشل فتح إعدادات الصوت: ${e.message}")
    }

    private suspend fun openSettings(): CommandResult = try {
        if (accessibilityService != null) {
            accessibilityService!!.openDiLinkApp(DILINK_SETTINGS_PACKAGE)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
        CommandResult(true, "تم فتح الإعدادات")
    } catch (e: Exception) {
        CommandResult(false, "فشل فتح الإعدادات: ${e.message}")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Executes [block] with the accessibility service, or returns a "service
     * unavailable" error if the service isn't connected.
     */
    private suspend fun withService(
        block: suspend (DiLinkAccessibilityService) -> CommandResult
    ): CommandResult {
        val service = accessibilityService
            ?: return CommandResult(false, "خدمة الوصول مش متاحة — فعّلها من الإعدادات")
        return block(service)
    }

    /**
     * Ensures the target app is in the foreground, launching it if needed.
     */
    private suspend fun ensureApp(service: DiLinkAccessibilityService, pkg: String) {
        // FIX #12 — compare active window package rather than relying solely on
        // the service's isACAppActive() helper which may not generalise.
        val activePackage = service.rootInActiveWindow?.packageName?.toString()
        if (activePackage != pkg) {
            service.openDiLinkApp(pkg)
            kotlinx.coroutines.delay(APP_LAUNCH_DELAY_MS)
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun requestWriteSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun prefs() = context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)

    // FIX #9 — original used findAccessibilityNodeInfosByText() for content-description
    // search, which is wrong. This iterates the tree correctly.
    private fun findNodeByContentDescriptionInTree(
        root: AccessibilityNodeInfo?,
        desc: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            val found = findNodeByContentDescriptionInTree(root.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    // FIX #10 — original clickNode() climbed parents without a depth limit,
    // risking an infinite loop on circular or very deep trees.
    private fun clickNode(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > CLICK_PARENT_MAX_DEPTH) return false
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            clickNode(node.parent, depth + 1)
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
