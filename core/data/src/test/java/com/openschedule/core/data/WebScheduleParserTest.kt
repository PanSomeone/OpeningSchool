package com.openschedule.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WebScheduleParserTest {

    @Test
    fun parsesZhengfangTable() {
        val html = """
            <html><body><table id="kbtable">
              <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
              <tr><td>1-2节</td>
                <td>
                  <div class="kbcontent1">高等数学<br>张老师<br>教一101<br>1-16周</div>
                  <hr>
                  <div class="kbcontent1">大学英语<br>李老师<br>外语楼201<br>1-8周</div>
                </td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr><td>3-4节</td>
                <td></td>
                <td><div class="kbcontent1">程序设计<br>王老师<br>实验楼302<br>1-16周(单)</div></td>
                <td></td><td></td><td></td><td></td><td></td>
              </tr>
            </table></body></html>
        """.trimIndent()

        val result = WebScheduleParser.parsePages(listOf(html), "测试课表").getOrThrow()
        val courses = result.timetable.courses
        assertEquals(3, courses.size)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals((1..16).toSet(), math.weeks)
        assertEquals("张老师", math.teacher)
        assertEquals("教一101", math.location)

        val english = courses.first { it.name == "大学英语" }
        assertEquals((1..8).toSet(), english.weeks)

        val programming = courses.first { it.name == "程序设计" }
        assertEquals(2, programming.day)
        assertEquals(3, programming.startPeriod)
        assertEquals(4, programming.endPeriod)
        assertEquals((1..15).filter { it % 2 == 1 }.toSet(), programming.weeks)
    }

    @Test
    fun expandsRowSpanIntoSeveralPeriods() {
        val html = """
            <html><body><table>
              <tr><td>时间</td><td>周一</td><td>周二</td><td>周三</td></tr>
              <tr><td>第1节</td>
                <td rowspan="2">线性代数<br>刘老师<br>二教201<br>1-16周</td>
                <td></td><td></td>
              </tr>
              <tr><td>第2节</td>
                <td>Python<br>赵老师<br>机房3<br>2-18周</td>
                <td></td>
              </tr>
            </table></body></html>
        """.trimIndent()

        val courses = WebScheduleParser.parsePages(listOf(html), "测试").getOrThrow().timetable.courses
        assertEquals(2, courses.size)
        val algebra = courses.first { it.name == "线性代数" }
        assertEquals(1, algebra.day)
        assertEquals(1, algebra.startPeriod)
        assertEquals(2, algebra.endPeriod)
        assertEquals(2, courses.first { it.name == "Python" }.day)
    }

    @Test
    fun parsesTransposedTable() {
        val html = """
            <html><body><table>
              <tr><td>时间</td><td>第1节</td><td>第2节</td><td>第3节</td><td>第4节</td></tr>
              <tr><td>周一</td><td>数据结构<br>孙老师<br>一教101<br>1-16周</td><td></td><td></td><td></td></tr>
              <tr><td>周二</td><td></td><td>操作系统<br>周老师<br>二教202<br>1-16周</td><td></td><td></td></tr>
              <tr><td>周三</td><td></td><td></td><td>计算机网络<br>吴老师<br>三教303<br>1-8周</td><td></td></tr>
            </table></body></html>
        """.trimIndent()

        val courses = WebScheduleParser.parsePages(listOf(html), "测试").getOrThrow().timetable.courses
        assertEquals(3, courses.size)
        val data = courses.first { it.name == "数据结构" }
        assertEquals(1, data.day)
        assertEquals(1, data.startPeriod)
        assertEquals(1, data.endPeriod)
        val network = courses.first { it.name == "计算机网络" }
        assertEquals(3, network.day)
        assertEquals(3, network.startPeriod)
    }

    @Test
    fun parsesEmbeddedKbListJson() {
        val html = """
            <html><body><pre>{"kbList":[{"kcmc":"离散数学","xqj":"3","jcs":"5-6","zcd":"1-16周","xm":"陈老师","cdmc":"三教101"}]}</pre></body></html>
        """.trimIndent()

        val courses = WebScheduleParser.parsePages(listOf(html), "测试").getOrThrow().timetable.courses
        assertEquals(1, courses.size)
        val course = courses.single()
        assertEquals("离散数学", course.name)
        assertEquals(3, course.day)
        assertEquals(5, course.startPeriod)
        assertEquals(6, course.endPeriod)
        assertEquals("三教101", course.location)
    }

    @Test
    fun infersSemesterStartFromCurrentWeek() {
        val html = """
            <html><body>
              <div>当前周次：第3周</div>
              <table>
                <tr><td>节次</td><td>周一</td><td>周二</td><td>周三</td></tr>
                <tr><td>1</td><td>物理<br>李老师<br>一教101<br>1-16周</td><td></td><td></td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val parsed = WebScheduleParser.parsePages(
            pages = listOf(html),
            fallbackName = "测试",
            today = LocalDate.of(2026, 10, 21),
        ).getOrThrow()
        assertEquals("2026-10-05", parsed.timetable.semesterStart)
        assertTrue(parsed.warnings.any { it.contains("推算") })
    }

    @Test
    fun failsWhenNoTableIsPresent() {
        val result = WebScheduleParser.parsePages(
            listOf("<html><body><p>请先登录</p></body></html>"),
            "测试",
        )
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun picksTheTableWithMoreCourses() {
        val layout = """
            <html><body>
              <table><tr><td>布局</td></tr></table>
              <table>
                <tr><td>节次</td><td>周一</td><td>周二</td><td>周三</td></tr>
                <tr><td>1</td><td>物理<br>李老师<br>一教101<br>1-16周</td><td>化学<br>王老师<br>二教201<br>1-16周</td><td></td></tr>
              </table>
            </body></html>
        """.trimIndent()
        val courses = WebScheduleParser.parsePages(listOf(layout), "测试").getOrThrow().timetable.courses
        assertEquals(2, courses.size)
    }
}
