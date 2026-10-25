package com.openschedule.feature.home

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WeatherAreaFeed(
    val areas: List<WeatherArea> = emptyList(),
)

@Serializable
private data class WeatherArea(
    val code: String,
    val name: String,
    val province: String = "",
    val city: String = "",
    val latitude: Double,
    val longitude: Double,
    val level: Int = 2,
    val pinyin: String = "",
)

/** Offline province/city/county index; Open-Meteo is only a fallback for other places. */
internal class WeatherAreaIndex(context: Context) {

    private val appContext = context.applicationContext
    private val areas: List<WeatherArea> by lazy { load() }

    fun search(query: String, limit: Int = 20): List<WeatherPlace> {
        val key = normalize(query)
        if (key.length < 2) return emptyList()
        return areas.asSequence()
            .mapNotNull { area ->
                val name = normalize(area.name)
                val pinyin = normalize(area.pinyin)
                val city = normalize(area.city)
                val province = normalize(area.province)
                val rank = when {
                    name == key || (pinyin.isNotBlank() && pinyin == key) -> 0
                    name.startsWith(key) -> 10
                    pinyin.isNotBlank() && pinyin.startsWith(key) -> 12
                    key.contains(name) && name.length >= 2 -> 20
                    city.startsWith(key) || province.startsWith(key) -> 30
                    name.contains(key) -> 40
                    pinyin.isNotBlank() && pinyin.contains(key) -> 42
                    city.contains(key) || province.contains(key) -> 50
                    else -> return@mapNotNull null
                } + area.level
                rank to area
            }
            .sortedWith(
                compareBy<Pair<Int, WeatherArea>> { it.first }
                    .thenBy { it.second.name.length }
                    .thenBy { it.second.code },
            )
            .take(limit)
            .map { (_, area) -> area.toPlace() }
            .toList()
    }

    private fun WeatherArea.toPlace(): WeatherPlace {
        val parts = listOf(province, city)
            .filter { it.isNotBlank() && it != name }
            .distinct()
        val subtitle = when {
            parts.isNotEmpty() -> parts.joinToString(" · ")
            level == 1 -> "直辖市"
            else -> ""
        }
        return WeatherPlace(
            id = "cn:$code",
            name = name,
            latitude = latitude,
            longitude = longitude,
            subtitle = subtitle,
        )
    }

    private fun normalize(value: String): String =
        value.trim().replace(" ", "").lowercase()

    private fun load(): List<WeatherArea> = runCatching {
        val raw = appContext.assets.open("weather_areas.json")
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString(WeatherAreaFeed.serializer(), raw).areas
    }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
