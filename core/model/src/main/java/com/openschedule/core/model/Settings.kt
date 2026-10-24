package com.openschedule.core.model

import kotlinx.serialization.Serializable

const val MAX_NAV_SLOTS = 5

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class BackgroundStyle { PLAIN, TINT, GRID, IMAGE }

@Serializable
data class NavSlot(
    val id: String,
    val label: String = "",
    val visible: Boolean = true,
    val order: Int = 0,
    val iconKey: String = "",
)

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** ISO day the week starts on. */
    val weekStartDay: Int = 1,
    val showWeekend: Boolean = true,
    /** Height of one period row, in dp. */
    val cellHeight: Int = 64,
    val background: BackgroundStyle = BackgroundStyle.GRID,
    val backgroundImage: String? = null,
    val nav: List<NavSlot> = emptyList(),
    /** Built-in plugins the user has removed from the app. */
    val disabledPlugins: Set<String> = emptySet(),
    /** 0.55–1.0, how solid the floating nav looks over the page. */
    val navOpacity: Float = 0.92f,
    val homeLayout: HomeLayout = HomeLayout(),
    /** File name in filesDir, used on every page without an override. */
    val appWallpaper: String? = null,
    /** Page id -> file name in filesDir; overrides [appWallpaper] for that page. */
    val pageWallpapers: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MIN_CELL_HEIGHT = 44
        const val MAX_CELL_HEIGHT = 112
        const val MIN_NAV_OPACITY = 0.55f
    }
}
