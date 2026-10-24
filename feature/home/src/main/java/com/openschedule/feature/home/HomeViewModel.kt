package com.openschedule.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.AppLog
import com.openschedule.core.data.TimetableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class DailyWeather(
    val date: String,
    val condition: String,
    val high: Int,
    val low: Int,
)

data class WeatherUi(
    val city: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val daily: List<DailyWeather> = emptyList(),
)

class HomeViewModel(
    timetableStore: TimetableStore,
    private val weatherStore: WeatherStore,
) : ViewModel() {

    val timetable = timetableStore.timetable

    private val _places = MutableStateFlow(
        weatherStore.loadPlaces().ifEmpty { listOf(WeatherStore.Wuhan) },
    )
    val places: StateFlow<List<WeatherPlace>> = _places.asStateFlow()

    private val _activePlace = MutableStateFlow(
        _places.value.firstOrNull { it.id == weatherStore.loadActiveId() }
            ?: _places.value.first(),
    )
    val activePlace: StateFlow<WeatherPlace> = _activePlace.asStateFlow()

    private val _weather = MutableStateFlow<WeatherUi?>(null)
    val weather: StateFlow<WeatherUi?> = _weather.asStateFlow()

    private val _loadingWeather = MutableStateFlow(false)
    val loadingWeather: StateFlow<Boolean> = _loadingWeather.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<WeatherPlace>>(emptyList())
    val searchResults: StateFlow<List<WeatherPlace>> = _searchResults.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var weatherJob: Job? = null
    private var searchJob: Job? = null
    private var weatherRequest = 0

    init {
        persist()
        refreshWeather()
    }

    fun refreshWeather() {
        val place = _activePlace.value
        val request = ++weatherRequest
        weatherJob?.cancel()
        _loadingWeather.value = true
        _weatherError.value = null
        weatherJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchWeather(place) }
                if (request == weatherRequest) _weather.value = result
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == weatherRequest) {
                    AppLog.w("Weather", "${place.name} 刷新失败: ${error.message}")
                    _weatherError.value = "天气暂不可用"
                }
            } finally {
                if (request == weatherRequest) _loadingWeather.value = false
            }
        }
    }

    fun selectPlace(id: String) {
        val place = _places.value.firstOrNull { it.id == id } ?: return
        if (place.id == _activePlace.value.id) return
        _activePlace.value = place
        _weather.value = null
        persist()
        refreshWeather()
    }

    fun addPlace(place: WeatherPlace) {
        val existing = _places.value.firstOrNull {
            it.id == place.id ||
                (it.name == place.name && it.latitude == place.latitude && it.longitude == place.longitude)
        }
        if (existing != null) {
            selectPlace(existing.id)
            return
        }
        val next = (_places.value + place).take(MAX_PLACES)
        _places.value = next
        _activePlace.value = place
        _weather.value = null
        persist()
        refreshWeather()
    }

    fun removePlace(id: String) {
        if (_places.value.size <= 1) return
        val next = _places.value.filterNot { it.id == id }
        if (next.isEmpty()) return
        _places.value = next
        if (_activePlace.value.id == id) {
            _activePlace.value = next.first()
            _weather.value = null
            persist()
            refreshWeather()
        } else {
            persist()
        }
    }

    fun searchPlaces(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _searchError.value = null
        if (trimmed.isBlank()) {
            _searching.value = false
            return
        }
        _searching.value = true
        searchJob = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { geocode(trimmed) }
                _searchResults.value = results
                if (results.isEmpty()) _searchError.value = "没有找到该地区"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.w("Weather", "地区搜索失败: ${error.message}")
                _searchError.value = "搜索失败，请检查网络"
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _searchError.value = null
        _searching.value = false
    }

    private fun persist() {
        weatherStore.save(_places.value, _activePlace.value.id)
    }

    private fun fetchWeather(place: WeatherPlace): WeatherUi {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=Asia%2FShanghai&forecast_days=3"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("weather HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(body).jsonObject
            val current = root["current"]?.jsonObject ?: throw IOException("missing current")
            val daily = root["daily"]?.jsonObject ?: throw IOException("missing daily")
            val temperature = current["temperature_2m"]?.jsonPrimitive?.double
                ?: throw IOException("missing temperature")
            val code = current["weather_code"]?.jsonPrimitive?.int ?: 0
            val highs = daily["temperature_2m_max"]?.jsonArray
            val lows = daily["temperature_2m_min"]?.jsonArray
            val codes = daily["weather_code"]?.jsonArray
            val dates = daily["time"]?.jsonArray
            val high = highs?.firstOrNull()?.jsonPrimitive?.double ?: temperature
            val low = lows?.firstOrNull()?.jsonPrimitive?.double ?: temperature
            val forecast = dates?.mapIndexedNotNull { index, date ->
                val dayHigh = highs?.getOrNull(index)?.jsonPrimitive?.double
                    ?: return@mapIndexedNotNull null
                val dayLow = lows?.getOrNull(index)?.jsonPrimitive?.double
                    ?: return@mapIndexedNotNull null
                val dayCode = codes?.getOrNull(index)?.jsonPrimitive?.int ?: 0
                DailyWeather(
                    date = date.jsonPrimitive.content,
                    condition = weatherLabel(dayCode),
                    high = dayHigh.roundToInt(),
                    low = dayLow.roundToInt(),
                )
            }.orEmpty()
            return WeatherUi(
                city = place.name,
                temperature = temperature.roundToInt(),
                high = high.roundToInt(),
                low = low.roundToInt(),
                condition = weatherLabel(code),
                humidity = current["relative_humidity_2m"]?.jsonPrimitive?.int,
                windSpeed = current["wind_speed_10m"]?.jsonPrimitive?.double,
                daily = forecast,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun geocode(query: String): List<WeatherPlace> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=8&language=zh&format=json"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()
            return results.mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val latitude = item["latitude"]?.jsonPrimitive?.double ?: return@mapNotNull null
                val longitude = item["longitude"]?.jsonPrimitive?.double ?: return@mapNotNull null
                val admin = item["admin1"]?.jsonPrimitive?.content.orEmpty()
                val country = item["country"]?.jsonPrimitive?.content.orEmpty()
                val rawId = item["id"]?.jsonPrimitive?.content
                    ?: "${latitude},${longitude}"
                WeatherPlace(
                    id = "geo:$rawId",
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    subtitle = listOf(admin, country)
                        .filter { it.isNotBlank() && it != name }
                        .distinct()
                        .joinToString(" · "),
                )
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherLabel(code: Int): String = when (code) {
        0 -> "晴"
        1, 2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..57 -> "毛毛雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "阵雨"
        in 85..86 -> "阵雪"
        in 95..99 -> "雷雨"
        else -> "多云"
    }

    private companion object {
        const val MAX_PLACES = 12
    }
}
