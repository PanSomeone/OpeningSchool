package com.openschedule.feature.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.Course
import com.openschedule.core.model.WeekMath
import com.openschedule.core.ui.EmptyState
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.WebCaptureDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private data class WebTarget(val name: String, val url: String)

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    onOpenSettings: () -> Unit = {},
) {
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val timetables by viewModel.timetables.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val importers by viewModel.importers.collectAsStateWithLifecycle()
    val loadingImporters by viewModel.loadingImporters.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var showSwitcher by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var webTarget by remember { mutableStateOf<WebTarget?>(null) }
    var editing by remember { mutableStateOf<Course?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var defaultDay by remember { mutableStateOf(1) }
    var defaultPeriod by remember { mutableStateOf(1) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(error, webTarget) {
        if (webTarget != null) return@LaunchedEffect
        val text = error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeError()
    }
    LaunchedEffect(pendingImport) {
        if (pendingImport == null) return@LaunchedEffect
        webTarget = null
        showImport = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimetableHeader(
                title = timetable.name.ifBlank { "课表" },
                onSwitch = { showSwitcher = true },
                onAdd = {
                    editing = null
                    defaultDay = 1
                    defaultPeriod = 1
                    editorOpen = true
                },
                onImport = { showImport = true },
                onSettings = onOpenSettings,
            )

            WeekStrip(
                week = week,
                totalWeeks = timetable.totalWeeks,
                semesterStart = timetable.semesterStart,
                weekStartDay = settings.weekStartDay,
                onSelect = viewModel::setWeek,
                onToday = viewModel::goToCurrentWeek,
                onStartDateChange = viewModel::saveSemesterStart,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(week, timetable.totalWeeks) {
                        val threshold = maxOf(56.dp.toPx(), size.width * 0.1f)
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragCancel = { dragged = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                dragged += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                val direction = when {
                                    dragged <= -threshold -> 1
                                    dragged >= threshold -> -1
                                    else -> 0
                                }
                                val target = (week + direction)
                                    .coerceIn(1, timetable.totalWeeks)
                                if (direction != 0 && target != week) {
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                    viewModel.setWeek(target)
                                }
                            },
                        )
                    },
            ) {
                TimetableGrid(
                    timetable = timetable,
                    settings = settings,
                    week = week,
                    onCourseClick = { course ->
                        editing = course
                        editorOpen = true
                    },
                    onEmptyClick = { day, period ->
                        editing = null
                        defaultDay = day
                        defaultPeriod = period
                        editorOpen = true
                    },
                )

                if (timetable.courses.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 20.dp),
                    ) {
                        EmptyState(
                            icon = Icons.Filled.CalendarMonth,
                            title = "还没有课程",
                            hint = "点空白格添加，或从教务系统导入",
                            action = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = {
                                        editing = null
                                        editorOpen = true
                                    }) { Text("添加") }
                                    TextButton(onClick = { showImport = true }) { Text("导入") }
                                }
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalFloatingNavSpace.current + 16.dp),
        )
    }

    if (showSwitcher) {
        TimetableSwitcherSheet(
            collection = timetables,
            onSwitch = viewModel::switchTimetable,
            onCreate = viewModel::createTimetable,
            onRename = viewModel::renameTimetable,
            onDelete = viewModel::deleteTimetable,
            onDismiss = { showSwitcher = false },
        )
    }

    if (editorOpen) {
        CourseEditorSheet(
            timetable = timetable,
            settings = settings,
            initial = editing,
            defaultDay = defaultDay,
            defaultPeriod = defaultPeriod,
            onDismiss = {
                editorOpen = false
                editing = null
            },
            onSave = viewModel::upsertCourse,
            onDelete = { viewModel.deleteCourse(it.id) },
        )
    }

    if (showImport) {
        ImportSheet(
            importers = importers,
            loading = loadingImporters,
            importing = importing,
            onRefresh = viewModel::refreshImporters,
            onImport = viewModel::importTimetable,
            onOpenWeb = { name, url ->
                showImport = false
                webTarget = WebTarget(name, url)
            },
            onDismiss = { showImport = false },
        )
    }

    webTarget?.let { target ->
        WebCaptureDialog(
            title = target.name,
            startUrl = target.url,
            hint = "登录并打开课表页，再点导入",
            actionLabel = "导入",
            busy = importing,
            error = error,
            onErrorConsumed = viewModel::consumeError,
            onCapture = { pages -> viewModel.importHtml(pages, "${target.name}课表") },
            onClose = {
                webTarget = null
                viewModel.consumeError()
            },
        )
    }

    pendingImport?.let { preview ->
        ImportPreviewSheet(
            preview = preview,
            onReplace = { viewModel.commitPendingImport(createNew = false) },
            onCreateNew = { viewModel.commitPendingImport(createNew = true) },
            onDismiss = viewModel::dismissPendingImport,
        )
    }
}

@Composable
private fun TimetableHeader(
    title: String,
    onSwitch: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSwitch)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "切换课表",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(24.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("添加课程") },
                    onClick = {
                        menuOpen = false
                        onAdd()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("导入课表") },
                    onClick = {
                        menuOpen = false
                        onImport()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("课表设置") },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun WeekStrip(
    week: Int,
    totalWeeks: Int,
    semesterStart: String,
    weekStartDay: Int,
    onSelect: (Int) -> Unit,
    onToday: () -> Unit,
    onStartDateChange: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var startPickerOpen by remember { mutableStateOf(false) }
    val startDate = WeekMath.parseDate(semesterStart)
    val rangeStart = startDate?.let { WeekMath.dateOfWeekDay(it, week, 1, weekStartDay) }
    val rangeEnd = startDate?.let { WeekMath.dateOfWeekDay(it, week, 7, weekStartDay) }
    val dateLabel = if (rangeStart != null && rangeEnd != null) {
        "${rangeStart.monthValue}.${rangeStart.dayOfMonth} – " +
            "${rangeEnd.monthValue}.${rangeEnd.dayOfMonth}"
    } else {
        "设置开学日期"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (week > 1) onSelect(week - 1) },
                    enabled = week > 1,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "上一周",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "第 $week 周",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { pickerOpen = true },
                            onDoubleClick = onToday,
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                IconButton(
                    onClick = { if (week < totalWeeks) onSelect(week + 1) },
                    enabled = week < totalWeeks,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "下一周",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { startPickerOpen = true }
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "设置开学日期",
                tint = if (startDate == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (startDate == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (startPickerOpen) {
        val initial = startDate
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
            ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { startPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onStartDateChange(date.toString())
                    }
                    startPickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { startPickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("选择周次") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..totalWeeks).forEach { value ->
                            val selected = value == week
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .size(width = 44.dp, height = 36.dp)
                                    .clickable {
                                        onSelect(value)
                                        pickerOpen = false
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = value.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "左右滑动切换周次 · 双击周次回到本周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("关闭") }
            },
        )
    }
}
