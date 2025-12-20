data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main,
    val name: String,
    val coord: Coord
)

data class Weather(
    val id: Int,           // 追加
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feels_like: Double,
    val humidity: Int
)

data class Coord(
    val lat: Double,
    val lon: Double
)
