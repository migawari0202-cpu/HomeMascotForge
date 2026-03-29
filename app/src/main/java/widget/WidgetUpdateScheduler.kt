package widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import widget.TimeWidgetProvider
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

        private const val ONE_MINUTE_MS = 60 * 1000L
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val TEN_MINUTES_MS = 10 * 60 * 1000L
        private const val THIRTY_SECONDS_MS = 30 * 1000L
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "Exact alarms not allowed, using window alarm")
                    scheduleWindowAlarm(pendingIntent, nextMinute)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextMinute, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextMinute, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException, falling back to window alarm", e)
            scheduleWindowAlarm(pendingIntent, nextMinute)
        }
    }

    fun scheduleWeatherUpdate() {
        cancelWeatherUpdate()

        val intent = Intent(context, WeatherUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE_WEATHER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEATHER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextUpdate = System.currentTimeMillis() + ONE_HOUR_MS

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextUpdate, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextUpdate, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule weather update", e)
        }
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextUpdate, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextUpdate, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule speech update", e)
        }
    }

    private fun scheduleWindowAlarm(pendingIntent: PendingIntent, triggerAt: Long) {
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            THIRTY_SECONDS_MS,
            pendingIntent
        )
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