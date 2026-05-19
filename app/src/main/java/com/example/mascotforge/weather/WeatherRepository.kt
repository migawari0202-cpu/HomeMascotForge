package com.example.mascotforge.weather

import com.example.mascotforge.BuildConfig
import com.example.mascotforge.WeatherApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository(
    private val apiService: WeatherApiService = defaultApiService(),
    private val apiKey: String = BuildConfig.WEATHER_API_KEY
) {
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): CurrentWeather {
        val response = apiService.getCurrentWeather(latitude, longitude, apiKey)
        if (!response.isSuccessful) {
            throw WeatherApiException(response.code(), response.errorBody()?.string())
        }

        val body = response.body() ?: throw WeatherApiException(response.code(), "Empty body")
        val weatherId = body.weather.firstOrNull()?.id
            ?: throw WeatherApiException(response.code(), "Missing weather")

        return CurrentWeather(
            weatherId = weatherId,
            temperature = body.main.temp,
            cityName = body.name.ifBlank { "Tokyo" }
        )
    }

    private companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

        fun defaultApiService(): WeatherApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApiService::class.java)
        }
    }
}

data class CurrentWeather(
    val weatherId: Int,
    val temperature: Double,
    val cityName: String
)

class WeatherApiException(
    val statusCode: Int,
    body: String?
) : Exception("Weather API error: status=$statusCode, body=$body")
