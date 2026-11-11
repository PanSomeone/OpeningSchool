package com.openschedule.plugin.sample.viewbased

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.openschedule.plugin.sdk.HostPlugin
import com.openschedule.plugin.sdk.NativeScreen
import com.openschedule.plugin.sdk.NativeWidget
import com.openschedule.plugin.sdk.PluginHost

/**
 * Sample native plugin.
 *
 * Everything here is plain Android: the host embeds these views with
 * `AndroidView`, and no WebView, HTML or JS is involved. Build the APK with
 * `./gradlew :plugin:sample-native:assembleDebug`, install it, then enable the
 * plugin in 插件 → 原生插件.
 */
class SampleNativePlugin : HostPlugin {

    private lateinit var host: PluginHost
    private var count: Int = 0

    override fun onCreate(host: PluginHost) {
        this.host = host
        count = host.getString("count", "0")?.toIntOrNull() ?: 0
        host.log("示例原生插件已启动，计数=$count")
        host.onEvent("timetable.updated") {
            host.log("收到课表更新事件")
        }
    }

    override fun onDestroy() {
        host.log("示例原生插件已停止")
    }

    override fun screens(): List<NativeScreen> = listOf(
        NativeScreen(id = "main", title = "原生示例", iconKey = "science") { context ->
            buildScreen(context)
        },
    )

    override fun widgets(): List<NativeWidget> = listOf(
        NativeWidget(
            id = "counter",
            title = "原生计数",
            iconKey = "science",
            columns = 1,
            rows = 1,
        ) { context ->
            buildCard(context, compact = true)
        },
    )

    private fun buildScreen(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
        }
        root.addView(
            TextView(context).apply {
                text = "这是插件 APK 里的原生 View，不是网页。"
                textSize = 14f
                setTextColor(Color.GRAY)
            },
        )
        root.addView(buildCard(context, compact = false), topMargin(context, 12))
        root.addView(
            TextView(context).apply {
                text = "宿主用 AndroidView 嵌入；布局、按钮和状态都在插件自己的代码里，计数保存在插件私有存储中。"
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, dp(context, 12), 0, 0)
            },
        )
        return root
    }

    private fun buildCard(context: Context, compact: Boolean): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 16).toFloat()
                setColor(Color.parseColor("#1F6FEB"))
            }
        }
        val label = TextView(context).apply {
            text = if (compact) "原生组件" else "原生页面"
            textSize = 11f
            setTextColor(Color.parseColor("#D7E3FF"))
        }
        val value = TextView(context).apply {
            text = count.toString()
            textSize = if (compact) 26f else 40f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        fun applyDelta(delta: Int) {
            count = (count + delta).coerceAtLeast(0)
            host.putString("count", count.toString())
            value.text = count.toString()
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(makeButton(context, "加一") { applyDelta(1) })
        row.addView(makeButton(context, "清零") { applyDelta(-count) })

        card.addView(label)
        card.addView(value)
        card.addView(row, topMargin(context, 8))
        return card
    }

    private fun makeButton(context: Context, text: String, onClick: () -> Unit): View =
        Button(context).apply {
            this.text = text
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            setOnClickListener { onClick() }
        }

    private fun topMargin(context: Context, value: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(context, value) }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
