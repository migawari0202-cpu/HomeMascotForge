package com.example.mascotforge.widget.cache

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager as SystemBatteryManager
import android.util.Log
import com.example.mascotforge.widget.WidgetCacheManager
import com.example.mascotforge.widget.TimeWidgetProvider

/**
 * バッテリー変更のシステムブロードキャストを受信し、キャッシュを更新するレシーバー
 */
class BatteryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BatteryReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        try {
            val level = intent.getIntExtra(SystemBatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(SystemBatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(SystemBatteryManager.EXTRA_STATUS, -1)

            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                val isCharging = status == SystemBatteryManager.BATTERY_STATUS_CHARGING ||
                        status == SystemBatteryManager.BATTERY_STATUS_FULL

                // WidgetCacheManagerのbatteryManagerを安全に取得
                val manager = WidgetCacheManager.batteryManager
                val oldLevel = manager.getBatteryLevel()
                val oldCharging = manager.isCharging()

                // キャッシュの更新 (Command)
                manager.updateBatteryInfo(batteryPct, isCharging)

                // 値が実際に変わった場合のみウィジェットを更新する
                if (oldLevel != batteryPct || oldCharging != isCharging) {
                    Log.d(TAG, "Battery status changed (Level: $oldLevel% -> $batteryPct%, Charging: $oldCharging -> $isCharging). Requesting widget update.")
                    TimeWidgetProvider.updateAllWidgets(context)
                }
            } else {
                Log.w(TAG, "Invalid battery data in broadcast: level=$level, scale=$scale")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse battery broadcast", e)
        }
    }
}
