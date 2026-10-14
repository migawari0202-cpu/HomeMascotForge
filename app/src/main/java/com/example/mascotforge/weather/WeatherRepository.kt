package com.example.mascotforge.weather

import com.example.mascotforge.WeatherApiService
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository(
    private val apiService: WeatherApiService = defaultApiService()
) {
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): CurrentWeather {
        val response = apiService.getCurrentWeather(latitude, longitude)
        if (!response.isSuccessful) {
            throw WeatherApiException(response.code(), response.errorBody()?.string())
        }

        val body = response.body() ?: throw WeatherApiException(response.code(), "Empty body")
        val currentWeather = body.currentWeather
            ?: throw WeatherApiException(response.code(), "Missing current_weather")

        return CurrentWeather(
            weatherCode = currentWeather.weatherCode,
            temperature = currentWeather.temperature,
            cityName = "" // OpenMeteo doesn't provide city name
        )
    }

    private companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/"

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
    val weatherCode: Int,
    val temperature: Double,
    val cityName: String
)

data class OpenMeteoResponse(
    @SerializedName("current")
    val currentWeather: CurrentWeatherData? = null
)

data class CurrentWeatherData(
    @SerializedName("temperature_2m")
    val temperature: Double,
    @SerializedName("wind_speed_10m")
    val windspeed: Double,
    @SerializedName("wind_direction_10m")
    val winddirection: Double,
    @SerializedName("weather_code")
    val weatherCode: Int,
    @SerializedName("time")
    val time: String
)

class WeatherApiException(
    val statusCode: Int,
    body: String?
) : Exception("Weather API error: status=$statusCode, body=$body")
