package com.openschedule.plugin.hzau.calendar

/**
 * 校历数据：华中农业大学 2026—2027 学年第一学期。
 *
 * 数据来源：学校企业微信推送的《校历》图文（2026-06-30，信息技术中心）。
 * 原图为长图，此处为人工核对后的结构化数据；学期第 1 周周一为 2026-08-31。
 */
object CalendarData {

    const val TITLE = "2026—2027 学年 第一学期"
    const val SUBTITLE = "华中农业大学 · 校历"
    const val SEMESTER_START = "2026-08-31"
    const val SEMESTER_END = "2027-01-17"
    const val TOTAL_WEEKS = 20

    const val NOTE = "寒假：2027 年 1 月 16 日 — 2 月 20 日（腊月初九至正月十五），" +
        "2 月 6 日为 2027 年春节。"

    data class Event(
        val date: String,
        val label: String,
        val detail: String = "",
    )

    val events: List<Event> = listOf(
        Event("2026-08-31", "老生报到", "9 月 1—4 日学年鉴定"),
        Event("2026-09-01", "新生报到", "9 月 2—4 日入学教育"),
        Event("2026-09-05", "本科新生军训", "9 月 5—24 日"),
        Event("2026-09-07", "老生上课"),
        Event("2026-09-23", "中国农民丰收节"),
        Event("2026-09-25", "中秋节"),
        Event("2026-09-28", "新生上课"),
        Event("2026-10-01", "国庆节", "10 月 2 日为建校 128 周年纪念日"),
        Event("2026-10-23", "第 65 届运动会", "10 月 23—24 日"),
        Event("2026-11-18", "本科生期中考试", "11 月 18—20 日"),
        Event("2026-12-31", "狮山欢乐节"),
        Event("2027-01-01", "元旦"),
        Event("2027-01-11", "本科生期末考试", "第 20 周，1 月 11—17 日"),
        Event("2027-01-16", "寒假开始", "至 2 月 20 日"),
        Event("2027-02-06", "春节", "正月初一"),
    )

    /** 周次标签，1 起。 */
    val weekNotes: Map<Int, String> = mapOf(
        1 to "老生报到 · 学年鉴定",
        4 to "新生上课 · 国庆节",
        13 to "建校纪念 · 狮山欢乐节",
        20 to "期末考试",
    )
}
