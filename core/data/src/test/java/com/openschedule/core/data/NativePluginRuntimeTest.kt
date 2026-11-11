package com.openschedule.core.data

import android.view.View
import com.openschedule.plugin.sdk.HostPlugin
import com.openschedule.plugin.sdk.NativeScreen
import com.openschedule.plugin.sdk.NativeWidget
import com.openschedule.plugin.sdk.PluginHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Covers the native plugin contract that can be exercised on the JVM.
 *
 * On a device the runtime hands a `DexClassLoader` over the plugin APK to
 * [NativePluginLoader]; here a plain class loader over the test classpath stands
 * in for it, so entry-class validation and the lifecycle/storage/event contract
 * are still covered without Android.
 */
class NativePluginRuntimeTest {

    /** Stands in for a third-party APK's implementation. */
    class FakePlugin : HostPlugin {
        lateinit var host: PluginHost
        var created = 0
        var destroyed = 0
        val events = mutableListOf<String>()

        override fun onCreate(host: PluginHost) {
            created += 1
            this.host = host
            host.onEvent("timetable.updated") { payload -> events += payload }
        }

        override fun onDestroy() {
            destroyed += 1
        }

        override fun screens(): List<NativeScreen> = listOf(
            NativeScreen("main", "原生页面", "science") { View(it) },
        )

        override fun widgets(): List<NativeWidget> = listOf(
            NativeWidget("counter", "原生计数", "science") { View(it) },
        )
    }

    /** Loads an entry class that does not implement the contract. */
    class NotAPlugin(private val unused: String = "") {
        @Suppress("unused")
        fun marker() = unused
    }

    private fun loader(): ClassLoader = FakePlugin::class.java.classLoader!!

    @Test
    fun loaderInstantiatesPluginEntry() {
        val plugin = NativePluginLoader.instantiate(loader(), FakePlugin::class.java.name)
        assertTrue(plugin is FakePlugin)
    }

    @Test
    fun loaderRejectsClassThatDoesNotImplementHostPlugin() {
        val error = runCatching {
            NativePluginLoader.instantiate(loader(), NotAPlugin::class.java.name)
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("HostPlugin"))
    }

    @Test
    fun loaderRejectsUnknownClass() {
        val error = runCatching {
            NativePluginLoader.instantiate(loader(), "com.example.Missing")
        }.exceptionOrNull()
        assertTrue(error is ClassNotFoundException)
    }

    @Test
    fun lifecycleAndSlotsBehaveLikeAPlugin() {
        val plugin = NativePluginLoader.instantiate(loader(), FakePlugin::class.java.name)
        val host = FakeHost("com.example.demo")
        plugin.onCreate(host)
        assertEquals(1, (plugin as FakePlugin).created)
        assertEquals("main", plugin.screens().single().id)
        assertEquals("counter", plugin.widgets().single().id)
        plugin.onDestroy()
        assertEquals(1, plugin.destroyed)
    }

    @Test
    fun storageIsNamespacedPerPlugin() {
        val store = PluginDataStore(Files.createTempDirectory("store").toFile())
        store.set("com.example.a", "count", "3")
        store.set("com.example.b", "count", "9")
        assertEquals("3", store.get("com.example.a", "count"))
        assertEquals("9", store.get("com.example.b", "count"))
        assertEquals("", store.get("com.example.c", "count"))
    }

    @Test
    fun descriptorRejectsIncompatibleApiVersion() {
        assertTrue(descriptor(apiVersion = 1).isCompatible)
        assertFalse(descriptor(apiVersion = 99).isCompatible)
    }

    @Test
    fun widgetTypeIsStableAndNamespaced() {
        val info = NativePluginRuntime.PluginInfo(
            id = "com.example.a",
            label = "A",
            version = "1.0",
            iconKey = "science",
            status = NativePluginRuntime.Status.Active,
        )
        assertEquals("native:com.example.a:counter", info.widgetType("counter"))
    }

    @Test
    fun disabledPluginHasNoSlots() {
        val info = NativePluginRuntime.PluginInfo(
            id = "com.example.a",
            label = "A",
            version = "1.0",
            iconKey = "science",
            status = NativePluginRuntime.Status.Disabled,
            enabled = false,
        )
        assertTrue(info.screens.isEmpty())
        assertTrue(info.widgets.isEmpty())
    }

    /** Minimal host for contract tests; no Android types are touched. */
    private class FakeHost(override val pluginId: String) : PluginHost {
        val values = mutableMapOf<String, String>()
        val handlers = mutableMapOf<String, (String) -> Unit>()

        override val hostVersion: String = "1.1.0"

        override fun dataDir(): File =
            File(System.getProperty("java.io.tmpdir"), "fake-$pluginId").apply { mkdirs() }

        override fun log(message: String) = Unit

        override fun getString(key: String, fallback: String?): String? =
            values[key] ?: fallback

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun onEvent(channel: String, handler: (payloadJson: String) -> Unit) {
            handlers[channel] = handler
        }

        override fun emit(channel: String, payloadJson: String) = Unit
    }

    private fun descriptor(apiVersion: Int) = NativePluginDescriptor(
        id = "com.example.a",
        packageName = "com.example.a",
        label = "示例",
        version = "1.0",
        iconKey = "science",
        entry = "com.example.a.Plugin",
        apiVersion = apiVersion,
        sourceDir = "/data/app/com.example.a/base.apk",
        nativeLibraryDir = null,
    )
}
