package com.openschedule.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginRuntimeTest {

    private class Harness {
        val root = Files.createTempDirectory("plugin-runtime-test").toFile()
        val dataDir = File(root, "plugin-data")
        val modules = MutableStateFlow<List<InstalledModule>>(emptyList())

        fun install(id: String, script: String, requires: List<String> = emptyList()): InstalledModule {
            val directory = File(root, id).apply { mkdirs() }
            File(directory, "main.js").writeText(script)
            val manifest = ModuleManifest(
                id = id,
                name = id,
                apiVersion = ModuleManifest.API_V2,
                type = ModuleManifest.TYPE_APP,
                requires = requires,
            )
            return InstalledModule(manifest, directory, enabled = true)
        }

        fun v1Module(id: String): InstalledModule {
            val directory = File(root, id).apply { mkdirs() }
            File(directory, "main.js").writeText("function parse() {}")
            return InstalledModule(
                ModuleManifest(id = id, name = id, startUrl = "https://example.com"),
                directory,
                enabled = true,
            )
        }

        /** A DEX module: its entry is `classes.dex`, not JavaScript. */
        fun nativeModule(id: String): InstalledModule {
            val directory = File(root, id).apply { mkdirs() }
            File(directory, "classes.dex").writeBytes(byteArrayOf(0x64, 0x65, 0x78, 0x0a))
            return InstalledModule(
                ModuleManifest(
                    id = id,
                    name = id,
                    apiVersion = ModuleManifest.API_V2,
                    type = ModuleManifest.TYPE_NATIVE,
                    entry = "classes.dex",
                    entryClass = "$id.Plugin",
                ),
                directory,
                enabled = true,
            )
        }

        fun cleanup() {
            root.deleteRecursively()
        }
    }

    private suspend fun PluginRuntime.awaitState(
        pluginId: String,
        status: PluginRuntime.Status,
    ) {
        withTimeout(5_000) {
            states.first { it[pluginId]?.status == status }
        }
    }

    @Test
    fun appliesModuleAndPublishesHomeWidget() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val module = harness.install(
                "demo.counter",
                """
                function apply(ctx) {
                  ctx.ui.homeWidget({
                    id: "counter",
                    title: "计数器",
                    iconKey: "star",
                    columns: 1,
                    rows: 1,
                    view: { type: "column", children: [
                      { type: "text", text: "次数", size: "small", emphasis: "secondary" },
                      { type: "text", text: "0", size: "title", emphasis: "primary" },
                      { type: "button", text: "＋1", action: "inc" }
                    ]}
                  });
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.counter", PluginRuntime.Status.Active)

            val widgets = runtime.homeWidgets.value
            assertEquals(1, widgets.size)
            assertEquals("module:demo.counter:counter", widgets.first().widgetId)
            assertEquals("计数器", widgets.first().spec.title)
            assertEquals("column", widgets.first().spec.view.type)
            assertEquals(3, widgets.first().spec.view.children.size)
            assertEquals("button", widgets.first().spec.view.children[2].type)
            assertEquals("inc", widgets.first().spec.view.children[2].action)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun actionUpdatesStorageAndWidget() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val module = harness.install(
                "demo.counter",
                """
                function apply(ctx) {
                  var count = parseInt(ctx.storage.get("count", "0"), 10);
                  function render() {
                    ctx.ui.homeWidget({
                      id: "counter",
                      view: { type: "text", text: String(count) }
                    });
                  }
                  ctx.events.on("action:inc", function () {
                    count += 1;
                    ctx.storage.set("count", String(count));
                    render();
                  });
                  render();
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.counter", PluginRuntime.Status.Active)
            assertEquals("0", runtime.homeWidgets.value.first().spec.view.text)

            runtime.dispatchAction("demo.counter", "inc")
            runtime.awaitIdle()
            assertEquals("1", runtime.homeWidgets.value.first().spec.view.text)

            runtime.dispatchAction("demo.counter", "inc")
            runtime.awaitIdle()
            assertEquals("2", runtime.homeWidgets.value.first().spec.view.text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun storageSurvivesReload() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val script = """
                function apply(ctx) {
                  var count = parseInt(ctx.storage.get("count", "0"), 10);
                  ctx.events.on("action:inc", function () {
                    count += 1;
                    ctx.storage.set("count", String(count));
                    ctx.ui.homeWidget({ id: "w", view: { type: "text", text: String(count) } });
                  });
                  ctx.events.on("action:bye", function () {
                    ctx.storage.set("bye", "1");
                  });
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: String(count) } });
                }
                """.trimIndent()
            var module = harness.install("demo.persist", script)
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.persist", PluginRuntime.Status.Active)

            runtime.dispatchAction("demo.persist", "inc")
            runtime.awaitIdle()
            assertEquals("1", runtime.homeWidgets.value.first().spec.view.text)

            // Disable: the instance is disposed and disappears.
            harness.modules.value = emptyList()
            withTimeout(5_000) {
                runtime.states.first { it.isEmpty() }
            }
            assertTrue(runtime.homeWidgets.value.isEmpty())

            // Re-enable: storage still holds the counter.
            module = harness.install("demo.persist", script)
            harness.modules.value = listOf(module)
            runtime.awaitState("demo.persist", PluginRuntime.Status.Active)
            assertEquals("1", runtime.homeWidgets.value.first().spec.view.text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun hostEventDeliversParsedPayload() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val module = harness.install(
                "demo.listener",
                """
                function apply(ctx) {
                  ctx.events.on("timetable.updated", function (data) {
                    var week = data && data.week ? data.week : "none";
                    ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "week:" + week } });
                  });
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "init" } });
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.listener", PluginRuntime.Status.Active)

            runtime.emitHostEvent("timetable.updated", """{"week":8}""")
            runtime.awaitIdle()
            assertEquals("week:8", runtime.homeWidgets.value.first().spec.view.text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun pluginEmitsEventToOtherPluginsOnly() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val sender = harness.install(
                "demo.sender",
                """
                function apply(ctx) {
                  ctx.events.on("tick", function () {
                    ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "self-tick" } });
                  });
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "sender" } });
                  ctx.events.emit("tick", { from: "sender" });
                }
                """.trimIndent(),
            )
            val receiver = harness.install(
                "demo.receiver",
                """
                function apply(ctx) {
                  ctx.events.on("tick", function (data) {
                    ctx.ui.homeWidget({
                      id: "w",
                      view: { type: "text", text: "from:" + data.from }
                    });
                  });
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "receiver" } });
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(sender, receiver)
            runtime.start()
            runtime.awaitState("demo.sender", PluginRuntime.Status.Active)
            runtime.awaitState("demo.receiver", PluginRuntime.Status.Active)
            runtime.awaitIdle()

            val widgets = runtime.homeWidgets.value.associateBy { it.pluginId }
            // The receiver got the event; the sender did not see its own emit.
            assertEquals("from:sender", widgets.getValue("demo.receiver").spec.view.text)
            assertEquals("sender", widgets.getValue("demo.sender").spec.view.text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun loadsDependenciesFirstAndSupportsServiceCalls() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val provider = harness.install(
                "demo.provider",
                """
                function apply(ctx) {
                  ctx.services.provide("math", {
                    add: function (a, b) { return a + b; },
                    greet: function (name) { return "hi " + name; }
                  });
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "provider" } });
                }
                """.trimIndent(),
            )
            val consumer = harness.install(
                "demo.consumer",
                """
                function apply(ctx) {
                  var sum = ctx.services.call("demo.provider", "math", "add", 20, 22);
                  ctx.ui.homeWidget({
                    id: "w",
                    view: { type: "text", text: String(sum) }
                  });
                }
                """.trimIndent(),
                requires = listOf("demo.provider"),
            )
            harness.modules.value = listOf(consumer, provider)
            runtime.start()
            runtime.awaitState("demo.provider", PluginRuntime.Status.Active)
            runtime.awaitState("demo.consumer", PluginRuntime.Status.Active)

            val widgets = runtime.homeWidgets.value.associateBy { it.pluginId }
            assertEquals("42", widgets.getValue("demo.consumer").spec.view.text)

            // Removing the provider blocks the consumer on the next sync.
            harness.modules.value = listOf(consumer)
            runtime.awaitState("demo.consumer", PluginRuntime.Status.Blocked)
            assertEquals("缺少依赖 demo.provider", runtime.states.value.getValue("demo.consumer").message)
            assertTrue(runtime.homeWidgets.value.none { it.pluginId == "demo.consumer" })
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun failingModuleReportsError() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val module = harness.install(
                "demo.broken",
                "function apply(ctx) { throw new Error('boom'); }",
            )
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.broken", PluginRuntime.Status.Error)
            assertTrue(
                runtime.states.value.getValue("demo.broken").message.contains("boom"),
            )
            assertTrue(runtime.homeWidgets.value.isEmpty())
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun ignoresNativeModules() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            // Native DEX modules are owned by NativePluginRuntime; the JS host
            // must not try to evaluate their classes.dex as a script.
            val native = harness.nativeModule("demo.native")
            val app = harness.install(
                "demo.app",
                """
                function apply(ctx) {
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "app" } });
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(native, app)
            runtime.start()
            runtime.awaitState("demo.app", PluginRuntime.Status.Active)
            runtime.awaitIdle()

            assertNull(runtime.states.value["demo.native"])
            assertTrue(runtime.homeWidgets.value.none { it.pluginId == "demo.native" })
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun ignoresV1Modules() = runBlocking {        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val v1 = harness.v1Module("demo.importer")
            val v2 = harness.install(
                "demo.app",
                """
                function apply(ctx) {
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: "app" } });
                }
                """.trimIndent(),
            )
            harness.modules.value = listOf(v1, v2)
            runtime.start()
            runtime.awaitState("demo.app", PluginRuntime.Status.Active)
            runtime.awaitIdle()

            assertNull(runtime.states.value["demo.importer"])
            assertTrue(runtime.homeWidgets.value.none { it.pluginId == "demo.importer" })
            assertEquals(1, runtime.homeWidgets.value.size)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun screenSlotRegistersAndUpdatesOnAction() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val script = """
                var taps = 0;
                function render(ctx) {
                  ctx.ui.screen({
                    title: "计数页",
                    iconKey: "star",
                    view: {
                      type: "column",
                      children: [
                        { type: "text", text: "点击 " + taps, size: "title" },
                        { type: "button", text: "加一", action: "inc" }
                      ]
                    }
                  });
                }
                function apply(ctx) {
                  render(ctx);
                  ctx.events.on("action:inc", function () {
                    taps++;
                    render(ctx);
                  });
                }
                """.trimIndent()
            harness.modules.value = listOf(harness.install("demo.page", script))
            runtime.start()
            runtime.awaitState("demo.page", PluginRuntime.Status.Active)

            val screen = runtime.screens.value.getValue("demo.page").screen
            assertEquals("计数页", screen.title)
            assertEquals("点击 0", screen.view.children.first().text)

            runtime.dispatchAction("demo.page", "inc")
            runtime.awaitIdle()

            val updated = runtime.screens.value.getValue("demo.page").screen
            assertEquals("点击 1", updated.view.children.first().text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun screenIsRemovedWhenPluginUnloads() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val script = """
                function apply(ctx) {
                  ctx.ui.screen({ title: "页", view: { type: "text", text: "hi" } });
                }
                """.trimIndent()
            harness.modules.value = listOf(harness.install("demo.page2", script))
            runtime.start()
            runtime.awaitState("demo.page2", PluginRuntime.Status.Active)
            assertTrue(runtime.screens.value.containsKey("demo.page2"))

            harness.modules.value = emptyList()
            withTimeout(5_000) { runtime.states.first { it.isEmpty() } }
            runtime.awaitIdle()
            assertFalse(runtime.screens.value.containsKey("demo.page2"))
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }

    @Test
    fun disposeHandlerRunsOnUnload() = runBlocking {
        val harness = Harness()
        val runtime = PluginRuntime(harness.dataDir, harness.modules)
        try {
            val script = """
                function apply(ctx) {
                  var flag = ctx.storage.get("disposed", "never");
                  ctx.ui.homeWidget({ id: "w", view: { type: "text", text: flag } });
                  ctx.storage.set("disposed", "0");
                  ctx.dispose(function () {
                    ctx.storage.set("disposed", "1");
                  });
                }
                """.trimIndent()
            var module = harness.install("demo.cleanup", script)
            harness.modules.value = listOf(module)
            runtime.start()
            runtime.awaitState("demo.cleanup", PluginRuntime.Status.Active)
            assertEquals("never", runtime.homeWidgets.value.first().spec.view.text)

            harness.modules.value = emptyList()
            withTimeout(5_000) {
                runtime.states.first { it.isEmpty() }
            }

            module = harness.install("demo.cleanup", script)
            harness.modules.value = listOf(module)
            runtime.awaitState("demo.cleanup", PluginRuntime.Status.Active)
            // "1" proves the dispose hook ran and persisted before the reload.
            assertEquals("1", runtime.homeWidgets.value.first().spec.view.text)
        } finally {
            runtime.shutdown()
            harness.cleanup()
        }
    }
}
