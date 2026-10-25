package com.openschedule.feature.settings

import androidx.lifecycle.ViewModel
import com.openschedule.core.data.SettingsStore
import com.openschedule.core.model.AppSettings
import com.openschedule.core.model.MAX_NAV_SLOTS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    fun moveNav(id: String, direction: Int) {
        settingsStore.update { current ->
            val ordered = current.nav.sortedBy { it.order }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            val to = from + direction
            if (from < 0 || to !in ordered.indices) return@update current
            val item = ordered.removeAt(from)
            ordered.add(to, item)
            current.copy(nav = ordered.mapIndexed { i, slot -> slot.copy(order = i) })
        }
    }

    fun setNavVisible(id: String, visible: Boolean) {
        if (id == "settings" && !visible) {
            _message.value = "设置必须保留在导航栏"
            return
        }
        val current = settingsStore.settings.value
        if (visible && current.nav.count { it.visible } >= MAX_NAV_SLOTS) {
            _message.value = "底部导航最多 $MAX_NAV_SLOTS 个"
            return
        }
        settingsStore.update { current ->
            // A hidden last item would leave the user with no way back here.
            if (!visible && current.nav.count { it.visible } <= 1) {
                return@update current
            }
            current.copy(nav = current.nav.map { if (it.id == id) it.copy(visible = visible) else it })
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setNavIcon(id: String, iconKey: String) {
        settingsStore.update { current ->
            current.copy(nav = current.nav.map { if (it.id == id) it.copy(iconKey = iconKey) else it })
        }
    }

    fun setNavLabel(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        settingsStore.update { current ->
            current.copy(nav = current.nav.map { if (it.id == id) it.copy(label = trimmed) else it })
        }
    }
}
