package com.openschedule.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

data class NavIcon(val key: String, val label: String, val icon: ImageVector)

object NavIcons {

    val catalog: List<NavIcon> = listOf(
        NavIcon("calendar", "课表", Icons.Filled.CalendarMonth),
        NavIcon("water", "热水", Icons.Filled.WaterDrop),
        NavIcon("settings", "设置", Icons.Filled.Settings),
        NavIcon("extension", "插件", Icons.Filled.Extension),
        NavIcon("school", "学校", Icons.Filled.School),
        NavIcon("grid", "格子", Icons.Filled.GridView),
        NavIcon("date", "日期", Icons.Filled.DateRange),
        NavIcon("book", "书本", Icons.Filled.Book),
        NavIcon("star", "星标", Icons.Filled.Star),
        NavIcon("home", "主页", Icons.Filled.Home),
        NavIcon("apps", "应用", Icons.Filled.Apps),
        NavIcon("bell", "提醒", Icons.Filled.Notifications),
        NavIcon("widgets", "组件", Icons.Filled.Widgets),
        NavIcon("timer", "计时", Icons.Filled.Timer),
        NavIcon("coffee", "休息", Icons.Filled.Coffee),
        NavIcon("music", "音乐", Icons.Filled.MusicNote),
        NavIcon("science", "科学", Icons.Filled.Science),
        NavIcon("brush", "画笔", Icons.Filled.Brush),
        NavIcon("code", "代码", Icons.Filled.Code),
        NavIcon("cloud", "云端", Icons.Filled.Cloud),
        NavIcon("bolt", "闪电", Icons.Filled.Bolt),
        NavIcon("heart", "喜欢", Icons.Filled.Favorite),
        NavIcon("flag", "旗帜", Icons.Filled.Flag),
        NavIcon("explore", "探索", Icons.Filled.Explore),
        NavIcon("list", "列表", Icons.AutoMirrored.Filled.List),
        NavIcon("tune", "调节", Icons.Filled.Tune),
        NavIcon("palette", "配色", Icons.Filled.Palette),
        NavIcon("sun", "晴天", Icons.Filled.WbSunny),
    )

    private val byKey = catalog.associateBy { it.key }

    fun icon(key: String): ImageVector = byKey[key]?.icon ?: Icons.Filled.CalendarMonth
}
