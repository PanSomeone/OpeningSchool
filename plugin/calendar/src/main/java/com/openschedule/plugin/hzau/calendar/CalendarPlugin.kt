package com.openschedule.plugin.hzau.calendar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.openschedule.plugin.sdk.HostPlugin
import com.openschedule.plugin.sdk.NativeScreen
import com.openschedule.plugin.sdk.NativeWidget
import com.openschedule.plugin.sdk.PluginHost
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 校历插件：用原生 Android View 呈现学期周历与关键日程。
 *
 * 不访问网络、不申请权限；数据随插件打包，界面全部由代码构建。
 */
class CalendarPlugin : HostPlugin {

    private var host: PluginHost? = null

    override fun onCreate(host: PluginHost) {
        this.host = host
        host.log("校历插件已加载：${CalendarData.TITLE}")
    }

    override fun onDestroy() {
        host = null
    }

    override fun screens(): List<NativeScreen> = listOf(
        NativeScreen(id = "calendar", title = "校历", iconKey = "date") { context ->
            buildScreen(context)
        },
    )

    override fun widgets(): List<NativeWidget> = listOf(
        NativeWidget(id = "progress", title = "教学周", iconKey = "date") { context ->
            buildWidget(context)
        },
    )

    // ---------------------------------------------------------------- 页面

    private fun buildScreen(context: Context): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 24))
        }

        column.addView(
            text(context, CalendarData.SUBTITLE, 13f, Color.parseColor("#8A8F98")),
        )
        column.addView(
            text(context, CalendarData.TITLE, 22f, null, bold = true).apply {
                setPadding(0, dp(context, 2), 0, dp(context, 12))
            },
        )

        column.addView(summaryCard(context))
        sectionTitle(context, "关键日程")?.let { column.addView(it) }
        CalendarData.events.forEach { column.addView(eventRow(context, it)) }

        sectionTitle(context, "教学周历")?.let { column.addView(it) }
        column.addView(weekTable(context))

        column.addView(
            text(context, CalendarData.NOTE, 12f, Color.parseColor("#8A8F98")).apply {
                setPadding(0, dp(context, 14), 0, 0)
                setLineSpacing(dp(context, 3).toFloat(), 1f)
            },
        )

        return ScrollView(context).apply { addView(column) }
    }

    private fun summaryCard(context: Context): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, Color.parseColor("#1F2E7D32"), dp(context, 14).toFloat())
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        }
        val current = currentWeek()
        val status = when {
            current == null -> "学期未开始"
            current > CalendarData.TOTAL_WEEKS -> "学期已结束"
            else -> "第 $current 周"
        }
        card.addView(text(context, status, 18f, Color.parseColor("#2E7D32"), bold = true))
        card.addView(
            text(
                context,
                "开学 ${pretty(CalendarData.SEMESTER_START)} · 共 ${CalendarData.TOTAL_WEEKS} 周",
                13f,
                Color.parseColor("#5F6368"),
            ).apply { setPadding(0, dp(context, 4), 0, 0) },
        )
        val weekRange = weekRange(current ?: 0)
        if (weekRange != null) {
            card.addView(
                text(context, "本周 $weekRange", 13f, Color.parseColor("#5F6368")).apply {
                    setPadding(0, dp(context, 2), 0, 0)
                },
            )
        }
        return card
    }

    private fun eventRow(context: Context, event: CalendarData.Event): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 7), 0, dp(context, 7))
        }
        val date = runCatching { LocalDate.parse(event.date) }.getOrNull()
        val dateText = date?.format(DateTimeFormatter.ofPattern("MM/dd")) ?: event.date
        row.addView(
            text(context, dateText, 13f, Color.parseColor("#2E7D32"), bold = true).apply {
                width = dp(context, 52)
            },
        )
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(context, event.label, 14f, null))
        if (event.detail.isNotBlank()) {
            body.addView(text(context, event.detail, 12f, Color.parseColor("#8A8F98")))
        }
        row.addView(
            body,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return row
    }

    private fun weekTable(context: Context): View {
        val table = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val current = currentWeek()
        for (week in 1..CalendarData.TOTAL_WEEKS) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                if (week == current) {
                    background = rounded(context, Color.parseColor("#142E7D32"), dp(context, 10).toFloat())
                }
            }
            row.addView(
                text(
                    context,
                    "第 $week 周",
                    13f,
                    if (week == current) Color.parseColor("#2E7D32") else null,
                    bold = week == current,
                ).apply { width = dp(context, 62) },
            )
            row.addView(
                text(context, weekRange(week).orEmpty(), 13f, Color.parseColor("#5F6368")).apply {
                    width = dp(context, 104)
                },
            )
            val note = CalendarData.weekNotes[week]
            row.addView(
                text(
                    context,
                    note.orEmpty(),
                    12f,
                    if (note.isNullOrBlank()) Color.parseColor("#B0B4BA") else Color.parseColor("#2E7D32"),
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            table.addView(row)
            if (week != CalendarData.TOTAL_WEEKS) {
                table.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(context, 1),
                        )
                        setBackgroundColor(Color.parseColor("#22808080"))
                    },
                )
            }
        }
        return table
    }

    // ---------------------------------------------------------------- 组件

    private fun buildWidget(context: Context): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = rounded(context, Color.parseColor("#1F2E7D32"), dp(context, 16).toFloat())
        }
        val current = currentWeek()
        val status = when {
            current == null -> "未开学"
            current > CalendarData.TOTAL_WEEKS -> "已结束"
            else -> "第 $current 周"
        }
        card.addView(text(context, "教学周", 11f, Color.parseColor("#8A8F98")))
        card.addView(
            text(context, status, 20f, Color.parseColor("#2E7D32"), bold = true).apply {
                setPadding(0, dp(context, 2), 0, 0)
            },
        )
        val range = weekRange(current ?: 0)
        if (range != null) {
            card.addView(
                text(context, range, 11f, Color.parseColor("#5F6368")).apply {
                    setPadding(0, dp(context, 2), 0, 0)
                },
            )
        }
        return card
    }

    // ---------------------------------------------------------------- 计算

    /** 当前教学周；开学前或超出总周数时返回 null / 超界值。 */
    private fun currentWeek(): Int? {
        val start = runCatching { LocalDate.parse(CalendarData.SEMESTER_START) }.getOrNull()
            ?: return null
        val today = LocalDate.now()
        if (today.isBefore(start)) return null
        val days = ChronoUnit.DAYS.between(start, today)
        return (days / 7).toInt() + 1
    }

    private fun weekRange(week: Int): String? {
        if (week <= 0) return null
        val start = runCatching { LocalDate.parse(CalendarData.SEMESTER_START) }.getOrNull()
            ?: return null
        val monday = start.plusWeeks((week - 1).toLong())
        val sunday = monday.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MM/dd")
        return "${monday.format(fmt)} — ${sunday.format(fmt)}"
    }

    private fun pretty(iso: String): String = runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))
    }.getOrDefault(iso)

    // ---------------------------------------------------------------- 视图辅助

    private fun sectionTitle(context: Context, label: String): TextView =
        text(context, label, 13f, Color.parseColor("#8A8F98"), bold = true).apply {
            setPadding(0, dp(context, 18), 0, dp(context, 4))
        }

    private fun text(
        context: Context,
        value: String,
        sizeSp: Float,
        color: Int?,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp
        if (color != null) setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }

    private fun rounded(context: Context, color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
