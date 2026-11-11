package com.openschedule.core.data

import kotlinx.serialization.json.JsonPrimitive
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Shared Rhino sandbox used by both the v1 web-importer runtime and the v2
 * plugin runtime. Scripts see no Java classes, no Android and no network; the
 * only bridge is the narrow `host`/`ctx` object the callers provide.
 *
 * Every execution runs with a wall-clock deadline enforced through Rhino's
 * instruction observer. Nested executions (a service call made from inside an
 * event handler) inherit the outer budget instead of resetting it.
 */
internal object RhinoSandbox {

    private const val INSTRUCTION_THRESHOLD = 50_000

    private val deadline = ThreadLocal.withInitial<Long?> { null }

    val factory: ContextFactory = object : ContextFactory() {
        override fun makeContext(): Context {
            val context = super.makeContext()
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            context.setClassShutter(ClassShutter { false })
            context.setInstructionObserverThreshold(INSTRUCTION_THRESHOLD)
            return context
        }

        override fun observeInstructionCount(context: Context, instructionCount: Int) {
            val limit = deadline.get() ?: return
            if (System.currentTimeMillis() > limit) {
                throw EvaluatorException("模块脚本执行超时")
            }
        }
    }

    /**
     * Runs [block] with a sandbox context entered on the current thread.
     * Re-entrant calls reuse the already entered context and its deadline.
     */
    fun <T> run(name: String, timeoutMillis: Long, block: (Context) -> T): T {
        val nested = deadline.get() != null
        if (!nested) deadline.set(System.currentTimeMillis() + timeoutMillis)
        val context = factory.enterContext()
        try {
            return block(context)
        } finally {
            Context.exit()
            if (!nested) deadline.remove()
        }
    }

    /** Converts a JS value to a JSON string; plain strings become JSON strings. */
    fun stringify(context: Context, scope: Scriptable, value: Any?): String = when (value) {
        null, is Undefined -> "null"
        is String -> JsonPrimitive(value).toString()
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> NativeJSON.stringify(context, scope, value, null, null)?.toString() ?: "null"
    }
}

/** A Kotlin function exposed to JS as a plain callable value. */
internal class JsHostFunction(
    private val block: (Context, Array<out Any?>) -> Any?,
) : BaseFunction() {
    override fun call(
        cx: Context?,
        scope: Scriptable?,
        thisObj: Scriptable?,
        args: Array<out Any?>,
    ): Any? = block(cx ?: RhinoSandbox.factory.enterContext(), args)
}

internal fun ScriptableObject.jsFunction(name: String, block: (Context, Array<out Any?>) -> Any?) {
    put(name, this, JsHostFunction(block))
}

internal fun Array<out Any?>.stringAt(index: Int): String {
    val value = getOrNull(index)
    return when (value) {
        null, is Undefined -> ""
        else -> value.toString()
    }
}

internal fun Array<out Any?>.intAt(index: Int): Int =
    (getOrNull(index) as? Number)?.toInt() ?: -1

internal fun Array<out Any?>.functionAt(index: Int): Function? =
    getOrNull(index) as? Function
