package com.openschedule.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object WeekMath {

    private val DAY_LABELS = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun parseDate(raw: String): LocalDate? = try {
        if (raw.isBlank()) null else LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Week number for [date], one-based. Returns 0 when the semester has not
     * started yet, so callers can tell "before" apart from week one.
     */
    fun weekOf(date: LocalDate, semesterStart: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(semesterStart, date)
        return (Math.floorDiv(days, 7L).toInt() + 1).coerceAtLeast(0)
    }

    fun currentWeek(
        today: LocalDate,
        semesterStart: LocalDate?,
        totalWeeks: Int,
        fallback: Int = 1,
    ): Int {
        if (semesterStart == null) return fallback.coerceIn(1, totalWeeks)
        return weekOf(today, semesterStart).coerceIn(1, totalWeeks)
    }

    /** Position of an ISO weekday in the visible columns, starting from [weekStartDay]. */
    fun columnOf(isoDay: Int, weekStartDay: Int): Int =
        Math.floorMod(isoDay - weekStartDay, 7) + 1

    /** Inverse of [columnOf]. */
    fun isoDayOf(column: Int, weekStartDay: Int): Int =
        Math.floorMod(weekStartDay - 1 + column - 1, 7) + 1

    fun dayLabel(isoDay: Int): String = DAY_LABELS.getOrElse(isoDay) { "" }

    fun shortDayLabel(isoDay: Int): String = dayLabel(isoDay).removePrefix("周")

    fun weekStartLabel(weekStartDay: Int): String = dayLabel(weekStartDay)

    fun todayIsoDay(): Int = LocalDate.now().dayOfWeek.value

    fun asIsoDay(dayOfWeek: DayOfWeek): Int = dayOfWeek.value

    fun dateOfWeekDay(
        semesterStart: LocalDate?,
        week: Int,
        column: Int,
        weekStartDay: Int,
    ): LocalDate? {
        if (semesterStart == null) return null
        val firstDay = semesterStart.plusDays(((week - 1) * 7).toLong())
        return firstDay.plusDays((column - 1).toLong())
    }
}
