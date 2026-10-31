package com.openschedule.feature.home

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.Course
import com.openschedule.core.model.CourseSlot
import com.openschedule.core.model.HomeLayout
import com.openschedule.core.model.HomeWidgetPlacement
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.WeekMath
import com.openschedule.core.model.normalizedColor
import com.openschedule.core.ui.LocalCourseColors
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.ScreenHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

data class HomeShortcut(
    val id: String,
    val label: String,
    val iconKey: String,
    val installed: Boolean,
)

/** A widget supplied by a plugin module. */
class HomeWidgetSpec(
    val id: String,
    val title: String,
    val iconKey: String,
    val available: Boolean,
    val content: @Composable (Modifier) -> Unit,
)

private data class TodayCourse(val course: Course, val slot: CourseSlot)

private data class WidgetMeta(
    val type: String,
    val title: String,
    val iconKey: String,
    val single: Boolean,
    val available: Boolean,
)

private data class WidgetSize(val columns: Int, val rows: Int) {
    val label: String get() = "$columns × $rows"
}

private val SIZE_OPTIONS = listOf(
    WidgetSize(1, 1),
    WidgetSize(2, 1),
    WidgetSize(1, 2),
    WidgetSize(2, 2),
)

private const val TYPE_FOLDER = "folder"
private const val CELL_HEIGHT_DP = 122

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    layout: HomeLayout,
    onLayoutChange: (HomeLayout) -> Unit,
    externalWidgets: List<HomeWidgetSpec>,
    shortcuts: List<HomeShortcut>,
    timetableEnabled: Boolean,
    onOpen: (String) -> Unit,
    unreadAnnouncements: Int = 0,
    onOpenAnnouncements: () -> Unit = {},
) {
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val loadingWeather by viewModel.loadingWeather.collectAsStateWithLifecycle()
    val weatherError by viewModel.weatherError.collectAsStateWithLifecycle()

    val safeLayout = remember(layout) { sanitizeLayout(layout) }
    val today = LocalDate.now()
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }
    val week = WeekMath.currentWeek(
        today = today,
        semesterStart = WeekMath.parseDate(timetable.semesterStart),
        totalWeeks = timetable.totalWeeks,
    )
    val allTodayCourses = if (timetableEnabled) {
        timetable.courses
            .flatMap { course ->
                course.slotsOn(today.dayOfWeek.value, week).map { slot ->
                    TodayCourse(course, slot)
                }
            }
            .sortedBy { it.slot.startPeriod }
    } else {
        emptyList()
    }
    val todayCourses = allTodayCourses.filter { occurrence ->
        val end = timetable.period(occurrence.slot.endPeriod)?.end
        val endTime = end?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        endTime == null || endTime.isAfter(now)
    }
    val todayFinished = allTodayCourses.isNotEmpty() && todayCourses.isEmpty()

    var editMode by remember { mutableStateOf(false) }
    var showWeather by remember { mutableStateOf(false) }
    var pickerCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sizingId by remember { mutableStateOf<String?>(null) }
    var sizeError by remember { mutableStateOf<String?>(null) }
    var folderConfigId by remember { mutableStateOf<String?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showWeather) {
        WeatherScreen(
            viewModel = viewModel,
            onBack = { showWeather = false },
        )
        return
    }

    val meta = buildMeta(externalWidgets)
    val installedShortcuts = shortcuts.filter { it.installed }

    fun persist(next: HomeLayout) {
        onLayoutChange(sanitizeLayout(next))
    }

    fun showMessage(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun addWidget(type: String, row: Int, col: Int) {
        val info = meta.firstOrNull { it.type == type } ?: return
        if (info.single && safeLayout.widgets.any { it.type == type }) return
        val placement = if (type == TYPE_FOLDER) {
            HomeWidgetPlacement(
                id = "folder-${UUID.randomUUID()}",
                type = type,
                row = row,
                col = col,
                title = "文件夹",
                shortcuts = installedShortcuts.map { it.id },
            )
        } else {
            HomeWidgetPlacement(id = type, type = type, row = row, col = col)
        }
        if (overlaps(safeLayout.widgets, null, row, col, 1, 1)) {
            showMessage("这里放不下")
            return
        }
        persist(safeLayout.copy(widgets = safeLayout.widgets + placement))
    }

    fun resizeWidget(id: String, size: WidgetSize) {
        val result = placeWithSize(safeLayout, id, size)
        if (result == null) {
            sizeError = "没有足够的空位"
            return
        }
        persist(result)
        sizingId = null
        sizeError = null
    }

    fun updateWidget(updated: HomeWidgetPlacement) {
        persist(
            safeLayout.copy(
                widgets = safeLayout.widgets.map {
                    if (it.id == updated.id) updated else it
                },
            ),
        )
    }

    fun addRow() {
        if (safeLayout.rows >= HomeLayout.MAX_ROWS) return
        persist(safeLayout.copy(rows = safeLayout.rows + 1))
    }

    fun removeLastRow() {
        val rows = safeLayout.rows
        if (rows <= HomeLayout.MIN_ROWS) return
        val lastRowOccupied = safeLayout.widgets.any { widget ->
            widget.row + widget.rowSpan > rows - 1
        }
        if (lastRowOccupied) {
            showMessage("最后一行还有组件")
            return
        }
        persist(safeLayout.copy(rows = rows - 1))
    }

    val pickerOptions = meta.filter { option ->
        option.available &&
            (!option.single || safeLayout.widgets.none { it.type == option.type })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = if (editMode) "编辑主页" else "主页") {
                if (editMode) {
                    TextButton(onClick = { editMode = false }) { Text("完成") }
                } else {
                    if (unreadAnnouncements > 0) {
                        IconButton(onClick = onOpenAnnouncements) {
                            Box {
                                Icon(
                                    Icons.Filled.Campaign,
                                    contentDescription = "查看公告，$unreadAnnouncements 条未读",
                                    modifier = Modifier.size(24.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.surface,
                                            CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                    IconButton(onClick = { editMode = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑主页")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
            ) {
                Text(
                    text = buildString {
                        append("${today.monthValue} 月 ${today.dayOfMonth} 日 ")
                        append(WeekMath.dayLabel(today.dayOfWeek.value))
                        if (timetableEnabled) append(" · 第 $week 周")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                if (editMode) {
                    Text(
                        text = "长按拖动，点空白格添加；右上角改大小",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))

                HomeCanvas(
                    layout = safeLayout,
                    editMode = editMode,
                    meta = meta,
                    externalWidgets = externalWidgets,
                    shortcuts = installedShortcuts,
                    timetable = timetable,
                    timetableEnabled = timetableEnabled,
                    todayCourses = todayCourses,
                    todayFinished = todayFinished,
                    weather = weather,
                    loadingWeather = loadingWeather,
                    onOpen = onOpen,
                    onOpenWeather = { showWeather = true },
                    onPickCell = { row, col -> pickerCell = row to col },
                    onRequestSize = {
                        sizingId = it
                        sizeError = null
                    },
                    onRequestFolderConfig = { folderConfigId = it },
                    onOpenFolder = { openFolderId = it },
                    onMove = { id, row, col ->
                        val moved = movedWidgets(safeLayout.widgets, id, row, col)
                        if (moved == null) {
                            showMessage("目标位置放不下")
                        } else {
                            persist(safeLayout.copy(widgets = moved))
                        }
                    },
                    onRemove = { id ->
                        persist(safeLayout.copy(widgets = safeLayout.widgets.filterNot { it.id == id }))
                    },
                )

                if (editMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = ::addRow,
                            enabled = safeLayout.rows < HomeLayout.MAX_ROWS,
                        ) { Text("添加一行") }
                        TextButton(
                            onClick = ::removeLastRow,
                            enabled = safeLayout.rows > HomeLayout.MIN_ROWS,
                            modifier = Modifier.weight(1f),
                        ) { Text("删除空行") }
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

    pickerCell?.let { (row, col) ->
        WidgetPickerSheet(
            options = pickerOptions,
            onPick = { type ->
                addWidget(type, row, col)
                pickerCell = null
            },
            onDismiss = { pickerCell = null },
        )
    }

    sizingId?.let { id ->
        val widget = safeLayout.widgets.firstOrNull { it.id == id }
        if (widget != null) {
            SizeDialog(
                current = WidgetSize(widget.colSpan, widget.rowSpan),
                error = sizeError,
                onPick = { size -> resizeWidget(id, size) },
                onDismiss = {
                    sizingId = null
                    sizeError = null
                },
            )
        } else {
            LaunchedEffect(id) { sizingId = null }
        }
    }

    folderConfigId?.let { id ->
        val folder = safeLayout.widgets.firstOrNull { it.id == id }
        if (folder != null) {
            FolderConfigSheet(
                placement = folder,
                shortcuts = installedShortcuts,
                onSave = { title, selected ->
                    updateWidget(
                        folder.copy(
                            title = title,
                            shortcuts = selected,
                        ),
                    )
                    folderConfigId = null
                },
                onDismiss = { folderConfigId = null },
            )
        } else {
            LaunchedEffect(id) { folderConfigId = null }
        }
    }

    openFolderId?.let { id ->
        val folder = safeLayout.widgets.firstOrNull { it.id == id }
        if (folder != null) {
            FolderSheet(
                title = folder.title.ifBlank { "文件夹" },
                entries = folderEntries(folder, installedShortcuts),
                onOpen = { shortcutId ->
                    openFolderId = null
                    onOpen(shortcutId)
                },
                onDismiss = { openFolderId = null },
            )
        } else {
            LaunchedEffect(id) { openFolderId = null }
        }
    }
}

@Composable
private fun HomeCanvas(
    layout: HomeLayout,
    editMode: Boolean,
    meta: List<WidgetMeta>,
    externalWidgets: List<HomeWidgetSpec>,
    shortcuts: List<HomeShortcut>,
    timetable: Timetable,
    timetableEnabled: Boolean,
    todayCourses: List<TodayCourse>,
    todayFinished: Boolean,
    weather: WeatherUi?,
    loadingWeather: Boolean,
    onOpen: (String) -> Unit,
    onOpenWeather: () -> Unit,
    onPickCell: (Int, Int) -> Unit,
    onRequestSize: (String) -> Unit,
    onRequestFolderConfig: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val rows = layout.rows
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragSize by remember { mutableStateOf(WidgetSize(1, 1)) }
    var dragStart by remember { mutableStateOf<Pair<Int, Int>>(0 to 0) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val cellWidth = (maxWidth - gap) / 2
        val cellHeight = CELL_HEIGHT_DP.dp
        val stepX = with(density) { (cellWidth + gap).toPx() }
        val stepY = with(density) { (cellHeight + gap).toPx() }
        val gridHeight = cellHeight * rows + gap * (rows - 1)
        val occupied = layout.widgets.flatMap { it.cells() }.toSet()

        Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
            if (editMode) {
                for (row in 0 until rows) {
                    for (col in 0 until HomeLayout.COLUMNS) {
                        if ((row to col) in occupied) continue
                        Box(
                            modifier = Modifier
                                .offset(x = (cellWidth + gap) * col, y = (cellHeight + gap) * row)
                                .width(cellWidth)
                                .height(cellHeight)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(18.dp),
                                )
                                .clickable { onPickCell(row, col) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "添加组件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            layout.widgets.forEach { widget ->
                val info = meta.firstOrNull { it.type == widget.type }
                val available = info?.available == true
                val x = (cellWidth + gap) * widget.col
                val y = (cellHeight + gap) * widget.row
                val w = cellWidth * widget.colSpan + gap * (widget.colSpan - 1)
                val h = cellHeight * widget.rowSpan + gap * (widget.rowSpan - 1)
                val dragging = dragId == widget.id
                val targetCell = dragTarget
                val snapTarget = if (dragging && targetCell != null) {
                    Offset(
                        x = (targetCell.second - widget.col) * stepX,
                        y = (targetCell.first - widget.row) * stepY,
                    )
                } else {
                    Offset.Zero
                }
                val animatedSnap by animateOffsetAsState(
                    targetValue = snapTarget,
                    animationSpec = tween(durationMillis = 110),
                    label = "widgetDragSnap",
                )
                val dragTranslation = if (dragging) animatedSnap else Offset.Zero
                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(w)
                        .height(h)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = dragTranslation.x
                            translationY = dragTranslation.y
                        },
                ) {
                    if (available) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (widget.type == "weather") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (widget.type == "weather") 0.dp else 1.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (dragging) 0.9f else 1f),
                        ) {
                            val fontScale = when {
                                widget.colSpan == 2 && widget.rowSpan == 2 -> 1.10f
                                widget.rowSpan == 2 -> 1.06f
                                widget.colSpan == 2 -> 1.03f
                                else -> 1f
                            }
                            val widgetDensity = remember(
                                density.density,
                                density.fontScale,
                                fontScale,
                            ) {
                                Density(density.density, density.fontScale * fontScale)
                            }
                            CompositionLocalProvider(LocalDensity provides widgetDensity) {
                                WidgetContent(
                                    widget = widget,
                                    modifier = Modifier.fillMaxSize(),
                                    externalWidgets = externalWidgets,
                                    shortcuts = shortcuts,
                                    timetable = timetable,
                                    timetableEnabled = timetableEnabled,
                                    todayCourses = todayCourses,
                                    todayFinished = todayFinished,
                                    weather = weather,
                                    loadingWeather = loadingWeather,
                                    onOpen = onOpen,
                                    onOpenWeather = onOpenWeather,
                                    onOpenFolder = { onOpenFolder(widget.id) },
                                )
                            }
                        }

                        if (!editMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    )
                                    .pointerInput(
                                        widget.id,
                                        rows,
                                        stepX,
                                        stepY,
                                        widget.colSpan,
                                        widget.rowSpan,
                                    ) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                                dragId = widget.id
                                                dragStart = widget.row to widget.col
                                                dragSize = WidgetSize(widget.colSpan, widget.rowSpan)
                                                dragOffset = Offset.Zero
                                                dragTarget = widget.row to widget.col
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                val row = (dragStart.first +
                                                    (dragOffset.y / stepY).roundToInt())
                                                    .coerceIn(0, rows - widget.rowSpan)
                                                val col = (dragStart.second +
                                                    (dragOffset.x / stepX).roundToInt())
                                                    .coerceIn(0, HomeLayout.COLUMNS - widget.colSpan)
                                                dragTarget = row to col
                                            },
                                            onDragEnd = {
                                                val target = dragTarget
                                                val id = dragId
                                                if (id != null && target != null &&
                                                    target != (widget.row to widget.col)
                                                ) {
                                                    onMove(id, target.first, target.second)
                                                }
                                                dragId = null
                                                dragTarget = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragId = null
                                                dragTarget = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.DragIndicator,
                                    contentDescription = "长按拖动组件",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.45f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        if (editMode) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(widget.id, rows, stepX, stepY, widget.colSpan, widget.rowSpan) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragId = widget.id
                                                dragStart = widget.row to widget.col
                                                dragSize = WidgetSize(widget.colSpan, widget.rowSpan)
                                                dragOffset = Offset.Zero
                                                dragTarget = widget.row to widget.col
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                val row = (dragStart.first +
                                                    (dragOffset.y / stepY).roundToInt())
                                                    .coerceIn(0, rows - widget.rowSpan)
                                                val col = (dragStart.second +
                                                    (dragOffset.x / stepX).roundToInt())
                                                    .coerceIn(0, HomeLayout.COLUMNS - widget.colSpan)
                                                dragTarget = row to col
                                            },
                                            onDragEnd = {
                                                val target = dragTarget
                                                val id = dragId
                                                if (id != null && target != null &&
                                                    target != (widget.row to widget.col)
                                                ) {
                                                    onMove(id, target.first, target.second)
                                                }
                                                dragId = null
                                                dragTarget = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragId = null
                                                dragTarget = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                            )
                            WidgetEditControls(
                                isFolder = widget.type == TYPE_FOLDER,
                                onRemove = { onRemove(widget.id) },
                                onResize = { onRequestSize(widget.id) },
                                onConfigure = { onRequestFolderConfig(widget.id) },
                            )
                        }
                    } else if (editMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(18.dp),
                                ),
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = info?.title ?: widget.type,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "插件未安装",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            WidgetRemoveButton(
                                onClick = { onRemove(widget.id) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            )
                        }
                    }
                }
            }

            if (dragId != null && dragTarget != null) {
                val target = dragTarget!!
                val draggingId = dragId
                val valid = draggingId != null && canDrop(
                    layout.widgets,
                    draggingId,
                    target.first,
                    target.second,
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = (cellWidth + gap) * target.second,
                            y = (cellHeight + gap) * target.first,
                        )
                        .width(cellWidth * dragSize.columns + gap * (dragSize.columns - 1))
                        .height(cellHeight * dragSize.rows + gap * (dragSize.rows - 1))
                        .border(
                            2.dp,
                            if (valid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            RoundedCornerShape(18.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    widget: HomeWidgetPlacement,
    modifier: Modifier,
    externalWidgets: List<HomeWidgetSpec>,
    shortcuts: List<HomeShortcut>,
    timetable: Timetable,
    timetableEnabled: Boolean,
    todayCourses: List<TodayCourse>,
    todayFinished: Boolean,
    weather: WeatherUi?,
    loadingWeather: Boolean,
    onOpen: (String) -> Unit,
    onOpenWeather: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    when (widget.type) {
        "weather" -> WeatherWidget(
            weather = weather,
            loading = loadingWeather,
            colSpan = widget.colSpan,
            rowSpan = widget.rowSpan,
            onClick = onOpenWeather,
            modifier = modifier,
        )

        "today" -> TodayWidget(
            timetable = timetable,
            timetableEnabled = timetableEnabled,
            courses = todayCourses,
            finished = todayFinished,
            colSpan = widget.colSpan,
            rowSpan = widget.rowSpan,
            onClick = { onOpen(if (timetableEnabled) "timetable" else "plugins") },
            modifier = modifier,
        )

        "date" -> DateWidget(
            timetable = timetable,
            timetableEnabled = timetableEnabled,
            onClick = { onOpen(if (timetableEnabled) "timetable" else "plugins") },
            modifier = modifier,
        )

        TYPE_FOLDER -> FolderWidget(
            placement = widget,
            shortcuts = folderEntries(widget, shortcuts),
            onClick = onOpen,
            onOpenFolder = onOpenFolder,
            modifier = modifier,
        )

        else -> {
            val spec = externalWidgets.firstOrNull { it.id == widget.type && it.available }
            if (spec != null) spec.content(modifier)
        }
    }
}

@Composable
private fun WeatherWidget(
    weather: WeatherUi?,
    loading: Boolean,
    colSpan: Int,
    rowSpan: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = colSpan == 2
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                NavIcons.icon("sun"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = weather?.city ?: "武汉",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        if (wide) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = weather?.let { "${it.temperature}°" } ?: if (loading) "…" else "--",
                        fontSize = if (rowSpan == 2) 48.sp else 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = weather?.condition ?: if (loading) "获取天气" else "点击查看天气",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (weather != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "最高 ${weather.high}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "最低 ${weather.low}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (rowSpan == 2) {
                            Text(
                                text = weather.humidity?.let { "湿度 $it%" }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                } else if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            if (weather != null) {
                Text(
                    text = "${weather.temperature}°",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${weather.condition} · ${weather.high}°/${weather.low}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = if (loading) "获取天气" else "点击查看天气",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun TodayWidget(
    timetable: Timetable,
    timetableEnabled: Boolean,
    courses: List<TodayCourse>,
    finished: Boolean,
    colSpan: Int,
    rowSpan: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = colSpan == 2 || rowSpan == 2
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                NavIcons.icon("calendar"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "今日课程",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        if (!timetableEnabled) {
            Spacer(Modifier.weight(1f))
            Text("课程表未添加", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "点击去插件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (courses.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (finished) "今天的课已结束" else "今天没有课",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "查看课表",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!expanded) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "${courses.size} 节剩余",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val next = courses.first()
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CourseTimes(timetable, next.slot)
                Text(
                    text = next.course.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 7.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val visible = if (rowSpan == 2) courses.take(4) else courses.take(2)
                val palette = LocalCourseColors.current
                visible.forEach { occurrence ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CourseTimes(timetable, occurrence.slot)
                        Surface(
                            color = occurrence.course.customColor
                                ?.let { Color(it.toInt()) }
                                ?: palette[normalizedColor(occurrence.course.color)].container,
                            modifier = Modifier
                                .padding(start = 5.dp)
                                .width(3.dp)
                                .height(28.dp),
                        ) {}
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 7.dp),
                        ) {
                            Text(
                                text = occurrence.course.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = occurrence.course.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (courses.size > visible.size) {
                    Text(
                        text = "还有 ${courses.size - visible.size} 节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 51.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseTimes(
    timetable: Timetable,
    slot: CourseSlot,
    modifier: Modifier = Modifier,
) {
    val start = timetable.period(slot.startPeriod)?.start.orEmpty()
    val end = timetable.period(slot.endPeriod)?.end.orEmpty()
    Column(
        modifier = modifier.width(44.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = start.ifBlank { "--" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        if (end.isNotBlank()) {
            Text(
                text = end,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun DateWidget(
    timetable: Timetable,
    timetableEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val week = WeekMath.currentWeek(
        today = today,
        semesterStart = WeekMath.parseDate(timetable.semesterStart),
        totalWeeks = timetable.totalWeeks,
    )
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                NavIcons.icon("date"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "日期",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = today.dayOfMonth.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = buildString {
                append("${today.monthValue} 月 ${WeekMath.dayLabel(today.dayOfWeek.value)}")
                if (timetableEnabled) append(" · 第 $week 周")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderWidget(
    placement: HomeWidgetPlacement,
    shortcuts: List<HomeShortcut>,
    onClick: (String) -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = if (placement.colSpan == 2) 3 else 2
    val visibleRows = if (placement.rowSpan == 2) 3 else 2
    val capacity = columns * visibleRows
    val visible = shortcuts.take(capacity)
    val hasMore = shortcuts.size > capacity

    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFolder),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = placement.title.ifBlank { "文件夹" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasMore) {
                Text(
                    text = "+${shortcuts.size - capacity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (visible.isEmpty()) {
            Text(
                text = "空文件夹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            visible.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.forEach { shortcut ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onClick(shortcut.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                NavIcons.icon(shortcut.iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = shortcut.label,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetEditControls(
    isFolder: Boolean,
    onRemove: () -> Unit,
    onResize: () -> Unit,
    onConfigure: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isFolder) {
            SmallCircleIcon(
                icon = Icons.Filled.Settings,
                contentDescription = "文件夹设置",
                onClick = onConfigure,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            )
        }
        SmallCircleIcon(
            icon = Icons.Filled.OpenInFull,
            contentDescription = "调整大小",
            onClick = onResize,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
        )
        SmallCircleIcon(
            icon = Icons.Filled.Close,
            contentDescription = "移除组件",
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
    }
}

@Composable
private fun WidgetRemoveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallCircleIcon(
        icon = Icons.Filled.Close,
        contentDescription = "移除组件",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SmallCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        modifier = modifier
            .size(24.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPickerSheet(
    options: List<WidgetMeta>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "添加组件",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (options.isEmpty()) {
                Text(
                    text = "没有可添加的组件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option.type) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                NavIcons.icon(option.iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != options.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SizeDialog(
    current: WidgetSize,
    error: String?,
    onPick: (WidgetSize) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("组件大小") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SIZE_OPTIONS.forEach { size ->
                    val selected = size == current
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(size) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${size.label} 格",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when {
                                    size.columns == 1 && size.rows == 1 -> "一格"
                                    size.columns == 2 && size.rows == 1 -> "横向两格"
                                    size.columns == 1 && size.rows == 2 -> "竖向两格"
                                    else -> "四格"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderConfigSheet(
    placement: HomeWidgetPlacement,
    shortcuts: List<HomeShortcut>,
    onSave: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(placement.id) {
        mutableStateOf(placement.title.ifBlank { "文件夹" })
    }
    var selected by remember(placement.id) {
        mutableStateOf(placement.shortcuts.toSet())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "文件夹设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(8) },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "包含的功能",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            shortcuts.forEachIndexed { index, shortcut ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (shortcut.id in selected) {
                                selected - shortcut.id
                            } else {
                                selected + shortcut.id
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        NavIcons.icon(shortcut.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = shortcut.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                    Checkbox(
                        checked = shortcut.id in selected,
                        onCheckedChange = {
                            selected = if (it) selected + shortcut.id else selected - shortcut.id
                        },
                    )
                }
                if (index != shortcuts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Button(
                onClick = {
                    onSave(
                        title.trim().ifBlank { "文件夹" },
                        shortcuts.filter { it.id in selected }.map { it.id },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(50.dp),
            ) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSheet(
    title: String,
    entries: List<HomeShortcut>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (entries.isEmpty()) {
                Text(
                    text = "空文件夹，编辑主页时点齿轮添加功能",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            entries.forEachIndexed { index, shortcut ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(shortcut.id) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        NavIcons.icon(shortcut.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = shortcut.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
                if (index != entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun buildMeta(externalWidgets: List<HomeWidgetSpec>): List<WidgetMeta> {
    val internal = listOf(
        WidgetMeta("weather", "天气", "sun", single = true, available = true),
        WidgetMeta("today", "今日课程", "calendar", single = true, available = true),
        WidgetMeta("date", "日期", "date", single = true, available = true),
        WidgetMeta(TYPE_FOLDER, "文件夹", "widgets", single = false, available = true),
    )
    val external = externalWidgets.map {
        WidgetMeta(it.id, it.title, it.iconKey, single = true, available = it.available)
    }
    return internal + external
}

private fun sanitizeLayout(layout: HomeLayout): HomeLayout {
    val rows = layout.rows.coerceIn(HomeLayout.MIN_ROWS, HomeLayout.MAX_ROWS)
    val seen = mutableSetOf<String>()
    val seenSingleTypes = mutableSetOf<String>()
    val occupied = mutableSetOf<Pair<Int, Int>>()
    val widgets = mutableListOf<HomeWidgetPlacement>()
    layout.widgets.forEach { widget ->
        if (widget.id.isBlank() || !seen.add(widget.id)) return@forEach
        if (widget.type in SINGLE_TYPES_ORDER && !seenSingleTypes.add(widget.type)) return@forEach
        val colSpan = widget.colSpan.coerceIn(1, HomeLayout.COLUMNS)
        val rowSpan = widget.rowSpan.coerceIn(1, 2)
        val col = widget.col.coerceIn(0, HomeLayout.COLUMNS - colSpan)
        val row = widget.row.coerceIn(0, rows - rowSpan)
        val cells = widget.copy(row = row, col = col, colSpan = colSpan, rowSpan = rowSpan).cells()
        if (cells.any { it in occupied }) return@forEach
        occupied += cells
        widgets += widget.copy(row = row, col = col, colSpan = colSpan, rowSpan = rowSpan)
    }
    return HomeLayout(rows, widgets)
}

private fun HomeWidgetPlacement.cells(): List<Pair<Int, Int>> {
    val cells = mutableListOf<Pair<Int, Int>>()
    for (r in row until row + rowSpan) {
        for (c in col until col + colSpan) cells += r to c
    }
    return cells
}

private fun overlaps(
    widgets: List<HomeWidgetPlacement>,
    excludeId: String?,
    row: Int,
    col: Int,
    colSpan: Int,
    rowSpan: Int,
): Boolean {
    val target = rectangleCells(row, col, colSpan, rowSpan)
    return widgets.any { widget ->
        widget.id != excludeId && widget.cells().any { it in target }
    }
}

private fun rectangleCells(
    row: Int,
    col: Int,
    colSpan: Int,
    rowSpan: Int,
): Set<Pair<Int, Int>> {
    val cells = mutableSetOf<Pair<Int, Int>>()
    for (r in row until row + rowSpan) {
        for (c in col until col + colSpan) cells += r to c
    }
    return cells
}

/** Empty target is fine; a single overlapping widget can swap if both fit. */
private fun canDrop(
    widgets: List<HomeWidgetPlacement>,
    id: String,
    row: Int,
    col: Int,
): Boolean = movedWidgets(widgets, id, row, col) != null

private fun movedWidgets(
    widgets: List<HomeWidgetPlacement>,
    id: String,
    row: Int,
    col: Int,
): List<HomeWidgetPlacement>? {
    val widget = widgets.firstOrNull { it.id == id } ?: return null
    val target = rectangleCells(row, col, widget.colSpan, widget.rowSpan)
    val occupied = widgets.filter { other ->
        other.id != id && other.cells().any { it in target }
    }
    if (occupied.size > 1) return null
    val next = if (occupied.isEmpty()) {
        widgets.map { item ->
            if (item.id == id) item.copy(row = row, col = col) else item
        }
    } else {
        val other = occupied.single()
        widgets.map { item ->
            when (item.id) {
                id -> item.copy(row = row, col = col)
                other.id -> item.copy(row = widget.row, col = widget.col)
                else -> item
            }
        }
    }
    val valid = next.all { a ->
        next.none { b ->
            b.id != a.id && a.cells().any { it in b.cells() }
        }
    }
    return next.takeIf { valid }
}

private fun placeWithSize(
    layout: HomeLayout,
    id: String,
    size: WidgetSize,
): HomeLayout? {
    val widget = layout.widgets.firstOrNull { it.id == id } ?: return null
    val others = layout.widgets.filterNot { it.id == id }
    val candidates = mutableListOf<Pair<Int, Int>>()
    candidates += widget.row to widget.col
    for (row in 0..(layout.rows - size.rows)) {
        for (col in 0..(HomeLayout.COLUMNS - size.columns)) {
            candidates += row to col
        }
    }
    candidates.firstOrNull { (row, col) ->
        !overlaps(others, null, row, col, size.columns, size.rows)
    }?.let { (row, col) ->
        return layout.copy(
            widgets = layout.widgets.map {
                if (it.id == id) it.copy(row = row, col = col, colSpan = size.columns, rowSpan = size.rows)
                else it
            },
        )
    }
    for (rows in (layout.rows + 1)..HomeLayout.MAX_ROWS) {
        for (row in 0..(rows - size.rows)) {
            for (col in 0..(HomeLayout.COLUMNS - size.columns)) {
                if (!overlaps(others, null, row, col, size.columns, size.rows)) {
                    return layout.copy(
                        rows = rows,
                        widgets = layout.widgets.map {
                            if (it.id == id) it.copy(row = row, col = col, colSpan = size.columns, rowSpan = size.rows)
                            else it
                        },
                    )
                }
            }
        }
    }
    return null
}

private fun folderEntries(
    folder: HomeWidgetPlacement,
    shortcuts: List<HomeShortcut>,
): List<HomeShortcut> {
    val byId = shortcuts.associateBy { it.id }
    return folder.shortcuts.mapNotNull { byId[it] }
}

private val SINGLE_TYPES_ORDER = setOf("weather", "today", "date", "hotwater", "scores")
