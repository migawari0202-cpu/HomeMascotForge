package com.example.mascotforge.weather

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenMeteoResponseTest {
    @Test
    fun parsesOpenMeteoCurrentWeatherResponse() {
        val json = """
            {
              "latitude": 35.68,
              "longitude": 139.69,
              "current": {
                "temperature_2m": 21.4,
                "wind_speed_10m": 8.2,
                "wind_direction_10m": 180,
                "weather_code": 2,
                "time": "2026-10-14T12:00"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, OpenMeteoResponse::class.java)
        assertNotNull(response.currentWeather)
        val currentWeather = response.currentWeather!!

        assertEquals(21.4, currentWeather.temperature, 0.0)
        assertEquals(8.2, currentWeather.windspeed, 0.0)
        assertEquals(180.0, currentWeather.winddirection, 0.0)
        assertEquals(2, currentWeather.weatherCode)
        assertEquals("2026-10-14T12:00", currentWeather.time)
    }

}