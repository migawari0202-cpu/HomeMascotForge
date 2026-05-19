package com.example.mascotforge.ui

import com.example.mascotforge.BuildConfig
import com.example.mascotforge.WeatherApiService
import com.example.mascotforge.WeatherResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository {
    private val apiKey = BuildConfig.WEATHER_API_KEY
    private val baseUrl = "https://api.openweathermap.org/data/2.5/"

    private val apiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WeatherApiService::class.java) // ← これでOK！

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponse? {
        return try {
            val response = apiService.getCurrentWeather(latitude, longitude, apiKey)
            if (response.isSuccessful) {
                response.body()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
