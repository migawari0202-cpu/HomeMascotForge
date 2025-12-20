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
import androidx.work.*
import com.example.mascotforge.WeatherUpdateWorker
import com.mascotforge.ui.CharacterInstallActivity
import widget.TimeWidgetProvider
import widget.WidgetUpdateScheduler
import java.util.concurrent.TimeUnit

class Karioki : ComponentActivity() {

    companion object {
        private const val TAG = "Karioki"
        private const val PREFS_NAME = "app_init_prefs"
        // private const val KEY_WEATHER_SCHEDULED = "weather_scheduled" // WorkManagerなので不要
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
        private const val KEY_CLOCK_SCHEDULED = "clock_scheduled"
        private const val KEY_EXACT_ALARM_REQUESTED = "exact_alarm_requested"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private lateinit var prefs: SharedPreferences
    private var exactAlarmDialog: AlertDialog? = null

    // -------------------------------
    // 権限リクエスト
    // -------------------------------
    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        Log.d(TAG, "権限結果: FINE=$fineGranted, COARSE=$coarseGranted")
        prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()

        // 権限リクエストが完了したら、全てのスケジュールとアクティビティ終了へ
        scheduleAllUpdates()
        onInitializationCompleted() // ★追加：権限取得後に完了処理へ進む
    }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        prefs.edit().putBoolean(KEY_EXACT_ALARM_REQUESTED, true).apply()

        // 設定画面から戻ってきたら、クロックをスケジュールし、アクティビティを閉じる
        executeClockScheduling()
        onInitializationCompleted() // ★追加：設定から戻ってきたら終了
    }

    // -------------------------------
    // Activity Lifecycle
    // -------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val isLaunchedFromWidget = intent?.action == "FROM_WIDGET"

        if (isFirstLaunch) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            setupInitializationLayout()
            Log.d(TAG, "初回起動: 初期化処理開始")

            // 権限チェックへ進み、その後のフローで scheduleAllUpdates と onInitializationCompleted が呼ばれる
            checkAndRequestPermissions()
            return
        }

        // 通常起動は CharacterInstallActivity に遷移
        if (!isLaunchedFromWidget) {
            startActivity(Intent(this, CharacterInstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        // ウィジェット経由起動は初期化表示
        setupInitializationLayout()
        Log.d(TAG, "ウィジェット起動: 初期化処理開始")

        // ウィジェット起動時もスケジュールを実行し、完了したらアクティビティを閉じる
        // ※ウィジェット経由の場合、既に権限は取得済みとして扱い、即座にスケジュールを実行
        scheduleAllUpdates()
        onInitializationCompleted() // ★追加：ウィジェット起動時はすぐに終了
    }

    override fun onDestroy() {
        super.onDestroy()
        exactAlarmDialog?.dismiss()
        Log.d(TAG, "Karioki Activity 終了")
    }

    // -------------------------------
    // 初期化レイアウト
    // -------------------------------
    private fun setupInitializationLayout() {
        // レイアウトの定義は変更なし
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val textView = TextView(this).apply {
            text = "初期設定とデータ更新中..." // 初回起動とウィジェットの両方に対応するテキストに変更
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@Karioki, android.R.color.black))
        }

        val progressBar = ProgressBar(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 32
            layoutParams = lp
        }

        layout.addView(textView)
        layout.addView(progressBar)
        setContentView(layout)
    }

    // -------------------------------
    // 権限チェック
    // -------------------------------
    private fun checkAndRequestPermissions() {
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "位置情報権限は既に許可されています")
            prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()

            // 権限許可済みの場合、スケジュール実行後、アクティビティを閉じる
            scheduleAllUpdates()
            onInitializationCompleted() // ★追加：権限許可済みですぐ終了
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
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            .setNegativeButton("許可しない") { _, _ ->
                prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()

                // 許可しない場合もスケジュール実行後、アクティビティを閉じる
                scheduleAllUpdates()
                onInitializationCompleted() // ★追加：許可しない場合も終了
            }
            .setCancelable(false)
            .show()
    }

    // -------------------------------
    // 時計更新スケジュール (正確なアラーム権限チェックとアクティビティ終了を含む)
    // -------------------------------
    private fun scheduleClockUpdatesAndFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exactRequested = prefs.getBoolean(KEY_EXACT_ALARM_REQUESTED, false)
            if (!alarmManager.canScheduleExactAlarms() && !exactRequested) {
                // 権限が必要な場合、ダイアログを表示し、完了処理はダイアログの決定に委ねる
                showExactAlarmDialog()
                return
            }
        }
        // 権限が不要または既に許可されている場合
        executeClockScheduling()
        onInitializationCompleted() // ★追加：権限不要/許可済みの場合も終了
    }

    private fun showExactAlarmDialog() {
        exactAlarmDialog?.dismiss()
        exactAlarmDialog = AlertDialog.Builder(this)
            .setTitle("時計の正確な更新について")
            .setMessage("正確な時計更新には「アラームとリマインダー」の権限が必要です。")
            .setPositiveButton("設定を開く") { _, _ -> openExactAlarmSettings() }
            .setNegativeButton("スキップ") { _, _ -> continueWithoutExactAlarm() }
            .setCancelable(true)
            .setOnCancelListener { continueWithoutExactAlarm() }
            .create()
        exactAlarmDialog?.show()
    }

    private fun openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .apply { data = Uri.parse("package:$packageName") }
                exactAlarmSettingsLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exact Alarm 設定画面を開けませんでした", e)
            continueWithoutExactAlarm()
        }
    }

    private fun continueWithoutExactAlarm() {
        prefs.edit().putBoolean(KEY_EXACT_ALARM_REQUESTED, true).apply()
        executeClockScheduling()
        onInitializationCompleted() // ★追加：スキップ時も終了
    }

    private fun executeClockScheduling() {
        try {
            val scheduler = WidgetUpdateScheduler(this)
            scheduler.scheduleClockUpdate()
            TimeWidgetProvider.updateClockOnly(this)
        } catch (e: Exception) { // 例外オブジェクトに 'e' という名前を付ける
            // ★推奨: エラー原因を特定するため、例外オブジェクト(e)をログに出力します
            Log.e(TAG, "時計更新スケジュール失敗", e)
        }
        prefs.edit().putBoolean(KEY_CLOCK_SCHEDULED, true).apply()
    }

    // -------------------------------
    // 天気更新スケジュール
    // -------------------------------
    private fun scheduleWeatherUpdates() {
        try {
            // WorkManagerによる天気更新のスケジュールロジックは変更なし
            val periodicWork = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                2, TimeUnit.HOURS
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

    // -------------------------------
    // すべての更新をスケジュール
    // -------------------------------
    private fun scheduleAllUpdates() {
        scheduleWeatherUpdates() // WorkManagerにジョブをキューイング

        // クロックのスケジュールは、権限チェックの必要性があるため別関数に分離し、
        // 完了したパスの最後で onInitializationCompleted() を呼ぶようにする。
        if (!prefs.getBoolean(KEY_CLOCK_SCHEDULED, false)) {
            // クロックが未スケジュールの場合、権限チェック込みのスケジュールと終了処理へ
            scheduleClockUpdatesAndFinish()
        } else {
            // クロックが既にスケジュール済みの場合、天気スケジュール（即時/定期）が完了したとして終了
            // ただし、onCreateでonInitializationCompleted()を呼んでいるため、ここでは不要（念のためコメントアウト）
            // onInitializationCompleted()
        }
    }

    // -------------------------------
    // 初期化処理完了
    // -------------------------------
    /**
     * すべての初期化ステップが完了した際に呼び出す。
     * アクティビティを終了し、必要に応じてメイン画面に遷移させる。
     */
    private fun onInitializationCompleted() {
        // すでに終了処理中/終了済みであれば何もしない
        if (isFinishing || isDestroyed) return

        Log.d(TAG, "初期化処理完了: アクティビティを終了します")

        val isLaunchedFromWidget = intent?.action == "FROM_WIDGET"

        if (!isLaunchedFromWidget && prefs.getBoolean(KEY_FIRST_LAUNCH, false) == false) {
            // 初回起動時のパスの場合は、メインのアクティビティに遷移する
            startActivity(Intent(this, CharacterInstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        // ウィジェット起動を含む、すべての場合でアクティビティを閉じる
        finish()
    }
}