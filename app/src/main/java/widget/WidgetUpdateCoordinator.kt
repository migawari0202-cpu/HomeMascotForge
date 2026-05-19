package widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.util.Log
import android.widget.RemoteViews
import com.example.mascotforge.R
import com.example.mascotforge.CharacterManager
import com.example.mascotforge.character.SafeCharacterLoader
import widget.cache.ClockCache
import widget.cache.UserWeatherCache
import widget.cache.BatteryManager as WidgetBatteryManager
import widget.database.MemoRepository
import kotlinx.coroutines.flow.first
import java.util.*

class WidgetUpdateCoordinator(private val context: Context) {

    companion object {
        private const val TAG = "WidgetUpdateCoordinator"
        private const val NORMAL_WIDTH_THRESHOLD = 340
    }

    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val characterManager = CharacterManager(context)
    private val contextLoader = SafeCharacterLoader(context)
    private val weatherCache = UserWeatherCache(context)
    private val memoRepository = MemoRepository(context)
    private val viewUpdater = WidgetViewUpdater(context)
    private val batteryManager = WidgetBatteryManager()

    data class WidgetSize(val widthDp: Int, val heightDp: Int)

    enum class WidgetLayoutType { COMPACT, NORMAL }

    // ---- layout helpers ----

    private fun getWidgetSize(widgetId: Int): WidgetSize {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        Log.d(TAG, "widgetId=$widgetId widthDp=$minWidth heightDp=$minHeight")
        return WidgetSize(minWidth, minHeight)
    }

    private fun determineLayoutType(widgetId: Int): WidgetLayoutType {
        val size = getWidgetSize(widgetId)
        val layoutType = when {
            size.widthDp >= NORMAL_WIDTH_THRESHOLD -> WidgetLayoutType.NORMAL
            else -> WidgetLayoutType.COMPACT
        }
        Log.d(TAG, "widgetId=$widgetId → layoutType=$layoutType (width=${size.widthDp}dp)")
        return layoutType
    }

    private fun getLayoutId(layoutType: WidgetLayoutType): Int = when (layoutType) {
        WidgetLayoutType.NORMAL -> R.layout.widget_normal
        WidgetLayoutType.COMPACT -> R.layout.widget_layout_compact
    }

    private fun WidgetLayoutType.toUpdaterLayoutType() = when (this) {
        WidgetLayoutType.NORMAL -> WidgetViewUpdater.LayoutType.NORMAL
        WidgetLayoutType.COMPACT -> WidgetViewUpdater.LayoutType.COMPACT
    }

    // ---- iteration helper ----

    private fun allWidgetIds(): IntArray =
        appWidgetManager.getAppWidgetIds(ComponentName(context, TimeWidgetProvider::class.java))

    /** 全ウィジェットIDをループし、例外をキャッチしてログに残す */
    private suspend fun forEachWidget(errorTag: String, block: suspend (widgetId: Int) -> Unit) {
        val widgetIds = allWidgetIds()
        if (widgetIds.isEmpty()) return
        widgetIds.forEach { widgetId ->
            try {
                block(widgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating $errorTag for widget $widgetId", e)
            }
        }
    }

    // ---- section update helpers ----

    private fun updateClockSection(views: RemoteViews, widgetId: Int, size: WidgetSize) {
        val cal = Calendar.getInstance()
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val clockCache = if (prefs.getBoolean("use_clock_cache", true)) ClockCache else null
        viewUpdater.updateClockViews(
            views, clockCache, widgetId,
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
            size.widthDp
        )
    }

    private fun updateBatterySection(views: RemoteViews, updaterLayoutType: WidgetViewUpdater.LayoutType) {
        val batteryLevel = batteryManager.getBatteryLevel(context).coerceIn(0, 100)
        val isCharging = batteryManager.isCharging(context)
        viewUpdater.updateBatteryViews(views, batteryLevel, isCharging, updaterLayoutType)
    }

    private suspend fun updateMemoSection(views: RemoteViews, size: WidgetSize, layoutId: Int) {
        val memoTexts = memoRepository.widgetMemos.first().map { it.text }
        val memoTextSize = when {
            size.widthDp >= 240 -> 12f
            size.widthDp >= 180 -> 11f
            else -> 10f
        }
        viewUpdater.updateMemoViews(views, memoTexts, memoTextSize, layoutId)
    }

    // ---- public API ----

    suspend fun updateAllWidgets() {
        val widgetIds = allWidgetIds()
        if (widgetIds.isEmpty()) {
            Log.w(TAG, "No widgets found")
            return
        }
        widgetIds.forEach { widgetId ->
            val layoutType = determineLayoutType(widgetId)
            updateWidget(widgetId, layoutType)
        }
    }

    suspend fun updateWidget(widgetId: Int, layoutType: WidgetLayoutType = WidgetLayoutType.COMPACT) {
        try {
            val layoutId = getLayoutId(layoutType)
            val size = getWidgetSize(widgetId)
            val views = RemoteViews(context.packageName, layoutId)
            val updaterLayoutType = layoutType.toUpdaterLayoutType()

            updateClockSection(views, widgetId, size)
            updateBatterySection(views, updaterLayoutType)

            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(views, weather, size.widthDp, updaterLayoutType)

            if (layoutType == WidgetLayoutType.NORMAL) {
                updateMemoSection(views, size, layoutId)
            }

            updateCharacter(views, layoutType, widgetId)

            setupButtonClickListeners(views, widgetId, layoutType)

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId", e)
        }
    }

    private suspend fun updateCharacter(views: RemoteViews, layoutType: WidgetLayoutType, widgetId: Int) {
        try {
            val characterId = characterManager.getCharacterIdForWidget(widgetId)
            val provider = characterManager.getProviderForWidget(widgetId)
            val speechContext = contextLoader.getCurrentContext(characterId)
            val updaterLayoutType = layoutType.toUpdaterLayoutType()

            Log.d(TAG, "updateCharacter: widgetId=$widgetId, layoutType=$layoutType, characterId=$characterId")

            val speechText = provider.getSpeech(speechContext)
            if (speechText != null) {
                Log.d(TAG, "Speech: $speechText")
                viewUpdater.updateSpeechViews(views, speechText, updaterLayoutType)
            }

            val characterImage = provider.getCharaImage(speechContext)
            Log.d(TAG, "Character image: ${characterImage?.width}x${characterImage?.height}")
            viewUpdater.updateCharacterImageViews(views, characterImage, updaterLayoutType)

            setupCharacterTouchListener(views, layoutType, widgetId, characterId)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update character", t)
        }
    }

    suspend fun updateSpeechForWidget(appWidgetId: Int, characterId: String) {
        try {
            val layoutType = determineLayoutType(appWidgetId)
            val views = RemoteViews(context.packageName, getLayoutId(layoutType))

            updateCharacter(views, layoutType, appWidgetId)

            setupButtonClickListeners(views, appWidgetId, layoutType)

            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateSpeechForWidget: Widget $appWidgetId updated for char $characterId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating speech for single widget $appWidgetId", e)
        }
    }

    suspend fun updateClockOnly() {
        forEachWidget("clock") { widgetId ->
            val layoutType = determineLayoutType(widgetId)
            if (layoutType != WidgetLayoutType.NORMAL) return@forEachWidget

            val size = getWidgetSize(widgetId)
            val views = RemoteViews(context.packageName, getLayoutId(layoutType))

            updateClockSection(views, widgetId, size)
            updateBatterySection(views, WidgetViewUpdater.LayoutType.NORMAL)

            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            if (minute % 10 == 0 || weatherCache.hasWeatherChanged()) {
                val weather = weatherCache.getCurrentWeather()
                viewUpdater.updateWeatherViews(views, weather, size.widthDp, WidgetViewUpdater.LayoutType.NORMAL)
            }

            setupButtonClickListeners(views, widgetId, layoutType)
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }
    }

    suspend fun updateWeatherOnly() {
        contextLoader.clearCache()
        forEachWidget("weather") { widgetId ->
            val layoutType = determineLayoutType(widgetId)
            val size = getWidgetSize(widgetId)
            val views = RemoteViews(context.packageName, getLayoutId(layoutType))

            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(views, weather, size.widthDp, layoutType.toUpdaterLayoutType())
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }
    }

    suspend fun updateSpeechOnly() {
        contextLoader.clearCache()
        forEachWidget("speech") { widgetId ->
            val layoutType = determineLayoutType(widgetId)
            val views = RemoteViews(context.packageName, getLayoutId(layoutType))

            updateCharacter(views, layoutType, widgetId)
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }
    }

    suspend fun updateMemosOnly() {
        forEachWidget("memos") { widgetId ->
            val layoutType = determineLayoutType(widgetId)
            if (layoutType != WidgetLayoutType.NORMAL) return@forEachWidget

            val size = getWidgetSize(widgetId)
            val layoutId = getLayoutId(layoutType)
            val views = RemoteViews(context.packageName, layoutId)

            updateMemoSection(views, size, layoutId)
            appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
        }
    }

    private fun setupCharacterTouchListener(
        views: RemoteViews,
        layoutType: WidgetLayoutType,
        widgetId: Int,
        characterId: String
    ) {
        val characterImageId: Int = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.widget_character_image
            WidgetLayoutType.COMPACT -> R.id.widget_character_image_compact
        }

        val touchIntent = Intent(context, TimeWidgetProvider::class.java).apply {
            action = TimeWidgetProvider.ACTION_RECORD_TOUCH
            putExtra("CHARACTER_ID", characterId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

        val touchPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId + characterId.hashCode(),
            touchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(characterImageId, touchPendingIntent)
        Log.d(TAG, "Touch listener set for widget $widgetId on char $characterId (Image ID: $characterImageId)")
    }

    private fun setupActivityButton(
        views: RemoteViews,
        buttonId: Int?,
        activityClass: Class<*>,
        requestCode: Int
    ) {
        if (buttonId == null) return
        val intent = Intent(context, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        views.setOnClickPendingIntent(buttonId, pendingIntent)
    }

    private fun setupButtonClickListeners(
        views: RemoteViews,
        widgetId: Int,
        layoutType: WidgetLayoutType
    ) {
        val rootId = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.widget_root_normal
            WidgetLayoutType.COMPACT -> R.id.widget_root_compact
        }
        views.setOnClickPendingIntent(rootId, null)

        val memoButtonId: Int? = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.memo_button_normal
            WidgetLayoutType.COMPACT -> R.id.memo_button_compact
        }

        setupActivityButton(views, memoButtonId, com.example.mascotforge.MemoFindActivity::class.java, 2100 + widgetId)
    }
}
