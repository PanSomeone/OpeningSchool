package com.openschedule.feature.home

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeatherPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String = "",
)

class WeatherStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("openschedule_weather", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadPlaces(): List<WeatherPlace> {
        val raw = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<WeatherPlace>>(raw)
        }.getOrDefault(emptyList())
    }

    fun loadActiveId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun save(places: List<WeatherPlace>, activeId: String) {
        prefs.edit()
            .putString(KEY_PLACES, json.encodeToString(places))
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    companion object {
        val Wuhan = WeatherPlace(
            id = "wuhan",
            name = "武汉",
            latitude = 30.5928,
            longitude = 114.3055,
        )

        private const val KEY_PLACES = "places"
        private const val KEY_ACTIVE = "active"
    }
}
