package com.example.mascotforge

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mascotforge.ui.CharacterInstallActivity
import com.example.mascotforge.widget.TimeWidgetProvider
import com.example.mascotforge.widget.WidgetUpdateScheduler
import com.example.mascotforge.widget.cache.UserWeatherCache
import java.util.concurrent.TimeUnit

class Karioki : ComponentActivity() {

    companion object {
        private const val TAG = "Karioki"
        private const val PREFS_NAME = "app_init_prefs"

        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
        private const val KEY_CLOCK_SCHEDULED = "clock_scheduled"
        private const val KEY_EXACT_ALARM_REQUESTED = "exact_alarm_requested"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private enum class CompletionTarget {
        FINISH,
        LAUNCH_CHARACTER_INSTALL
    }

    private lateinit var prefs: SharedPreferences
    private var exactAlarmDialog: AlertDialog? = null
    private var initializationCompleted = false
    private var completionTarget: CompletionTarget = CompletionTarget.FINISH

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

        Log.d(TAG, "権限結果: FINE=$fineGranted, COARSE=$coarseGranted")
        markPermissionsRequested()
        scheduleWeatherAndClockUpdates()
    }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        markExactAlarmRequested()
        executeClockScheduling()
        completeInitialization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val launchedFromWidget = intent?.action == "FROM_WIDGET"

        if (isFirstLaunch) {
            markFirstLaunchCompleted()
            Log.d(TAG, "初回起動: 初期化処理開始")

            completionTarget = CompletionTarget.FINISH
            setupInitializationLayout()
            checkAndRequestPermissions()
            return
        }

        if (!launchedFromWidget) {
            enqueueWeatherRefreshIfNeeded()
            launchCharacterInstall()
            return
        }

        completionTarget = CompletionTarget.FINISH
        setupInitializationLayout()
        Log.d(TAG, "ウィジェット起動: 初期化処理開始")

        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        exactAlarmDialog?.dismiss()
        Log.d(TAG, "Karioki Activity 終了")
    }

    private fun setupInitializationLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val textView = TextView(this).apply {
            text = "初期設定とデータ更新中..."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@Karioki, android.R.color.black))
        }

        val progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 32 }
        }

        layout.addView(textView)
        layout.addView(progressBar)
        setContentView(layout)
    }

    private fun checkAndRequestPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            Log.d(TAG, "位置情報権限は既に許可されています")
            markPermissionsRequested()
            scheduleWeatherAndClockUpdates()
        } else {
            showLocationPermissionDialog()
        }
    }

    private fun showLocationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("位置情報の使用について")
            .setMessage("現在地に基づいた天気情報を表示します。\n許可しない場合は東京の天気になります。")
            .setPositiveButton("許可する") { _, _ ->
                requestMultiplePermissions.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("許可しない") { _, _ ->
                markPermissionsRequested()
                scheduleWeatherAndClockUpdates()
            }
            .setCancelable(false)
            .show()
    }

    private fun scheduleWeatherAndClockUpdates() {
        scheduleWeatherUpdates()
        requestClockScheduling()
    }

    private fun requestClockScheduling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exactRequested = prefs.getBoolean(KEY_EXACT_ALARM_REQUESTED, false)

            if (!alarmManager.canScheduleExactAlarms() && !exactRequested) {
                showExactAlarmDialog()
                return
            }
        }

        executeClockScheduling()
        completeInitialization()
    }

    private fun showExactAlarmDialog() {
        exactAlarmDialog?.dismiss()

        exactAlarmDialog = AlertDialog.Builder(this)
            .setTitle("時計の正確な更新について")
            .setMessage("正確な時計更新には「アラームとリマインダー」の権限が必要です。")
            .setPositiveButton("設定を開く") { _, _ ->
                openExactAlarmSettings()
            }
            .setNegativeButton("スキップ") { _, _ ->
                continueWithoutExactAlarm()
            }
            .setCancelable(true)
            .setOnCancelListener {
                continueWithoutExactAlarm()
            }
            .create()

        exactAlarmDialog?.show()
    }

    private fun openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                exactAlarmSettingsLauncher.launch(intent)
            } else {
                continueWithoutExactAlarm()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exact Alarm 設定画面を開けませんでした", e)
            continueWithoutExactAlarm()
        }
    }

    private fun continueWithoutExactAlarm() {
        markExactAlarmRequested()
        executeClockScheduling()
        completeInitialization()
    }

    private fun executeClockScheduling() {
        try {
            WidgetUpdateScheduler(this).scheduleClockUpdate()
            TimeWidgetProvider.updateClockOnly(this)
        } catch (e: Exception) {
            Log.e(TAG, "時計更新スケジュール失敗", e)
        }

        prefs.edit().putBoolean(KEY_CLOCK_SCHEDULED, true).apply()
    }

    private fun scheduleWeatherUpdates() {
        try {
            val periodicWork = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("weather_periodic")
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "weather_periodic",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )

            val immediateWork = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("weather_immediate")
                .build()

            WorkManager.getInstance(applicationContext).enqueue(immediateWork)
        } catch (e: Exception) {
            Log.e(TAG, "天気更新スケジュール失敗", e)
        }
    }

    private fun completeInitialization() {
        if (initializationCompleted || isFinishing || isDestroyed) return

        initializationCompleted = true
        Log.d(TAG, "初期化処理完了")

        when (completionTarget) {
            CompletionTarget.FINISH -> finish()
            CompletionTarget.LAUNCH_CHARACTER_INSTALL -> launchCharacterInstall()
        }
    }

    private fun enqueueWeatherRefreshIfNeeded() {
        val weatherCache = UserWeatherCache(applicationContext)

        if (weatherCache.getCurrentWeather() == null) {
            Log.d(TAG, "天気キャッシュなし → 即時取得をエンキュー")

            val immediateWork = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("weather_immediate_on_launch")
                .build()

            WorkManager.getInstance(applicationContext).enqueue(immediateWork)
        }
    }

    private fun launchCharacterInstall() {
        startActivity(
            Intent(this, CharacterInstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun markPermissionsRequested() {
        prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()
    }

    private fun markExactAlarmRequested() {
        prefs.edit().putBoolean(KEY_EXACT_ALARM_REQUESTED, true).apply()
    }

    private fun markFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
}