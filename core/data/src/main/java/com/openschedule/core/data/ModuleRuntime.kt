package com.openschedule.core.data

import com.openschedule.core.model.COURSE_COLOR_COUNT
import com.openschedule.core.model.Course
import com.openschedule.core.model.CourseSlot
import com.openschedule.core.model.ImportResult
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.WeekMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.UUID

/**
 * Runs a module's `main.js` in Rhino. The script only gets a narrow Jsoup-based
 * host API and must return a JSON timetable; no Android or Java packages are
 * exposed to it.
 */
object ModuleRuntime {

    private const val MAX_PAGES = 40
    private const val MAX_SCRIPT_MILLIS = 6_000L

    suspend fun parse(
        module: InstalledModule,
        pages: List<String>,
    ): ImportResult = withContext(Dispatchers.IO) {
        val scriptFile = File(module.directory, module.manifest.entry)
        if (!scriptFile.isFile) {
            return@withContext ImportResult.Failure("模块入口文件不存在")
        }
        val script = runCatching { scriptFile.readText() }.getOrElse {
            return@withContext ImportResult.Failure("模块脚本无法读取")
        }
        val raw = runCatching {
            execute(script, module.manifest.entry, pages.take(MAX_PAGES))
        }.getOrElse { error ->
            AppLog.w("Module", "模块 ${module.manifest.id} 执行失败: ${error.message}")
            return@withContext ImportResult.Failure(
                "模块执行失败：${error.message ?: "未知错误"}",
            )
        }
        mapResult(raw, module.manifest.name)
    }

    private fun execute(script: String, name: String, pages: List<String>): String {
        deadline.set(System.currentTimeMillis() + MAX_SCRIPT_MILLIS)
        val context = factory.enterContext()
        try {
            val scope = context.initStandardObjects()
            ScriptableObject.putProperty(scope, "host", moduleHostScope(ModuleHost(pages)))
            context.evaluateString(scope, script, name, 1, null)
            val parse = scope.get("parse", scope)
            if (parse !is Function) {
                throw EvaluatorException("模块必须导出 parse() 函数")
            }
            val result = parse.call(context, scope, scope, emptyArray())
            return when (result) {
                null -> "null"
                is CharSequence -> result.toString()
                else -> NativeJSON.stringify(context, scope, result, null, null)
                    ?.toString()
                    ?: "null"
            }
        } finally {
            deadline.remove()
            Context.exit()
        }
    }

    private fun mapResult(raw: String, moduleName: String): ImportResult {
        val parsed = runCatching {
            json.decodeFromString(JsTimetable.serializer(), raw)
        }.getOrElse {
            return ImportResult.Failure("模块返回的数据格式不正确")
        }
        if (parsed.courses.isEmpty()) {
            return ImportResult.Failure("模块没有解析出课程")
        }
        val warnings = mutableListOf<String>()
        val periods = Timetable.defaultPeriods()
        val specifiedMaxWeek = parsed.courses
            .flatMap { it.slots }
            .flatMap { it.weeks }
            .filter { it in 1..30 }
            .maxOrNull() ?: 0
        val totalWeeks = maxOf(parsed.totalWeeks, specifiedMaxWeek)
            .takeIf { it > 0 }
            ?.coerceIn(1, 30)
            ?: Timetable.DEFAULT_TOTAL_WEEKS

        fun validWeeks(values: List<Int>): Set<Int> {
            val filtered = values.filter { it in 1..totalWeeks }.toSet()
            return if (values.isEmpty() || filtered.isEmpty()) {
                (1..totalWeeks).toSet()
            } else {
                filtered
            }
        }

        val courses = parsed.courses.mapNotNull { rawCourse ->
            val name = rawCourse.name.trim()
            if (name.isBlank()) {
                warnings += "跳过了一门没有名称的课程"
                return@mapNotNull null
            }
            val slots = rawCourse.slots.mapNotNull { rawSlot ->
                val start = rawSlot.start.coerceAtLeast(1)
                val end = rawSlot.end.coerceAtMost(periods.size)
                if (rawSlot.day !in 1..7 || start > end) {
                    warnings += "$name 有一个时间段不合法，已跳过"
                    return@mapNotNull null
                }
                CourseSlot(
                    day = rawSlot.day,
                    startPeriod = start,
                    endPeriod = end,
                    weeks = validWeeks(rawSlot.weeks),
                )
            }
            if (slots.isEmpty()) {
                warnings += "$name 没有有效时间段，已跳过"
                return@mapNotNull null
            }
            val first = slots.first()
            val color = Math.floorMod(
                rawCourse.color ?: name.hashCode(),
                COURSE_COLOR_COUNT,
            )
            Course(
                id = UUID.randomUUID().toString(),
                name = name,
                teacher = rawCourse.teacher.trim(),
                location = rawCourse.location.trim(),
                day = first.day,
                startPeriod = first.startPeriod,
                endPeriod = first.endPeriod,
                weeks = slots.flatMap { it.weeks }.toSet(),
                color = color,
                slots = slots,
            )
        }
        if (courses.isEmpty()) {
            return ImportResult.Failure("模块没有解析出有效课程")
        }
        val semesterStart = WeekMath.parseDate(parsed.semesterStart)?.toString().orEmpty()
        if (semesterStart.isBlank()) {
            warnings += "模块没有提供有效的开学日期，请在课表设置里补充"
        }
        val timetable = Timetable(
            name = parsed.name.takeIf { it.isNotBlank() } ?: "$moduleName 课表",
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            periods = periods,
            courses = courses,
        )
        return ImportResult.Success(timetable, warnings.distinct())
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val deadline = ThreadLocal<Long>()

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context {
            val context = super.makeContext()
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            context.setClassShutter(ClassShutter { false })
            context.setInstructionObserverThreshold(50_000)
            return context
        }

        override fun observeInstructionCount(context: Context, instructionCount: Int) {
            val limit = deadline.get() ?: return
            if (System.currentTimeMillis() > limit) {
                throw EvaluatorException("模块脚本执行超时")
            }
        }
    }

    private fun moduleHostScope(api: ModuleHost): ScriptableObject {
        val host = NativeObject()
        fun add(name: String, block: (Array<out Any?>) -> Any?) {
            host.put(name, host, HostFunction(block))
        }
        add("pageCount") { api.pageCount() }
        add("page") { args -> api.page(args.intAt(0)) }
        add("text") { args -> api.text(args.intAt(0), args.stringAt(1)) }
        add("texts") { args -> api.texts(args.intAt(0), args.stringAt(1)) }
        add("attr") { args ->
            api.attr(args.intAt(0), args.stringAt(1), args.stringAt(2))
        }
        add("attrs") { args ->
            api.attrs(args.intAt(0), args.stringAt(1), args.stringAt(2))
        }
        add("html") { args -> api.html(args.intAt(0), args.stringAt(1)) }
        add("count") { args -> api.count(args.intAt(0), args.stringAt(1)) }
        add("log") { args -> api.log(args.stringAt(0)) }
        return host
    }

    private class HostFunction(
        private val block: (Array<out Any?>) -> Any?,
    ) : BaseFunction() {
        override fun call(
            cx: Context?,
            scope: Scriptable?,
            thisObj: Scriptable?,
            args: Array<out Any?>,
        ): Any? = block(args)
    }

    private fun Array<out Any?>.intAt(index: Int): Int =
        (getOrNull(index) as? Number)?.toInt() ?: -1

    private fun Array<out Any?>.stringAt(index: Int): String =
        getOrNull(index)?.toString().orEmpty()

    private class ModuleHost(private val pages: List<String>) {
        private val documents: List<Document> by lazy {
            pages.map { runCatching { Jsoup.parse(it) }.getOrElse { Document("") } }
        }

        fun pageCount(): Int = pages.size

        fun page(index: Int): String = pages.getOrNull(index).orEmpty()

        fun text(page: Int, selector: String): String =
            document(page)?.selectFirst(selector)?.text().orEmpty()

        fun texts(page: Int, selector: String): String =
            json.encodeToString(
                ListSerializer(String.serializer()),
                document(page)?.select(selector)?.map { it.text() }.orEmpty(),
            )

        fun attr(page: Int, selector: String, attribute: String): String =
            document(page)?.selectFirst(selector)?.attr(attribute).orEmpty()

        fun attrs(page: Int, selector: String, attribute: String): String =
            json.encodeToString(
                ListSerializer(String.serializer()),
                document(page)?.select(selector)?.map { it.attr(attribute) }.orEmpty(),
            )

        fun html(page: Int, selector: String): String =
            document(page)?.selectFirst(selector)?.outerHtml().orEmpty()

        fun count(page: Int, selector: String): Int =
            document(page)?.select(selector)?.size ?: 0

        fun log(message: String) {
            AppLog.i("Module", message.take(500))
        }

        private fun document(page: Int): Document? = documents.getOrNull(page)
    }
}

@Serializable
private data class JsTimetable(
    val name: String = "",
    val semesterStart: String = "",
    val totalWeeks: Int = 0,
    val courses: List<JsCourse> = emptyList(),
)

@Serializable
private data class JsCourse(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val color: Int? = null,
    val slots: List<JsSlot> = emptyList(),
)

@Serializable
private data class JsSlot(
    val day: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
    val weeks: List<Int> = emptyList(),
)
