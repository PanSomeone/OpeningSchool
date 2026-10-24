package com.openschedule.adapter.zhengfang

import com.openschedule.core.model.ScheduleText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class ParsedCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)

data class ParsedTimetable(
    val courses: List<ParsedCourse>,
    val maxPeriod: Int,
    val semesterStart: String?,
    val title: String?,
)

/**
 * Pure parsing for the two shapes a Zhengfang timetable endpoint returns:
 * the new JSON (`kbList`) and the old HTML table (`#kbtable`).
 *
 * Kept free of Android types so every rule here is unit-testable against a
 * captured payload.
 */
object ZhengfangParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseWeeks(raw: String): Set<Int> = ScheduleText.parseWeeks(raw)

    fun parsePeriodRange(raw: String): IntRange? = ScheduleText.parsePeriodRange(raw)

    fun parseDay(raw: String): Int? = ScheduleText.parseDay(raw)

    fun parseJson(body: String, totalWeeks: Int = 0): ParsedTimetable? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val list = (root["kbList"] as? JsonArray)
            ?: ((root["data"] as? JsonObject)?.get("kbList") as? JsonArray)
            ?: return null

        val courses = list.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = item.text("kcmc", "courseName")?.let(::cleanText) ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null

            val day = parseDay(item.text("xqj", "xqjmc", "weekDay") ?: "") ?: return@mapNotNull null
            val periodRange = parsePeriodRange(item.text("jcs", "jc", "jcor") ?: "") ?: return@mapNotNull null
            val weeks = parseWeeks(item.text("zcd", "zcmc", "weeks") ?: "")

            ParsedCourse(
                name = name,
                teacher = item.text("xm", "jsxm", "teacher").orEmpty().let(::cleanText),
                location = item.text("cdmc", "classroom", "jxo").orEmpty().let(::cleanText),
                day = day,
                startPeriod = periodRange.first,
                endPeriod = periodRange.last,
                weeks = weeks,
            )
        }

        val xsxx = root["xsxx"] as? JsonObject
        val semesterStart = xsxx?.let(::findStartDate)
        val title = xsxx?.let(::titleOf)
        val maxPeriod = courses.maxOfOrNull { it.endPeriod } ?: 0

        return ParsedTimetable(
            courses = courses,
            maxPeriod = maxPeriod,
            semesterStart = semesterStart,
            title = title,
        )
    }

    fun parseHtml(body: String): ParsedTimetable? {
        val doc = runCatching { Jsoup.parse(body) }.getOrNull() ?: return null
        val table = doc.selectFirst("#kbtable")
            ?: doc.selectFirst("table.kbtable")
            ?: doc.selectFirst("table")
            ?: return null

        val courses = mutableListOf<ParsedCourse>()
        var dataRow = 0
        var maxPeriod = 0

        table.select("tr").forEach { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@forEach
            if (cells.any { it.tagName() == "th" }) return@forEach
            val periodCell = cells.firstOrNull()?.text().orEmpty()
            if (periodCell.contains("节次") || periodCell.contains("星期")) return@forEach

            dataRow += 1
            val periodRange = parsePeriodRange(periodCell)
                ?: (dataRow..dataRow)
            val dayCells = cells.drop(1).take(7)

            dayCells.forEachIndexed { column, cell ->
                val content = cell.selectFirst(".kbcontent, .kbcontent1, .kb_content")
                    ?: cell.takeIf { it.text().isNotBlank() }
                    ?: return@forEachIndexed
                val day = column + 1
                val text = textWithLineBreaks(content)
                val blocks = text.split(Regex("[-—]{3,}")).map { it.trim() }.filter { it.isNotEmpty() }

                blocks.forEach { block ->
                    val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) return@forEach
                    val name = cleanText(lines[0])
                    if (name.isBlank()) return@forEach

                    val weekLine = lines.firstOrNull { it.contains("周") }
                    val weekIndex = weekLine?.let { lines.indexOf(it) } ?: -1
                    val weeks = weekLine?.let(::parseWeeks) ?: emptySet()
                    val teacher = lines.getOrNull(1)?.takeIf { weekIndex != 1 }.orEmpty()
                    val location = lines.drop(if (weekIndex >= 0) weekIndex + 1 else 2)
                        .firstOrNull { !it.contains("周") }
                        .orEmpty()

                    courses += ParsedCourse(
                        name = name,
                        teacher = cleanText(teacher),
                        location = cleanText(location),
                        day = day,
                        startPeriod = periodRange.first,
                        endPeriod = periodRange.last,
                        weeks = weeks,
                    )
                    maxPeriod = maxOf(maxPeriod, periodRange.last)
                }
            }
        }

        if (courses.isEmpty()) return null
        return ParsedTimetable(
            courses = courses,
            maxPeriod = maxPeriod,
            semesterStart = null,
            title = null,
        )
    }

    private fun JsonObject.text(vararg keys: String): String? {
        keys.forEach { key ->
            val primitive = this[key] as? JsonPrimitive ?: return@forEach
            val value = primitive.content
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun findStartDate(xsxx: JsonObject): String? {
        val preferred = listOf("XQKSRQ", "xqksrq", "XQKSRQ1", "kssj", "startDate")
        preferred.forEach { key ->
            val value = (xsxx[key] as? JsonPrimitive)?.content
            normalizeDate(value)?.let { return it }
        }
        xsxx.entries.firstOrNull { (key, _) -> key.contains("KSRQ", ignoreCase = true) }
            ?.value
            ?.let { (it as? JsonPrimitive)?.content }
            ?.let(::normalizeDate)
            ?.let { return it }
        return null
    }

    private fun titleOf(xsxx: JsonObject): String? {
        val year = (xsxx["XNMC"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val term = (xsxx["XQMMC"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return listOfNotNull(year, term?.let { "第 $it 学期" }).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace('/', '-').take(10)
        val parts = cleaned.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun cleanText(raw: String): String =
        runCatching { Jsoup.parse(raw).text() }.getOrDefault(raw).trim()

    private fun textWithLineBreaks(element: Element): String {
        val builder = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> when (node.tagName()) {
                    "br" -> builder.append('\n')
                    "hr" -> builder.append("\n---\n")
                    else -> node.childNodes().forEach(::walk)
                }
                else -> Unit
            }
        }
        element.childNodes().forEach(::walk)
        return builder.toString()
    }
}
