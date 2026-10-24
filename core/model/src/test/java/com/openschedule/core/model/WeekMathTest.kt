package com.openschedule.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekMathTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun weekOfCountsFromSemesterStart() {
        assertEquals(1, WeekMath.weekOf(monday, monday))
        assertEquals(1, WeekMath.weekOf(monday.plusDays(6), monday))
        assertEquals(2, WeekMath.weekOf(monday.plusDays(7), monday))
        assertEquals(0, WeekMath.weekOf(monday.minusDays(1), monday))
        assertEquals(0, WeekMath.weekOf(monday.minusDays(30), monday))
    }

    @Test
    fun currentWeekClampsToSemester() {
        assertEquals(1, WeekMath.currentWeek(monday.minusYears(1), monday, 20))
        assertEquals(20, WeekMath.currentWeek(monday.plusYears(1), monday, 20))
        assertEquals(3, WeekMath.currentWeek(monday.plusDays(15), monday, 20))
        assertEquals(1, WeekMath.currentWeek(LocalDate.now(), null, 20))
    }

    @Test
    fun columnFollowsConfiguredWeekStart() {
        assertEquals(1, WeekMath.columnOf(isoDay = 1, weekStartDay = 1))
        assertEquals(7, WeekMath.columnOf(isoDay = 7, weekStartDay = 1))
        assertEquals(1, WeekMath.columnOf(isoDay = 7, weekStartDay = 7))
        assertEquals(2, WeekMath.columnOf(isoDay = 1, weekStartDay = 7))
        assertEquals(7, WeekMath.columnOf(isoDay = 3, weekStartDay = 4))
    }

    @Test
    fun isoDayInvertsColumn() {
        for (weekStart in 1..7) {
            for (column in 1..7) {
                val iso = WeekMath.isoDayOf(column, weekStart)
                assertEquals(
                    "weekStart=$weekStart column=$column",
                    column,
                    WeekMath.columnOf(iso, weekStart),
                )
            }
        }
    }

    @Test
    fun courseOccursEveryWeekWhenWeeksEmpty() {
        val course = Course(
            id = "c",
            name = "数学",
            day = 1,
            startPeriod = 1,
            endPeriod = 2,
        )
        assertEquals(true, course.occursIn(1))
        assertEquals(true, course.occursIn(18))
        assertEquals(2, course.periodSpan)
    }

    @Test
    fun courseOccursOnlyInListedWeeks() {
        val course = Course(
            id = "c",
            name = "物理",
            day = 3,
            startPeriod = 3,
            endPeriod = 4,
            weeks = setOf(2, 4),
        )
        assertEquals(false, course.occursIn(1))
        assertEquals(true, course.occursIn(4))
    }

    @Test
    fun parseDateRejectsInvalidInput() {
        assertEquals(monday, WeekMath.parseDate("2026-09-07"))
        assertNull(WeekMath.parseDate(""))
        assertNull(WeekMath.parseDate("2026/09/07"))
        assertNull(WeekMath.parseDate("2026-13-01"))
    }

    @Test
    fun dateOfWeekDayUsesDisplayColumn() {
        assertEquals(monday, WeekMath.dateOfWeekDay(monday, week = 1, column = 1, weekStartDay = 1))
        assertEquals(monday.plusDays(8), WeekMath.dateOfWeekDay(monday, week = 2, column = 2, weekStartDay = 1))
        assertNull(WeekMath.dateOfWeekDay(null, week = 1, column = 1, weekStartDay = 1))
    }
}
