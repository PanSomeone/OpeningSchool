package com.openschedule.plugin.scores

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openschedule.core.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

internal class ScoreStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("openschedule_scores", Context.MODE_PRIVATE)

    fun load(): List<GpaCourse> {
        val raw = prefs.getString(KEY_COURSES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<GpaCourse>>(raw)
        }.getOrDefault(emptyList())
    }

    fun save(courses: List<GpaCourse>) {
        prefs.edit().putString(KEY_COURSES, json.encodeToString(courses)).apply()
    }

    private companion object {
        const val KEY_COURSES = "gpa_courses"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

internal class ScoresViewModel(
    private val store: ScoreStore,
) : ViewModel() {

    private val _courses = MutableStateFlow(store.load())
    val courses: StateFlow<List<GpaCourse>> = _courses.asStateFlow()

    private val _gradeImporting = MutableStateFlow(false)
    val gradeImporting: StateFlow<Boolean> = _gradeImporting.asStateFlow()

    private val _pendingGrades = MutableStateFlow<ParsedGrades?>(null)
    val pendingGrades: StateFlow<ParsedGrades?> = _pendingGrades.asStateFlow()

    private val _gradeError = MutableStateFlow<String?>(null)
    val gradeError: StateFlow<String?> = _gradeError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun upsert(course: GpaCourse) {
        val next = _courses.value.let { current ->
            if (current.any { it.id == course.id }) {
                current.map { if (it.id == course.id) course else it }
            } else {
                current + course
            }
        }
        store.save(next)
        _courses.value = next
    }

    fun delete(id: String) {
        val next = _courses.value.filterNot { it.id == id }
        store.save(next)
        _courses.value = next
    }

    fun setCountsInGpa(id: String, include: Boolean) {
        val next = _courses.value.map {
            if (it.id == id) it.copy(countsInGpa = include) else it
        }
        store.save(next)
        _courses.value = next
    }

    fun clear() {
        store.save(emptyList())
        _courses.value = emptyList()
    }

    fun importGrades(pages: List<String>) {
        if (_gradeImporting.value) return
        _gradeImporting.value = true
        _gradeError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { GradeParser.parsePages(pages) }
            result.fold(
                onSuccess = {
                    AppLog.i("Grades", "识别 ${it.courses.size} 门成绩, 跳过 ${it.skippedLevel} 门等级制")
                    _pendingGrades.value = it
                },
                onFailure = {
                    AppLog.w("Grades", "成绩解析失败: ${it.message}")
                    _gradeError.value = it.message ?: "成绩解析失败"
                },
            )
            _gradeImporting.value = false
        }
    }

    fun commitGrades(replace: Boolean) {
        val pending = _pendingGrades.value ?: return
        val imported = pending.courses.map { it.copy(id = UUID.randomUUID().toString()) }
        val existing = _courses.value
        val next = if (replace) {
            imported
        } else {
            val existingKeys = existing.map { it.identity() }.toSet()
            existing + imported.filterNot { it.identity() in existingKeys }
        }
        store.save(next)
        _courses.value = next
        val added = next.size - existing.size
        AppLog.i("Grades", "${if (replace) "替换" else "追加"}成绩 · 新增 $added 门")
        _pendingGrades.value = null
        _message.value = buildString {
            append("已导入 $added 门课")
            if (pending.skippedLevel > 0) {
                append("，跳过 ${pending.skippedLevel} 门等级制成绩")
            }
        }
    }

    fun dismissPendingGrades() {
        _pendingGrades.value = null
    }

    fun consumeGradeError() {
        _gradeError.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun GpaCourse.identity(): String =
        "${name.trim()}|$credit|$score|${term.trim()}"
}

@Composable
internal fun rememberScoresViewModel(): ScoresViewModel {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val factory = remember(context) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScoresViewModel(ScoreStore(context)) as T
            }
        }
    }
    return viewModel(key = "scores", factory = factory)
}
