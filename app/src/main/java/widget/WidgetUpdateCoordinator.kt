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
import com.mascotforge.character.SafeCharacterLoader
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
        private const val MINI_WIDTH_THRESHOLD = 200
    }

    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val characterManager = CharacterManager(context)
    private val contextLoader = SafeCharacterLoader(context)
    private val weatherCache = UserWeatherCache(context)
    private val memoRepository = MemoRepository(context)
    private val viewUpdater = WidgetViewUpdater(context)
    private val batteryManager = WidgetBatteryManager()

    data class WidgetSize(val widthDp: Int, val heightDp: Int)

    enum class WidgetLayoutType { MINI, COMPACT, NORMAL }

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
            size.widthDp < MINI_WIDTH_THRESHOLD -> WidgetLayoutType.MINI
            else -> WidgetLayoutType.COMPACT
        }
        Log.d(TAG, "widgetId=$widgetId → layoutType=$layoutType (width=${size.widthDp}dp)")
        return layoutType
    }

    private fun getLayoutId(layoutType: WidgetLayoutType): Int {
        return when (layoutType) {
            WidgetLayoutType.NORMAL -> R.layout.widget_normal
            WidgetLayoutType.COMPACT -> R.layout.widget_layout_compact
            WidgetLayoutType.MINI -> R.layout.widget_mini
        }
    }

    suspend fun updateAllWidgets() {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TimeWidgetProvider::class.java)
        )
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
            val actualLayoutId = getLayoutId(layoutType)
            val size = getWidgetSize(widgetId)
            val views = RemoteViews(context.packageName, actualLayoutId)

            // レイアウトタイプ変換: MINI は COMPACT と同等扱い
            val updaterLayoutType = when (layoutType) {
                WidgetLayoutType.NORMAL -> WidgetViewUpdater.LayoutType.NORMAL
                WidgetLayoutType.COMPACT, WidgetLayoutType.MINI -> WidgetViewUpdater.LayoutType.COMPACT
            }

            // 時計を更新 (NORMAL と COMPACT)
            if (layoutType == WidgetLayoutType.NORMAL || layoutType == WidgetLayoutType.COMPACT) {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val minute = Calendar.getInstance().get(Calendar.MINUTE)
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val useClockCache = prefs.getBoolean("use_clock_cache", true)
                val clockCache = if (useClockCache) ClockCache else null

                // NOTE: WidgetViewUpdater.updateClockViews のシグネチャに layoutType は無いので渡さない
                viewUpdater.updateClockViews(
                    views,
                    clockCache,
                    widgetId,
                    hour,
                    minute,
                    size.widthDp
                )
            }

            // バッテリーを更新 (MINI以外)
            if (layoutType != WidgetLayoutType.MINI) {
                val batteryLevel = batteryManager.getBatteryLevel(context).coerceIn(0, 100)
                val isCharging = batteryManager.isCharging(context)
                viewUpdater.updateBatteryViews(views, batteryLevel, isCharging, updaterLayoutType)
            }

            // 天気を更新
            val weather = weatherCache.getCurrentWeather()
            viewUpdater.updateWeatherViews(views, weather, size.widthDp, updaterLayoutType)

            // メモを更新 (NORMAL のみ)
            if (layoutType == WidgetLayoutType.NORMAL) {
                val memos = memoRepository.widgetMemos.first()
                val memoTexts = memos.map { it.text }
                val memoTextSize = when {
                    size.widthDp >= 240 -> 12f
                    size.widthDp >= 180 -> 11f
                    else -> 10f
                }
                viewUpdater.updateMemoViews(views, memoTexts, memoTextSize, actualLayoutId)
            }

            // キャラクターを更新
            updateCharacter(views, layoutType, widgetId)

            // ボタンのクリックイベント設定 (MINI以外)
            if (layoutType != WidgetLayoutType.MINI) {
                setupButtonClickListeners(views, widgetId, layoutType)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId", e)
        }
    }

    private suspend fun updateCharacter(views: RemoteViews, layoutType: WidgetLayoutType, widgetId: Int) {
        try {
            val characterId = characterManager.getCharacterIdForWidget(widgetId)
            val provider = characterManager.getProviderForWidget(widgetId)
            val speechContext = contextLoader.getCurrentContext()

            Log.d(TAG, "updateCharacter: widgetId=$widgetId, layoutType=$layoutType, characterId=$characterId")

            // レイアウトタイプ変換
            val updaterLayoutType = when (layoutType) {
                WidgetLayoutType.NORMAL -> WidgetViewUpdater.LayoutType.NORMAL
                WidgetLayoutType.COMPACT, WidgetLayoutType.MINI -> WidgetViewUpdater.LayoutType.COMPACT
            }

            // セリフを更新
            val speechText = provider.getSpeech(speechContext)
            if (speechText != null) {
                Log.d(TAG, "Speech: $speechText")
                viewUpdater.updateSpeechViews(views, speechText, updaterLayoutType)
            }

            // 画像を更新
            val characterImage = provider.getCharaImage(speechContext)
            Log.d(TAG, "Character image: ${characterImage?.width}x${characterImage?.height}")
            viewUpdater.updateCharacterImageViews(views, characterImage, updaterLayoutType)

            // タッチイベント設定
            setupCharacterTouchListener(views, layoutType, widgetId, characterId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update character", e)
        }
    }

    suspend fun updateSpeechForWidget(appWidgetId: Int, characterId: String) {
        try {
            val layoutType = determineLayoutType(appWidgetId)
            val actualLayoutId = getLayoutId(layoutType)
            val views = RemoteViews(context.packageName, actualLayoutId)

            updateCharacter(views, layoutType, appWidgetId)

            if (layoutType != WidgetLayoutType.MINI) {
                setupButtonClickListeners(views, appWidgetId, layoutType)
            }

            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateSpeechForWidget: Widget $appWidgetId updated for char $characterId")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating speech for single widget $appWidgetId", e)
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
            WidgetLayoutType.MINI -> R.id.widget_character_image_mini
        }

        val touchIntent = Intent(context, TimeWidgetProvider::class.java).apply {
            action = TimeWidgetProvider.ACTION_RECORD_TOUCH
            putExtra("CHARACTER_ID", characterId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

        val requestCode = widgetId + characterId.hashCode()
        val touchPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            touchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(characterImageId, touchPendingIntent)
        Log.d(TAG, "Touch listener set for widget $widgetId on char $characterId (Image ID: $characterImageId)")
    }

    private fun setupButtonClickListeners(
        views: RemoteViews,
        widgetId: Int,
        layoutType: WidgetLayoutType
    ) {
        val rootId = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.widget_root_normal
            WidgetLayoutType.COMPACT -> R.id.widget_root_compact
            WidgetLayoutType.MINI -> return
        }
        views.setOnClickPendingIntent(rootId, null)

        val memoButtonId: Int? = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.memo_button_normal
            WidgetLayoutType.COMPACT -> R.id.memo_button_compact
            WidgetLayoutType.MINI -> null
        }

        if (memoButtonId != null) {
            val memoIntent = Intent(context, com.example.mascotforge.MemoFindActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val memoPendingIntent = PendingIntent.getActivity(
                context,
                2100 + widgetId,
                memoIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            views.setOnClickPendingIntent(memoButtonId, memoPendingIntent)
        }

        val gameButtonId: Int? = when (layoutType) {
            WidgetLayoutType.NORMAL -> R.id.game_button_normal
            WidgetLayoutType.COMPACT -> R.id.game_button_compact
            WidgetLayoutType.MINI -> null
        }

        if (gameButtonId != null) {
            val gameIntent = Intent(context, com.example.mascotforge.CharacterSelectorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val gamePendingIntent = PendingIntent.getActivity(
                context,
                2200 + widgetId,
                gameIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            views.setOnClickPendingIntent(gameButtonId, gamePendingIntent)
        }
    }

    suspend fun updateClockOnly() {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TimeWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return

        widgetIds.forEach { widgetId ->
            try {
                val layoutType = determineLayoutType(widgetId)
                if (layoutType != WidgetLayoutType.NORMAL) return@forEach

                val size = getWidgetSize(widgetId)
                val actualLayoutId = getLayoutId(layoutType)
                val views = RemoteViews(context.packageName, actualLayoutId)

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val minute = Calendar.getInstance().get(Calendar.MINUTE)
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val useClockCache = prefs.getBoolean("use_clock_cache", true)
                val clockCache = if (useClockCache) ClockCache else null

                // layoutType は不要（時計関数のシグネチャに合わせる）
                viewUpdater.updateClockViews(
                    views,
                    clockCache,
                    widgetId,
                    hour,
                    minute,
                    size.widthDp
                )

                val batteryLevel = batteryManager.getBatteryLevel(context).coerceIn(0, 100)
                val isCharging = batteryManager.isCharging(context)
                viewUpdater.updateBatteryViews(views, batteryLevel, isCharging, WidgetViewUpdater.LayoutType.NORMAL)

                val shouldUpdateWeather = (minute % 10 == 0) || weatherCache.hasWeatherChanged()
                if (shouldUpdateWeather) {
                    val weather = weatherCache.getCurrentWeather()
                    viewUpdater.updateWeatherViews(views, weather, size.widthDp, WidgetViewUpdater.LayoutType.NORMAL)
                }

                setupButtonClickListeners(views, widgetId, layoutType)
                appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating clock for widget $widgetId", e)
            }
        }
    }

    suspend fun updateWeatherOnly() {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TimeWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return
        contextLoader.clearCache()

        widgetIds.forEach { widgetId ->
            try {
                val layoutType = determineLayoutType(widgetId)
                val size = getWidgetSize(widgetId)
                val actualLayoutId = getLayoutId(layoutType)
                val views = RemoteViews(context.packageName, actualLayoutId)

                val weather = weatherCache.getCurrentWeather()
                val updaterLayoutType = when (layoutType) {
                    WidgetLayoutType.NORMAL -> WidgetViewUpdater.LayoutType.NORMAL
                    WidgetLayoutType.COMPACT, WidgetLayoutType.MINI -> WidgetViewUpdater.LayoutType.COMPACT
                }
                viewUpdater.updateWeatherViews(views, weather, size.widthDp, updaterLayoutType)
                appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating weather for widget $widgetId", e)
            }
        }
    }

    suspend fun updateSpeechOnly() {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TimeWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return
        contextLoader.clearCache()

        widgetIds.forEach { widgetId ->
            try {
                val layoutType = determineLayoutType(widgetId)
                val actualLayoutId = getLayoutId(layoutType)
                val views = RemoteViews(context.packageName, actualLayoutId)

                updateCharacter(views, layoutType, widgetId)
                appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating speech for widget $widgetId", e)
            }
        }
    }

    suspend fun updateMemosOnly() {
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TimeWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return

        widgetIds.forEach { widgetId ->
            try {
                val layoutType = determineLayoutType(widgetId)
                if (layoutType != WidgetLayoutType.NORMAL) return@forEach

                val size = getWidgetSize(widgetId)
                val actualLayoutId = getLayoutId(layoutType)
                val views = RemoteViews(context.packageName, actualLayoutId)

                val memos = memoRepository.widgetMemos.first()
                val memoTexts = memos.map { it.text }
                val memoTextSize = when {
                    size.widthDp >= 240 -> 12f
                    size.widthDp >= 180 -> 11f
                    else -> 10f
                }
                viewUpdater.updateMemoViews(views, memoTexts, memoTextSize, actualLayoutId)
                appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating memos for widget $widgetId", e)
            }
        }
    }
}
