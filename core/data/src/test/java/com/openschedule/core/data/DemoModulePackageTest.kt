package com.openschedule.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Loads the shipped `demo-app.osplugin` exactly as the app would: unzip it,
 * read its manifest and run `main.js` through [PluginRuntime]. This keeps the
 * sample in `feature/plugins/src/main/assets` honest instead of letting it rot.
 */
class DemoModulePackageTest {

    @Test
    fun bundledSampleRunsAndRegistersWidgetAndScreen() = runBlocking {
        val packageFile = findSamplePackage()
        assumeTrue("示例模块包不存在，跳过", packageFile != null)
        val dir = Files.createTempDirectory("demo-app").toFile()
        try {
            ZipFile(packageFile!!).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    val output = File(dir, entry.name)
                    output.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { target -> input.copyTo(target) }
                    }
                }
            }
            val manifest = json.decodeFromString(
                ModuleManifest.serializer(),
                File(dir, "plugin.json").readText().removePrefix("\uFEFF"),
            )
            ModuleManifestValidator.validate(manifest)
            assertEquals(ModuleManifest.API_V2, manifest.apiVersion)
            assertEquals(ModuleManifest.TYPE_APP, manifest.type)

            val module = InstalledModule(manifest, dir, enabled = true)
            val dataDir = File(dir, "data")
            val runtime = PluginRuntime(dataDir, MutableStateFlow(listOf(module)))
            try {
                runtime.start()
                runtime.awaitActive(manifest.id)

                val widget = runtime.homeWidgets.value.firstOrNull {
                    it.pluginId == manifest.id
                }
                assertNotNull("示例模块应注册主页组件", widget)
                assertEquals("module:demo.app:counter", widget!!.widgetId)

                val screen = runtime.screens.value[manifest.id]
                assertNotNull("示例模块应注册页面", screen)
                assertEquals("计数器", screen!!.screen.title)

                runtime.dispatchAction(manifest.id, "inc")
                runtime.awaitIdle()

                val afterTap = runtime.screens.value.getValue(manifest.id).screen
                val counterText = afterTap.view.children.first().children[1].text
                assertEquals("1", counterText)
            } finally {
                runtime.shutdown()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun PluginRuntime.awaitActive(pluginId: String) {
        withTimeout(5_000) {
            states.first { it[pluginId]?.status == PluginRuntime.Status.Active }
        }
    }

    /** Walks up from the test working directory to find the packaged sample. */
    private fun findSamplePackage(): File? {
        var current = File("").absoluteFile
        repeat(6) {
            val candidate = File(
                current,
                "feature/plugins/src/main/assets/demo-app.osplugin",
            )
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return null
        }
        return null
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
}
