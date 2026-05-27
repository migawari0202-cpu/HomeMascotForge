package com.example.mascotforge.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mascotforge.WeatherUpdateWorker
import com.example.mascotforge.widget.TimeWidgetProvider
import com.example.mascotforge.widget.cache.UserWeatherCache

class ScreenReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action != Intent.ACTION_SCREEN_ON && intent?.action != Intent.ACTION_USER_PRESENT) return

        // 画面点灯/ユーザー復帰時にウィジェットを全更新
        TimeWidgetProvider.updateAllWidgets(context)

        // キャッシュ切れなら即時フェッチをキューイング
        val cache = UserWeatherCache(context)
        if (cache.getCurrentWeather() == null) {
            Log.d(TAG, "画面点灯時に天気キャッシュ切れ → 即時フェッチをキューイング")
            val work = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("weather_screen_fetch")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "weather_stale_fetch",
                    ExistingWorkPolicy.KEEP,
                    work
                )
        }
    }
}
