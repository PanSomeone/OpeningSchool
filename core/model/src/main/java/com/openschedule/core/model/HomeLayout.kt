package com.openschedule.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HomeWidgetPlacement(
    val id: String,
    val type: String,
    val row: Int,
    val col: Int,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    /** Folder title; ignored for other widget types. */
    val title: String = "",
    /** Folder contents, by shortcut id. */
    val shortcuts: List<String> = emptyList(),
) {
    fun occupies(row: Int, col: Int): Boolean =
        row in this.row until (this.row + rowSpan) &&
            col in this.col until (this.col + colSpan)
}

@Serializable
data class HomeLayout(
    val rows: Int = DEFAULT_ROWS,
    val widgets: List<HomeWidgetPlacement> = defaultWidgets(),
) {
    companion object {
        const val MIN_ROWS = 4
        const val MAX_ROWS = 12
        const val COLUMNS = 2
        const val DEFAULT_ROWS = 4

        fun defaultWidgets(): List<HomeWidgetPlacement> = listOf(
            HomeWidgetPlacement(
                id = "weather",
                type = "weather",
                row = 0,
                col = 0,
                colSpan = 2,
            ),
            HomeWidgetPlacement(id = "today", type = "today", row = 1, col = 0),
            HomeWidgetPlacement(id = "hotwater", type = "hotwater", row = 1, col = 1),
            HomeWidgetPlacement(id = "scores", type = "scores", row = 2, col = 0),
            HomeWidgetPlacement(
                id = "folder-main",
                type = "folder",
                row = 2,
                col = 1,
                title = "常用",
                shortcuts = listOf("timetable", "hotwater", "scores", "plugins", "settings"),
            ),
        )
    }
}
