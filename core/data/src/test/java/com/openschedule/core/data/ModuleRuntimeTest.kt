package com.openschedule.core.data

import com.openschedule.core.model.ImportResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModuleRuntimeTest {

    @Test
    fun runsScriptWithNarrowHostApi() = runBlocking {
        val dir = Files.createTempDirectory("module-test").toFile()
        try {
            File(dir, "main.js").writeText(
                """
                function parse() {
                  return {
                    name: host.text(0, "#title"),
                    semesterStart: "2026-09-07",
                    totalWeeks: 20,
                    courses: [{
                      name: host.text(0, ".course-name"),
                      teacher: host.text(0, ".teacher"),
                      location: host.text(0, ".location"),
                      slots: [{ day: 1, start: 1, end: 2, weeks: [1, 2, 3] }]
                    }]
                  };
                }
                """.trimIndent(),
            )
            val module = InstalledModule(
                manifest = ModuleManifest(
                    id = "test.module",
                    name = "测试模块",
                    startUrl = "https://example.com",
                    entry = "main.js",
                ),
                directory = dir,
                enabled = true,
            )
            val html = """
                <html><body>
                  <h1 id="title">测试课表</h1>
                  <div class="course-name">高等数学</div>
                  <div class="teacher">张三</div>
                  <div class="location">三教 A101</div>
                </body></html>
            """.trimIndent()

            val result = ModuleRuntime.parse(module, listOf(html))
            if (result is ImportResult.Failure) {
                org.junit.Assert.fail(result.message)
            }
            assertTrue(result is ImportResult.Success)
            val timetable = (result as ImportResult.Success).timetable
            assertEquals("测试课表", timetable.name)
            assertEquals(1, timetable.courses.size)
            val course = timetable.courses.first()
            assertEquals("高等数学", course.name)
            assertEquals("张三", course.teacher)
            assertEquals("三教 A101", course.location)
            assertEquals(1, course.slots.size)
            assertEquals(setOf(1, 2, 3), course.slots.first().weeks)
        } finally {
            dir.deleteRecursively()
        }
    }
}
