package com.example.mascotforge.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.example.mascotforge.R
import com.example.mascotforge.CharacterManager
import com.example.mascotforge.WeatherUpdateWorker
import com.example.mascotforge.character.CharacterStateManager
import com.example.mascotforge.character.DynamicCharacter
import com.example.mascotforge.characters.CharacterRegistry
import com.example.mascotforge.speech.SpeechContextFactory
import com.example.mascotforge.widget.cache.UserWeatherCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mascot Forge ウィジェットの AppWidgetProvider（完全版）
 *
 * 機能:
 * - 時計、天気、セリフ、メモの更新を一元管理
 * - サイズ変更に応じたレイアウト切り替え
 * - キャラクター別の状態管理（独立した touchCount）
 * - 特定ウィジェットのみ更新（全体更新の回避）
 * - スレッドセーフなキャッシュ管理
 *
 * 最適化:
 * - 不要な全体更新を削減
 * - キャラクター状態の完全分離
 * - メモリ効率的なキャッシュ
 */
class TimeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "TimeWidgetProvider"

        // レイアウト切り替えの閾値
        private const val FULLSCREEN_WIDTH_THRESHOLD = 300
        private const val FULLSCREEN_HEIGHT_THRESHOLD = 105

        // カスタムアクション
        const val ACTION_RECORD_TOUCH = "ACTION_RECORD_TOUCH"
        const val ACTION_RECORD_WEATHER_TOUCH = "ACTION_RECORD_WEATHER_TOUCH"
        private const val TOUCH_WINDOW_MS = 150L
        private val pendingTouches = mutableMapOf<Int, MutableList<WidgetTouchEvent>>()

        // コルーチンスコープ
        private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Widget サイズに応じたレイアウト取得
         */
        fun getLayoutForSize(context: Context, appWidgetId: Int): Int {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)

            return if (minWidth >= FULLSCREEN_WIDTH_THRESHOLD &&
                minHeight >= FULLSCREEN_HEIGHT_THRESHOLD) {
                R.layout.widget_normal
            } else {
                R.layout.widget_layout_compact
            }
        }

        /**
         * スコープがアクティブか確認（必要なら再生成）
         */
        private fun ensureScopeActive() {
            if (!scope.isActive) {
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                Log.d(TAG, "CoroutineScope recreated")
            }
        }

        /**
         * すべてのウィジェットを更新
         * 用途: 初回配置、アプリ起動時、設定変更時
         */
        fun updateAllWidgets(context: Context) {
            ensureScopeActive()
            scope.launch {
                try {
                    WidgetCacheManager.initialize(context)
                    WidgetUpdateCoordinator(context).updateAllWidgets()
                    Log.d(TAG, "All widgets updated")
                } catch (e: Exception) {
                    Log.e(TAG, "updateAllWidgets failed", e)
                }
            }
        }

        /**
         * 天気のみ更新（全ウィジェット）
         * 用途: 1時間ごとの天気更新
         */
        fun updateWeatherOnly(context: Context) {
            ensureScopeActive()
            scope.launch {
                try {
                    WidgetCacheManager.initialize(context)
                    WidgetUpdateCoordinator(context).updateWeatherOnly()
                    Log.d(TAG, "Weather updated")
                } catch (e: Exception) {
                    Log.e(TAG, "updateWeatherOnly failed", e)
                }
            }
        }

        /**
         * セリフのみ更新（全ウィジェット）
         * 用途: 定期的なセリフ変更
         */
        fun updateSpeechOnly(context: Context) {
            ensureScopeActive()
            scope.launch {
                try {
                    WidgetCacheManager.initialize(context)
                    WidgetUpdateCoordinator(context).updateSpeechOnly()
                    Log.d(TAG, "Speech updated")
                } catch (e: Exception) {
                    Log.e(TAG, "updateSpeechOnly failed", e)
                }
            }
        }

        /**
         * メモ変更通知
         * 用途: メモアプリから変更があった時
         */
        fun notifyMemoChanged(context: Context) {
            ensureScopeActive()
            scope.launch {
                try {
                    WidgetCacheManager.initialize(context)
                    WidgetUpdateCoordinator(context).updateAllWidgets()
                    Log.d(TAG, "Memo changed notification handled")
                } catch (e: Exception) {
                    Log.e(TAG, "notifyMemoChanged failed", e)
                }
            }
        }

        /**
         * ウィジェット破棄時の後片付け
         */
        fun cleanup() {
            Log.d(TAG, "Cleanup started")
            WidgetCacheManager.cleanup()
            scope.cancel()
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ライフサイクルメソッド
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * カスタムアクションを処理
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: action=${intent.action}")

        when (intent.action) {
            ACTION_RECORD_TOUCH -> enqueueTouch(context, intent, WidgetTouchTarget.CHARACTER, goAsync())
            ACTION_RECORD_WEATHER_TOUCH -> enqueueTouch(context, intent, WidgetTouchTarget.WEATHER, goAsync())
        }
    }

    /**
     * 最初のウィジェットが追加された時
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget enabled")
        WidgetCacheManager.initialize(context)
        WeatherUpdateWorker.schedulePeriodicUpdate(context)
        WeatherUpdateWorker.enqueueImmediateUpdate(context, "weather_widget_enabled")
    }

    /**
     * ウィジェット更新時（システムからの定期更新含む）
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgets")

        WidgetCacheManager.initialize(context)

        ensureScopeActive()
        scope.launch {
            // システムUIの更新を待つ
            delay(200)
            WidgetUpdateCoordinator(context).updateAllWidgets()
        }

        // 定期更新をスケジュール
        val scheduler = WidgetUpdateScheduler(context)
        scheduler.scheduleWeatherUpdate()  // 1時間ごと
        scheduler.scheduleSpeechUpdate()   // 10分ごと
    }

    /**
     * ウィジェットサイズが変更された時
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        Log.d(TAG, "Widget $appWidgetId resized: ${minWidth}x${minHeight}")

        WidgetCacheManager.initialize(context)
        updateAllWidgets(context)
    }

    /**
     * ウィジェットが削除された時
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "Widgets deleted: ${appWidgetIds.joinToString()}")

        // 残りのウィジェットを確認
        val remaining = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TimeWidgetProvider::class.java))

        // すべて削除されたらスケジュールをキャンセル
        if (remaining.isEmpty()) {
            Log.d(TAG, "No widgets remaining, canceling all updates")
            WidgetUpdateScheduler(context).cancelAllUpdates()
        }
    }

    /**
     * 最後のウィジェットが削除された時
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last widget disabled")

        WidgetUpdateScheduler(context).cancelAllUpdates()
        cleanup()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // プライベートメソッド
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** ウィジェットごとの近接タップを受信順のタイムスタンプでまとめる。 */
    private fun enqueueTouch(
        context: Context, intent: Intent, target: WidgetTouchTarget,
        pendingResult: BroadcastReceiver.PendingResult
    ) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingResult.finish()
            return
        }
        // BroadcastReceiver は短命なので、イベントをプロセス内のスコープに保持して近接タップをまとめる。
        synchronized(pendingTouches) {
            val events = pendingTouches.getOrPut(widgetId) { mutableListOf() }
            events.add(WidgetTouchEvent(target, SystemClock.elapsedRealtimeNanos()))
        }
        ensureScopeActive()
        scope.launch {
            try {
                delay(TOUCH_WINDOW_MS)
                val events = synchronized(pendingTouches) { pendingTouches.remove(widgetId)?.toList().orEmpty() }
                if (events.isEmpty()) return@launch
                val characterId = CharacterManager(context).getCharacterIdForWidget(widgetId)
                val firstEvent = events.minByOrNull { it.timestamp } ?: return@launch
                val selected = if (events.any { it.timestamp == firstEvent.timestamp && it.target != firstEvent.target }) {
                    val character = CharacterRegistry.getCharacterById(context, characterId)
                    if (character is DynamicCharacter) {
                        val base = SpeechContextFactory.create(context, characterId)
                        selectWidgetTouchEvent(
                            events,
                            character.matchingSpeechPriority(base.copy(wasTouched = true, weatherTouched = false)),
                            character.matchingSpeechPriority(base.copy(wasTouched = false, weatherTouched = true))
                        ) ?: firstEvent
                    } else selectWidgetTouchEvent(events, 0, 0) ?: firstEvent
                } else firstEvent
                handleTouchAction(context, widgetId, characterId, selected.target)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle touch for widget $widgetId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleTouchAction(context: Context, appWidgetId: Int, characterId: String, target: WidgetTouchTarget) {
        if (target == WidgetTouchTarget.CHARACTER) {
            val stateManager = CharacterStateManager(context)
            val currentState = stateManager.getState(characterId)
            stateManager.recordTouch(currentState)
            stateManager.saveState(characterId, currentState)

            Log.d(TAG, "Touch recorded: character=$characterId, widget=$appWidgetId, " +
                "count=${currentState.touchCount}, today=${currentState.touchCountToday}, " +
                "consecutive=${currentState.consecutiveTouchCount}")

            // セリフを再生成する前にキャラの ON_TOUCH ルールを発火。
            try {
                val character = CharacterRegistry.getCharacterById(context, characterId)
                if (character is DynamicCharacter) {
                    val ctx = SpeechContextFactory.create(context, characterId)
                    character.triggerTouchRules(ctx)
                    Log.d(TAG, "ON_TOUCH rules fired for character $characterId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fire ON_TOUCH rules for $characterId", e)
            }
        }

        // 天気タップではキャラのタッチ回数や ON_TOUCH ルールを変更しない。
        updateSingleWidget(context, appWidgetId, characterId, target)
        if (target == WidgetTouchTarget.WEATHER && UserWeatherCache(context).getCurrentWeather() == null) {
            // 取得前・期限切れ・取得失敗時は、セリフ更新と並行して既存の天気取得を依頼する。
            WeatherUpdateWorker.enqueueImmediateUpdate(context, tag = "weather_touch")
        }
    }

    /**
     * 特定のウィジェットのみ更新
     *
     * @param appWidgetId 更新対象のウィジェットID
     * @param characterId キャラクターID
     */
    private fun updateSingleWidget(context: Context, appWidgetId: Int, characterId: String, target: WidgetTouchTarget) {
        ensureScopeActive()
        scope.launch {
            try {
                WidgetCacheManager.initialize(context)

                val coordinator = WidgetUpdateCoordinator(context)
                // このキャラのこのウィジェットのみ更新
                coordinator.updateSpeechForWidget(appWidgetId, characterId, target)

                Log.d(TAG, "Widget $appWidgetId updated for character $characterId")

            } catch (e: Exception) {
                Log.e(TAG, "updateSingleWidget failed: widget=$appWidgetId, character=$characterId", e)
            }
        }
    }
}
