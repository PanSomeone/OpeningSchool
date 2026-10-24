package com.openschedule.adapter.zhengfang

import com.openschedule.core.model.AdapterConfigField
import com.openschedule.core.model.COURSE_COLOR_COUNT
import com.openschedule.core.model.Course
import com.openschedule.core.model.ImportResult
import com.openschedule.core.model.Period
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.TimetableImporter
import com.openschedule.core.model.WeekMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class ZhengfangImporter : TimetableImporter {

    override val id: String = "zhengfang"
    override val name: String = "正方教务"
    override val description: String = "新版正方教务系统，账号密码登录"

    override val configFields: List<AdapterConfigField> = listOf(
        AdapterConfigField(
            key = "baseUrl",
            label = "教务系统地址",
            placeholder = "https://jw.example.edu.cn",
        ),
        AdapterConfigField(key = "username", label = "学号"),
        AdapterConfigField(key = "password", label = "密码", secret = true),
        AdapterConfigField(
            key = "year",
            label = "学年",
            placeholder = "2026-2027",
        ),
        AdapterConfigField(
            key = "term",
            label = "学期",
            placeholder = "1 或 2",
            defaultValue = "1",
        ),
        AdapterConfigField(
            key = "startDate",
            label = "开学日期",
            placeholder = "2026-09-01（可选）",
            required = false,
        ),
    )

    override suspend fun import(config: Map<String, String>): ImportResult = withContext(Dispatchers.IO) {
        val baseUrl = config["baseUrl"].orEmpty().trim()
        val username = config["username"].orEmpty().trim()
        val password = config["password"].orEmpty()
        val year = config["year"].orEmpty().trim()
        val term = config["term"].orEmpty().trim().ifBlank { "1" }

        val missing = buildList {
            if (baseUrl.isBlank()) add("教务系统地址")
            if (username.isBlank()) add("学号")
            if (password.isBlank()) add("密码")
            if (year.isBlank()) add("学年")
        }
        if (missing.isNotEmpty()) {
            return@withContext ImportResult.Failure("请填写：${missing.joinToString("、")}")
        }

        try {
            val client = ZhengfangClient(baseUrl)
            val fetched = client.loginAndFetch(
                ZhengfangLogin(
                    username = username,
                    password = password,
                    year = year,
                    term = term,
                ),
            )
            buildTimetable(fetched, config["startDate"].orEmpty())
        } catch (e: ZhengfangException) {
            ImportResult.Failure(e.message ?: "导入失败")
        } catch (e: IOException) {
            ImportResult.Failure("连不上教务系统：${e.message ?: "网络错误"}")
        } catch (e: Exception) {
            ImportResult.Failure("导入失败：${e.message ?: e::class.simpleName}")
        }
    }

    private fun buildTimetable(fetched: ZhengfangFetch, startDateInput: String): ImportResult {
        val warnings = fetched.warnings.toMutableList()
        val parsed = fetched.timetable

        val maxWeek = parsed.courses.flatMap { it.weeks }.maxOrNull() ?: 0
        val totalWeeks = when {
            maxWeek <= 0 -> Timetable.DEFAULT_TOTAL_WEEKS
            else -> maxWeek.coerceIn(14, 30)
        }

        val periods = ensurePeriods(parsed.maxPeriod)
        if (parsed.maxPeriod > Timetable.defaultPeriods().size) {
            warnings += "课表超出自带的 ${Timetable.defaultPeriods().size} 节，已自动补到第 ${periods.size} 节，请核对时间"
        }

        val semesterStart = WeekMath.parseDate(startDateInput)?.toString()
            ?: parsed.semesterStart
            ?: ""
        if (semesterStart.isBlank()) {
            warnings += "没有识别到开学日期，请在「设置 → 学期」里填写，否则周次无法对应到日期"
        }

        val courses = parsed.courses.map { parsedCourse ->
            Course(
                id = UUID.randomUUID().toString(),
                name = parsedCourse.name,
                teacher = parsedCourse.teacher,
                location = parsedCourse.location,
                day = parsedCourse.day,
                startPeriod = parsedCourse.startPeriod,
                endPeriod = parsedCourse.endPeriod,
                weeks = parsedCourse.weeks.filter { it in 1..totalWeeks }.toSet(),
                color = Math.floorMod(parsedCourse.name.hashCode(), COURSE_COLOR_COUNT),
            )
        }

        if (courses.isEmpty()) {
            return ImportResult.Failure("教务系统里有课表，但没有解析出可显示的课程")
        }

        return ImportResult.Success(
            timetable = Timetable(
                name = parsed.title ?: "正方课表",
                semesterStart = semesterStart,
                totalWeeks = totalWeeks,
                periods = periods,
                courses = courses,
            ),
            warnings = warnings,
        )
    }

    private fun ensurePeriods(maxPeriod: Int): List<Period> {
        val base = Timetable.defaultPeriods()
        if (maxPeriod <= base.size) return base

        val extended = base.toMutableList()
        while (extended.size < maxPeriod) {
            val previous = extended.last()
            val start = previous.end
            val end = addMinutes(start, 45)
            extended += Period(extended.size + 1, start, end)
        }
        return extended
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(':')
        if (parts.size != 2) return ""
        val hour = parts[0].toIntOrNull() ?: return ""
        val minute = parts[1].toIntOrNull() ?: return ""
        val total = (hour * 60 + minute + minutes).mod(24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
