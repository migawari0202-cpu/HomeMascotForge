package com.example.mascotforge.widget

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
import com.example.mascotforge.widget.cache.ClockCache
import com.example.mascotforge.widget.cache.UserWeatherCache
import com.example.mascotforge.widget.database.MemoRepository
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
    private val batteryManager = WidgetCacheManager.batteryManager

    data class WidgetSize(val widthDp: Int, val heightDp: Int)

    enum class WidgetLayoutType { COMPACT, NORMAL }

    /**
     * 1ウィジェット分の作業に必要な情報をひとまとめにしたもの。
     * これを作る場所を createWidgetContext() の1箇所に集約する。
     */
    data class WidgetContext(
        val widgetId: Int,
        val layoutType: WidgetLayoutType,
        val size: WidgetSize,
        val layoutId: Int,
        val views: RemoteViews,
        val updaterLayoutType: WidgetViewUpdater.LayoutType
    )

    // ---- layout helpers ----

    private fun getWidgetSize(widgetId: Int): WidgetSize {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        Log.d(TAG, "widgetId=$widgetId widthDp=$minWidth heightDp=$minHeight")
        return WidgetSize(minWidth, minHeight)
    }

    private fun determineLayoutType(size: WidgetSize): WidgetLayoutType = when {
        size.widthDp >= NORMAL_WIDTH_THRESHOLD -> WidgetLayoutType.NORMAL
        else -> WidgetLayoutType.COMPACT
    }

    /** widgetIdからサイズ取得込みで判定したいときはこちら（ログ付き） */
    private fun determineLayoutType(widgetId: Int): WidgetLayoutType {
        val size = getWidgetSize(widgetId)
        val layoutType = determineLayoutType(size)
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

    /**
     * widgetId → layoutType/size/layoutId/RemoteViews をまとめて組み立てる。
     * 以前は各メソッドでこの4点セットを毎回手書きしていた。
     */
    private fun createWidgetContext(
        widgetId: Int,
        layoutType: WidgetLayoutType? = null
    ): WidgetContext {
        val size = getWidgetSize(widgetId)
        val resolvedLayoutType = layoutType ?: determineLayoutType(size)
        val layoutId = getLayoutId(resolvedLayoutType)
        val views = RemoteViews(context.packageName, layoutId)
        val updaterLayoutType = resolvedLayoutType.toUpdaterLayoutType()
        return WidgetContext(widgetId, resolvedLayoutType, size, layoutId, views, updaterLayoutType)
    }

    // ---- iteration helpers ----

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

    /**
     * "全ウィジェットをループ → WidgetContextを組み立て → block内で編集 → 部分更新を反映"
     * という updateXxxOnly() 系メソッドで繰り返されていたパターンをまとめたもの。
     * normalOnly=true にすると COMPACT レイアウトはスキップする（以前の
     * `if (layoutType != NORMAL) return@forEachWidget` を置き換える）。
     */
    private suspend fun updateWidgetsPartially(
        errorTag: String,
        normalOnly: Boolean = false,
        block: suspend (WidgetContext) -> Unit
    ) {
        forEachWidget(errorTag) { widgetId ->
            val ctx = createWidgetContext(widgetId)
            if (normalOnly && ctx.layoutType != WidgetLayoutType.NORMAL) return@forEachWidget

            block(ctx)

            appWidgetManager.partiallyUpdateAppWidget(widgetId, ctx.views)
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
        val batteryLevel = batteryManager.getBatteryLevel().coerceIn(0, 100)
        val isCharging = batteryManager.isCharging()
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
            val ctx = createWidgetContext(widgetId, layoutType)

            updateClockSection(ctx.views, widgetId, ctx.size)
            updateBatterySection(ctx.views, ctx.updaterLayoutType)

            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(ctx.views, weather, ctx.size.widthDp, ctx.updaterLayoutType)

            if (layoutType == WidgetLayoutType.NORMAL) {
                updateMemoSection(ctx.views, ctx.size, ctx.layoutId)
            }

            updateCharacter(ctx.views, layoutType, widgetId)

            setupButtonClickListeners(ctx.views, widgetId, layoutType)

            appWidgetManager.updateAppWidget(widgetId, ctx.views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId", e)
        }
    }

    suspend fun updateSpeechForWidget(appWidgetId: Int, characterId: String) {
        try {
            val ctx = createWidgetContext(appWidgetId)

            updateCharacter(ctx.views, ctx.layoutType, appWidgetId)
            setupButtonClickListeners(ctx.views, appWidgetId, ctx.layoutType)

            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, ctx.views)
            Log.d(TAG, "updateSpeechForWidget: Widget $appWidgetId updated for char $characterId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating speech for single widget $appWidgetId", e)
        }
    }

    suspend fun updateClockOnly() = updateWidgetsPartially("clock", normalOnly = true) { ctx ->
        updateClockSection(ctx.views, ctx.widgetId, ctx.size)
        updateBatterySection(ctx.views, ctx.updaterLayoutType)

        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        if (minute % 10 == 0 || weatherCache.hasWeatherChanged()) {
            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(ctx.views, weather, ctx.size.widthDp, ctx.updaterLayoutType)
        }

        setupButtonClickListeners(ctx.views, ctx.widgetId, ctx.layoutType)
    }

    suspend fun updateWeatherOnly() {
        contextLoader.clearCache()
        updateWidgetsPartially("weather") { ctx ->
            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(ctx.views, weather, ctx.size.widthDp, ctx.updaterLayoutType)
        }
    }

    suspend fun updateSpeechOnly() {
        contextLoader.clearCache()
        updateWidgetsPartially("speech") { ctx ->
            updateCharacter(ctx.views, ctx.layoutType, ctx.widgetId)
        }
    }

    suspend fun updateMemosOnly() = updateWidgetsPartially("memos", normalOnly = true) { ctx ->
        updateMemoSection(ctx.views, ctx.size, ctx.layoutId)
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
            (widgetId * 31) xor characterId.hashCode(),
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