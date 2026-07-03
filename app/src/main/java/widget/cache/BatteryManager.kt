package com.example.mascotforge.widget.cache

import android.content.Context
import android.content.Intent
import android.os.BatteryManager as SystemBatteryManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * バッテリー情報のキャッシュ管理（CQS準拠）
 * 状態の取得（Query）と状態の更新（Command）を完全に分離
 */
class BatteryManager {
    // AtomicXXX でスレッドセーフに
    private val cachedLevel = AtomicInteger(DEFAULT_LEVEL)
    private val cachedIsCharging = AtomicBoolean(false)

    companion object {
        private const val TAG = "BatteryManager"
        private const val DEFAULT_LEVEL = -1
        const val FALLBACK_BATTERY_LEVEL = 50
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Queries (Query: Side-effect free)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * バッテリーレベルを取得（副作用なし）
     * 未初期化の場合はデフォルト値（FALLBACK_BATTERY_LEVEL）を返す
     */
    fun getBatteryLevel(): Int {
        val level = cachedLevel.get()
        return if (level == DEFAULT_LEVEL) FALLBACK_BATTERY_LEVEL else level
    }

    /**
     * 充電中かどうかを取得（副作用なし）
     */
    fun isCharging(): Boolean {
        return cachedIsCharging.get()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Commands (Command: Modifies state, returns void/Unit)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * バッテリー情報を更新（状態を変更するが、値は返さない）
     */
    fun updateBatteryInfo(level: Int, isCharging: Boolean) {
        val oldLevel = cachedLevel.getAndSet(level)
        val oldCharging = cachedIsCharging.getAndSet(isCharging)

        if (oldLevel != level || oldCharging != isCharging) {
            Log.d(TAG, "Battery status updated: level=$level% (was $oldLevel%), charging=$isCharging (was $oldCharging)")
        }
    }

    /**
     * キャッシュを強制クリア
     */
    fun clearCache() {
        cachedLevel.set(DEFAULT_LEVEL)
        cachedIsCharging.set(false)
        Log.d(TAG, "Battery cache cleared")
    }
}