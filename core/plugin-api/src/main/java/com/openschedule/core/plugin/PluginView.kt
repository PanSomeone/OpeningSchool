package com.openschedule.core.plugin

import kotlinx.serialization.Serializable

/**
 * Declarative view tree exchanged between a plugin module and the host.
 *
 * A JS module never receives Compose or Android types; it registers a plain
 * JSON description like `{"type":"column","children":[{"type":"text","text":"Hi"}]}`
 * and the host renders it with native components. Anything the host does not
 * understand is dropped or coerced at the boundary, so a module can never
 * inject layout parameters the host did not choose to support.
 */
@Serializable
data class PluginViewNode(
    /** One of [PluginViewTypes]: column, row, card, text, icon, button, spacer, divider. */
    val type: String = PluginViewTypes.TEXT,
    /** Literal text for text/button nodes; ignored elsewhere. */
    val text: String = "",
    /** [com.openschedule.core.ui.NavIcons] key for icon/button nodes. */
    val icon: String = "",
    /** none | primary | secondary | error. */
    val emphasis: String = EMPHASIS_NONE,
    /** small | medium | large | title. */
    val size: String = SIZE_MEDIUM,
    /**
     * Interaction channel: tapping the node dispatches `action:<action>` to the
     * owning plugin. Blank means the node is not interactive.
     */
    val action: String = "",
    /** Gap between children of a column/row, in dp (0..24). */
    val spacing: Int = 4,
    /** Children of container nodes (column, row, card, button). */
    val children: List<PluginViewNode> = emptyList(),
) {
    companion object {
        const val EMPHASIS_NONE = "none"
        const val EMPHASIS_PRIMARY = "primary"
        const val EMPHASIS_SECONDARY = "secondary"
        const val EMPHASIS_ERROR = "error"
        const val SIZE_SMALL = "small"
        const val SIZE_MEDIUM = "medium"
        const val SIZE_LARGE = "large"
        const val SIZE_TITLE = "title"
    }
}

/** Whitelisted node types; anything else degrades to a plain text node. */
object PluginViewTypes {
    const val COLUMN = "column"
    const val ROW = "row"
    const val CARD = "card"
    const val TEXT = "text"
    const val ICON = "icon"
    const val BUTTON = "button"
    const val SPACER = "spacer"
    const val DIVIDER = "divider"

    val CONTAINERS = setOf(COLUMN, ROW, CARD)
    val ALL = setOf(COLUMN, ROW, CARD, TEXT, ICON, BUTTON, SPACER, DIVIDER)
}

/**
 * A home-canvas widget contributed by a plugin through `ctx.ui.homeWidget(...)`.
 * Sizes are coerced to the home grid (1..2 columns, 1..2 rows).
 */
@Serializable
data class PluginHomeWidget(
    /** Widget id, unique within the plugin. */
    val id: String = "widget",
    /** Shown in the widget picker on the home canvas. */
    val title: String = "",
    val iconKey: String = "extension",
    val columns: Int = 1,
    val rows: Int = 1,
    val view: PluginViewNode = PluginViewNode(text = "…"),
)

/**
 * A full page contributed by a plugin through `ctx.ui.screen({...})`. The host
 * renders it in its own scaffold, so a module never controls navigation chrome.
 */
@Serializable
data class PluginScreen(
    val title: String = "",
    val iconKey: String = "extension",
    val view: PluginViewNode = PluginViewNode(text = "…"),
)

/**
 * Extension slots a plugin can contribute to. API v2 implements the home
 * widget and nav page slots; the others are reserved so manifests stay
 * forward-compatible.
 */
object PluginSlots {
    const val HOME_WIDGET = "/home/widget"
    const val NAV_PAGE = "/nav/page"
    const val TIMETABLE_IMPORT = "/timetable/import"
    const val SETTINGS_SECTION = "/settings/section"
}
