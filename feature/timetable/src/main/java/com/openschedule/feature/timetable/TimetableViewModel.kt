package com.openschedule.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.AppLog
import com.openschedule.core.data.ExternalAdapterCatalog
import com.openschedule.core.data.SettingsStore
import com.openschedule.core.data.TimetableStore
import com.openschedule.core.data.WebScheduleParser
import com.openschedule.core.model.AppSettings
import com.openschedule.core.model.Course
import com.openschedule.core.model.ImportResult
import com.openschedule.core.model.Period
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.TimetableImporter
import com.openschedule.core.model.WeekMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ImportPreview(
    val timetable: Timetable,
    val warnings: List<String>,
    val source: String,
)

class TimetableViewModel(
    private val timetableStore: TimetableStore,
    private val settingsStore: SettingsStore,
    private val builtInImporters: List<TimetableImporter>,
    private val externalAdapters: ExternalAdapterCatalog,
) : ViewModel() {

    val timetable: StateFlow<Timetable> = timetableStore.timetable
    val timetables = timetableStore.collection
    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _week = MutableStateFlow(currentWeek())
    val week: StateFlow<Int> = _week.asStateFlow()

    private val _importers = MutableStateFlow<List<TimetableImporter>>(builtInImporters)
    val importers: StateFlow<List<TimetableImporter>> = _importers.asStateFlow()

    private val _loadingImporters = MutableStateFlow(false)
    val loadingImporters: StateFlow<Boolean> = _loadingImporters.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _pendingImport = MutableStateFlow<ImportPreview?>(null)
    val pendingImport: StateFlow<ImportPreview?> = _pendingImport.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshImporters()
    }

    fun currentWeek(): Int {
        val value = timetable.value
        return WeekMath.currentWeek(
            today = LocalDate.now(),
            semesterStart = WeekMath.parseDate(value.semesterStart),
            totalWeeks = value.totalWeeks,
        )
    }

    fun goToCurrentWeek() {
        _week.value = currentWeek()
    }

    fun setWeek(week: Int) {
        _week.value = week.coerceIn(1, timetable.value.totalWeeks)
    }

    // ------------------------------------------------------------ timetable list

    fun createTimetable() {
        timetableStore.add("我的课表")
        _week.value = currentWeek()
        _message.value = "已新建课表"
    }

    fun switchTimetable(id: String) {
        if (id == timetables.value.activeId) return
        timetableStore.switchTo(id)
        _week.value = currentWeek()
    }

    fun renameTimetable(id: String, name: String) {
        timetableStore.rename(id, name)
        _message.value = "已重命名"
    }

    fun deleteTimetable(id: String) {
        val deleted = timetableStore.delete(id)
        if (!deleted) {
            _error.value = "至少保留一张课表"
            return
        }
        _week.value = currentWeek()
        _message.value = "已删除课表"
    }

    // ---------------------------------------------------------------- courses

    fun upsertCourse(course: Course) {
        timetableStore.update { current ->
            val exists = current.courses.any { it.id == course.id }
            current.copy(
                courses = if (exists) {
                    current.courses.map { if (it.id == course.id) course else it }
                } else {
                    current.courses + course
                },
            )
        }
    }

    fun deleteCourse(courseId: String) {
        timetableStore.update { current ->
            current.copy(courses = current.courses.filterNot { it.id == courseId })
        }
    }

    fun renameActive(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        timetableStore.rename(timetableStore.active.id, trimmed)
    }

    fun saveSemester(name: String, semesterStart: String, totalWeeks: Int) {
        val weeks = totalWeeks.coerceIn(1, 30)
        timetableStore.update {
            it.copy(
                name = name.trim().ifBlank { "我的课表" },
                semesterStart = semesterStart,
                totalWeeks = weeks,
            )
        }
        _week.value = _week.value.coerceIn(1, weeks)
    }

    fun saveSemesterStart(date: String) {
        timetableStore.update { it.copy(semesterStart = date) }
        _week.value = currentWeek()
    }

    fun setTotalWeeks(weeks: Int) {
        val value = weeks.coerceIn(1, 30)
        timetableStore.update { it.copy(totalWeeks = value) }
        _week.value = _week.value.coerceIn(1, value)
    }

    fun savePeriods(periods: List<Period>) {
        if (periods.isEmpty()) return
        val normalized = periods.mapIndexed { index, period ->
            period.copy(index = index + 1)
        }
        timetableStore.update { current ->
            current.copy(
                periods = normalized,
                courses = current.courses.map { course ->
                    course.copy(
                        slots = course.effectiveSlots.map { slot ->
                            val start = slot.startPeriod.coerceIn(1, normalized.size)
                            slot.copy(
                                startPeriod = start,
                                endPeriod = slot.endPeriod.coerceIn(start, normalized.size),
                            )
                        },
                        startPeriod = course.startPeriod.coerceIn(1, normalized.size),
                        endPeriod = course.endPeriod.coerceIn(
                            course.startPeriod.coerceIn(1, normalized.size),
                            normalized.size,
                        ),
                    )
                },
                noonBreakAfterPeriod = current.noonBreakAfterPeriod
                    .takeIf { it in 1 until normalized.size } ?: 0,
            )
        }
    }

    fun updatePeriod(
        index: Int,
        start: String,
        end: String,
        shiftFollowing: Boolean = true,
    ) {
        timetableStore.update { current ->
            val old = current.periods.firstOrNull { it.index == index } ?: return@update current
            val oldEnd = minutesOf(old.end)
            val newEnd = minutesOf(end)
            val delta = if (oldEnd != null && newEnd != null) newEnd - oldEnd else 0
            current.copy(
                periods = current.periods.map { period ->
                    when {
                        period.index == index -> period.copy(start = start, end = end)
                        shiftFollowing && period.index > index && delta != 0 -> {
                            val shiftedStart = shiftTime(period.start, delta)
                            val shiftedEnd = shiftTime(period.end, delta)
                            if (shiftedStart != null && shiftedEnd != null) {
                                period.copy(start = shiftedStart, end = shiftedEnd)
                            } else {
                                period
                            }
                        }

                        else -> period
                    }
                },
            )
        }
    }

    fun setNoonBreak(afterPeriod: Int) {
        timetableStore.update { current ->
            val max = (current.periods.size - 1).coerceAtLeast(0)
            current.copy(noonBreakAfterPeriod = afterPeriod.coerceIn(0, max))
        }
    }

    fun addPeriod() {
        timetableStore.update { current ->
            val last = current.periods.lastOrNull()
            val nextStart = last?.end?.let { plusMinutes(it, 10) } ?: "08:00"
            val nextEnd = plusMinutes(nextStart, 45)
            current.copy(
                periods = current.periods + Period(
                    index = (last?.index ?: 0) + 1,
                    start = nextStart,
                    end = nextEnd,
                ),
            )
        }
    }

    fun removePeriod(index: Int) {
        timetableStore.update { current ->
            if (current.periods.size <= 1) return@update current
            val kept = current.periods.filterNot { it.index == index }
                .mapIndexed { i, period -> period.copy(index = i + 1) }
            current.copy(
                periods = kept,
                courses = current.courses.map { course ->
                    course.copy(
                        slots = course.effectiveSlots.map { slot ->
                            val start = slot.startPeriod.coerceIn(1, kept.size)
                            slot.copy(
                                startPeriod = start,
                                endPeriod = slot.endPeriod.coerceIn(start, kept.size),
                            )
                        },
                        startPeriod = course.startPeriod.coerceIn(1, kept.size),
                        endPeriod = course.endPeriod.coerceIn(
                            course.startPeriod.coerceIn(1, kept.size),
                            kept.size,
                        ),
                    )
                },
                noonBreakAfterPeriod = current.noonBreakAfterPeriod
                    .takeIf { it in 1 until kept.size } ?: 0,
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    // ----------------------------------------------------------------- import

    fun refreshImporters() {
        if (_loadingImporters.value) return
        _loadingImporters.value = true
        viewModelScope.launch {
            val external = runCatching { externalAdapters.loadDescriptors() }.getOrDefault(emptyList())
            _importers.value = builtInImporters + external
            _loadingImporters.value = false
        }
    }

    fun importTimetable(importer: TimetableImporter, config: Map<String, String>) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            when (val result = importer.import(config)) {
                is ImportResult.Success -> {
                    AppLog.i("Import", "${importer.name} 识别 ${result.timetable.courses.size} 门课")
                    _pendingImport.value = ImportPreview(
                        timetable = result.timetable,
                        warnings = result.warnings.filter { it.isNotBlank() },
                        source = importer.name,
                    )
                }

                is ImportResult.Failure -> {
                    AppLog.w("Import", "${importer.name} 失败: ${result.message}")
                    _error.value = result.message
                }
            }
            _importing.value = false
        }
    }

    /** Runs the generic scraper off the main thread; the sheet then asks where to put it. */
    fun importHtml(pages: List<String>, source: String) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                WebScheduleParser.parsePages(pages, fallbackName = source)
            }
            result.fold(
                onSuccess = { parsed ->
                    AppLog.i(
                        "Import",
                        "网页 $source: ${pages.size} 页, ${parsed.timetable.courses.size} 门课, ${parsed.warnings.size} 条提示",
                    )
                    _pendingImport.value = ImportPreview(
                        timetable = parsed.timetable,
                        warnings = parsed.warnings,
                        source = source,
                    )
                },
                onFailure = {
                    AppLog.w("Import", "网页 $source 解析失败: ${it.message}")
                    _error.value = it.message ?: "没有从页面里识别出课表"
                },
            )
            _importing.value = false
        }
    }

    fun commitPendingImport(createNew: Boolean) {
        val preview = _pendingImport.value ?: return
        if (createNew) {
            timetableStore.add(preview.timetable.name, preview.timetable.copy(id = ""))
        } else {
            timetableStore.save(preview.timetable.copy(id = timetableStore.active.id))
        }
        AppLog.i(
            "Import",
            (if (createNew) "新建课表" else "替换当前课表") + " · ${preview.timetable.courses.size} 门课",
        )
        _week.value = currentWeek()
        _message.value = "已导入 ${preview.timetable.courses.size} 门课"
        _pendingImport.value = null
    }

    fun dismissPendingImport() {
        _pendingImport.value = null
    }

    // ------------------------------------------------------------------- data

    fun exportJson(): String = timetableStore.exportJson()

    fun importJson(raw: String): String? {
        val result = timetableStore.replaceFromJson(raw)
        return result.fold(
            onSuccess = {
                AppLog.i("Backup", "JSON 恢复 ${it.courses.size} 门课")
                _week.value = currentWeek()
                _message.value = "已从 JSON 恢复 ${it.courses.size} 门课"
                null
            },
            onFailure = {
                AppLog.w("Backup", "JSON 恢复失败: ${it.message}")
                it.message ?: "JSON 无法解析"
            },
        )
    }

    fun clearAllCourses() {
        timetableStore.update { it.copy(courses = emptyList()) }
        _message.value = "课程已清空"
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeError() {
        _error.value = null
    }

    private fun plusMinutes(raw: String, minutes: Int): String {
        val total = minutesOf(raw) ?: return raw
        val next = (total + minutes).mod(24 * 60)
        return "%02d:%02d".format(next / 60, next % 60)
    }

    private fun shiftTime(raw: String, delta: Int): String? {
        val current = minutesOf(raw) ?: return null
        val next = current + delta
        if (next !in 0 until 24 * 60) return null
        return "%02d:%02d".format(next / 60, next % 60)
    }

    private fun minutesOf(raw: String): Int? {
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
