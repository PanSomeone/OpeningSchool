package com.openschedule.feature.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.AppSettings
import com.openschedule.core.model.Period
import com.openschedule.core.model.WeekMath
import com.openschedule.core.ui.LocalCourseColors
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NumberStepper
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle
import com.openschedule.core.ui.SettingsCard
import com.openschedule.core.ui.SettingsRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettingsScreen(
    viewModel: TimetableViewModel,
    onBack: () -> Unit,
    onOpenWallpaper: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var datePickerOpen by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<Period?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(error) {
        val text = error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeError()
    }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(viewModel.exportJson().toByteArray())
            } ?: error("无法写入")
        }.onFailure { scope.launch { snackbar.showSnackbar("导出失败") } }
    }

    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (raw == null) {
            scope.launch { snackbar.showSnackbar("无法读取文件") }
        } else {
            viewModel.importJson(raw)?.let { scope.launch { snackbar.showSnackbar(it) } }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "课表设置",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTitle("课程表")

                SettingsCard {
                    var name by remember(timetable.id, timetable.name) { mutableStateOf(timetable.name) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(20) },
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (name.isNotBlank() && name != timetable.name) {
                            IconButton(onClick = { viewModel.renameActive(name) }) {
                                Icon(Icons.Filled.Check, contentDescription = "保存名称")
                            }
                        }
                    }
                    SettingsRow(title = "学期开始") {
                        TextButton(onClick = { datePickerOpen = true }) {
                            Text(
                                text = timetable.semesterStart.ifBlank { "未设置" },
                                color = if (timetable.semesterStart.isBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    SettingsRow(title = "总周数") {
                        NumberStepper(
                            value = timetable.totalWeeks,
                            min = 1,
                            max = 30,
                            onChange = viewModel::setTotalWeeks,
                        )
                    }
                    Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                        Text("一周开始", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        ) {
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = settings.weekStartDay == day,
                                    onClick = { viewModel.updateSettings { it.copy(weekStartDay = day) } },
                                    label = {
                                        Text(
                                            WeekMath.shortDayLabel(day),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    SettingsRow(title = "显示周末") {
                        Switch(
                            checked = settings.showWeekend,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(showWeekend = checked) }
                            },
                        )
                    }
                }

                SectionTitle("外观")

                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenWallpaper)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text("课表壁纸", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "与全局壁纸统一管理，可为课表页单独设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val cellHeight = settings.cellHeight
                        .coerceIn(AppSettings.MIN_CELL_HEIGHT, AppSettings.MAX_CELL_HEIGHT)
                    var sliderValue by remember(cellHeight) { mutableFloatStateOf(cellHeight.toFloat()) }
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("格子高度", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${sliderValue.roundToInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                viewModel.updateSettings { it.copy(cellHeight = sliderValue.roundToInt()) }
                            },
                            valueRange = AppSettings.MIN_CELL_HEIGHT.toFloat()..AppSettings.MAX_CELL_HEIGHT.toFloat(),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val storedTextScale = settings.courseTextScale.coerceIn(
                        AppSettings.MIN_COURSE_TEXT_SCALE,
                        AppSettings.MAX_COURSE_TEXT_SCALE,
                    )
                    var textScaleValue by remember(storedTextScale) {
                        mutableFloatStateOf(storedTextScale)
                    }
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("课程字号", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${(textScaleValue * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = textScaleValue,
                            onValueChange = { textScaleValue = it },
                            onValueChangeFinished = {
                                viewModel.updateSettings { it.copy(courseTextScale = textScaleValue) }
                            },
                            valueRange = AppSettings.MIN_COURSE_TEXT_SCALE..AppSettings.MAX_COURSE_TEXT_SCALE,
                        )
                        CourseCardPreview(
                            cellHeight = sliderValue,
                            textScale = textScaleValue,
                            showWeekend = settings.showWeekend,
                        )
                    }
                }

                SectionTitle("作息")

                SettingsCard {
                    timetable.periods.forEachIndexed { index, period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingPeriod = period }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "第 ${period.index} 节",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(76.dp),
                            )
                            Text(
                                text = "${period.start} – ${period.end}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index != timetable.periods.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    TextButton(
                        onClick = viewModel::addPeriod,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加一节")
                    }
                    val noonMax = timetable.periods.size - 1
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(title = "午休分割线") {
                        Switch(
                            checked = timetable.noonBreakAfterPeriod > 0,
                            onCheckedChange = { enabled ->
                                viewModel.setNoonBreak(
                                    if (enabled) minOf(4, noonMax).coerceAtLeast(1) else 0,
                                )
                            },
                            enabled = noonMax >= 1,
                        )
                    }
                    if (timetable.noonBreakAfterPeriod > 0) {
                        SettingsRow(title = "分割在第几节后") {
                            NumberStepper(
                                value = timetable.noonBreakAfterPeriod,
                                min = 1,
                                max = noonMax,
                                onChange = viewModel::setNoonBreak,
                            )
                        }
                    }
                }

                SectionTitle("数据")

                SettingsCard {
                    SettingsRow(title = "导出") {
                        TextButton(onClick = { exportJson.launch("timetable.json") }) {
                            Text("JSON")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(title = "导入") {
                        TextButton(onClick = {
                            importJson.launch(arrayOf("application/json", "text/plain", "text/*"))
                        }) { Text("JSON") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmClear = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "清空课程",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
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

    if (datePickerOpen) {
        val initial = WeekMath.parseDate(timetable.semesterStart)
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.saveSemesterStart(date.toString())
                    }
                    datePickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    editingPeriod?.let { period ->
        PeriodDialog(
            period = period,
            canDelete = timetable.periods.size > 1,
            onSave = { start, end, shiftFollowing ->
                viewModel.updatePeriod(period.index, start, end, shiftFollowing)
                editingPeriod = null
            },
            onDelete = {
                viewModel.removePeriod(period.index)
                editingPeriod = null
            },
            onDismiss = { editingPeriod = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有课程？") },
            text = { Text("作息和设置会保留") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAllCourses()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CourseCardPreview(
    cellHeight: Float,
    textScale: Float,
    showWeekend: Boolean,
) {
    val palette = LocalCourseColors.current.first()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        val days = if (showWeekend) 7 else 5
        val dayWidth = ((maxWidth - 56.dp) / days).coerceAtLeast(36.dp)
        Column {
            Text(
                text = "预览 · 约 ${dayWidth.value.roundToInt()}dp 一列 · 两节",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = palette.container,
                contentColor = palette.onContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(dayWidth - 4.dp)
                    .height((cellHeight * 2 - 4).coerceAtLeast(80f).dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
                    Text(
                        text = "智能媒体数据处理",
                        fontSize = (11f * textScale).sp,
                        lineHeight = (12.5f * textScale).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "高俊祥",
                        fontSize = (8.5f * textScale).sp,
                        lineHeight = (9.5f * textScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "三教A403",
                        fontSize = (8.5f * textScale).sp,
                        lineHeight = (9.5f * textScale).sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodDialog(
    period: Period,
    canDelete: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(period.start) }
    var end by remember { mutableStateOf(period.end) }
    var shiftFollowing by remember { mutableStateOf(true) }
    val normalizedStart = normalizeTime(start)
    val normalizedEnd = normalizeTime(end)
    val valid = normalizedStart != null && normalizedEnd != null && normalizedEnd > normalizedStart

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${period.index} 节") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("开始") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("结束") },
                    placeholder = { Text("08:45") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid) {
                    Text(
                        text = "时间格式为 HH:MM，结束需晚于开始",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("顺延后面的节次", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "时长变化后，后续节次一起平移",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = shiftFollowing,
                        onCheckedChange = { shiftFollowing = it },
                    )
                }
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("删除这一节", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (normalizedStart != null && normalizedEnd != null) {
                        onSave(normalizedStart, normalizedEnd, shiftFollowing)
                    }
                },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private val TIME_REGEX = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

private fun normalizeTime(raw: String): String? {
    val trimmed = raw.trim()
    if (!TIME_REGEX.matches(trimmed)) return null
    val (hour, minute) = trimmed.split(":")
    return "%02d:%s".format(hour.toInt(), minute)
}
