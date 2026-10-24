package com.openschedule.plugin.scores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeParserTest {

    @Test
    fun parsesZhengfangGradeTable() {
        val html = """
            <html><body>
              <div>2025-2026 学年第一学期 成绩</div>
              <table>
                <tr><td colspan="10">学生成绩表</td></tr>
                <tr>
                  <th>课程名称</th><th>课程代码</th><th>学分</th><th>绩点</th>
                  <th>成绩</th><th>课程性质</th><th>学年</th><th>学期</th>
                </tr>
                <tr>
                  <td>高等数学</td><td>A001</td><td>4</td><td>4.0</td>
                  <td>90</td><td>必修</td><td>2025-2026</td><td>1</td>
                </tr>
                <tr>
                  <td>大学英语</td><td>A002</td><td>3</td><td>3.0</td>
                  <td>80.5</td><td>必修</td><td>2025-2026</td><td>1</td>
                </tr>
                <tr>
                  <td>体育</td><td>A003</td><td>2</td><td>3.7</td>
                  <td>优秀</td><td>必修</td><td>2025-2026</td><td>1</td>
                </tr>
                <tr>
                  <td>合计</td><td></td><td>7</td><td></td><td></td><td></td><td></td><td></td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val result = GradeParser.parsePages(listOf(html)).getOrThrow()
        assertEquals(2, result.courses.size)
        assertEquals(1, result.skippedLevel)
        val math = result.courses.first { it.name == "高等数学" }
        assertEquals(4.0, math.credit, 0.0001)
        assertEquals(90.0, math.score, 0.0001)
        assertEquals("2025-2026 1", math.term)
        val english = result.courses.first { it.name == "大学英语" }
        assertEquals(80.5, english.score, 0.0001)
    }

    @Test
    fun ignoresGradePointColumnWhenItIsNotCredit() {
        val html = """
            <html><body><table>
              <tr><th>课程</th><th>学分绩点</th><th>学分</th><th>成绩</th><th>成绩排名</th></tr>
              <tr><td>物理</td><td>12.0</td><td>3</td><td>89</td><td>4</td></tr>
            </table></body></html>
        """.trimIndent()

        val course = GradeParser.parsePages(listOf(html)).getOrThrow().courses.single()
        assertEquals("物理", course.name)
        assertEquals(3.0, course.credit, 0.0001)
        assertEquals(89.0, course.score, 0.0001)
    }

    @Test
    fun parsesJsonGradePayload() {
        val html = """
            <html><body><pre>{"items":[
              {"kcmc":"线性代数","xf":"3.0","cj":"88","xnmc":"2025-2026","xqmmc":"1"},
              {"kcmc":"体育","xf":"2.0","cj":"良好","xnmc":"2025-2026","xqmmc":"1"}
            ]}</pre></body></html>
        """.trimIndent()

        val result = GradeParser.parsePages(listOf(html)).getOrThrow()
        val course = result.courses.single()
        assertEquals("线性代数", course.name)
        assertEquals(3.0, course.credit, 0.0001)
        assertEquals(88.0, course.score, 0.0001)
        assertEquals(1, result.skippedLevel)
    }

    @Test
    fun failsWhenOnlyLevelGradesExist() {
        val html = """
            <html><body><table>
              <tr><th>课程名称</th><th>学分</th><th>成绩</th></tr>
              <tr><td>音乐鉴赏</td><td>1</td><td>优秀</td></tr>
            </table></body></html>
        """.trimIndent()

        val result = GradeParser.parsePages(listOf(html))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("等级制"))
    }

    @Test
    fun failsWhenThereIsNoGradeTable() {
        val result = GradeParser.parsePages(
            listOf("<html><body><p>请先登录</p></body></html>"),
        )
        assertTrue(result.isFailure)
    }
}
