import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository {
    private val apiKey = "4e2c1361f8808f80b5ed84d9d0c9409b"
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
