package com.example.mascotforge.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mascotforge.WeatherUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import widget.TimeWidgetProvider
import widget.cache.UserWeatherCache

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed, starting widget update")

                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val widgetIds = appWidgetManager.getAppWidgetIds(
                            ComponentName(context, TimeWidgetProvider::class.java)
                        )

                        if (widgetIds.isNotEmpty()) {
                            Log.d(TAG, "Updating ${widgetIds.size} widgets")

                            // TimeWidgetProviderのonUpdateを直接呼び出す
                            val provider = TimeWidgetProvider()
                            provider.onUpdate(context, appWidgetManager, widgetIds)

                            Log.d(TAG, "Widget update completed successfully")
                        } else {
                            Log.d(TAG, "No widgets found")
                        }

                        // キャッシュ切れなら即時フェッチをキューイング
                        val cache = UserWeatherCache(context)
                        if (cache.getCurrentWeather() == null) {
                            Log.d(TAG, "起動後に天気キャッシュ切れ → 即時フェッチをキューイング")
                            val work = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                                )
                                .addTag("weather_boot_fetch")
                                .build()
                            WorkManager.getInstance(context)
                                .enqueueUniqueWork(
                                    "weather_stale_fetch",
                                    ExistingWorkPolicy.KEEP,
                                    work
                                )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating widgets after boot", e)
                    } finally {
                        try {
                            pendingResult.finish()
                            Log.d(TAG, "PendingResult finished")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error finishing PendingResult", e)
                        }
                    }
                }
            }
        }
    }
}