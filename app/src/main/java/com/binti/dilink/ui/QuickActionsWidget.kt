package com.binti.dilink.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.binti.dilink.BintiService
import com.binti.dilink.R
import org.json.JSONObject

/**
 * Quick Actions Widget
 *
 * Home screen widget providing quick access to common Binti commands.
 * Designed for easy access on BYD DiLink home screen.
 *
 * Features:
 * - One-tap voice activation
 * - Quick action buttons (AC, Navigation, Media)
 * - Status display
 * - Customizable shortcuts
 *
 * @author Dr. Waleed Mandour
 */
class QuickActionsWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "QuickActionsWidget"

        // Action constants
        const val ACTION_START_VOICE = "com.binti.dilink.action.START_VOICE"
        const val ACTION_TOGGLE_AC = "com.binti.dilink.action.TOGGLE_AC"
        const val ACTION_NAVIGATE_HOME = "com.binti.dilink.action.NAVIGATE_HOME"
        const val ACTION_MEDIA_PLAY = "com.binti.dilink.action.MEDIA_PLAY"
        const val ACTION_NEXT_TRACK = "com.binti.dilink.action.NEXT_TRACK"
        const val ACTION_TOGGLE_FAVORITE = "com.binti.dilink.action.TOGGLE_FAVORITE"

        // Extra keys
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_ACTION = "action"

        /**
         * Update all widgets
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, QuickActionsWidget::class.java)
            )

            val intent = Intent(context, QuickActionsWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "Updating widgets: ${appWidgetIds.joinToString()}")

        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_START_VOICE -> startVoiceService(context)
            ACTION_TOGGLE_AC -> toggleAC(context)
            ACTION_NAVIGATE_HOME -> navigateHome(context)
            ACTION_MEDIA_PLAY -> toggleMedia(context)
            ACTION_NEXT_TRACK -> nextTrack(context)
            ACTION_TOGGLE_FAVORITE -> toggleFavorite(context)
        }
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "Widget disabled")
    }

    /**
     * Update a specific widget
     */
    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)

        // Set up click intents for each button
        views.setOnClickPendingIntent(
            R.id.btn_voice,
            createPendingIntent(context, widgetId, ACTION_START_VOICE)
        )

        views.setOnClickPendingIntent(
            R.id.btn_ac,
            createPendingIntent(context, widgetId, ACTION_TOGGLE_AC)
        )

        views.setOnClickPendingIntent(
            R.id.btn_nav,
            createPendingIntent(context, widgetId, ACTION_NAVIGATE_HOME)
        )

        views.setOnClickPendingIntent(
            R.id.btn_media,
            createPendingIntent(context, widgetId, ACTION_MEDIA_PLAY)
        )

        views.setOnClickPendingIntent(
            R.id.btn_next,
            createPendingIntent(context, widgetId, ACTION_NEXT_TRACK)
        )

        // Update status text
        val prefs = context.getSharedPreferences("binti_widget_prefs", Context.MODE_PRIVATE)
        val status = prefs.getString("status_$widgetId", context.getString(R.string.widget_status_ready))
        views.setTextViewText(R.id.tv_status, status)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    /**
     * Create a pending intent for a widget action
     */
    private fun createPendingIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, QuickActionsWidget::class.java).apply {
            this.action = action
            putExtra(EXTRA_WIDGET_ID, widgetId)
            putExtra(EXTRA_ACTION, action)
        }

        return PendingIntent.getBroadcast(
            context,
            widgetId * 1000 + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Start voice service
     */
    private fun startVoiceService(context: Context) {
        Log.i(TAG, "Starting voice service from widget")

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_START_LISTENING
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        updateWidgetStatus(context, context.getString(R.string.widget_status_listening))
    }

    /**
     * Toggle AC
     */
    private fun toggleAC(context: Context) {
        Log.i(TAG, "Toggle AC from widget")

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", JSONObject().apply {
                put("action", "AC_CONTROL")
                put("pattern", "شغل المكيف")
            }.toString())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        updateWidgetStatus(context, context.getString(R.string.widget_status_ac))
    }

    /**
     * Navigate home
     */
    private fun navigateHome(context: Context) {
        Log.i(TAG, "Navigate home from widget")

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", JSONObject().apply {
                put("action", "NAVIGATION")
                put("pattern", "خديني للبيت")
            }.toString())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        updateWidgetStatus(context, context.getString(R.string.widget_status_navigating))
    }

    /**
     * Toggle media playback
     */
    private fun toggleMedia(context: Context) {
        Log.i(TAG, "Toggle media from widget")

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", JSONObject().apply {
                put("action", "MEDIA")
                put("entities", JSONObject().apply {
                    put("media_action", "play")
                }.toString())
            }.toString())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        updateWidgetStatus(context, context.getString(R.string.widget_status_media))
    }

    /**
     * Next track
     */
    private fun nextTrack(context: Context) {
        Log.i(TAG, "Next track from widget")

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", JSONObject().apply {
                put("action", "MEDIA")
                put("entities", JSONObject().apply {
                    put("media_action", "next")
                }.toString())
            }.toString())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Toggle favorite action
     */
    private fun toggleFavorite(context: Context) {
        Log.i(TAG, "Toggle favorite from widget")

        val prefs = context.getSharedPreferences("binti_widget_prefs", Context.MODE_PRIVATE)
        val favoriteAction = prefs.getString("favorite_action", "AC_CONTROL") ?: "AC_CONTROL"

        val intent = Intent(context, BintiService::class.java).apply {
            action = BintiService.ACTION_EXECUTE_COMMAND
            putExtra("command", JSONObject().apply {
                put("action", favoriteAction)
            }.toString())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Update widget status text
     */
    private fun updateWidgetStatus(context: Context, status: String) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, QuickActionsWidget::class.java)
        )

        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setTextViewText(R.id.tv_status, status)
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }

        // Reset status after delay
        android.os.Handler(context.mainLooper).postDelayed({
            resetWidgetStatus(context)
        }, 3000)
    }

    /**
     * Reset widget status
     */
    private fun resetWidgetStatus(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, QuickActionsWidget::class.java)
        )

        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setTextViewText(R.id.tv_status, context.getString(R.string.widget_status_ready))
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }
    }
}

/**
 * Quick Actions Configuration Manager
 *
 * Manages user-configured quick actions for the widget.
 */
class QuickActionsConfig(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "binti_quick_actions"
        private const val KEY_CONFIGURED_ACTIONS = "configured_actions"
        private const val KEY_FAVORITE_ACTION = "favorite_action"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get configured quick actions
     */
    fun getConfiguredActions(): List<QuickAction> {
        val defaultActions = listOf(
            QuickAction(
                id = "ac_toggle",
                labelAr = "المكيف",
                labelEn = "AC",
                iconRes = R.drawable.ic_ac,
                action = "AC_CONTROL",
                pattern = "شغل المكيف"
            ),
            QuickAction(
                id = "nav_home",
                labelAr = "البيت",
                labelEn = "Home",
                iconRes = R.drawable.ic_home,
                action = "NAVIGATION",
                pattern = "خديني للبيت"
            ),
            QuickAction(
                id = "media_play",
                labelAr = "تشغيل",
                labelEn = "Play",
                iconRes = R.drawable.ic_play,
                action = "MEDIA",
                pattern = "شغل موسيقى"
            ),
            QuickAction(
                id = "next_track",
                labelAr = "التالي",
                labelEn = "Next",
                iconRes = R.drawable.ic_next,
                action = "MEDIA_NEXT",
                pattern = "التالي"
            )
        )

        val json = prefs.getString(KEY_CONFIGURED_ACTIONS, null) ?: return defaultActions

        return try {
            val jsonArray = org.json.JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                jsonToAction(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            defaultActions
        }
    }

    /**
     * Save configured actions
     */
    fun saveConfiguredActions(actions: List<QuickAction>) {
        val jsonArray = org.json.JSONArray()
        actions.forEach { action ->
            jsonArray.put(actionToJson(action))
        }
        prefs.edit().putString(KEY_CONFIGURED_ACTIONS, jsonArray.toString()).apply()
    }

    /**
     * Set favorite action
     */
    fun setFavoriteAction(action: String) {
        prefs.edit().putString(KEY_FAVORITE_ACTION, action).apply()
    }

    /**
     * Get favorite action
     */
    fun getFavoriteAction(): String {
        return prefs.getString(KEY_FAVORITE_ACTION, "AC_CONTROL") ?: "AC_CONTROL"
    }

    private fun actionToJson(action: QuickAction): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", action.id)
            put("labelAr", action.labelAr)
            put("labelEn", action.labelEn)
            put("iconRes", action.iconRes)
            put("action", action.action)
            put("pattern", action.pattern)
        }
    }

    private fun jsonToAction(json: org.json.JSONObject): QuickAction {
        return QuickAction(
            id = json.getString("id"),
            labelAr = json.getString("labelAr"),
            labelEn = json.getString("labelEn"),
            iconRes = json.getInt("iconRes"),
            action = json.getString("action"),
            pattern = json.getString("pattern")
        )
    }
}

/**
 * Quick Action data class
 */
data class QuickAction(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val iconRes: Int,
    val action: String,
    val pattern: String
)

/**
 * Widget UI State for data binding
 */
data class WidgetState(
    val status: String,
    val isListening: Boolean,
    val temperature: Int?,
    val battery: Int?,
    val activeProfile: String?
)
