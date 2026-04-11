package com.example.mascotforge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import widget.TimeWidgetProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * WeatherUpdateWorker（改善版）
 *
 * 改善点:
 * - ネットワーク制約追加（接続がある時のみ実行）
 * - リトライ間隔: 2分
 * - 最大リトライ回数: 3回
 * - 指数バックオフではなく固定間隔（シンプル）
 */
class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeatherWorker"
        private const val LOCATION_TIMEOUT = 10000L
        private const val KEY_RETRY_COUNT = "RETRY_COUNT"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MINUTES = 2L

        // デフォルト位置（東京）
        private const val DEFAULT_LAT = 35.6895
        private const val DEFAULT_LON = 139.6917
        private const val DEFAULT_CITY = "Tokyo"

        /**
         * 定期実行をスケジュール（ネットワーク制約付き）
         */
        fun schedulePeriodicUpdate(context: Context) {
            val weatherWork = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                1, TimeUnit.HOURS  // 1時間毎
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)  // ネットワーク必須
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "weather_update_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                weatherWork
            )
            Log.d(TAG, "定期天気更新をスケジュール（1時間毎、ネットワーク制約付き）")
        }
    }

    override suspend fun doWork(): Result {
        val retryCount = inputData.getInt(KEY_RETRY_COUNT, 0)
        Log.d(TAG, "=== 天気更新 Worker 開始 (試行: ${retryCount + 1}/${MAX_RETRY_COUNT + 1}) ===")

        return try {
            // 位置情報取得
            val locationInfo = getCurrentLocationWithDetails()

            // API呼び出し
            val apiResponse = fetchWeatherFromApi(locationInfo.first, locationInfo.second)
            val json = JSONObject(apiResponse)
            val weatherArray = json.getJSONArray("weather")
            val weatherId = weatherArray.getJSONObject(0).getInt("id")
            val temp = json.getJSONObject("main").getDouble("temp")
            val cityName = json.optString("name", DEFAULT_CITY)

            val emoji = getWeatherEmoji(weatherId)
            val code = getWeatherCode(weatherId)

            Log.d(TAG, "✓ 天気取得成功: $emoji ($code) ${temp}°C at $cityName (${locationInfo.third})")

            // 天気をキャッシュ
            try {
                val cache = widget.cache.UserWeatherCache(applicationContext)
                val info = widget.cache.WeatherInfo(temp.toFloat(), emoji, code)
                cache.updateFromWorker(info)
                Log.d(TAG, "✓ 天気データをキャッシュに保存しました")
            } catch (e: Exception) {
                Log.e(TAG, "キャッシュ保存中にエラー", e)
            }

            // ウィジェット更新
            try {
                TimeWidgetProvider.updateAllWidgets(applicationContext)
                Log.d(TAG, "✓ ウィジェット即時更新完了")
            } catch (e: Exception) {
                Log.e(TAG, "ウィジェット更新中にエラー", e)
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "✗ 天気更新失敗 (試行 ${retryCount + 1}回目): ${e.message}", e)

            // 最大リトライ回数チェック
            if (retryCount >= MAX_RETRY_COUNT) {
                Log.e(TAG, "最大リトライ回数(${MAX_RETRY_COUNT})に達しました。次回の定期実行まで待機します。")
                return Result.failure()
            }

            // 2分後にリトライ
            Log.d(TAG, "${RETRY_DELAY_MINUTES}分後に再試行します (残り${MAX_RETRY_COUNT - retryCount}回)")

            val retryData = Data.Builder()
                .putInt(KEY_RETRY_COUNT, retryCount + 1)
                .build()

            val retryWork = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setInputData(retryData)
                .setInitialDelay(RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)  // リトライ時もネットワーク必須
                        .build()
                )
                .build()

            WorkManager.getInstance(applicationContext).enqueue(retryWork)

            // このWorkerは終了（リトライは新しいWorkerで）
            Result.failure()
        }
    }


    // =============================
    // 位置情報取得処理
    // =============================
    private suspend fun getCurrentLocationWithDetails(): Triple<Double, Double, String> {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "位置情報権限なし → デフォルト位置を使用")
            return Triple(DEFAULT_LAT, DEFAULT_LON, "権限なし/デフォルト(東京)")
        }

        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // まず最後の既知の位置を試す（高速）
        try {
            val lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnownNetwork != null && isLocationFresh(lastKnownNetwork)) {
                Log.d(TAG, "最後の既知位置を使用（ネットワーク）")
                return Triple(lastKnownNetwork.latitude, lastKnownNetwork.longitude, "ネットワーク/キャッシュ")
            }

            val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnownGps != null && isLocationFresh(lastKnownGps)) {
                Log.d(TAG, "最後の既知位置を使用（GPS）")
                return Triple(lastKnownGps.latitude, lastKnownGps.longitude, "GPS/キャッシュ")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "最後の既知位置の取得に失敗", e)
        }

        // 新規取得を試みる（タイムアウトあり）
        getLocationFromProvider(locationManager, LocationManager.NETWORK_PROVIDER)?.let { (lat, lon) ->
            return Triple(lat, lon, "ネットワーク/新規取得")
        }

        getLocationFromProvider(locationManager, LocationManager.GPS_PROVIDER)?.let { (lat, lon) ->
            return Triple(lat, lon, "GPS/新規取得")
        }

        Log.w(TAG, "位置情報取得不可 → デフォルト位置を使用")
        return Triple(DEFAULT_LAT, DEFAULT_LON, "取得失敗/デフォルト(東京)")
    }

    // 位置情報が新鮮か（1時間以内）
    private fun isLocationFresh(location: Location): Boolean {
        val age = System.currentTimeMillis() - location.time
        return age < 1 * 60 * 60 * 1000L // 1時間以内
    }

    private suspend fun getLocationFromProvider(
        locationManager: LocationManager,
        provider: String
    ): Pair<Double, Double>? = withContext(Dispatchers.Main) {
        if (!locationManager.isProviderEnabled(provider)) {
            Log.d(TAG, "$provider プロバイダーが無効")
            return@withContext null
        }

        return@withContext try {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(Pair(location.latitude, location.longitude))
                        }
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }

                locationManager.requestLocationUpdates(
                    provider,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )

                Handler(Looper.getMainLooper()).postDelayed({
                    locationManager.removeUpdates(listener)
                    if (continuation.isActive) {
                        Log.d(TAG, "$provider でタイムアウト")
                        continuation.resume(null)
                    }
                }, LOCATION_TIMEOUT)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "$provider での位置取得中に権限エラー", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "$provider での位置取得中にエラー", e)
            null
        }
    }

    // =============================
    // 天気API通信処理
    // =============================
    private suspend fun fetchWeatherFromApi(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.WEATHER_API_KEY
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric"

            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText()
                    throw Exception("HTTPエラー: ${connection.responseCode}, body=$errorBody")
                }

                val response = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "API応答受信 (${response.length}文字)")
                response
            } finally {
                connection.disconnect()
            }
        }
    }

    // =============================
    // 天気コード→表情変換
    // =============================
    private fun getWeatherEmoji(weatherId: Int): String = when (weatherId) {
        800 -> "☀️"
        in 801..804 -> "☁️"
        in 500..531 -> "🌧️"
        in 200..232 -> "⛈️"
        in 600..622 -> "❄️"
        in 701..781 -> "🌫️"
        in 300..321 -> "🌦️"
        else -> "☀️"
    }

    private fun getWeatherCode(weatherId: Int): String = when (weatherId) {
        in 200..232 -> "thunder"
        in 300..321 -> "drizzle"
        in 500..531 -> "rain"
        in 600..622 -> "snow"
        771, 781 -> "storm"
        in 701..781 -> "fog"
        800 -> "clear"
        in 801..802 -> "partly_cloudy"
        in 803..804 -> "cloudy"
        else -> "clear"
    }
}