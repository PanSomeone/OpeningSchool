package com.openschedule.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableModelTest {

    @Test
    fun legacyCourseFallsBackToSingleSlot() {
        val course = Course(
            id = "1",
            name = "高数",
            day = 2,
            startPeriod = 1,
            endPeriod = 2,
        )
        assertEquals(listOf(CourseSlot(2, 1, 2)), course.effectiveSlots)
        assertTrue(course.slotsOn(1).isEmpty())
        assertEquals(1, course.slotsOn(2).size)
    }

    @Test
    fun multipleSlotsOnDifferentDaysAreKept() {
        val course = Course(
            id = "1",
            name = "实验",
            day = 1,
            startPeriod = 1,
            endPeriod = 2,
            slots = listOf(
                CourseSlot(1, 1, 2),
                CourseSlot(3, 5, 6),
                CourseSlot(3, 9, 10),
            ),
        )
        assertEquals(2, course.slotsOn(3).size)
        assertEquals(3, course.effectiveSlots.size)
        assertEquals(2, course.slotsOn(1).single().endPeriod)
    }

    @Test
    fun homeLayoutWithSpansAndFoldersSurvivesSettingsJson() {
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
        val settings = AppSettings(
            homeLayout = HomeLayout(
                rows = 5,
                widgets = listOf(
                    HomeWidgetPlacement("weather", "weather", 0, 0, colSpan = 2),
                    HomeWidgetPlacement(
                        id = "folder-1",
                        type = "folder",
                        row = 1,
                        col = 0,
                        rowSpan = 2,
                        title = "学习",
                        shortcuts = listOf("timetable", "scores"),
                    ),
                ),
            ),
            appWallpaper = "wallpaper_global.img",
            pageWallpapers = mapOf("home" to "wallpaper_home.img"),
        )
        val raw = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), raw)
        assertEquals(settings.homeLayout, decoded.homeLayout)
        assertEquals(settings.appWallpaper, decoded.appWallpaper)
        assertEquals(settings.pageWallpapers, decoded.pageWallpapers)
    }
}
