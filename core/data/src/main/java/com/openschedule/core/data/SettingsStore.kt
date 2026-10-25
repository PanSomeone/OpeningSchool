package com.openschedule.core.data

import android.content.Context
import com.openschedule.core.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(AppSettings.serializer(), next)).apply()
        _settings.value = next
    }

    fun set(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(AppSettings.serializer(), settings)).apply()
        _settings.value = settings
    }

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        val migrated = migrateLegacyBackground(raw)
        val settings = runCatching { json.decodeFromString(AppSettings.serializer(), migrated) }
            .getOrElse { AppSettings() }
        if (migrated != raw) prefs.edit().putString(KEY_SETTINGS, migrated).apply()
        return settings
    }

    /** The timetable used to keep its own background image; it is now the timetable page wallpaper. */
    private fun migrateLegacyBackground(raw: String): String = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
        val legacy = (root["backgroundImage"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return@runCatching raw
        val pages = root["pageWallpapers"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        if (!pages.containsKey("timetable")) {
            pages["timetable"] = JsonPrimitive(legacy)
            root["pageWallpapers"] = JsonObject(pages)
        }
        root.remove("backgroundImage")
        root.remove("background")
        JsonObject(root).toString()
    }.getOrDefault(raw)

    private companion object {
        const val PREFS_NAME = "openschedule_settings"
        const val KEY_SETTINGS = "settings_json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}
