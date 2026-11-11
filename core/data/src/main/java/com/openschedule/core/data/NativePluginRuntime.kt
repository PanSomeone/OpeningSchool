package com.openschedule.core.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import com.openschedule.plugin.sdk.HostPlugin
import com.openschedule.plugin.sdk.NativeHostEvents
import com.openschedule.plugin.sdk.NativePluginApi
import com.openschedule.plugin.sdk.NativeScreen
import com.openschedule.plugin.sdk.NativeWidget
import com.openschedule.plugin.sdk.PluginHost
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer

/**
 * Loads native plugin APKs in-process and exposes what they contribute.
 *
 * A plugin is trusted code: it runs in the host process with the host's
 * permissions, exactly like a built-in module. Discovery only reads manifests;
 * nothing is instantiated until the user enables the plugin.
 *
 * Lifecycle methods must be called on the main thread; `createView` is invoked
 * from composition, also on the main thread.
 */
class NativePluginRuntime(
    context: Context,
    private val catalog: NativePluginCatalog,
    private val dataStore: PluginDataStore,
    private val hostVersion: String,
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    /** DEX modules installed through the plugin market; null disables that source. */
    private val modules: StateFlow<List<InstalledModule>>? = null,
    /** Relays enable/disable for DEX modules to the module store. */
    private val moduleToggle: ((String, Boolean) -> Unit)? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    enum class Status { Active, Disabled, Error, Incompatible }

    data class ScreenInfo(val id: String, val title: String, val iconKey: String)

    data class WidgetInfo(
        val id: String,
        val title: String,
        val iconKey: String,
        val columns: Int,
        val rows: Int,
    )

    data class PluginInfo(
        val id: String,
        val label: String,
        val version: String,
        val iconKey: String,
        val status: Status,
        val message: String = "",
        val enabled: Boolean = true,
        val source: NativePluginSource = NativePluginSource.APK,
        val screens: List<ScreenInfo> = emptyList(),
        val widgets: List<WidgetInfo> = emptyList(),
    ) {
        /** Home-canvas widget type, stable across restarts. */
        fun widgetType(widgetId: String): String = "native:$id:$widgetId"

        /** Human-readable delivery form, shown in the plugin list. */
        val sourceLabel: String
            get() = when (source) {
                NativePluginSource.DEX_MODULE -> "应用内模块"
                NativePluginSource.APK -> "APK"
            }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loaded = linkedMapOf<String, LoadedPlugin>()

    /** pluginId → channel → handlers, for plugin-emitted and host-emitted events. */
    private val listeners = mutableMapOf<String, MutableMap<String, MutableList<(String) -> Unit>>>()

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    private var descriptors: List<NativePluginDescriptor> = emptyList()

    /** Load failures keyed by plugin id, so concurrent failures are not conflated. */
    private val loadErrors = mutableMapOf<String, String>()

    /** Re-scans installed packages and DEX modules, applying adds, removals and upgrades. */
    fun refresh() {
        val apks = catalog.discover()
        val dexModules = modules?.value.orEmpty()
            .filter { it.manifest.type == ModuleManifest.TYPE_NATIVE }
            .mapNotNull { catalog.describeModule(it) }
        descriptors = (apks + dexModules).sortedBy { it.label.lowercase() }
        val present = descriptors.mapTo(mutableSetOf()) { it.id }
        loaded.keys.toList().filter { it !in present }.forEach { unloadLocked(it) }

        descriptors.forEach { descriptor ->
            val current = loaded[descriptor.id]
            val stale = current != null &&
                (current.descriptor.version != descriptor.version ||
                    current.descriptor.entry != descriptor.entry ||
                    current.descriptor.sourceDir != descriptor.sourceDir)
            when {
                !isEnabled(descriptor.id) -> unloadLocked(descriptor.id)
                !descriptor.isCompatible -> unloadLocked(descriptor.id)
                stale -> {
                    unloadLocked(descriptor.id)
                    loadLocked(descriptor)
                }

                current == null -> loadLocked(descriptor)
            }
        }
        publish()
    }

    /** Starts observing installed DEX modules so enabling one loads it immediately. */
    fun start() {
        if (started || modules == null) return
        started = true
        scope.launch { modules.collect { refresh() } }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val descriptor = descriptors.firstOrNull { it.id == id }
        if (descriptor?.source == NativePluginSource.DEX_MODULE && descriptor.moduleId != null) {
            // The module store owns enabled state; its flow triggers a refresh.
            moduleToggle?.invoke(descriptor.moduleId, enabled)
            return
        }
        prefs.edit().putBoolean(enabledKey(id), enabled).apply()
        if (enabled) {
            if (descriptor != null && descriptor.isCompatible && loaded[id] == null) {
                loadLocked(descriptor)
            }
        } else {
            unloadLocked(id)
        }
        publish()
    }

    fun isEnabled(id: String): Boolean {
        val module = modules?.value?.firstOrNull { it.manifest.id == id }
        if (module != null) return module.enabled
        return prefs.getBoolean(enabledKey(id), true)
    }

    /** Creates a fresh view for one of a plugin's screens; null when unavailable. */
    fun createScreenView(pluginId: String, screenId: String, context: Context): View? {
        val plugin = loaded[pluginId] ?: return null
        val screen = plugin.screens.firstOrNull { it.id == screenId } ?: return null
        return runCatching { screen.createView(plugin.contextFor(context)) }
            .onFailure { error ->
                AppLog.w("NativePlugin", "模块 $pluginId 页面 $screenId 创建失败: ${error.message}")
            }
            .getOrNull()
    }

    /** Creates a fresh view for one of a plugin's widgets; null when unavailable. */
    fun createWidgetView(pluginId: String, widgetId: String, context: Context): View? {
        val plugin = loaded[pluginId] ?: return null
        val widget = plugin.widgets.firstOrNull { it.id == widgetId } ?: return null
        return runCatching { widget.createView(plugin.contextFor(context)) }
            .onFailure { error ->
                AppLog.w("NativePlugin", "模块 $pluginId 组件 $widgetId 创建失败: ${error.message}")
            }
            .getOrNull()
    }

    /** Delivers a host event to every running plugin on the main thread. */
    fun emitHostEvent(channel: String, payloadJson: String = "null") {
        val snapshot = listeners.values.map { it.toMap() }
        if (snapshot.isEmpty()) return
        mainHandler.post {
            snapshot.forEach { channels ->
                channels[channel]?.forEach { handler ->
                    runCatching { handler(payloadJson) }
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
        loaded.keys.toList().forEach { unloadLocked(it) }
        _plugins.value = emptyList()
    }

    private fun loadLocked(descriptor: NativePluginDescriptor) {
        if (!descriptor.isCompatible) return
        loadErrors.remove(descriptor.id)
        val plugin = runCatching { instantiate(descriptor) }
            .onFailure { error ->
                loadErrors[descriptor.id] = error.message ?: "加载失败"
                AppLog.w("NativePlugin", "模块 ${descriptor.id} 加载失败: ${error.message}")
            }
            .getOrNull() ?: return

        loaded[descriptor.id] = plugin
        val host = PluginHostImpl(descriptor)
        plugin.host = host
        runCatching { plugin.plugin.onCreate(host) }
            .onFailure { error ->
                plugin.error = error.message ?: "onCreate 失败"
                AppLog.w("NativePlugin", "模块 ${descriptor.id} onCreate 失败: ${error.message}")
            }
        runCatching {
            plugin.screens = plugin.plugin.screens().take(MAX_SLOTS).filter {
                it.id.isNotBlank() && it.title.isNotBlank()
            }
            plugin.widgets = plugin.plugin.widgets().take(MAX_SLOTS).filter {
                it.id.isNotBlank() && it.title.isNotBlank()
            }
        }.onFailure { error ->
            plugin.error = error.message ?: "声明插槽失败"
            AppLog.w("NativePlugin", "模块 ${descriptor.id} 插槽读取失败: ${error.message}")
        }
        AppLog.i("NativePlugin", "模块 ${descriptor.id} ${descriptor.version} 已启动")
        dispatchLocally(NativeHostEvents.PLUGIN_LOADED, "\"${descriptor.id}\"")
    }

    private fun unloadLocked(id: String) {
        val plugin = loaded.remove(id) ?: return
        (plugin.host as? PluginHostImpl)?.detach()
        runCatching { plugin.plugin.onDestroy() }
            .onFailure { error ->
                AppLog.w("NativePlugin", "模块 $id onDestroy 失败: ${error.message}")
            }
        listeners.remove(id)
        AppLog.i("NativePlugin", "模块 $id 已停止")
    }

    private fun instantiate(descriptor: NativePluginDescriptor): LoadedPlugin {
        val loader = when (descriptor.source) {
            NativePluginSource.APK -> DexClassLoader(
                descriptor.sourceDir,
                catalog.dexDir(descriptor.id).absolutePath,
                descriptor.nativeLibraryDir,
                HostPlugin::class.java.classLoader,
            )

            NativePluginSource.DEX_MODULE -> InMemoryDexClassLoader(
                ByteBuffer.wrap(File(descriptor.sourceDir).readBytes()),
                HostPlugin::class.java.classLoader,
            )
        }
        val instance = NativePluginLoader.instantiate(loader, descriptor.entry)
        val packageContext = if (descriptor.source == NativePluginSource.APK) {
            runCatching {
                appContext.createPackageContext(
                    descriptor.packageName,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            }.getOrElse { appContext }
        } else {
            appContext
        }
        return LoadedPlugin(descriptor, instance, packageContext)
    }

    private fun publish() {
        _plugins.value = descriptors.map { descriptor ->
            val plugin = loaded[descriptor.id]
            val status = when {
                !isEnabled(descriptor.id) -> Status.Disabled
                !descriptor.isCompatible -> Status.Incompatible
                plugin == null || plugin.error != null -> Status.Error
                else -> Status.Active
            }
            PluginInfo(
                id = descriptor.id,
                label = descriptor.label,
                version = descriptor.version,
                iconKey = descriptor.iconKey,
                status = status,
                message = when {
                    status == Status.Incompatible -> "需要插件 API ≤ ${NativePluginApi.VERSION}"
                    status == Status.Error -> plugin?.error ?: loadErrors[descriptor.id].orEmpty()
                    else -> ""
                },
                enabled = isEnabled(descriptor.id),
                source = descriptor.source,
                screens = plugin?.screens.orEmpty()
                    .map { ScreenInfo(it.id, it.title, it.iconKey) },
                widgets = plugin?.widgets.orEmpty()
                    .map { WidgetInfo(it.id, it.title, it.iconKey, it.columns, it.rows) },
            )
        }
    }

    /** Delivers to local handlers only (used for host lifecycle events). */
    private fun dispatchLocally(channel: String, payloadJson: String) {
        listeners.values.forEach { channels ->
            channels[channel]?.forEach { handler -> runCatching { handler(payloadJson) } }
        }
    }

    private fun enabledKey(id: String) = "enabled:$id"

    private inner class PluginHostImpl(
        private val descriptor: NativePluginDescriptor,
    ) : PluginHost {

        private val directory = catalog.dataDir(descriptor.id)
        private var detached = false

        override val pluginId: String get() = descriptor.id

        override val hostVersion: String get() = this@NativePluginRuntime.hostVersion

        override fun dataDir(): File = directory.apply { mkdirs() }

        override fun log(message: String) {
            AppLog.i("NativePlugin/${descriptor.id}", message.take(500))
        }

        override fun getString(key: String, fallback: String?): String? {
            val stored = dataStore.get(descriptor.id, key, fallback ?: "")
            return if (stored.isEmpty() && fallback != null) fallback else stored
        }

        override fun putString(key: String, value: String) {
            dataStore.set(descriptor.id, key, value)
        }

        override fun remove(key: String) {
            dataStore.remove(descriptor.id, key)
        }

        override fun onEvent(channel: String, handler: (payloadJson: String) -> Unit) {
            if (detached || channel.isBlank()) return
            listeners.getOrPut(descriptor.id) { mutableMapOf() }
                .getOrPut(channel) { mutableListOf() } += handler
        }

        override fun emit(channel: String, payloadJson: String) {
            if (detached || channel.isBlank()) return
            val snapshot = listeners
                .filterKeys { it != descriptor.id }
                .map { (_, channels) -> channels.toMap() }
            mainHandler.post {
                snapshot.forEach { channels ->
                    channels[channel]?.forEach { handler ->
                        runCatching { handler(payloadJson) }
                    }
                }
            }
        }

        fun detach() {
            detached = true
            listeners.remove(descriptor.id)
        }
    }

    private class LoadedPlugin(
        val descriptor: NativePluginDescriptor,
        val plugin: HostPlugin,
        private val packageContext: Context,
    ) {
        var host: PluginHost? = null
        var screens: List<NativeScreen> = emptyList()
        var widgets: List<NativeWidget> = emptyList()
        var error: String? = null

        fun contextFor(fallback: Context): Context = packageContext ?: fallback
    }

    private companion object {
        const val MAX_SLOTS = 8
        const val PREFS_NAME = "openschedule_native_plugins"
    }
}
