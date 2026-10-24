package com.openschedule.feature.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openschedule.core.model.AppSettings
import com.openschedule.core.model.Course
import com.openschedule.core.model.CourseSlot
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.WeekMath
import com.openschedule.core.model.normalizedColor
import com.openschedule.core.ui.LocalCourseColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditorSheet(
    timetable: Timetable,
    settings: AppSettings,
    initial: Course?,
    defaultDay: Int,
    defaultPeriod: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
) {
    val totalWeeks = timetable.totalWeeks
    val periodCount = timetable.periodCount

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(initial?.teacher.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var slots by remember {
        mutableStateOf(
            initial?.effectiveSlots
                ?.map { slot ->
                    val start = slot.startPeriod.coerceIn(1, periodCount)
                    slot.copy(
                        day = slot.day.coerceIn(1, 7),
                        startPeriod = start,
                        endPeriod = slot.endPeriod.coerceIn(start, periodCount),
                    )
                }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    CourseSlot(
                        day = defaultDay,
                        startPeriod = defaultPeriod.coerceIn(1, periodCount),
                        endPeriod = defaultPeriod.coerceIn(1, periodCount),
                    ),
                ),
        )
    }
    var color by remember { mutableStateOf(normalizedColor(initial?.color ?: 0)) }
    var selectedWeeks by remember {
        mutableStateOf(
            initial?.weeks
                ?.filter { it in 1..totalWeeks }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: (1..totalWeeks).toSet(),
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dayOrder = (1..7).map { WeekMath.isoDayOf(it, settings.weekStartDay) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (initial == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (initial != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除课程",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课程名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("老师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("上课时间", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val last = slots.last()
                        slots = slots + last
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加时间段")
                }
            }
            slots.forEachIndexed { index, slot ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "时间段 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (slots.size > 1) {
                                IconButton(
                                    onClick = {
                                        slots = slots.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除时间段",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            dayOrder.forEach { isoDay ->
                                FilterChip(
                                    selected = slot.day == isoDay,
                                    onClick = {
                                        slots = slots.toMutableList().also {
                                            it[index] = it[index].copy(day = isoDay)
                                        }
                                    },
                                    label = { Text(WeekMath.shortDayLabel(isoDay)) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PeriodStepper(
                                label = "开始",
                                value = slot.startPeriod,
                                min = 1,
                                max = slot.endPeriod,
                                modifier = Modifier.weight(1f),
                                onChange = { value ->
                                    slots = slots.toMutableList().also {
                                        it[index] = it[index].copy(startPeriod = value)
                                    }
                                },
                            )
                            PeriodStepper(
                                label = "结束",
                                value = slot.endPeriod,
                                min = slot.startPeriod,
                                max = periodCount,
                                modifier = Modifier.weight(1f),
                                onChange = { value ->
                                    slots = slots.toMutableList().also {
                                        it[index] = it[index].copy(endPeriod = value)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("周次", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { selectedWeeks = (1..totalWeeks).toSet() },
                ) { Text("全选") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                (1..totalWeeks).forEach { week ->
                    val selected = week in selectedWeeks
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .size(width = 34.dp, height = 30.dp)
                            .clickable {
                                val next = if (selected) selectedWeeks - week else selectedWeeks + week
                                if (next.isNotEmpty()) selectedWeeks = next
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = week.toString(),
                                fontSize = 11.sp,
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

            FieldLabel("颜色")
            val palette = LocalCourseColors.current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEachIndexed { index, courseColors ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(courseColors.container, CircleShape)
                            .border(
                                width = if (color == index) 2.dp else 1.dp,
                                color = if (color == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { color = index },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color == index) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = courseColors.onContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val normalizedSlots = slots
                        .map { slot ->
                            val start = slot.startPeriod.coerceIn(1, periodCount)
                            slot.copy(
                                day = slot.day.coerceIn(1, 7),
                                startPeriod = start,
                                endPeriod = slot.endPeriod.coerceIn(start, periodCount),
                            )
                        }
                        .sortedWith(compareBy({ it.day }, { it.startPeriod }))
                    val first = normalizedSlots.first()
                    val base = initial ?: Course(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        day = first.day,
                        startPeriod = first.startPeriod,
                        endPeriod = first.endPeriod,
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            teacher = teacher.trim(),
                            location = location.trim(),
                            note = note.trim(),
                            day = first.day,
                            startPeriod = first.startPeriod,
                            endPeriod = first.endPeriod,
                            slots = normalizedSlots,
                            weeks = if (selectedWeeks.size == totalWeeks) emptySet() else selectedWeeks,
                            color = color,
                        ),
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这门课？") },
            text = { Text(initial.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(initial)
                        onDismiss()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun PeriodStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "第 $value 节",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = value > min,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "减少", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = value < max,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "增加", modifier = Modifier.size(16.dp))
            }
        }
    }
}
