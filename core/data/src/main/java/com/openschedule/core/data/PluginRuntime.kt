package com.openschedule.core.data

import com.openschedule.core.plugin.PluginHomeWidget
import com.openschedule.core.plugin.PluginScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.json.JsonParser
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Host for API v2 "app" modules: a small DSH/VS Code style plugin runtime.
 *
 * A module's `main.js` is evaluated once in the shared Rhino sandbox and its
 * `apply(ctx)` is called with a context object that exposes:
 *
 * - `ctx.log(message)`
 * - `ctx.storage.get/set/remove/clear/keys` — namespaced, capped KV storage
 * - `ctx.events.on(channel, handler)` / `ctx.events.emit(channel, data)`
 * - `ctx.ui.homeWidget(spec)` — declarative home canvas widget
 * - `ctx.services.provide(name, api)` / `ctx.services.call(id, name, method, ...)`
 * - `ctx.dispose(handler)`
 *
 * All JS executes on the IO dispatcher under a single mutex, so plugin state
 * is accessed serially; event delivery and UI action dispatch are asynchronous
 * so handlers never re-enter the host while it holds the lock. Modules are
 * loaded in dependency order ([PluginDependencyGraph]); disabled or removed
 * modules are disposed.
 *
 * API v1 web-importer modules never enter this runtime; they keep using
 * [ModuleRuntime] unchanged.
 */
class PluginRuntime(
    dataDirectory: File,
    modules: StateFlow<List<InstalledModule>>,
) {

    enum class Status { Loading, Active, Blocked, Error }

    data class PluginState(
        val manifest: ModuleManifest,
        val status: Status,
        val message: String = "",
    )

    /** A home widget currently provided by a running plugin. */
    data class ActiveWidget(val pluginId: String, val spec: PluginHomeWidget) {
        /** Stable id used by the home canvas layout. */
        val widgetId: String get() = "module:$pluginId:${spec.id}"
    }

    /** A plugin page (`ctx.ui.screen`) offered by a running plugin. */
    data class ActiveScreen(val pluginId: String, val screen: PluginScreen)

    private val modulesFlow = modules
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dataStore = PluginDataStore(dataDirectory)
    private val instances = linkedMapOf<String, PluginInstance>()
    private val loadErrors = mutableMapOf<String, String>()
    private val deliveries = CopyOnWriteArrayList<Job>()

    private val _states = MutableStateFlow<Map<String, PluginState>>(emptyMap())
    val states: StateFlow<Map<String, PluginState>> = _states.asStateFlow()

    private val _homeWidgets = MutableStateFlow<List<ActiveWidget>>(emptyList())
    val homeWidgets: StateFlow<List<ActiveWidget>> = _homeWidgets.asStateFlow()

    private val _screens = MutableStateFlow<Map<String, ActiveScreen>>(emptyMap())
    val screens: StateFlow<Map<String, ActiveScreen>> = _screens.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            modulesFlow.collect { sync(it) }
        }
    }

    /** Emits a host event to every running plugin, asynchronously. */
    fun emitHostEvent(channel: String, dataJson: String = "null") {
        launchDelivery {
            mutex.withLock {
                instances.values.toList().forEach { instance ->
                    deliverEventLocked(instance, channel, dataJson)
                }
            }
        }
    }

    /** Dispatches a declarative-view action (`action:<name>`) to one plugin. */
    fun dispatchAction(pluginId: String, action: String) {
        if (action.isBlank()) return
        launchDelivery {
            mutex.withLock {
                val instance = instances[pluginId] ?: return@withLock
                deliverEventLocked(instance, "action:$action", "null")
            }
        }
    }

    /** Stops the runtime and cancels pending deliveries. */
    fun shutdown() {
        scope.cancel()
    }

    private suspend fun sync(modules: List<InstalledModule>) {
        // Native DEX modules share apiVersion 2 but run in NativePluginRuntime;
        // their entry is a classes.dex, not a main.js, so skip them here.
        val target = modules.filter {
            it.enabled &&
                it.manifest.apiVersion == ModuleManifest.API_V2 &&
                it.manifest.type != ModuleManifest.TYPE_NATIVE
        }
        mutex.withLock {
            val targetIds = target.mapTo(mutableSetOf()) { it.manifest.id }
            instances.keys.toList()
                .filter { it !in targetIds }
                .forEach { id ->
                    unloadLocked(id)
                    notifyUnloaded(id)
                }
            loadErrors.keys.retainAll(targetIds)

            val resolved = PluginDependencyGraph.resolve(target.map { it.manifest })
            resolved.blocked.keys.forEach { id ->
                instances.remove(id)?.let(::disposeInstance)
                loadErrors.remove(id)
            }
            val newlyLoaded = mutableListOf<ModuleManifest>()
            for (manifest in resolved.order) {
                if (instances.containsKey(manifest.id)) continue
                val module = target.first { it.manifest.id == manifest.id }
                if (loadLocked(module)) {
                    newlyLoaded += manifest
                }
            }

            _states.value = target.associate { module ->
                val manifest = module.manifest
                val state = when {
                    instances.containsKey(manifest.id) ->
                        PluginState(manifest, Status.Active)

                    resolved.blocked.containsKey(manifest.id) ->
                        PluginState(manifest, Status.Blocked, resolved.blocked.getValue(manifest.id))

                    else ->
                        PluginState(
                            manifest,
                            Status.Error,
                            loadErrors[manifest.id] ?: "模块启动失败",
                        )
                }
                manifest.id to state
            }
            publishWidgetsLocked()
            newlyLoaded.forEach { manifest ->
                notifyLoaded(manifest.id)
            }
        }
    }

    /** Returns true when the module ended up running. */
    private fun loadLocked(module: InstalledModule): Boolean {
        val manifest = module.manifest
        val scriptFile = File(module.directory, manifest.entry)
        val script = runCatching { scriptFile.readText() }.getOrNull()
        if (script == null) {
            loadErrors[manifest.id] = "入口脚本 ${manifest.entry} 无法读取"
            AppLog.w("Plugin", "模块 ${manifest.id} 启动失败: 入口脚本缺失")
            return false
        }
        val instance = try {
            applyScript(manifest, script)
        } catch (error: Exception) {
            val message = (error.message ?: "未知错误").take(200)
            loadErrors[manifest.id] = message
            AppLog.w("Plugin", "模块 ${manifest.id} 启动失败: $message")
            return false
        }
        instances[manifest.id] = instance
        loadErrors.remove(manifest.id)
        AppLog.i(
            "Plugin",
            "模块 ${manifest.id} ${manifest.version} 已启动" +
                if (instance.applyFunction == null) "（未导出 apply）" else "",
        )
        return true
    }

    private fun unloadLocked(id: String) {
        val instance = instances.remove(id) ?: return
        disposeInstance(instance)
        publishWidgetsLocked()
        _screens.value = _screens.value - id
        AppLog.i("Plugin", "模块 $id 已停止")
    }

    private fun disposeInstance(instance: PluginInstance) {
        val handlers = instance.disposeHandlers.toList()
        if (handlers.isEmpty()) return
        runCatching {
            RhinoSandbox.run("dispose", HANDLER_TIMEOUT_MILLIS) { context ->
                handlers.forEach { handler ->
                    handler.call(context, instance.scope, instance.scope, emptyArray())
                }
            }
        }.onFailure { error ->
            AppLog.w("Plugin", "模块 ${instance.manifest.id} dispose 失败: ${error.message}")
        }
    }

    private fun applyScript(manifest: ModuleManifest, script: String): PluginInstance {
        val instance = PluginInstance(manifest)
        return RhinoSandbox.run(manifest.entry, APPLY_TIMEOUT_MILLIS) { context ->
            val jsScope = context.initStandardObjects()
            instance.scope = jsScope
            val ctxObject = buildContextObject(context, jsScope, instance)
            ScriptableObject.putProperty(jsScope, "ctx", ctxObject)
            context.evaluateString(jsScope, script, manifest.entry, 1, null)
            instance.applyFunction = jsScope.get("apply", jsScope) as? Function
            instance.applyFunction?.call(context, jsScope, jsScope, arrayOf(ctxObject))
            instance
        }
    }

    private fun buildContextObject(
        context: Context,
        jsScope: Scriptable,
        instance: PluginInstance,
    ): ScriptableObject {
        val manifest = instance.manifest
        val ctx = NativeObject()
        ctx.put("id", ctx, manifest.id)
        ctx.put("version", ctx, manifest.version)

        ctx.jsFunction("log") { _, args ->
            AppLog.i("Plugin/${manifest.id}", args.stringAt(0).take(500))
            null
        }
        ctx.jsFunction("dispose") { _, args ->
            val handler = args.functionAt(0)
                ?: throw EvaluatorException("dispose 需要函数参数")
            instance.disposeHandlers += handler
            null
        }

        val storage = NativeObject()
        storage.jsFunction("get") { _, args ->
            dataStore.get(manifest.id, args.stringAt(0), args.stringAt(1))
        }
        storage.jsFunction("set") { _, args ->
            dataStore.set(manifest.id, args.stringAt(0), args.stringAt(1))
            null
        }
        storage.jsFunction("remove") { _, args ->
            dataStore.remove(manifest.id, args.stringAt(0))
            null
        }
        storage.jsFunction("clear") { _, _ ->
            dataStore.clear(manifest.id)
            null
        }
        storage.jsFunction("keys") { _, _ ->
            json.encodeToString(
                ListSerializer(String.serializer()),
                dataStore.keys(manifest.id),
            )
        }
        ctx.put("storage", ctx, storage)

        val events = NativeObject()
        events.jsFunction("on") { _, args ->
            val channel = args.stringAt(0)
            val handler = args.functionAt(1)
                ?: throw EvaluatorException("events.on 需要函数参数")
            if (channel.isBlank()) throw EvaluatorException("事件名不能为空")
            instance.eventHandlers.getOrPut(channel) { mutableListOf() } += handler
            null
        }
        events.jsFunction("emit") { cx, args ->
            val channel = args.stringAt(0)
            if (channel.isBlank()) throw EvaluatorException("事件名不能为空")
            val payload = when (val data = args.getOrNull(1)) {
                null, is Undefined -> "null"
                else -> RhinoSandbox.stringify(cx, jsScope, data)
            }
            emitAsync(manifest.id, channel, payload)
            null
        }
        ctx.put("events", ctx, events)

        val ui = NativeObject()
        ui.jsFunction("homeWidget") { cx, args ->
            val raw = RhinoSandbox.stringify(cx, jsScope, args.getOrNull(0))
            val spec = runCatching {
                json.decodeFromString(PluginHomeWidget.serializer(), raw)
            }.getOrNull()
                ?: throw EvaluatorException("homeWidget 需要一个组件描述对象")
            val widget = PluginViewValidator.sanitize(spec)
            if (instance.homeWidgets.size >= MAX_WIDGETS_PER_PLUGIN &&
                widget.id !in instance.homeWidgets
            ) {
                throw EvaluatorException("每个模块最多注册 $MAX_WIDGETS_PER_PLUGIN 个组件")
            }
            instance.homeWidgets[widget.id] = widget
            publishWidgetsLocked()
            null
        }
        ui.jsFunction("screen") { cx, args ->
            val raw = RhinoSandbox.stringify(cx, jsScope, args.getOrNull(0))
            val screen = runCatching {
                json.decodeFromString(PluginScreen.serializer(), raw)
            }.getOrNull()
                ?: throw EvaluatorException("screen 需要一个页面描述对象")
            publishScreenLocked(
                instance,
                PluginViewValidator.sanitize(screen),
            )
            null
        }
        ctx.put("ui", ctx, ui)

        val services = NativeObject()
        services.jsFunction("provide") { _, args ->
            val name = args.stringAt(0)
            if (name.isBlank()) throw EvaluatorException("服务名不能为空")
            val api = args.getOrNull(1) as? Scriptable
                ?: throw EvaluatorException("services.provide 需要 API 对象")
            instance.services[name] = api
            null
        }
        services.jsFunction("call") { cx, args ->
            val providerId = args.stringAt(0)
            val name = args.stringAt(1)
            val method = args.stringAt(2)
            val provider = instances[providerId]
                ?: throw EvaluatorException("插件 $providerId 未运行")
            val api = provider.services[name]
                ?: throw EvaluatorException("插件 $providerId 未提供服务 $name")
            val function = api.get(method, api) as? Function
                ?: throw EvaluatorException("服务 $name 没有 $method 方法")
            function.call(cx, provider.scope, provider.scope, args.drop(3).toTypedArray())
        }
        ctx.put("services", ctx, services)
        return ctx
    }

    /**
     * Runs [block] for one plugin's event delivery. Only ever invoked with the
     * mutex held, directly on the caller's thread.
     */
    private fun deliverEventLocked(
        instance: PluginInstance,
        channel: String,
        dataJson: String,
    ) {
        val handlers = instance.eventHandlers[channel].orEmpty()
        if (handlers.isEmpty()) return
        runCatching {
            RhinoSandbox.run("event:$channel", HANDLER_TIMEOUT_MILLIS) { context ->
                val argument = runCatching {
                    JsonParser(context, instance.scope).parseValue(dataJson)
                }.getOrNull()
                handlers.forEach { handler ->
                    handler.call(context, instance.scope, instance.scope, arrayOf(argument))
                }
            }
        }.onFailure { error ->
            AppLog.w(
                "Plugin",
                "模块 ${instance.manifest.id} 事件 $channel 处理失败: ${error.message}",
            )
        }
    }

    private fun publishWidgetsLocked() {
        _homeWidgets.value = instances.values
            .sortedBy { it.manifest.id }
            .flatMap { instance ->
                instance.homeWidgets.values.map { widget ->
                    ActiveWidget(instance.manifest.id, widget)
                }
            }
    }

    private fun publishScreenLocked(instance: PluginInstance, screen: PluginScreen) {
        instance.screen = screen
        _screens.value = _screens.value +
            (instance.manifest.id to ActiveScreen(instance.manifest.id, screen))
    }

    private fun emitAsync(sourceId: String, channel: String, payload: String) {
        launchDelivery {
            mutex.withLock {
                instances.values
                    .toList()
                    .filter { it.manifest.id != sourceId }
                    .forEach { instance ->
                        deliverEventLocked(instance, channel, payload)
                    }
            }
        }
    }

    private fun notifyLoaded(pluginId: String) {
        launchDelivery {
            mutex.withLock {
                val payload = json.encodeToString(pluginId)
                instances.values
                    .toList()
                    .filter { it.manifest.id != pluginId }
                    .forEach { instance ->
                        deliverEventLocked(instance, "plugin.loaded", payload)
                    }
            }
        }
    }

    private fun notifyUnloaded(pluginId: String) {
        launchDelivery {
            mutex.withLock {
                val payload = json.encodeToString(pluginId)
                instances.values.toList().forEach { instance ->
                    deliverEventLocked(instance, "plugin.unloaded", payload)
                }
            }
        }
    }

    private fun launchDelivery(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        deliveries += job
        job.invokeOnCompletion { deliveries -= job }
    }

    /** Test hook: waits until the current sync and queued deliveries finish. */
    internal suspend fun awaitIdle() {
        repeat(4) {
            mutex.withLock { }
            deliveries.toList().joinAll()
        }
    }

    /** Test hook: current widgets/states for assertions after [awaitIdle]. */
    internal suspend fun waitForFirstSync() {
        mutex.withLock { }
    }

    private class PluginInstance(val manifest: ModuleManifest) {
        lateinit var scope: Scriptable
        var applyFunction: Function? = null
        val homeWidgets = linkedMapOf<String, PluginHomeWidget>()
        var screen: PluginScreen? = null
        val eventHandlers = mutableMapOf<String, MutableList<Function>>()
        val disposeHandlers = mutableListOf<Function>()
        val services = mutableMapOf<String, Scriptable>()
    }

    private companion object {
        const val APPLY_TIMEOUT_MILLIS = 6_000L
        const val HANDLER_TIMEOUT_MILLIS = 1_500L
        const val MAX_WIDGETS_PER_PLUGIN = 4
    }
}
