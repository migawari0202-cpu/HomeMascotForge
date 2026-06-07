package com.example.mascotforge.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.mascotforge.widget.cache.BatteryManager
import com.example.mascotforge.widget.cache.BatteryReceiver
import com.example.mascotforge.widget.cache.ClockCache
import com.example.mascotforge.widget.cache.MemoCache
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ウィジェット用のキャッシュを一元管理
 * Application スコープで保持される（スレッドセーフ）
 */
object WidgetCacheManager {

    private const val TAG = "WidgetCacheManager"

    private val initialized = AtomicBoolean(false)
    private var appContext: Context? = null
    private var batteryReceiver: BatteryReceiver? = null

    // 型を明示的に指定
    private val _batteryManager: BatteryManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkInitialized()
        BatteryManager().also {
            Log.d(TAG, "BatteryManager initialized")
        }
    }

    private val _memoCache: MemoCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkInitialized()
        val ctx = appContext ?: throw IllegalStateException("Context not set")
        MemoCache(ctx).also {
            Log.d(TAG, "MemoCache initialized")
        }
    }

    /**
     * 初期化（必ず最初に呼ぶ）
     */
    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            val appCtx = context.applicationContext
            appContext = appCtx
            Log.d(TAG, "WidgetCacheManager initialized")

            // BatteryReceiver を動的に登録
            if (batteryReceiver == null) {
                batteryReceiver = BatteryReceiver().also { receiver ->
                    try {
                        appCtx.registerReceiver(
                            receiver,
                            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                        )
                        Log.d(TAG, "BatteryReceiver registered dynamically")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to register BatteryReceiver", e)
                    }
                }
            }
        } else {
            Log.d(TAG, "Already initialized")
        }
    }

    private fun checkInitialized() {
        check(initialized.get()) {
            "WidgetCacheManager not initialized. Call initialize() first."
        }
    }

    val clockCache: ClockCache
        get() = ClockCache

    val batteryManager: BatteryManager
        get() = _batteryManager

    val memoCache: MemoCache
        get() = _memoCache

    /**
     * 全キャッシュをクリア（メモリ解放）
     */
    fun clearAll() {
        Log.d(TAG, "Clearing all caches")

        ClockCache.clear()

        if (initialized.get()) {
            runCatching { _batteryManager.clearCache() }
            // MemoCache に clear() があれば有効化
            // runCatching { _memoCache.clear() }
        }
    }

    /**
     * 完全クリーンアップ（全ウィジェット削除時）
     */
    fun cleanup() {
        Log.d(TAG, "Full cleanup")

        batteryReceiver?.let { receiver ->
            try {
                appContext?.unregisterReceiver(receiver)
                Log.d(TAG, "BatteryReceiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister BatteryReceiver", e)
            }
            batteryReceiver = null
        }

        clearAll()
        appContext = null
        initialized.set(false)
    }

    /**
     * デバッグ情報
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("WidgetCacheManager Status:")
            appendLine("  initialized: ${initialized.get()}")
            appendLine("  batteryManager: ${if (initialized.get()) "ready" else "not ready"}")
            appendLine("  memoCache: ${if (initialized.get()) "ready" else "not ready"}")
            appendLine("  clockCache: always ready")
        }
    }
}