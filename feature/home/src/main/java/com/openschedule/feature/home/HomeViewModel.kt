package com.openschedule.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.AppLog
import com.openschedule.core.data.TimetableStore
import kotlinx.coroutines.Dispatchers
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
) : ViewModel() {

    val timetable = timetableStore.timetable

    private val _weather = MutableStateFlow<WeatherUi?>(null)
    val weather: StateFlow<WeatherUi?> = _weather.asStateFlow()

    private val _loadingWeather = MutableStateFlow(false)
    val loadingWeather: StateFlow<Boolean> = _loadingWeather.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    init {
        refreshWeather()
    }

    fun refreshWeather() {
        if (_loadingWeather.value) return
        _loadingWeather.value = true
        _weatherError.value = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { fetchWeather() } }
                .onSuccess { _weather.value = it }
                .onFailure {
                    AppLog.w("Weather", "刷新失败: ${it.message}")
                    _weatherError.value = "天气暂不可用"
                }
            _loadingWeather.value = false
        }
    }

    private fun fetchWeather(): WeatherUi {
        val connection = URL(WEATHER_URL).openConnection() as HttpURLConnection
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
                city = "武汉",
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
        const val WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=30.5928&longitude=114.3055" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=Asia%2FShanghai&forecast_days=3"
    }
}
