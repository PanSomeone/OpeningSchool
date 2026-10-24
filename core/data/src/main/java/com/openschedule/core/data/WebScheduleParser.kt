package com.openschedule.core.data

import com.openschedule.core.model.COURSE_COLOR_COUNT
import com.openschedule.core.model.Course
import com.openschedule.core.model.Period
import com.openschedule.core.model.ScheduleText
import com.openschedule.core.model.Timetable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class ParsedWebTimetable(
    val timetable: Timetable,
    val warnings: List<String>,
)

/**
 * Best-effort scraper for a timetable page the user is already looking at.
 *
 * Pages differ a lot between schools, so this tries three shapes in order:
 * embedded Zhengfang `kbList` JSON, a table with weekday columns, and the
 * transposed table some systems use. It never talks to the server itself.
 */
object WebScheduleParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parsePages(
        pages: List<String>,
        fallbackName: String,
        today: LocalDate = LocalDate.now(),
    ): Result<ParsedWebTimetable> {
        val best = pages.asSequence()
            .mapNotNull { page -> runCatching { parsePage(page, fallbackName, today) }.getOrNull() }
            .filter { it.courses.isNotEmpty() }
            .maxByOrNull { it.courses.size }
            ?: return Result.failure(IllegalStateException("页面里没有找到课程表，请先打开课表页面再导入"))

        val warnings = mutableListOf<String>()
        val maxWeek = best.courses.flatMap { it.weeks }.maxOrNull() ?: 0
        val totalWeeks = if (maxWeek <= 0) {
            Timetable.DEFAULT_TOTAL_WEEKS
        } else {
            maxWeek.coerceIn(14, 30)
        }

        val basePeriods = Timetable.defaultPeriods()
        val maxPeriod = best.courses.maxOfOrNull { it.endPeriod } ?: basePeriods.size
        val periods = extendedPeriods(maxPeriod)
        if (maxPeriod > basePeriods.size) {
            warnings += "课表有 $maxPeriod 节，已自动补到第 ${periods.size} 节，请核对时间"
        }

        val semesterStart = best.semesterStart.orEmpty()
        if (semesterStart.isBlank()) {
            warnings += "没有识别到开学日期，请在课表设置里补上"
        } else if (best.semesterInferred) {
            warnings += "开学日期由页面当前周次推算，请核对"
        }
        if (best.courses.any { it.weeks.isEmpty() }) {
            warnings += "部分课程没有识别到周次，已按全周显示"
        }

        val courses = best.courses.map { course ->
            Course(
                id = UUID.randomUUID().toString(),
                name = course.name,
                teacher = course.teacher,
                location = course.location,
                day = course.day.coerceIn(1, 7),
                startPeriod = course.startPeriod.coerceAtLeast(1),
                endPeriod = course.endPeriod.coerceAtLeast(course.startPeriod),
                weeks = course.weeks.filter { it in 1..totalWeeks }.toSet(),
                color = Math.floorMod(course.name.hashCode(), COURSE_COLOR_COUNT),
            )
        }

        return Result.success(
            ParsedWebTimetable(
                timetable = Timetable(
                    name = fallbackName,
                    semesterStart = semesterStart,
                    totalWeeks = totalWeeks,
                    periods = periods,
                    courses = courses,
                ),
                warnings = warnings,
            ),
        )
    }

    private data class Extracted(
        val courses: List<ScrapedCourse>,
        val semesterStart: String?,
        val semesterInferred: Boolean,
    )

    private data class ScrapedCourse(
        val name: String,
        val teacher: String,
        val location: String,
        val day: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: Set<Int>,
    )

    private data class CellRef(
        val cell: Element,
        val row: Int,
        val col: Int,
        val rowspan: Int,
        val colspan: Int,
    )

    private data class Block(
        val name: String,
        val teacher: String,
        val location: String,
        val weeks: Set<Int>,
        val period: IntRange?,
    )

    private fun parsePage(html: String, fallbackName: String, today: LocalDate): Extracted? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val start = inferSemesterStart(doc, today)

        parseJsonPage(doc)?.let { courses ->
            return Extracted(courses, start.date, start.inferred)
        }

        val tables = doc.select("table").take(MAX_TABLES)
        val best = tables.asSequence()
            .mapNotNull { table -> runCatching { extractTable(table) }.getOrNull() }
            .maxByOrNull { it.size }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return Extracted(best, start.date, start.inferred)
    }

    // ------------------------------------------------------------------- JSON

    private fun parseJsonPage(doc: Document): List<ScrapedCourse>? {
        val text = doc.body().text().trim()
        if (!text.startsWith("{") || !text.contains("kbList")) return null
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val list = (root["kbList"] as? JsonArray)
            ?: ((root["data"] as? JsonObject)?.get("kbList") as? JsonArray)
            ?: return null

        val courses = list.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = item.text("kcmc", "courseName")?.let(::cleanText) ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            val day = Regex("\\d+").find(item.text("xqj", "xqjmc", "weekDay").orEmpty())
                ?.value?.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
            val period = ScheduleText.parsePeriodRange(item.text("jcs", "jc", "jcor").orEmpty())
                ?: return@mapNotNull null
            ScrapedCourse(
                name = name,
                teacher = item.text("xm", "jsxm", "teacher").orEmpty().let(::cleanText),
                location = item.text("cdmc", "classroom", "jxo").orEmpty().let(::cleanText),
                day = day,
                startPeriod = period.first,
                endPeriod = period.last,
                weeks = ScheduleText.parseWeeks(item.text("zcd", "zcmc", "weeks").orEmpty()),
            )
        }
        return courses.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.text(vararg keys: String): String? {
        keys.forEach { key ->
            val value = (this[key] as? JsonPrimitive)?.content
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    // ------------------------------------------------------------------ tables

    private fun extractTable(table: Element): List<ScrapedCourse> {
        val rows = directRows(table)
        if (rows.size < 2) return emptyList()
        val grid = expand(rows)
        val maxColumns = grid.maxOfOrNull { it.size } ?: return emptyList()
        if (maxColumns < 3) return emptyList()

        return extractByColumns(grid, maxColumns)
            ?: extractByRows(grid, maxColumns)
            ?: emptyList()
    }

    /** Weekdays across the top, periods down the left. */
    private fun extractByColumns(grid: List<List<CellRef?>>, maxColumns: Int): List<ScrapedCourse>? {
        var headerRow = -1
        var bestDays = 0
        for (row in 0 until minOf(4, grid.size)) {
            val days = dayColumns(grid[row])
            if (days.size > bestDays) {
                bestDays = days.size
                headerRow = row
            }
        }
        if (headerRow < 0 || bestDays < 3) return null

        val dayColumns = dayColumns(grid[headerRow])
        val periodByRow = mutableMapOf<Int, IntRange>()
        var nextPeriod = 1
        for (row in headerRow + 1 until grid.size) {
            val cells = grid[row].filterNotNull().distinct()
            if (cells.all { it.cell.text().isBlank() }) continue
            val left = cells
                .filter { it.col !in dayColumns }
                .minByOrNull { it.col }
            val range = left?.let { ScheduleText.parsePeriodLabel(it.cell.text()) }
            val start = range?.first ?: nextPeriod
            val end = range?.last ?: start
            periodByRow[row] = start..end
            nextPeriod = maxOf(nextPeriod, end + 1)
        }

        val courses = mutableListOf<ScrapedCourse>()
        val seen = mutableSetOf<Pair<Int, Int>>()
        grid.forEachIndexed { row, cells ->
            if (row <= headerRow) return@forEachIndexed
            cells.filterNotNull().forEach { ref ->
                if (!seen.add(ref.row to ref.col)) return@forEach
                if (ref.row != row) return@forEach
                val day = (ref.col until ref.col + ref.colspan)
                    .firstNotNullOfOrNull { dayColumns[it] }
                    ?: return@forEach
                val rowPeriod = periodByRow[ref.row] ?: return@forEach
                val fallback = rowPeriod.first..maxOf(rowPeriod.last, rowPeriod.first + ref.rowspan - 1)
                courses += blocksOf(ref.cell).mapNotNull { block ->
                    toCourse(block, day, fallback)
                }
                seen += ref.row to ref.col
            }
        }
        return courses.takeIf { it.isNotEmpty() }
    }

    /** Weekdays down the left, periods across the top. */
    private fun extractByRows(grid: List<List<CellRef?>>, maxColumns: Int): List<ScrapedCourse>? {
        var firstColumn = -1
        var bestDays = 0
        for (column in 0 until minOf(3, maxColumns)) {
            val days = (grid.indices).mapNotNull { row ->
                grid[row].getOrNull(column)?.let { ref ->
                    ScheduleText.parseDayLabel(ref.cell.text())?.let { row to it }
                }
            }.toMap()
            if (days.size > bestDays) {
                bestDays = days.size
                firstColumn = column
            }
        }
        if (firstColumn < 0 || bestDays < 3) return null

        val dayByRow = (grid.indices).mapNotNull { row ->
            grid[row].getOrNull(firstColumn)?.let { ref ->
                ScheduleText.parseDayLabel(ref.cell.text())?.let { row to it }
            }
        }.toMap()
        val firstDataRow = dayByRow.keys.minOrNull() ?: return null
        val firstDataColumn = firstColumn + 1

        val courses = mutableListOf<ScrapedCourse>()
        val seen = mutableSetOf<Pair<Int, Int>>()
        grid.forEachIndexed { row, cells ->
            val day = dayByRow[row] ?: return@forEachIndexed
            if (row < firstDataRow) return@forEachIndexed
            cells.filterNotNull().forEach { ref ->
                if (ref.row != row || ref.col < firstDataColumn) return@forEach
                if (!seen.add(ref.row to ref.col)) return@forEach
                val start = (ref.col - firstColumn).coerceAtLeast(1)
                val fallback = start..maxOf(start, start + ref.colspan - 1)
                courses += blocksOf(ref.cell).mapNotNull { block ->
                    toCourse(block, day, fallback)
                }
            }
        }
        return courses.takeIf { it.isNotEmpty() }
    }

    private fun dayColumns(row: List<CellRef?>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        row.filterNotNull().distinct().forEach { ref ->
            val day = ScheduleText.parseDayLabel(ref.cell.text()) ?: return@forEach
            for (col in ref.col until ref.col + ref.colspan) result.putIfAbsent(col, day)
        }
        return result
    }

    private fun toCourse(block: Block, day: Int, fallbackPeriod: IntRange): ScrapedCourse? {
        if (block.name.isBlank()) return null
        val startPeriod = block.period?.first ?: fallbackPeriod.first
        val endPeriod = block.period?.last ?: fallbackPeriod.last
        if (startPeriod > 30) return null
        return ScrapedCourse(
            name = block.name,
            teacher = block.teacher,
            location = block.location,
            day = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weeks = block.weeks,
        )
    }

    // ------------------------------------------------------------------- cells

    private fun blocksOf(cell: Element): List<Block> {
        val content = cell.select(".kbcontent1").ifEmpty {
            cell.select(".kbcontent, .kb_content")
        }
        val blocks = if (content.isNotEmpty()) {
            content.map { textWithMarkers(it) }
        } else {
            textWithMarkers(cell).split('\u0000')
        }
        return blocks.mapNotNull(::parseBlock)
    }

    private fun parseBlock(raw: String): Block? {
        val lines = raw.lines()
            .map(::cleanText)
            .filter { it.isNotBlank() }
            .filterNot { it == "&nbsp;" }
        if (lines.isEmpty()) return null

        val name = cleanCourseName(lines.first())
        if (name.isBlank() || name in IGNORED_NAMES) return null
        if (ScheduleText.parseDayLabel(name) != null && name.length <= 4) return null

        val remaining = lines.drop(1)
        val weekLines = (listOf(name) + remaining).filter { line ->
            line.contains("周") && line.any(Char::isDigit)
        }
        val weeks = weekLines.flatMap { ScheduleText.parseWeeks(it) }.toSet()
        val periodLine = remaining.firstOrNull { it.contains("节") && it.any(Char::isDigit) }
        val period = periodLine?.let { ScheduleText.parsePeriodRange(it) }
        val info = remaining.filterNot { it == periodLine || it in weekLines }
        var teacher = info.getOrNull(0).orEmpty()
        var location = info.getOrNull(1).orEmpty()
        if (info.size == 1 && looksLikeLocation(teacher)) {
            location = teacher
            teacher = ""
        }

        return Block(
            name = name,
            teacher = teacher,
            location = location,
            weeks = weeks,
            period = period,
        )
    }

    private fun cleanCourseName(raw: String): String =
        raw
            .replace(Regex("[（(]?\\d+\\s*[-~至]\\s*\\d+\\s*周[^）)]*[）)]?"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('★', '*', '●')

    private fun looksLikeLocation(raw: String): Boolean =
        LOCATION_HINTS.any { raw.contains(it) }

    private fun textWithMarkers(element: Element): String {
        val builder = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> when (node.tagName()) {
                    "br" -> builder.append('\n')
                    "hr" -> builder.append('\u0000')
                    "div", "p", "li" -> {
                        builder.append('\n')
                        node.childNodes().forEach(::walk)
                        builder.append('\n')
                    }

                    else -> node.childNodes().forEach(::walk)
                }

                else -> Unit
            }
        }
        element.childNodes().forEach(::walk)
        return builder.toString()
    }

    private fun cleanText(raw: String): String =
        raw.replace('\u00a0', ' ')
            .replace(Regex("[\\u200b\\u200e\\u200f]"), "")
            .trim()

    // ---------------------------------------------------------------- geometry

    private fun directRows(table: Element): List<Element> {
        val rows = mutableListOf<Element>()
        table.children().forEach { section ->
            when (section.tagName()) {
                "tr" -> rows += section
                "tbody", "thead", "tfoot" ->
                    section.children().filter { it.tagName() == "tr" }.forEach { rows += it }
            }
        }
        return rows
    }

    private fun expand(rows: List<Element>): List<List<CellRef?>> {
        val grid = mutableListOf<MutableList<CellRef?>>()
        rows.forEachIndexed { rowIndex, row ->
            while (grid.size <= rowIndex) grid.add(mutableListOf())
            var column = 0
            row.children()
                .filter { it.tagName() == "td" || it.tagName() == "th" }
                .forEach { cell ->
                    while (grid[rowIndex].getOrNull(column) != null) column += 1
                    val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 40) ?: 1
                    val colspan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 12) ?: 1
                    val ref = CellRef(cell, rowIndex, column, rowspan, colspan)
                    for (r in rowIndex until minOf(rowIndex + rowspan, MAX_ROWS)) {
                        while (grid.size <= r) grid.add(mutableListOf())
                        for (c in column until column + colspan) {
                            while (grid[r].size <= c) grid[r].add(null)
                            grid[r][c] = ref
                        }
                    }
                    column += colspan
                }
        }
        return grid
    }

    // ---------------------------------------------------------------- semester

    private data class SemesterStart(val date: String?, val inferred: Boolean)

    private fun inferSemesterStart(doc: Document, today: LocalDate): SemesterStart {
        val rawDate = Regex(
            "XQKSRQ\"?\\s*[:=]\\s*\"?(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})",
            RegexOption.IGNORE_CASE,
        ).find(doc.html())?.groupValues?.get(1)
        normalizeDate(rawDate)?.let { return SemesterStart(it, false) }

        val textNodes = doc.select("body *")
            .filter { it.children().isEmpty() }
            .filter { it.parents().none { parent -> parent.tagName() == "table" } }
            .map { it.text() }
        val currentWeek = textNodes.firstNotNullOfOrNull { text ->
            Regex("(?:当前|本周)(?:教学)?周(?:次)?\\s*[:：]?\\s*第?\\s*(\\d{1,2})\\s*周?")
                .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..30 }
        }
        if (currentWeek != null) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return SemesterStart(monday.minusWeeks((currentWeek - 1).toLong()).toString(), true)
        }
        return SemesterStart(null, false)
    }

    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace('/', '-')
        return try {
            LocalDate.parse(cleaned).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun extendedPeriods(maxPeriod: Int): List<Period> {
        val periods = Timetable.defaultPeriods().toMutableList()
        while (periods.size < maxPeriod) {
            val previous = periods.last()
            val start = previous.end
            val end = addMinutes(start, 45)
            periods += Period(periods.size + 1, start, end)
        }
        return periods
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(':')
        if (parts.size != 2) return time
        val hour = parts[0].toIntOrNull() ?: return time
        val minute = parts[1].toIntOrNull() ?: return time
        val total = (hour * 60 + minute + minutes).mod(24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private val IGNORED_NAMES = setOf("上午", "下午", "晚上", "节次", "星期", "时间", "课程", "备注")

    private val LOCATION_HINTS = listOf(
        "教室", "教学楼", "楼", "馆", "场", "中心", "校区", "室", "号",
    )

    private const val MAX_TABLES = 80
    private const val MAX_ROWS = 80
}
