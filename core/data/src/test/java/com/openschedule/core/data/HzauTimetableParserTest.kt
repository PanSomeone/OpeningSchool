package com.openschedule.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HzauTimetableParserTest {

    @Test
    fun parsesHzauGridCardsAndMergesSlots() {
        val html = """
            <html><body>
              <table id="kbgrid_table_0" class="timetable1"><tbody>
                <tr><td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td></tr>
                <tr><td>1</td>
                  <td rowspan="2" id="1-1" class="td_wrap">
                    ${card("高等数学☆", "blue", "(1-2节)2-5周,7-10周", "三教A101", "张老师", "高数-01")}
                  </td>
                  <td rowspan="4" id="2-5" class="td_wrap">
                    ${card("大学物理☆", "blue", "(5-6节)3-8周", "二教B202", "李老师", "物理-01")}
                    ${card("物理实验○", "blue", "(5-8节)10-12周", "实验楼305", "王老师", "物理实验-01")}
                  </td>
                  <td></td>
                </tr>
                <tr><td>3</td>
                  <td rowspan="2" id="3-3" class="td_wrap">
                    ${card("高等数学☆", "blue", "(3-4节)3-6周", "三教A101", "张老师", "高数-01")}
                    ${card("待筛选课程", "red", "(3-4节)1-16周", "三教A102", "赵老师", "待筛选-01")}
                  </td>
                </tr>
              </tbody></table>
            </body></html>
        """.trimIndent()

        val parsed = WebScheduleParser.parsePages(listOf(html), "华农课表").getOrThrow()
        val courses = parsed.timetable.courses
        assertEquals(3, courses.size)
        assertTrue(courses.none { it.name == "待筛选课程" })

        val math = courses.single { it.name == "高等数学" }
        assertEquals("张老师", math.teacher)
        assertEquals("三教A101", math.location)
        assertEquals((2..5).toSet() + (7..10).toSet() + (3..6).toSet(), math.weeks)
        assertEquals(2, math.effectiveSlots.size)
        assertEquals(1, math.effectiveSlots.first { it.day == 1 }.startPeriod)
        assertEquals(3, math.effectiveSlots.first { it.day == 3 }.startPeriod)
        assertEquals((3..6).toSet(), math.effectiveSlots.first { it.day == 3 }.weeks)

        val lab = courses.single { it.name == "物理实验" }
        assertEquals(5, lab.startPeriod)
        assertEquals(8, lab.endPeriod)
        assertEquals(2, lab.day)
    }

    private fun card(
        name: String,
        color: String,
        schedule: String,
        location: String,
        teacher: String,
        group: String,
    ): String = """
        <div class="timetable_con text-left">
          <span class="title"><font color="$color">$name</font></span>
          <p><span title="节/周"><font color="$color"><span class="glyphicon"></span></font></span><font color="$color"> $schedule</font></p>
          <p><span title="上课地点"><font color="$color"><span class="glyphicon"></span></font></span><font color="$color"> $location</font></p>
          <p><span title="教师"><font color="$color"><span class="glyphicon"></span></font></span><font color="$color"> $teacher</font></p>
          <p><span title="教学班名称"><font color="$color"><span class="glyphicon"></span></font></span><font color="$color"> $group</font></p>
        </div>
    """.trimIndent()
}
