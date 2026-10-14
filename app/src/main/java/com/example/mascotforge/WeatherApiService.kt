package com.example.mascotforge

import retrofit2.Response
import retrofit2.http.GET
import com.example.mascotforge.weather.OpenMeteoResponse
import retrofit2.http.Query

interface WeatherApiService {
    @GET("forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code,wind_speed_10m,wind_direction_10m",
        @Query("timezone") timezone: String = "auto"
    ): Response<OpenMeteoResponse>
}

