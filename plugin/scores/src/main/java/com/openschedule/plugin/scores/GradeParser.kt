package com.openschedule.plugin.scores

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedGrades(
    val courses: List<GpaCourse>,
    val skippedLevel: Int,
    val skippedInvalid: Int = 0,
)

/**
 * Reads a grade page the user is already viewing: either a rendered Zhengfang
 * table or a JSON payload. It never calls the school itself.
 */
object GradeParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parsePages(pages: List<String>): Result<ParsedGrades> {
        val parsed = pages.mapNotNull { page ->
            runCatching { parsePage(page) }.getOrNull()
        }
        val best = parsed.maxByOrNull { it.courses.size }
            ?: return Result.failure(
                IllegalStateException("页面里没有找到成绩表，请先打开成绩查询页再点导入"),
            )
        if (best.courses.isEmpty()) {
            val hint = if (best.skippedLevel > 0) {
                "只找到等级制成绩，暂不支持优秀/良好等换算"
            } else {
                "成绩表里没有可用的百分制课程"
            }
            return Result.failure(IllegalStateException(hint))
        }
        return Result.success(best)
    }

    private fun parsePage(html: String): ParsedGrades? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        parseJsonPage(doc)?.let { return it }

        val tables = doc.select("table")
        val candidates = tables.mapNotNull { table ->
            runCatching { parseTable(table) }.getOrNull()
        }
        return candidates.maxByOrNull { it.courses.size }
    }

    private fun parseJsonPage(doc: Document): ParsedGrades? {
        val text = doc.body().text().trim()
        if (!text.startsWith("{") && !text.startsWith("[")) return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val direct = root["items"] as? JsonArray
                    ?: root["list"] as? JsonArray
                    ?: root["rows"] as? JsonArray
                val data = root["data"]
                direct ?: when (data) {
                    is JsonArray -> data
                    is JsonObject -> data["items"] as? JsonArray ?: data["list"] as? JsonArray
                    else -> null
                }
            }

            else -> null
        } ?: return null

        var skippedLevel = 0
        var skippedInvalid = 0
        val courses = items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = item.text("kcmc", "courseName", "kcName", "name")?.let(::clean) ?: return@mapNotNull null
            val credit = item.text("xf", "credit", "xuefen")?.let(::number)
            val scoreRaw = item.text("cj", "score", "totalScore", "zpcj", "finalScore")
            if (credit == null || credit <= 0 || scoreRaw.isNullOrBlank()) {
                skippedInvalid += 1
                return@mapNotNull null
            }
            val score = number(scoreRaw)
            if (score == null) {
                if (isLevel(scoreRaw)) skippedLevel += 1 else skippedInvalid += 1
                return@mapNotNull null
            }
            val year = item.text("xnmc", "xnm").orEmpty()
            val term = item.text("xqmmc", "xqm").orEmpty()
            GpaCourse(
                id = "",
                name = name,
                credit = credit,
                score = score,
                term = listOf(year, term).filter { it.isNotBlank() }.joinToString(" "),
            )
        }.distinctBy { it.key() }

        if (courses.isEmpty() && skippedLevel == 0) return null
        return ParsedGrades(courses, skippedLevel, skippedInvalid)
    }

    private fun parseTable(table: Element): ParsedGrades? {
        val rows = directRows(table)
        if (rows.size < 2) return null
        val headersLimit = minOf(6, rows.size - 1)
        for (headerIndex in 0..headersLimit) {
            val headerCells = directCells(rows[headerIndex]).map { clean(it.text()) }
            val mapping = HeaderMapping.from(headerCells) ?: continue

            var skippedLevel = 0
            var skippedInvalid = 0
            val courses = mutableListOf<GpaCourse>()
            for (rowIndex in headerIndex + 1 until rows.size) {
                val cells = directCells(rows[rowIndex])
                if (cells.size <= mapping.requiredMaxIndex) continue
                val name = clean(cells[mapping.name].text())
                if (name.isBlank() || name in IGNORED_NAMES) continue
                if (name.contains("合计") || name.contains("平均")) continue

                val credit = number(cells[mapping.credit].text())
                val scoreText = clean(cells[mapping.score].text())
                val score = number(scoreText)
                if (credit == null || credit <= 0 || score == null) {
                    if (score == null && isLevel(scoreText)) skippedLevel += 1 else skippedInvalid += 1
                    continue
                }
                val term = mapping.term
                    .mapNotNull { index ->
                        if (index < cells.size) clean(cells[index].text()) else null
                    }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                courses += GpaCourse(
                    id = "",
                    name = name,
                    credit = credit,
                    score = score,
                    term = term,
                )
            }

            if (courses.isNotEmpty() || skippedLevel > 0) {
                return ParsedGrades(
                    courses = courses.distinctBy { it.key() },
                    skippedLevel = skippedLevel,
                    skippedInvalid = skippedInvalid,
                )
            }
        }
        return null
    }

    private data class HeaderMapping(
        val name: Int,
        val credit: Int,
        val score: Int,
        val term: List<Int>,
    ) {
        val requiredMaxIndex: Int get() = maxOf(name, credit, score)

        companion object {
            fun from(headers: List<String>): HeaderMapping? {
                val normalized = headers.map { it.replace(" ", "") }
                val name = normalized.indexOfFirst {
                    it.contains("课程名称") || it == "课程名" ||
                        it == "课程" || it.contains("科目名称")
                }.takeIf { it >= 0 } ?: return null

                val credit = normalized.indexOfFirst {
                    it == "学分" ||
                        (it.contains("学分") && !it.contains("绩点") &&
                            !it.contains("总学分") && !it.contains("已修") && !it.contains("未修"))
                }.takeIf { it >= 0 } ?: return null

                val exactScore = normalized.indexOfFirst {
                    it in setOf("成绩", "总成绩", "总评成绩", "最终成绩", "分数", "成绩(分)")
                }
                val score = if (exactScore >= 0) {
                    exactScore
                } else {
                    normalized.indexOfFirst {
                        it.contains("成绩") && !it.contains("绩点") &&
                            !it.contains("排名") && !it.contains("性质") &&
                            !it.contains("类别") && !it.contains("平时") &&
                            !it.contains("期末")
                    }
                }.takeIf { it >= 0 } ?: return null

                val term = normalized.mapIndexedNotNull { index, header ->
                    index.takeIf { header.contains("学期") || header.contains("学年") }
                }

                return HeaderMapping(name, credit, score, term)
            }
        }
    }

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

    private fun directCells(row: Element): List<Element> =
        row.children().filter { it.tagName() == "td" || it.tagName() == "th" }

    private fun JsonObject.text(vararg keys: String): String? {
        keys.forEach { key ->
            val value = (this[key] as? JsonPrimitive)?.content
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun number(raw: String): Double? {
        val text = raw.replace(',', '.').trim()
        val match = Regex("-?\\d+(?:\\.\\d+)?").find(text) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null
        return value.takeIf { it in 0.0..100.0 }
    }

    private fun isLevel(raw: String): Boolean {
        val text = raw.trim()
        return LEVEL_WORDS.any { text.contains(it) }
    }

    private fun clean(raw: String): String =
        raw.replace('\u00a0', ' ')
            .replace(Regex("[\\u200b\\u200e\\u200f]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun GpaCourse.key(): String =
        "${name.trim()}|$credit|$score|${term.trim()}"

    private val LEVEL_WORDS = listOf("优秀", "良好", "中等", "及格", "不及格", "合格", "不合格", "通过", "免修", "缓考", "缺考")

    private val IGNORED_NAMES = setOf("课程", "课程名称", "课程名", "科目", "成绩", "备注")
}
