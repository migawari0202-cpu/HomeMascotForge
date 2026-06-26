package com.example.mascotforge.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import com.example.mascotforge.WeatherUpdateWorker
import com.example.mascotforge.widget.TimeWidgetProvider
import android.os.Build
import android.util.Log
import java.util.*

class WidgetUpdateScheduler(private val context: Context) {

    companion object {
        private const val TAG = "WidgetUpdateScheduler"
        private const val REQUEST_CODE_CLOCK = 1001
        private const val REQUEST_CODE_WEATHER = 1002
        private const val REQUEST_CODE_SPEECH = 1003

        const val ACTION_UPDATE_CLOCK = "com.example.homemascot.UPDATE_CLOCK"
        const val ACTION_UPDATE_WEATHER = "com.example.homemascot.UPDATE_WEATHER"
        const val ACTION_UPDATE_SPEECH = "com.example.homemascot.UPDATE_SPEECH"
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleClockUpdate() {
        cancelClockUpdate()

        val intent = Intent(context, ClockUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_CLOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_CLOCK, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextMinute = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        scheduleNonExactAlarm(pendingIntent, nextMinute)
    }

    fun scheduleWeatherUpdate() {
        cancelWeatherUpdate()
        WeatherUpdateWorker.schedulePeriodicUpdate(context)
        WeatherUpdateWorker.enqueueImmediateUpdate(
            context,
            "weather_alarm_schedule",
            ExistingWorkPolicy.KEEP
        )

        val intent = Intent(context, WeatherUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_WEATHER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEATHER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextUpdate = System.currentTimeMillis() + ONE_HOUR_MS

        scheduleNonExactAlarm(pendingIntent, nextUpdate)
    }

    fun scheduleSpeechUpdate() {
        cancelSpeechUpdate()

        val intent = Intent(context, SpeechUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_SPEECH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_SPEECH, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextUpdate = System.currentTimeMillis() + TEN_MINUTES_MS

        scheduleNonExactAlarm(pendingIntent, nextUpdate)
    }

    private fun scheduleNonExactAlarm(pendingIntent: PendingIntent, triggerAt: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancelAllUpdates() {
        cancelClockUpdate()
        cancelWeatherUpdate()
        cancelSpeechUpdate()
    }

    private fun cancelClockUpdate() {
        val intent = Intent(context, ClockUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_CLOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_CLOCK, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun cancelWeatherUpdate() {
        val intent = Intent(context, WeatherUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_WEATHER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEATHER, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun cancelSpeechUpdate() {
        val intent = Intent(context, SpeechUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_SPEECH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_SPEECH, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}

class ClockUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            TimeWidgetProvider.updateClockOnly(context)
        } catch (e: Exception) {
            Log.e("ClockUpdateReceiver", "Update failed", e)
        } finally {
            // 成功しても失敗しても次をスケジュール
            try {
                WidgetUpdateScheduler(context).scheduleClockUpdate()
            } catch (e: Exception) {
                Log.e("ClockUpdateReceiver", "Failed to reschedule", e)
            }
        }
    }
}

class WeatherUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            WeatherUpdateWorker.enqueueImmediateUpdate(
                context,
                "weather_alarm_fetch",
                ExistingWorkPolicy.REPLACE
            )
            TimeWidgetProvider.updateWeatherOnly(context)
        } catch (e: Exception) {
            Log.e("WeatherUpdateReceiver", "Update failed", e)
        } finally {
            try {
                WidgetUpdateScheduler(context).scheduleWeatherUpdate()
            } catch (e: Exception) {
                Log.e("WeatherUpdateReceiver", "Failed to reschedule", e)
            }
        }
    }
}

class SpeechUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            TimeWidgetProvider.updateSpeechOnly(context)
        } catch (e: Exception) {
            Log.e("SpeechUpdateReceiver", "Update failed", e)
        } finally {
            try {
                WidgetUpdateScheduler(context).scheduleSpeechUpdate()
            } catch (e: Exception) {
                Log.e("SpeechUpdateReceiver", "Failed to reschedule", e)
            }
        }
    }
}
