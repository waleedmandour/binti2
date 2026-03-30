package com.binti.dilink.dilink

/**
 * BYD Model-specific resource IDs
 * 
 * Contains resource ID constants for different BYD vehicle models.
 * These IDs are used by the AccessibilityService to interact with DiLink UI elements.
 */
object BYDModels {
    
    // Yuan Plus 2023 / Atto 3 Resource IDs
    object YuanPlus2023 {
        const val PACKAGE_AC = "com.byd.auto.ac"
        const val PACKAGE_NAV = "com.byd.auto.navigation"
        const val PACKAGE_MEDIA = "com.byd.auto.media"
        const val PACKAGE_SETTINGS = "com.byd.auto.settings"
        const val PACKAGE_PHONE = "com.byd.auto.phone"
        const val PACKAGE_LAUNCHER = "com.byd.auto.launcher"
        const val PACKAGE_CLIMATE = "com.byd.auto.climate"
        const val PACKAGE_VEHICLE = "com.byd.auto.vehicleinfo"

        // AC Controls
        const val ID_AC_POWER = "com.byd.auto.ac:id/btn_power"
        const val ID_AC_TEMP_UP = "com.byd.auto.ac:id/btn_temp_up"
        const val ID_AC_TEMP_DOWN = "com.byd.auto.ac:id/btn_temp_down"
        const val ID_AC_TEMP_DISPLAY = "com.byd.auto.ac:id/tv_temperature"
        const val ID_AC_FAN_SPEED = "com.byd.auto.ac:id/sb_fan_speed"
        const val ID_AC_MODE_AUTO = "com.byd.auto.ac:id/btn_mode_auto"
        const val ID_AC_MODE_COOL = "com.byd.auto.ac:id/btn_mode_cool"
        const val ID_AC_MODE_HEAT = "com.byd.auto.ac:id/btn_mode_heat"
        const val ID_AC_MODE_FAN = "com.byd.auto.ac:id/btn_mode_fan"
        const val ID_AC_SYNC = "com.byd.auto.ac:id/btn_sync"
        const val ID_AC_AIR_CYCLE = "com.byd.auto.ac:id/btn_air_cycle"

        // Phone Controls
        const val ID_PHONE_DIALER = "com.byd.auto.phone:id/btn_dialer"
        const val ID_PHONE_CALL = "com.byd.auto.phone:id/btn_call"
        const val ID_PHONE_END = "com.byd.auto.phone:id/btn_end_call"
        const val ID_PHONE_ANSWER = "com.byd.auto.phone:id/btn_answer"
        const val ID_PHONE_REJECT = "com.byd.auto.phone:id/btn_reject"
        const val ID_PHONE_INPUT = "com.byd.auto.phone:id/et_phone_number"
        const val ID_PHONE_CONTACTS = "com.byd.auto.phone:id/btn_contacts"
        const val ID_PHONE_RECENT = "com.byd.auto.phone:id/btn_recent"

        // Media Controls
        const val ID_MEDIA_PLAY = "com.byd.auto.media:id/btn_play"
        const val ID_MEDIA_PAUSE = "com.byd.auto.media:id/btn_pause"
        const val ID_MEDIA_NEXT = "com.byd.auto.media:id/btn_next"
        const val ID_MEDIA_PREV = "com.byd.auto.media:id/btn_previous"
        const val ID_MEDIA_SEEK_BAR = "com.byd.auto.media:id/sb_progress"
        const val ID_MEDIA_TITLE = "com.byd.auto.media:id/tv_title"
        const val ID_MEDIA_ARTIST = "com.byd.auto.media:id/tv_artist"

        // Navigation Controls
        const val ID_NAV_SEARCH = "com.byd.auto.navigation:id/et_search"
        const val ID_NAV_START = "com.byd.auto.navigation:id/btn_start_nav"
        const val ID_NAV_CANCEL = "com.byd.auto.navigation:id/btn_cancel_nav"
        const val ID_NAV_HOME = "com.byd.auto.navigation:id/btn_home"
        const val ID_NAV_WORK = "com.byd.auto.navigation:id/btn_work"

        // Vehicle Info
        const val ID_VEHICLE_BATTERY = "com.byd.auto.vehicleinfo:id/tv_battery_percent"
        const val ID_VEHICLE_RANGE = "com.byd.auto.vehicleinfo:id/tv_range"
        const val ID_VEHICLE_SPEED = "com.byd.auto.vehicleinfo:id/tv_speed"
        const val ID_VEHICLE_TEMP_OUT = "com.byd.auto.vehicleinfo:id/tv_outside_temp"
        const val ID_VEHICLE_TEMP_IN = "com.byd.auto.vehicleinfo:id/tv_inside_temp"

        // System Controls
        const val ID_SYS_BRIGHTNESS = "com.byd.auto.settings:id/sb_brightness"
        const val ID_SYS_VOLUME = "com.byd.auto.settings:id/sb_volume"
    }
    
    // Fallback resource IDs for other BYD models
    object FallbackIDs {
        val AC_POWER = listOf(
            "com.byd.auto.ac:id/btn_power",
            "com.byd.auto.ac:id/iv_power",
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
    }
}
