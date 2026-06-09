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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mascotforge.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.example.mascotforge.widget.TimeWidgetProvider
import com.example.mascotforge.widget.cache.UserWeatherCache
import com.example.mascotforge.widget.cache.WeatherInfo
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeatherWorker"

        private const val UNIQUE_PERIODIC_WORK = "weather_update_periodic"
        private const val UNIQUE_IMMEDIATE_WORK = "weather_update_immediate"

        fun schedulePeriodicUpdate(context: Context) {
            val weatherWork = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                weatherWork
            )
            Log.d(TAG, "Periodic weather update scheduled")
        }

        fun enqueueImmediateUpdate(
            context: Context,
            tag: String = "weather_immediate",
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            val work = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(tag)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                policy,
                work
            )
            Log.d(TAG, "Immediate weather update enqueued: tag=$tag policy=$policy")
        }
    }

    private val weatherRepository = WeatherRepository()
    private val locationResolver = WeatherLocationResolver(applicationContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Weather update started")

        return try {
            val location = locationResolver.resolveLocation()
            val weather = weatherRepository.getCurrentWeather(location.latitude, location.longitude)
            val emoji = getWeatherEmoji(weather.weatherId)
            val code = getWeatherCode(weather.weatherId)

            Log.d(
                TAG,
                "Weather fetched: $emoji ($code) ${weather.temperature}C at ${weather.cityName} (${location.source})"
            )

            UserWeatherCache(applicationContext).updateFromWorker(
                WeatherInfo(weather.temperature.toFloat(), emoji, code)
            )
            TimeWidgetProvider.updateAllWidgets(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weather update failed", e)
            Result.retry()
        }
    }

    private fun getWeatherEmoji(weatherId: Int): String = when (weatherId) {
        800 -> "\u2600\uFE0F"
        in 801..804 -> "\u2601\uFE0F"
        in 500..531 -> "\uD83C\uDF27\uFE0F"
        in 200..232 -> "\u26C8\uFE0F"
        in 600..622 -> "\u2744\uFE0F"
        in 701..781 -> "\uD83C\uDF2B\uFE0F"
        in 300..321 -> "\uD83C\uDF26\uFE0F"
        else -> "\u2600\uFE0F"
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

private class WeatherLocationResolver(
    private val context: Context
) {
    private companion object {
        private const val TAG = "WeatherLocationResolver"
        private const val PREFS = "weather_location_policy"
        private const val KEY_LAST_ACTIVE_LOCATION_AT = "last_active_location_at"
        private const val LOCATION_TIMEOUT_MS = 5000L
        private const val LAST_KNOWN_MAX_AGE_MS = 6 * 60 * 60 * 1000L
        private const val ACTIVE_LOCATION_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val DEFAULT_LAT = 35.6895
        private const val DEFAULT_LON = 139.6917
    }

    suspend fun resolveLocation(): WeatherLocation {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission missing; using default location")
            return defaultLocation("permission_missing")
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        getBestLastKnownLocation(locationManager)?.let {
            return WeatherLocation(it.latitude, it.longitude, "last_known/${it.provider}")
        }

        if (!canRequestActiveLocation()) {
            Log.d(TAG, "Active location request throttled; using default location")
            return defaultLocation("active_location_throttled")
        }

        getActiveNetworkLocation(locationManager)?.let {
            return WeatherLocation(it.latitude, it.longitude, "active/network")
        }

        return defaultLocation("location_unavailable")
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun getBestLastKnownLocation(locationManager: LocationManager): Location? {
        val candidates = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                Log.e(TAG, "Last known location permission error: $provider", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.d(TAG, "Location provider unavailable: $provider")
                null
            }
        }

        return candidates
            .filter { System.currentTimeMillis() - it.time <= LAST_KNOWN_MAX_AGE_MS }
            .maxByOrNull { it.time }
    }

    private fun canRequestActiveLocation(): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastRequestedAt = prefs.getLong(KEY_LAST_ACTIVE_LOCATION_AT, 0L)
        return System.currentTimeMillis() - lastRequestedAt >= ACTIVE_LOCATION_MIN_INTERVAL_MS
    }

    private fun markActiveLocationRequested() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVE_LOCATION_AT, System.currentTimeMillis())
            .apply()
    }

    private suspend fun getActiveNetworkLocation(locationManager: LocationManager): Location? {
        return withContext(Dispatchers.Main) {
            if (!isProviderEnabled(locationManager, LocationManager.NETWORK_PROVIDER)) {
                return@withContext null
            }

            try {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) {
                                continuation.resume(location)
                                markActiveLocationRequested()
                            }
                        }

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }

                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }

                    continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }

                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        1000f,
                        listener,
                        Looper.getMainLooper()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        locationManager.removeUpdates(listener)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }, LOCATION_TIMEOUT_MS)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Active network location permission error", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Active network location failed", e)
                null
            }
        }
    }

    private fun isProviderEnabled(locationManager: LocationManager, provider: String): Boolean {
        return try {
            locationManager.isProviderEnabled(provider)
        } catch (e: Exception) {
            false
        }
    }

    private fun defaultLocation(source: String): WeatherLocation {
        return WeatherLocation(DEFAULT_LAT, DEFAULT_LON, "default/$source")
    }
}

private data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String
)
