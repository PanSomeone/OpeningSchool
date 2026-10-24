package com.openschedule.plugin.scores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HzauGradeParserTest {

    @Test
    fun parsesJqGridGradeTable() {
        val html = """
            <html><body>
              <table class="ui-jqgrid-htable"><thead><tr>
                <th id="tabGrid_ck">查看</th><th id="tabGrid_xnmmc">学年</th>
                <th id="tabGrid_xqmmc">学期</th><th id="tabGrid_kcmc">课程名称</th>
                <th id="tabGrid_xf">学分</th><th id="tabGrid_jd">绩点</th>
                <th id="tabGrid_cj">成绩</th><th id="tabGrid_cjbz">成绩备注</th>
              </tr></thead></table>
              <table id="tabGrid" class="ui-jqgrid-btable"><tbody>
                <tr class="jqgfirstrow" role="row"><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
                <tr role="row">
                  <td aria-describedby="tabGrid_ck">查看</td>
                  <td aria-describedby="tabGrid_xnmmc">2024-2025</td>
                  <td aria-describedby="tabGrid_xqmmc">1</td>
                  <td aria-describedby="tabGrid_kcmc">大学生心理发展与指导</td>
                  <td aria-describedby="tabGrid_xf">1.0</td>
                  <td aria-describedby="tabGrid_jd">4.00</td>
                  <td aria-describedby="tabGrid_cj">91</td>
                  <td aria-describedby="tabGrid_cjbz"></td>
                </tr>
                <tr role="row">
                  <td aria-describedby="tabGrid_ck">查看</td>
                  <td aria-describedby="tabGrid_xnmmc">2024-2025</td>
                  <td aria-describedby="tabGrid_xqmmc">1</td>
                  <td aria-describedby="tabGrid_kcmc">大学生心理素质拓展</td>
                  <td aria-describedby="tabGrid_xf">1.0</td>
                  <td aria-describedby="tabGrid_jd">1.00</td>
                  <td aria-describedby="tabGrid_cj">合格</td>
                  <td aria-describedby="tabGrid_cjbz">通过</td>
                </tr>
                <tr role="row">
                  <td aria-describedby="tabGrid_ck">查看</td>
                  <td aria-describedby="tabGrid_xnmmc">2024-2025</td>
                  <td aria-describedby="tabGrid_xqmmc">2</td>
                  <td aria-describedby="tabGrid_kcmc">微积分A（1）</td>
                  <td aria-describedby="tabGrid_xf">5.0</td>
                  <td aria-describedby="tabGrid_jd">2.30</td>
                  <td aria-describedby="tabGrid_cj">74</td>
                  <td aria-describedby="tabGrid_cjbz"></td>
                </tr>
              </tbody></table>
            </body></html>
        """.trimIndent()

        val parsed = GradeParser.parsePages(listOf(html)).getOrThrow()
        assertEquals(2, parsed.courses.size)
        assertEquals(1, parsed.skippedLevel)

        val psychology = parsed.courses.single { it.name == "大学生心理发展与指导" }
        assertEquals(1.0, psychology.credit, 0.0001)
        assertEquals(91.0, psychology.score, 0.0001)
        assertEquals("2024-2025 1", psychology.term)

        val math = parsed.courses.single { it.name == "微积分A（1）" }
        assertEquals(5.0, math.credit, 0.0001)
        assertEquals(74.0, math.score, 0.0001)
        assertEquals("2024-2025 2", math.term)
        assertTrue(parsed.courses.none { it.name == "大学生心理素质拓展" })
    }
}
