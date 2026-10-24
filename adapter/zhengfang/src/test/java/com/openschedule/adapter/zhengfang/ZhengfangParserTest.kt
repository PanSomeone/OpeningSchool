package com.openschedule.adapter.zhengfang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ZhengfangParserTest {

    @Test
    fun `weeks parse ranges, lists and parity`() {
        assertEquals((1..16).toSet(), ZhengfangParser.parseWeeks("1-16周"))
        assertEquals((1..16).filter { it % 2 == 1 }.toSet(), ZhengfangParser.parseWeeks("1-16周(单)"))
        assertEquals((1..16).filter { it % 2 == 0 }.toSet(), ZhengfangParser.parseWeeks("1-16周（双）"))
        assertEquals((1..8).toSet() + (10..16).toSet(), ZhengfangParser.parseWeeks("1-8周,10-16周"))
        assertEquals(setOf(3, 5, 7), ZhengfangParser.parseWeeks("3,5,7周"))
        assertEquals((1..16).toSet(), ZhengfangParser.parseWeeks("第1-16周"))
        assertEquals(emptySet<Int>(), ZhengfangParser.parseWeeks(""))
    }

    @Test
    fun `period range parses spans and single periods`() {
        assertEquals(1..2, ZhengfangParser.parsePeriodRange("1-2节"))
        assertEquals(3..3, ZhengfangParser.parsePeriodRange("第3节"))
        assertEquals(5..8, ZhengfangParser.parsePeriodRange("5-8"))
        assertEquals(null, ZhengfangParser.parsePeriodRange(""))
    }

    @Test
    fun `day parses numerals and chinese names`() {
        assertEquals(1, ZhengfangParser.parseDay("星期一"))
        assertEquals(7, ZhengfangParser.parseDay("周日"))
        assertEquals(3, ZhengfangParser.parseDay("3"))
        assertEquals(null, ZhengfangParser.parseDay(""))
    }

    @Test
    fun `json timetable maps every field`() {
        val body = """
            {
              "kbList": [
                {
                  "kcmc": "高等数学",
                  "xm": "张三",
                  "cdmc": "教一101",
                  "xqj": "1",
                  "jc": "1-2节",
                  "zcd": "1-16周"
                },
                {
                  "kcmc": "大学英语",
                  "xm": "李四",
                  "cdmc": "外语楼202",
                  "xqj": "3",
                  "jc": "3-4节",
                  "zcd": "1-16周(单)"
                }
              ],
              "xsxx": {
                "XNMC": "2026-2027",
                "XQMMC": "1",
                "XQKSRQ": "2026-09-01"
              }
            }
        """.trimIndent()

        val parsed = ZhengfangParser.parseJson(body)
        assertNotNull(parsed)
        parsed!!
        assertEquals(2, parsed.courses.size)
        assertEquals("高等数学", parsed.courses[0].name)
        assertEquals("张三", parsed.courses[0].teacher)
        assertEquals("教一101", parsed.courses[0].location)
        assertEquals(1, parsed.courses[0].day)
        assertEquals(1, parsed.courses[0].startPeriod)
        assertEquals(2, parsed.courses[0].endPeriod)
        assertEquals((1..16).toSet(), parsed.courses[0].weeks)
        assertEquals(4, parsed.maxPeriod)
        assertEquals("2026-09-01", parsed.semesterStart)
        assertEquals("2026-2027 第 1 学期", parsed.title)
    }

    @Test
    fun `html timetable parses the old table layout`() {
        val body = """
            <html><body>
            <table id="kbtable">
              <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th></tr>
              <tr>
                <td>第一节</td>
                <td><div class="kbcontent">高等数学<br>张三<br>1-16周<br>教一101</div></td>
                <td></td>
                <td></td>
              </tr>
              <tr>
                <td>第二节</td>
                <td></td>
                <td></td>
                <td><div class="kbcontent">大学物理<br>王五<br>2-18周<br>实验楼3</div></td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        val parsed = ZhengfangParser.parseHtml(body)
        assertNotNull(parsed)
        parsed!!
        assertEquals(2, parsed.courses.size)
        assertEquals("高等数学", parsed.courses[0].name)
        assertEquals(1, parsed.courses[0].day)
        assertEquals("大学物理", parsed.courses[1].name)
        assertEquals(3, parsed.courses[1].day)
        assertEquals(2, parsed.courses[1].startPeriod)
        assertEquals((2..18).toSet(), parsed.courses[1].weeks)
    }
}
