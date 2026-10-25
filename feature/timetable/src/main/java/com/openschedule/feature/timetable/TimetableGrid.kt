package com.openschedule.feature.timetable

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openschedule.core.model.AppSettings
import com.openschedule.core.model.BackgroundStyle
import com.openschedule.core.model.Course
import com.openschedule.core.model.CourseSlot
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.WeekMath
import com.openschedule.core.model.normalizedColor
import com.openschedule.core.ui.LocalCourseColors
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.LocalIsDarkTheme
import com.openschedule.core.ui.LocalWallpaperActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun TimetableGrid(
    timetable: Timetable,
    settings: AppSettings,
    week: Int,
    modifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (day: Int, period: Int) -> Unit,
) {
    val cellHeight = settings.cellHeight
        .coerceIn(AppSettings.MIN_CELL_HEIGHT, AppSettings.MAX_CELL_HEIGHT)
        .dp
    val periods = timetable.periods
    val gridHeight = cellHeight * periods.size
    val courseTextScale = settings.courseTextScale
        .coerceIn(AppSettings.MIN_COURSE_TEXT_SCALE, AppSettings.MAX_COURSE_TEXT_SCALE)

    val visibleDays = (1..7)
        .map { WeekMath.isoDayOf(it, settings.weekStartDay) }
        .filter { settings.showWeekend || it < 6 }

    val today = LocalDate.now()
    val currentWeek = WeekMath.currentWeek(
        today = today,
        semesterStart = WeekMath.parseDate(timetable.semesterStart),
        totalWeeks = timetable.totalWeeks,
    )
    val semesterStart = WeekMath.parseDate(timetable.semesterStart)

    Column(modifier = modifier.fillMaxSize()) {
        DayHeader(
            days = visibleDays,
            week = week,
            weekStartDay = settings.weekStartDay,
            semesterStart = semesterStart,
            highlightToday = week == currentWeek,
            todayIso = today.dayOfWeek.value,
            periodColumnWidth = PERIOD_COLUMN_WIDTH,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BackgroundLayer(settings)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    Row(modifier = Modifier.height(gridHeight)) {
                        PeriodColumn(periods = periods, cellHeight = cellHeight)
                        visibleDays.forEach { isoDay ->
                            val occurrences = timetable.courses
                                .flatMap { course ->
                                    course.slotsOn(isoDay, week).map { slot ->
                                        CourseOccurrence(course, slot)
                                    }
                                }
                                .sortedBy { it.slot.startPeriod }

                            DayColumn(
                                modifier = Modifier.weight(1f).height(gridHeight),
                                day = isoDay,
                                occurrences = occurrences,
                                periodCount = periods.size,
                                cellHeight = cellHeight,
                                textScale = courseTextScale,
                                highlight = week == currentWeek && isoDay == today.dayOfWeek.value,
                                onCourseClick = onCourseClick,
                                onEmptyClick = onEmptyClick,
                            )
                        }
                    }
                    val noon = timetable.noonBreakAfterPeriod
                    if (noon in 1 until periods.size) {
                        val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val y = size.height * noon / periods.size
                            drawLine(
                                color = lineColor,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 4f,
                            )
                        }
                    }
                }
                // Let the last period scroll clear of the floating nav.
                Spacer(Modifier.height(LocalFloatingNavSpace.current + 8.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(
    days: List<Int>,
    week: Int,
    weekStartDay: Int,
    semesterStart: LocalDate?,
    highlightToday: Boolean,
    todayIso: Int,
    periodColumnWidth: androidx.compose.ui.unit.Dp,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Box(modifier = Modifier.width(periodColumnWidth))
            days.forEach { isoDay ->
                val column = WeekMath.columnOf(isoDay, weekStartDay)
                val date = WeekMath.dateOfWeekDay(semesterStart, week, column, weekStartDay)
                val isToday = highlightToday && isoDay == todayIso
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WeekMath.dayLabel(isoDay),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Text(
                        text = date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodColumn(periods: List<com.openschedule.core.model.Period>, cellHeight: androidx.compose.ui.unit.Dp) {
    Column(modifier = Modifier.width(PERIOD_COLUMN_WIDTH)) {
        periods.forEach { period ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight)
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = period.index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (period.start.isNotBlank()) {
                    Text(
                        text = period.start,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (period.end.isNotBlank()) {
                    Text(
                        text = period.end,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class CourseOccurrence(val course: Course, val slot: CourseSlot)

@Composable
private fun DayColumn(
    modifier: Modifier,
    day: Int,
    occurrences: List<CourseOccurrence>,
    periodCount: Int,
    cellHeight: androidx.compose.ui.unit.Dp,
    textScale: Float,
    highlight: Boolean,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (day: Int, period: Int) -> Unit,
) {
    val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val colors = LocalCourseColors.current

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(day, occurrences, cellHeightPx) {
                detectTapGestures { offset ->
                    val period = (offset.y / cellHeightPx).toInt() + 1
                    val hit = occurrences.firstOrNull {
                        period in it.slot.startPeriod..it.slot.endPeriod
                    }
                    if (hit != null) {
                        onCourseClick(hit.course)
                    } else if (period in 1..periodCount) {
                        onEmptyClick(day, period)
                    }
                }
            },
    ) {
        if (highlight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val rowHeight = size.height / periodCount.coerceAtLeast(1)
            for (row in 1 until periodCount) {
                val y = row * rowHeight
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(size.width - 0.5f, 0f), end = androidx.compose.ui.geometry.Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
        }

        occurrences.forEach { occurrence ->
            val course = occurrence.course
            val slot = occurrence.slot
            val start = slot.startPeriod.coerceAtLeast(1)
            val end = slot.endPeriod.coerceAtMost(periodCount)
            if (start > end) return@forEach
            val palette = colors[normalizedColor(course.color)]
            val customColor = course.customColor?.let { Color(it.toInt()) }
            Surface(
                color = customColor ?: palette.container,
                contentColor = customColor?.let {
                    if (it.luminance() > 0.5f) Color(0xFF14161A) else Color.White
                } ?: palette.onContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight * (end - start + 1) - 4.dp)
                    .offset(y = cellHeight * (start - 1) + 2.dp)
                    .padding(horizontal = 2.dp)
                    .semantics {
                        contentDescription =
                            "${course.name}，${WeekMath.dayLabel(slot.day)}，第${slot.startPeriod}到${slot.endPeriod}节"
                        onClick(label = "编辑课程") {
                            onCourseClick(course)
                            true
                        }
                    },
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
                    Text(
                        text = course.name,
                        fontSize = (11f * textScale).sp,
                        lineHeight = (13f * textScale).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.location.isNotBlank()) {
                        Text(
                            text = course.location,
                            fontSize = (9f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundLayer(settings: AppSettings) {
    if (LocalWallpaperActive.current) return
    val dark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, settings.backgroundImage) {
        value = withContext(Dispatchers.IO) {
            val name = settings.backgroundImage ?: return@withContext null
            val file = File(context.filesDir, name)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            settings.background == BackgroundStyle.IMAGE && image != null -> {
                Image(
                    bitmap = image!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = if (dark) 0.55f else 0.35f)),
                )
            }

            settings.background == BackgroundStyle.TINT -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

private val PERIOD_COLUMN_WIDTH = 56.dp
