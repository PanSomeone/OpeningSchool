package com.openschedule.feature.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.ExternalAdapterCatalog
import com.openschedule.core.model.TimetableImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PluginsViewModel(
    private val builtInImporters: List<TimetableImporter>,
    private val externalAdapters: ExternalAdapterCatalog,
) : ViewModel() {

    private val _importers = MutableStateFlow<List<TimetableImporter>>(builtInImporters)
    val importers: StateFlow<List<TimetableImporter>> = _importers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val external = runCatching { externalAdapters.loadDescriptors() }.getOrDefault(emptyList())
            _importers.value = builtInImporters + external
            _loading.value = false
        }
    }
}
