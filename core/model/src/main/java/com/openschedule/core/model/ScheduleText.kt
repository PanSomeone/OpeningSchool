package com.openschedule.core.model

/**
 * Text rules shared by every timetable source: the same week/period strings
 * appear in Zhengfang payloads and in scraped HTML, so both parsers use these.
 */
object ScheduleText {

    fun parseWeeks(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val normalized = raw
            .replace('（', '(')
            .replace('）', ')')
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace(';', ',')
            .replace(" ", "")

        val weeks = sortedSetOf<Int>()
        normalized.split(',').forEach { part ->
            if (part.isBlank()) return@forEach
            val parity = when {
                part.contains("单") -> 1
                part.contains("双") -> 0
                else -> null
            }
            val numbers = Regex("\\d+").findAll(part).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) return@forEach

            if (numbers.size >= 2 && part.contains('-')) {
                val start = numbers[0]
                val end = numbers[1]
                if (start in 1..60 && end in start..60) {
                    for (week in start..end) {
                        if (parity == null || week % 2 == parity) weeks += week
                    }
                }
            } else {
                numbers.filter { it in 1..60 }.forEach { weeks += it }
            }
        }
        return weeks
    }

    fun parsePeriodRange(raw: String): IntRange? {
        val numbers = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return null
        val start = numbers[0].coerceAtLeast(1)
        val end = if (numbers.size >= 2) numbers[1].coerceAtLeast(start) else start
        return start..end.coerceAtMost(20)
    }

    fun parseDay(raw: String): Int? {
        Regex("\\d+").find(raw)?.value?.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        val chinese = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7,
        )
        return chinese.entries.firstOrNull { raw.contains(it.key) }?.value
    }

    /** Only accepts explicit weekday labels, so a period number never becomes a day. */
    fun parseDayLabel(raw: String): Int? {
        val match = Regex("(?:星期|周)\\s*([一二三四五六日天])").find(raw) ?: return null
        return when (match.groupValues[1]) {
            "一" -> 1
            "二" -> 2
            "三" -> 3
            "四" -> 4
            "五" -> 5
            "六" -> 6
            else -> 7
        }
    }

    fun parsePeriodLabel(raw: String): IntRange? {
        val cleaned = raw.trim()
        if (cleaned.isBlank() || cleaned.contains(':')) return null
        val explicit = Regex("(\\d{1,2})\\s*(?:[-~至]\\s*(\\d{1,2}))?\\s*节?").find(cleaned) ?: return null
        val start = explicit.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 } ?: return null
        val end = explicit.groupValues[2].toIntOrNull()?.coerceIn(start, 30) ?: start
        return start..end
    }
}
