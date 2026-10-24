package com.openschedule.feature.timetable

data class SchoolPortal(
    val id: String,
    val name: String,
    val url: String,
    val note: String = "",
)

val builtInSchoolPortals: List<SchoolPortal> = listOf(
    SchoolPortal(
        id = "hzau",
        name = "华中农业大学",
        url = "http://byjxyt.hzau.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
        note = "正方 · 统一认证 · 短信验证",
    ),
)
