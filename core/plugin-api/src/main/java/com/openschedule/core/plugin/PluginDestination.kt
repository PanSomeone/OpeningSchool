package com.openschedule.core.plugin

import androidx.compose.runtime.Composable

/**
 * One entry in the host's navigation.
 *
 * Feature modules expose factories that build these; the app wires them
 * together and the settings screen lets the user reorder, hide or re-icon them.
 * [content] is a lambda rather than a screen class so a module can construct
 * its own ViewModel without the host knowing anything about it.
 */
class PluginDestination(
    val id: String,
    val label: String,
    val iconKey: String,
    val defaultOrder: Int,
    val visibleByDefault: Boolean = true,
    /** Set when the entry comes from an optional plugin the user can remove. */
    val pluginId: String? = null,
    val content: @Composable () -> Unit,
)
