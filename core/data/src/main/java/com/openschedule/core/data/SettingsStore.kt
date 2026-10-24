package com.openschedule.core.data

import android.content.Context
import com.openschedule.core.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

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
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrElse { AppSettings() }
    }

    private companion object {
        const val PREFS_NAME = "openschedule_settings"
        const val KEY_SETTINGS = "settings_json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}
