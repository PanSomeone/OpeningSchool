package com.openschedule.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Period(
    val index: Int,
    val start: String,
    val end: String,
)

@Serializable
data class CourseSlot(
    /** ISO day of week: Monday = 1, Sunday = 7. */
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    /** Empty falls back to [Course.weeks], so one course can meet on different weeks per slot. */
    val weeks: Set<Int> = emptySet(),
) {
    val periodSpan: Int get() = (endPeriod - startPeriod + 1).coerceAtLeast(1)

    fun occursIn(week: Int, courseWeeks: Set<Int>): Boolean {
        val effective = weeks.ifEmpty { courseWeeks }
        return effective.isEmpty() || week in effective
    }
}

@Serializable
data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    /** ISO day of week: Monday = 1, Sunday = 7. Legacy single-slot fields. */
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    /** Empty means every week. */
    val weeks: Set<Int> = emptySet(),
    val color: Int = 0,
    /** Optional 0xAARRGGBB color that overrides [color]. */
    val customColor: Long? = null,
    val note: String = "",
    /** New multi-session source of truth; empty means [day]/[startPeriod]/[endPeriod]. */
    val slots: List<CourseSlot> = emptyList(),
) {
    val periodSpan: Int get() = (endPeriod - startPeriod + 1).coerceAtLeast(1)

    val effectiveSlots: List<CourseSlot>
        get() = slots.ifEmpty { listOf(CourseSlot(day, startPeriod, endPeriod)) }

    fun slotsOn(isoDay: Int): List<CourseSlot> = effectiveSlots.filter { it.day == isoDay }

    fun slotsOn(isoDay: Int, week: Int): List<CourseSlot> =
        effectiveSlots.filter { it.day == isoDay && it.occursIn(week, weeks) }

    fun occursIn(week: Int): Boolean = weeks.isEmpty() || week in weeks

    fun hasSlotIn(week: Int): Boolean = effectiveSlots.any { it.occursIn(week, weeks) }
}

@Serializable
data class Timetable(
    val id: String = "",
    val name: String = "我的课表",
    /** ISO date of the first day of week one. */
    val semesterStart: String = "",
    val totalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    val periods: List<Period> = defaultPeriods(),
    val courses: List<Course> = emptyList(),
    /** 0 hides the line; N draws it after period N. */
    val noonBreakAfterPeriod: Int = 0,
) {
    val periodCount: Int get() = periods.size.coerceAtLeast(1)

    fun coursesIn(week: Int): List<Course> = courses.filter { it.hasSlotIn(week) }

    fun period(index: Int): Period? = periods.firstOrNull { it.index == index }

    companion object {
        const val DEFAULT_TOTAL_WEEKS = 20

        fun defaultPeriods(): List<Period> = listOf(
            Period(1, "08:00", "08:45"),
            Period(2, "08:55", "09:40"),
            Period(3, "10:00", "10:45"),
            Period(4, "10:55", "11:40"),
            Period(5, "14:00", "14:45"),
            Period(6, "14:55", "15:40"),
            Period(7, "16:00", "16:45"),
            Period(8, "16:55", "17:40"),
            Period(9, "19:00", "19:45"),
            Period(10, "19:55", "20:40"),
            Period(11, "20:50", "21:35"),
            Period(12, "21:45", "22:30"),
        )
    }
}

@Serializable
data class TimetableCollection(
    val activeId: String = "",
    val items: List<Timetable> = emptyList(),
)

fun uniqueTimetableName(base: String, existing: Collection<Timetable>): String {
    val trimmed = base.trim().ifBlank { "我的课表" }
    if (existing.none { it.name == trimmed }) return trimmed
    var index = 2
    while (existing.any { it.name == "$trimmed $index" }) index += 1
    return "$trimmed $index"
}

const val COURSE_COLOR_COUNT = 20

fun normalizedColor(index: Int): Int =
    if (index in 0 until COURSE_COLOR_COUNT) index else 0
