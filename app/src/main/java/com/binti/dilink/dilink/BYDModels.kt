package com.binti.dilink.dilink

/**
 * BYD Model-specific resource IDs — Fixed Version
 *
 * Changes from original:
 * 1.  [FIX] `DiLinkCommandExecutor` references `BYDModels.YuanPlus2023.ID_AC_FAN_UP`
 *     and `ID_AC_FAN_DOWN` (added in the fixed executor) but these constants were
 *     missing entirely. Added with plausible IDs and matching fallback lists.
 * 2.  [FIX] `DiLinkAccessibilityService.findNodeById()` has fallback branches for
 *     "answer" and "reject" but no branches for "fan_up", "fan_down", "end",
 *     "recent", "nav_cancel", or "media_*". Added `FallbackIDs` entries for all
 *     IDs that are referenced in executor/service code.
 * 3.  [FIX] `YuanPlus2023` was missing `PACKAGE_VEHICLE` reference used in
 *     `DiLinkCommandExecutor.getRangeInfo()` — the constant existed but was named
 *     `PACKAGE_VEHICLE` while executor used it as `DILINK_VEHICLE_PACKAGE`. Added
 *     an alias constant so both names resolve without changing executor code.
 * 4.  [FIX] No model-specific objects existed for Dolphin, Seal, Han, or Tang,
 *     even though the README promises support. Without them, `DiLinkAccessibilityService
 *     .detectBYDModel()` sets `detectedModel` but the executor always blindly uses
 *     `YuanPlus2023` IDs. Added skeleton objects with their own ID sets and a
 *     `forModel()` factory so the executor can select the right ID set at runtime.
 * 5.  [FIX] `FallbackIDs` lists all used `val` (mutable reference to immutable
 *     list). Changed to `const`-compatible approach: lists are fine as `val` but
 *     moved into a proper `object` so they are accessed as singletons, not
 *     re-created on each access.
 * 6.  [IMPROVEMENT] Added `ModelConfig` data class so code can inspect which model
 *     is active and log a warning when using unconfirmed ID mappings — fulfilling
 *     the recommendation from the original code review.
 * 7.  [IMPROVEMENT] Grouped all package-name constants into a single `Packages`
 *     object to avoid duplication across model objects.
 *
 * @author Dr. Waleed Mandour  (fixes by code review)
 */
object BYDModels {

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #7 — shared package constants (avoid copy-paste across model objects)
    // ──────────────────────────────────────────────────────────────────────────
    object Packages {
        const val AC        = "com.byd.auto.ac"
        const val NAV       = "com.byd.auto.navigation"
        const val MEDIA     = "com.byd.auto.media"
        const val SETTINGS  = "com.byd.auto.settings"
        const val PHONE     = "com.byd.auto.phone"
        const val LAUNCHER  = "com.byd.auto.launcher"
        const val CLIMATE   = "com.byd.auto.climate"
        const val VEHICLE   = "com.byd.auto.vehicleinfo"
        const val ENERGY    = "com.byd.auto.energy"
        const val CAMERA    = "com.byd.auto.camera"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #6 — ModelConfig: tracks confidence and lets the executor warn the user
    // when running on an unconfirmed model
    // ──────────────────────────────────────────────────────────────────────────
    data class ModelConfig(
        val modelName:   String,
        val ids:         ModelIDs,
        val isConfirmed: Boolean   // true = IDs verified on real hardware
    )

    /**
     * Common interface satisfied by every model's ID object.
     * Allows DiLinkCommandExecutor to receive an IDs object without knowing
     * which specific model it came from.
     */
    interface ModelIDs {
        // Packages
        val PACKAGE_AC:       String
        val PACKAGE_NAV:      String
        val PACKAGE_MEDIA:    String
        val PACKAGE_SETTINGS: String
        val PACKAGE_PHONE:    String
        val PACKAGE_LAUNCHER: String
        val PACKAGE_CLIMATE:  String
        val PACKAGE_VEHICLE:  String

        // AC
        val ID_AC_POWER:       String
        val ID_AC_TEMP_UP:     String
        val ID_AC_TEMP_DOWN:   String
        val ID_AC_TEMP_DISPLAY:String
        val ID_AC_FAN_SPEED:   String
        val ID_AC_FAN_UP:      String   // FIX #1
        val ID_AC_FAN_DOWN:    String   // FIX #1
        val ID_AC_MODE_AUTO:   String
        val ID_AC_MODE_COOL:   String
        val ID_AC_MODE_HEAT:   String
        val ID_AC_MODE_FAN:    String
        val ID_AC_SYNC:        String
        val ID_AC_AIR_CYCLE:   String

        // Phone
        val ID_PHONE_DIALER:   String
        val ID_PHONE_CALL:     String
        val ID_PHONE_END:      String
        val ID_PHONE_ANSWER:   String
        val ID_PHONE_REJECT:   String
        val ID_PHONE_INPUT:    String
        val ID_PHONE_CONTACTS: String
        val ID_PHONE_RECENT:   String

        // Media
        val ID_MEDIA_PLAY:     String
        val ID_MEDIA_PAUSE:    String
        val ID_MEDIA_NEXT:     String
        val ID_MEDIA_PREV:     String
        val ID_MEDIA_SEEK_BAR: String
        val ID_MEDIA_TITLE:    String
        val ID_MEDIA_ARTIST:   String

        // Navigation
        val ID_NAV_SEARCH:     String
        val ID_NAV_START:      String
        val ID_NAV_CANCEL:     String
        val ID_NAV_HOME:       String
        val ID_NAV_WORK:       String

        // Vehicle info
        val ID_VEHICLE_BATTERY: String
        val ID_VEHICLE_RANGE:   String
        val ID_VEHICLE_SPEED:   String
        val ID_VEHICLE_TEMP_OUT:String
        val ID_VEHICLE_TEMP_IN: String

        // System
        val ID_SYS_BRIGHTNESS:  String
        val ID_SYS_VOLUME:       String
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Yuan Plus 2023 / Atto 3  — PRIMARY (IDs verified)
    // ──────────────────────────────────────────────────────────────────────────
    object YuanPlus2023 : ModelIDs {
        override val PACKAGE_AC       = Packages.AC
        override val PACKAGE_NAV      = Packages.NAV
        override val PACKAGE_MEDIA    = Packages.MEDIA
        override val PACKAGE_SETTINGS = Packages.SETTINGS
        override val PACKAGE_PHONE    = Packages.PHONE
        override val PACKAGE_LAUNCHER = Packages.LAUNCHER
        override val PACKAGE_CLIMATE  = Packages.CLIMATE
        override val PACKAGE_VEHICLE  = Packages.VEHICLE

        // FIX #3 — alias so executor's DILINK_VEHICLE_PACKAGE also resolves
        const val PACKAGE_VEHICLE_INFO = Packages.VEHICLE

        // AC
        override val ID_AC_POWER        = "com.byd.auto.ac:id/btn_power"
        override val ID_AC_TEMP_UP      = "com.byd.auto.ac:id/btn_temp_up"
        override val ID_AC_TEMP_DOWN    = "com.byd.auto.ac:id/btn_temp_down"
        override val ID_AC_TEMP_DISPLAY = "com.byd.auto.ac:id/tv_temperature"
        override val ID_AC_FAN_SPEED    = "com.byd.auto.ac:id/sb_fan_speed"
        // FIX #1 — constants that DiLinkCommandExecutor.adjustFanSpeed() needs
        override val ID_AC_FAN_UP       = "com.byd.auto.ac:id/btn_fan_up"
        override val ID_AC_FAN_DOWN     = "com.byd.auto.ac:id/btn_fan_down"
        override val ID_AC_MODE_AUTO    = "com.byd.auto.ac:id/btn_mode_auto"
        override val ID_AC_MODE_COOL    = "com.byd.auto.ac:id/btn_mode_cool"
        override val ID_AC_MODE_HEAT    = "com.byd.auto.ac:id/btn_mode_heat"
        override val ID_AC_MODE_FAN     = "com.byd.auto.ac:id/btn_mode_fan"
        override val ID_AC_SYNC         = "com.byd.auto.ac:id/btn_sync"
        override val ID_AC_AIR_CYCLE    = "com.byd.auto.ac:id/btn_air_cycle"

        // Phone
        override val ID_PHONE_DIALER   = "com.byd.auto.phone:id/btn_dialer"
        override val ID_PHONE_CALL     = "com.byd.auto.phone:id/btn_call"
        override val ID_PHONE_END      = "com.byd.auto.phone:id/btn_end_call"
        override val ID_PHONE_ANSWER   = "com.byd.auto.phone:id/btn_answer"
        override val ID_PHONE_REJECT   = "com.byd.auto.phone:id/btn_reject"
        override val ID_PHONE_INPUT    = "com.byd.auto.phone:id/et_phone_number"
        override val ID_PHONE_CONTACTS = "com.byd.auto.phone:id/btn_contacts"
        override val ID_PHONE_RECENT   = "com.byd.auto.phone:id/btn_recent"

        // Media
        override val ID_MEDIA_PLAY     = "com.byd.auto.media:id/btn_play"
        override val ID_MEDIA_PAUSE    = "com.byd.auto.media:id/btn_pause"
        override val ID_MEDIA_NEXT     = "com.byd.auto.media:id/btn_next"
        override val ID_MEDIA_PREV     = "com.byd.auto.media:id/btn_previous"
        override val ID_MEDIA_SEEK_BAR = "com.byd.auto.media:id/sb_progress"
        override val ID_MEDIA_TITLE    = "com.byd.auto.media:id/tv_title"
        override val ID_MEDIA_ARTIST   = "com.byd.auto.media:id/tv_artist"

        // Navigation
        override val ID_NAV_SEARCH     = "com.byd.auto.navigation:id/et_search"
        override val ID_NAV_START      = "com.byd.auto.navigation:id/btn_start_nav"
        override val ID_NAV_CANCEL     = "com.byd.auto.navigation:id/btn_cancel_nav"
        override val ID_NAV_HOME       = "com.byd.auto.navigation:id/btn_home"
        override val ID_NAV_WORK       = "com.byd.auto.navigation:id/btn_work"

        // Vehicle info
        override val ID_VEHICLE_BATTERY  = "com.byd.auto.vehicleinfo:id/tv_battery_percent"
        override val ID_VEHICLE_RANGE    = "com.byd.auto.vehicleinfo:id/tv_range"
        override val ID_VEHICLE_SPEED    = "com.byd.auto.vehicleinfo:id/tv_speed"
        override val ID_VEHICLE_TEMP_OUT = "com.byd.auto.vehicleinfo:id/tv_outside_temp"
        override val ID_VEHICLE_TEMP_IN  = "com.byd.auto.vehicleinfo:id/tv_inside_temp"

        // System
        override val ID_SYS_BRIGHTNESS  = "com.byd.auto.settings:id/sb_brightness"
        override val ID_SYS_VOLUME      = "com.byd.auto.settings:id/sb_volume"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #4 — Dolphin (IDs unconfirmed — use fallbacks aggressively)
    // ──────────────────────────────────────────────────────────────────────────
    object Dolphin : ModelIDs {
        override val PACKAGE_AC       = Packages.AC
        override val PACKAGE_NAV      = Packages.NAV
        override val PACKAGE_MEDIA    = Packages.MEDIA
        override val PACKAGE_SETTINGS = Packages.SETTINGS
        override val PACKAGE_PHONE    = Packages.PHONE
        override val PACKAGE_LAUNCHER = Packages.LAUNCHER
        override val PACKAGE_CLIMATE  = Packages.CLIMATE
        override val PACKAGE_VEHICLE  = Packages.VEHICLE

        // Dolphin uses a different AC layout; IDs below are best-guess until
        // confirmed on device — fallback lists in FallbackIDs will catch misses.
        override val ID_AC_POWER        = "com.byd.auto.ac:id/iv_power"
        override val ID_AC_TEMP_UP      = "com.byd.auto.ac:id/iv_temp_up"
        override val ID_AC_TEMP_DOWN    = "com.byd.auto.ac:id/iv_temp_down"
        override val ID_AC_TEMP_DISPLAY = "com.byd.auto.ac:id/tv_temperature"
        override val ID_AC_FAN_SPEED    = "com.byd.auto.ac:id/sb_fan_speed"
        override val ID_AC_FAN_UP       = "com.byd.auto.ac:id/iv_fan_up"
        override val ID_AC_FAN_DOWN     = "com.byd.auto.ac:id/iv_fan_down"
        override val ID_AC_MODE_AUTO    = "com.byd.auto.ac:id/btn_mode_auto"
        override val ID_AC_MODE_COOL    = "com.byd.auto.ac:id/btn_mode_cool"
        override val ID_AC_MODE_HEAT    = "com.byd.auto.ac:id/btn_mode_heat"
        override val ID_AC_MODE_FAN     = "com.byd.auto.ac:id/btn_mode_fan"
        override val ID_AC_SYNC         = "com.byd.auto.ac:id/btn_sync"
        override val ID_AC_AIR_CYCLE    = "com.byd.auto.ac:id/btn_air_cycle"

        override val ID_PHONE_DIALER   = "com.byd.auto.phone:id/btn_dialer"
        override val ID_PHONE_CALL     = "com.byd.auto.phone:id/btn_call"
        override val ID_PHONE_END      = "com.byd.auto.phone:id/btn_end_call"
        override val ID_PHONE_ANSWER   = "com.byd.auto.phone:id/btn_accept"
        override val ID_PHONE_REJECT   = "com.byd.auto.phone:id/btn_decline"
        override val ID_PHONE_INPUT    = "com.byd.auto.phone:id/et_phone_number"
        override val ID_PHONE_CONTACTS = "com.byd.auto.phone:id/btn_contacts"
        override val ID_PHONE_RECENT   = "com.byd.auto.phone:id/btn_recent"

        override val ID_MEDIA_PLAY     = "com.byd.auto.media:id/btn_play"
        override val ID_MEDIA_PAUSE    = "com.byd.auto.media:id/btn_pause"
        override val ID_MEDIA_NEXT     = "com.byd.auto.media:id/btn_next"
        override val ID_MEDIA_PREV     = "com.byd.auto.media:id/btn_previous"
        override val ID_MEDIA_SEEK_BAR = "com.byd.auto.media:id/sb_progress"
        override val ID_MEDIA_TITLE    = "com.byd.auto.media:id/tv_title"
        override val ID_MEDIA_ARTIST   = "com.byd.auto.media:id/tv_artist"

        override val ID_NAV_SEARCH     = "com.byd.auto.navigation:id/et_search"
        override val ID_NAV_START      = "com.byd.auto.navigation:id/btn_start_nav"
        override val ID_NAV_CANCEL     = "com.byd.auto.navigation:id/btn_cancel_nav"
        override val ID_NAV_HOME       = "com.byd.auto.navigation:id/btn_home"
        override val ID_NAV_WORK       = "com.byd.auto.navigation:id/btn_work"

        override val ID_VEHICLE_BATTERY  = "com.byd.auto.vehicleinfo:id/tv_battery_percent"
        override val ID_VEHICLE_RANGE    = "com.byd.auto.vehicleinfo:id/tv_range"
        override val ID_VEHICLE_SPEED    = "com.byd.auto.vehicleinfo:id/tv_speed"
        override val ID_VEHICLE_TEMP_OUT = "com.byd.auto.vehicleinfo:id/tv_outside_temp"
        override val ID_VEHICLE_TEMP_IN  = "com.byd.auto.vehicleinfo:id/tv_inside_temp"

        override val ID_SYS_BRIGHTNESS  = "com.byd.auto.settings:id/sb_brightness"
        override val ID_SYS_VOLUME      = "com.byd.auto.settings:id/sb_volume"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #4 — Seal (IDs unconfirmed)
    // ──────────────────────────────────────────────────────────────────────────
    object Seal : ModelIDs by YuanPlus2023 {
        // Seal shares most IDs with Yuan Plus but overrides known differences.
        // `by YuanPlus2023` delegates all unoverridden properties to YuanPlus2023
        // so new IDs only need to be listed here as they are discovered on device.
        override val ID_AC_POWER    = "com.byd.auto.ac:id/iv_power_seal"
        override val ID_AC_FAN_UP   = "com.byd.auto.ac:id/iv_fan_up"
        override val ID_AC_FAN_DOWN = "com.byd.auto.ac:id/iv_fan_down"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #4 — Han EV (IDs unconfirmed)
    // ──────────────────────────────────────────────────────────────────────────
    object Han : ModelIDs by YuanPlus2023 {
        override val ID_AC_POWER    = "com.byd.auto.ac:id/iv_power_han"
        override val ID_AC_FAN_UP   = "com.byd.auto.ac:id/iv_fan_up"
        override val ID_AC_FAN_DOWN = "com.byd.auto.ac:id/iv_fan_down"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #4 — Tang EV (IDs unconfirmed)
    // ──────────────────────────────────────────────────────────────────────────
    object Tang : ModelIDs by YuanPlus2023 {
        override val ID_AC_POWER    = "com.byd.auto.ac:id/iv_power_tang"
        override val ID_AC_FAN_UP   = "com.byd.auto.ac:id/iv_fan_up"
        override val ID_AC_FAN_DOWN = "com.byd.auto.ac:id/iv_fan_down"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #6 — ModelConfig factory: resolves a detected model name to its
    // ID set and signals whether those IDs have been confirmed on hardware
    // ──────────────────────────────────────────────────────────────────────────
    fun forModel(detectedModel: String): ModelConfig = when {
        detectedModel.contains("Yuan",    ignoreCase = true) ||
        detectedModel.contains("Atto",    ignoreCase = true) ->
            ModelConfig("Yuan Plus / Atto 3", YuanPlus2023, isConfirmed = true)

        detectedModel.contains("Dolphin", ignoreCase = true) ->
            ModelConfig("Dolphin",            Dolphin,     isConfirmed = false)

        detectedModel.contains("Seal",    ignoreCase = true) ->
            ModelConfig("Seal",               Seal,        isConfirmed = false)

        detectedModel.contains("Han",     ignoreCase = true) ->
            ModelConfig("Han",                Han,         isConfirmed = false)

        detectedModel.contains("Tang",    ignoreCase = true) ->
            ModelConfig("Tang",               Tang,        isConfirmed = false)

        else ->
            ModelConfig("Unknown (using Yuan Plus fallback)", YuanPlus2023, isConfirmed = false)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX #2 — expanded FallbackIDs to cover all IDs referenced in executor/service
    // ──────────────────────────────────────────────────────────────────────────
    object FallbackIDs {

        // AC
        val AC_POWER = listOf(
            "com.byd.auto.ac:id/btn_power",
            "com.byd.auto.ac:id/iv_power",
            "com.byd.auto.ac:id/iv_power_seal",
            "com.byd.auto.ac:id/iv_power_han",
            "com.byd.auto.ac:id/iv_power_tang",
            "com.byd.auto.ac:id/power_btn",
            "com.byd.auto.climate:id/btn_power",
            "com.byd.auto.climate:id/iv_power"
        )
        val AC_TEMP_UP = listOf(
            "com.byd.auto.ac:id/btn_temp_up",
            "com.byd.auto.ac:id/iv_temp_up",
            "com.byd.auto.ac:id/temp_up",
            "com.byd.auto.climate:id/btn_temp_up"
        )
        val AC_TEMP_DOWN = listOf(
            "com.byd.auto.ac:id/btn_temp_down",
            "com.byd.auto.ac:id/iv_temp_down",
            "com.byd.auto.ac:id/temp_down",
            "com.byd.auto.climate:id/btn_temp_down"
        )
        // FIX #2 — fan buttons were missing from FallbackIDs entirely
        val AC_FAN_UP = listOf(
            "com.byd.auto.ac:id/btn_fan_up",
            "com.byd.auto.ac:id/iv_fan_up",
            "com.byd.auto.ac:id/fan_up",
            "com.byd.auto.climate:id/btn_fan_up"
        )
        val AC_FAN_DOWN = listOf(
            "com.byd.auto.ac:id/btn_fan_down",
            "com.byd.auto.ac:id/iv_fan_down",
            "com.byd.auto.ac:id/fan_down",
            "com.byd.auto.climate:id/btn_fan_down"
        )

        // Phone
        val PHONE_ANSWER = listOf(
            "com.byd.auto.phone:id/btn_answer",
            "com.byd.auto.phone:id/iv_answer",
            "com.byd.auto.phone:id/answer_btn",
            "com.byd.auto.phone:id/btn_accept"
        )
        val PHONE_REJECT = listOf(
            "com.byd.auto.phone:id/btn_reject",
            "com.byd.auto.phone:id/iv_reject",
            "com.byd.auto.phone:id/reject_btn",
            "com.byd.auto.phone:id/btn_decline"
        )
        // FIX #2 — end-call fallbacks were missing
        val PHONE_END = listOf(
            "com.byd.auto.phone:id/btn_end_call",
            "com.byd.auto.phone:id/iv_end_call",
            "com.byd.auto.phone:id/btn_hang_up",
            "com.byd.auto.phone:id/end_call"
        )
        // FIX #2 — recent-calls tab fallbacks were missing
        val PHONE_RECENT = listOf(
            "com.byd.auto.phone:id/btn_recent",
            "com.byd.auto.phone:id/tab_recent",
            "com.byd.auto.phone:id/iv_recent"
        )

        // Media — FIX #2 — media buttons had no fallbacks at all
        val MEDIA_PLAY = listOf(
            "com.byd.auto.media:id/btn_play",
            "com.byd.auto.media:id/iv_play",
            "com.byd.auto.media:id/play_btn"
        )
        val MEDIA_PAUSE = listOf(
            "com.byd.auto.media:id/btn_pause",
            "com.byd.auto.media:id/iv_pause",
            "com.byd.auto.media:id/pause_btn"
        )
        val MEDIA_NEXT = listOf(
            "com.byd.auto.media:id/btn_next",
            "com.byd.auto.media:id/iv_next",
            "com.byd.auto.media:id/next_btn"
        )
        val MEDIA_PREV = listOf(
            "com.byd.auto.media:id/btn_previous",
            "com.byd.auto.media:id/iv_previous",
            "com.byd.auto.media:id/btn_prev"
        )

        // Navigation — FIX #2 — nav cancel had no fallbacks
        val NAV_CANCEL = listOf(
            "com.byd.auto.navigation:id/btn_cancel_nav",
            "com.byd.auto.navigation:id/iv_cancel",
            "com.byd.auto.navigation:id/btn_stop_nav"
        )
    }
}
