package com.openschedule.plugin.scores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.WebCaptureDialog
import java.util.UUID

@Composable
fun ScoresScreen() {
    val viewModel = rememberScoresViewModel()
    var tab by rememberSaveable { mutableStateOf(0) }
    var peGender by rememberSaveable { mutableStateOf(PeGender.MALE.name) }
    var peValues by rememberSaveable { mutableStateOf(List(9) { "" }) }
    var graduationValues by rememberSaveable { mutableStateOf(List(4) { "" }) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "成绩")

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("GPA") },
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("体测") },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = LocalFloatingNavSpace.current + 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tab == 0) {
                    GpaTab(viewModel)
                } else {
                    PeTab(
                        genderName = peGender,
                        values = peValues,
                        graduationValues = graduationValues,
                        onGenderChange = { peGender = it },
                        onValuesChange = { peValues = it },
                        onGraduationChange = { graduationValues = it },
                    )
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
}

@Composable
private fun GpaTab(viewModel: ScoresViewModel) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val included = remember(courses) { courses.filter { it.countsInGpa } }
    val gradeImporting by viewModel.gradeImporting.collectAsStateWithLifecycle()
    val pendingGrades by viewModel.pendingGrades.collectAsStateWithLifecycle()
    val gradeError by viewModel.gradeError.collectAsStateWithLifecycle()
    val includedCredits = remember(included) { included.sumOf { it.credit } }
    var editing by remember { mutableStateOf<GpaCourse?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var webUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingGrades) {
        if (pendingGrades != null) webUrl = null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "课程",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("共 ${courses.size} 门")
                    append(" · 计入 ${included.size} 门")
                    append(" · ${formatNumber(includedCredits)} 学分")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { showImportSheet = true },
            enabled = !gradeImporting,
        ) {
            Text("导入")
        }
        FilledTonalButton(
            onClick = { adding = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加")
        }
    }

    if (courses.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "还没有成绩",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "从教务导入，或手动添加课程成绩",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    } else {
        val grouped = remember(courses) {
            courses.groupBy { it.term.ifBlank { "未分学期" } }
                .toSortedMap(compareBy { if (it == "未分学期") "\uFFFF" else it })
        }
        grouped.forEach { (term, termCourses) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = term,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${termCourses.count { it.countsInGpa }}/${termCourses.size} 门",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    termCourses.forEachIndexed { index, course ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = course }
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = course.countsInGpa,
                                onCheckedChange = {
                                    viewModel.setCountsInGpa(course.id, it)
                                },
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = course.name.ifBlank { "未命名" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (course.countsInGpa) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOf(
                                        "${formatNumber(course.credit)} 学分",
                                        "${formatNumber(course.score)} 分",
                                    ).joinToString(" · ") +
                                        if (course.countsInGpa) "" else " · 不计入",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = String.format(
                                    "%.1f",
                                    GpaCalculator.gradePoint(course.score),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (course.countsInGpa) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                            IconButton(onClick = { viewModel.delete(course.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (index != termCourses.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        Text(
            text = "勾选会计入 GPA；不需要的课程取消勾选即可",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
        Button(
            onClick = { showResult = true },
            enabled = included.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(50.dp),
        ) {
            Text("计算 GPA", fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "按学分加权，换算表见插件设置",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp),
        )
    }

    if (adding || editing != null) {
        GpaCourseDialog(
            initial = editing,
            onSave = { course ->
                viewModel.upsert(course)
                adding = false
                editing = null
            },
            onDelete = { course ->
                viewModel.delete(course.id)
                editing = null
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }

    if (showImportSheet) {
        GradeImportSheet(
            onOpenWeb = { url ->
                showImportSheet = false
                webUrl = url
            },
            onDismiss = { showImportSheet = false },
        )
    }

    webUrl?.let { url ->
        WebCaptureDialog(
            title = "成绩导入",
            startUrl = url,
            hint = "登录并打开成绩查询页，再点导入",
            actionLabel = "导入",
            busy = gradeImporting,
            error = gradeError,
            onErrorConsumed = viewModel::consumeGradeError,
            onCapture = viewModel::importGrades,
            onClose = {
                webUrl = null
                viewModel.consumeGradeError()
            },
        )
    }

    pendingGrades?.let { pending ->
        GradeImportPreviewSheet(
            pending = pending,
            hasExisting = courses.isNotEmpty(),
            onAppend = { viewModel.commitGrades(replace = false) },
            onReplace = { viewModel.commitGrades(replace = true) },
            onDismiss = viewModel::dismissPendingGrades,
        )
    }

    if (showResult) {
        GpaResultSheet(
            courses = courses,
            onDismiss = { showResult = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpaResultSheet(
    courses: List<GpaCourse>,
    onDismiss: () -> Unit,
) {
    val overall = remember(courses) { GpaCalculator.calculate(courses) }
    val terms = remember(courses) { GpaCalculator.termResults(courses) }
    val excluded = courses.count { !it.countsInGpa }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "统计结果",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = "总体 GPA",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.3f", overall.gpa),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = " / 4.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        ResultStat("加权平均", String.format("%.1f", overall.weightedScore))
                        ResultStat("学分", formatNumber(overall.credits))
                        ResultStat("课程", "${overall.courseCount} 门")
                    }
                }
            }

            Text(
                text = "按学期",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (terms.isEmpty()) {
                        Text(
                            text = "没有计入 GPA 的课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                    terms.forEachIndexed { index, term ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = term.term,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format("%.3f", term.result.gpa),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${formatNumber(term.result.credits)} 学分",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != terms.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            if (excluded > 0) {
                Text(
                    text = "已排除 $excluded 门课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            Text(
                text = "GPA＝Σ(学分×绩点)÷Σ学分；换算表见插件设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun GpaCourseDialog(
    initial: GpaCourse?,
    onSave: (GpaCourse) -> Unit,
    onDelete: (GpaCourse) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var credit by remember { mutableStateOf(initial?.let { formatNumber(it.credit) }.orEmpty()) }
    var score by remember { mutableStateOf(initial?.let { formatNumber(it.score) }.orEmpty()) }
    var term by remember { mutableStateOf(initial?.term.orEmpty()) }
    var countsInGpa by remember { mutableStateOf(initial?.countsInGpa ?: true) }
    val creditValue = credit.replace(',', '.').toDoubleOrNull()
    val scoreValue = score.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() &&
        creditValue != null && creditValue > 0 &&
        scoreValue != null && scoreValue in 0.0..100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = credit,
                        onValueChange = { credit = filterNumber(it) },
                        label = { Text("学分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = filterNumber(it) },
                        label = { Text("成绩") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it.take(30) },
                    label = { Text("学期") },
                    placeholder = { Text("2025-2026 1（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("计入 GPA", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "不参与平均绩点计算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = countsInGpa,
                        onCheckedChange = { countsInGpa = it },
                    )
                }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        GpaCourse(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            credit = creditValue ?: 0.0,
                            score = scoreValue ?: 0.0,
                            term = term.trim(),
                            countsInGpa = countsInGpa,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun PeTab(
    genderName: String,
    values: List<String>,
    graduationValues: List<String>,
    onGenderChange: (String) -> Unit,
    onValuesChange: (List<String>) -> Unit,
    onGraduationChange: (List<String>) -> Unit,
) {
    val gender = PeGender.valueOf(genderName)

    fun update(index: Int, value: String) {
        onValuesChange(values.toMutableList().also { it[index] = filterNumber(value) })
    }

    fun score(index: Int): Double =
        values[index].toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0

    val itemScores = PeItemScores(
        bmi = score(0),
        vitalCapacity = score(1),
        sprint = score(2),
        sitAndReach = score(3),
        longJump = score(4),
        strength = score(5),
        endurance = score(6),
    )
    val bonus = (score(7) + score(8)).coerceAtMost(20.0)
    val standard = PeCalculator.standardScore(itemScores)
    val total = PeCalculator.totalScore(itemScores, bonus)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        FilterChip(
            selected = gender == PeGender.MALE,
            onClick = { onGenderChange(PeGender.MALE.name) },
            label = { Text("男") },
        )
        FilterChip(
            selected = gender == PeGender.FEMALE,
            onClick = { onGenderChange(PeGender.FEMALE.name) },
            label = { Text("女") },
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "填单项得分",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }

    SectionLabel("体测项目")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            PeInput("BMI", 15, values[0]) { update(0, it) }
            PeInput("肺活量", 15, values[1]) { update(1, it) }
            PeInput("50 米", 20, values[2]) { update(2, it) }
            PeInput("坐位体前屈", 10, values[3]) { update(3, it) }
            PeInput("立定跳远", 10, values[4]) { update(4, it) }
            PeInput(
                if (gender == PeGender.MALE) "引体向上" else "一分钟仰卧起坐",
                10,
                values[5],
            ) { update(5, it) }
            PeInput(
                if (gender == PeGender.MALE) "1000 米" else "800 米",
                20,
                values[6],
            ) { update(6, it) }
        }
    }

    SectionLabel("加分")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            PeInput("长跑加分", null, values[7], hint = "0–10") { update(7, it) }
            PeInput("力量加分", null, values[8], hint = "0–10") { update(8, it) }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "体测总分",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = String.format("%.1f", total),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PeCalculator.grade(total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "标准分 ${String.format("%.1f", standard)} · 加分 ${String.format("%.1f", bonus)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }

    SectionLabel("毕业总评")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf("大一", "大二", "大三", "大四").forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = graduationValues[index],
                        onValueChange = { value ->
                            onGraduationChange(
                                graduationValues.toMutableList().also {
                                    it[index] = filterNumber(value)
                                },
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text("总分") },
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
            val yearTotals = graduationValues.mapNotNull {
                it.toDoubleOrNull()?.coerceIn(0.0, 120.0)
            }
            val graduation = if (yearTotals.size == 4) {
                PeCalculator.graduationScore(yearTotals)
            } else {
                null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "毕业总评",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = graduation?.let { String.format("%.1f", it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (graduation != null) {
                    Text(
                        text = " · ${PeCalculator.grade(graduation)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Text(
        text = "标准分按 15/15/20/10/10/10/20% 加权；毕业总评＝大四 50% + 前三年平均 50%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun PeInput(
    label: String,
    weight: Int?,
    value: String,
    hint: String = "0–100",
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (weight != null) "$weight%" else hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp),
    )
}

private fun filterNumber(raw: String): String =
    raw.filter { it.isDigit() || it == '.' }
        .let { value ->
            val firstDot = value.indexOf('.')
            if (firstDot < 0) value else {
                value.substring(0, firstDot + 1) +
                    value.substring(firstDot + 1).replace(".", "")
            }
        }
        .take(6)

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
