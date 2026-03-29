package widget.cache

import android.content.Context
import android.content.Intent
import android.os.BatteryManager as SystemBatteryManager
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * バッテリー情報のキャッシュ管理
 * スレッドセーフな実装
 */
class BatteryManager {
    // AtomicXXX でスレッドセーフに
    private val cachedLevel = AtomicInteger(-1)
    private val lastUpdateTime = AtomicLong(0L)

    companion object {
        private const val TAG = "BatteryManager"
        private const val CACHE_DURATION_MS = 2 * 60 * 1000L  // 2分（5分は長すぎ）
        private const val DEFAULT_LEVEL = -1
    }

    /**
     * バッテリーレベルを取得（キャッシュ有効）
     */
    fun getBatteryLevel(context: Context): Int {
        val now = System.currentTimeMillis()

        // キャッシュが有効なら返す
        if (isCacheValid(now)) {
            return cachedLevel.get()
        }

        // キャッシュ更新
        val newLevel = fetchBatteryLevel(context)
        cachedLevel.set(newLevel)
        lastUpdateTime.set(now)

        return newLevel
    }

    /**
     * 充電中かどうかを取得
     */
    fun isCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = intent?.getIntExtra(SystemBatteryManager.EXTRA_STATUS, -1) ?: -1
            status == SystemBatteryManager.BATTERY_STATUS_CHARGING
                    || status == SystemBatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check charging status", e)
            false
        }
    }

    /**
     * キャッシュが有効かチェック
     */
    private fun isCacheValid(now: Long): Boolean {
        val level = cachedLevel.get()
        val lastUpdate = lastUpdateTime.get()
        return level != DEFAULT_LEVEL && (now - lastUpdate) < CACHE_DURATION_MS
    }

    /**
     * 実際にバッテリーレベルを取得
     */
    private fun fetchBatteryLevel(context: Context): Int {
        return try {
            // ACTION_BATTERY_CHANGED はエミュレータ・実機ともに信頼性が高い
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = intent?.getIntExtra(SystemBatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(SystemBatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                throw IllegalStateException("Invalid battery data: level=$level, scale=$scale")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch battery level", e)
            // エラー時は前回の値を返す（50%より親切）
            cachedLevel.get().takeIf { it != DEFAULT_LEVEL } ?: 50
        }
    }

    /**
     * キャッシュを強制クリア
     */
    fun clearCache() {
        cachedLevel.set(DEFAULT_LEVEL)
        lastUpdateTime.set(0L)
    }
}