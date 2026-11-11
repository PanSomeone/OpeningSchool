package com.openschedule.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Validates the packaged native module that ships in the app assets: manifest
 * shape, `.dex` entry presence and dex header. Loading itself needs a real
 * Android runtime, so that part is covered by the device checklist.
 */
class NativeModulePackageTest {

    @Test
    fun bundledNativeModuleIsWellFormed() {
        val packageFile = findSamplePackage()
        assumeTrue("示例原生模块包不存在，跳过", packageFile != null)

        ZipFile(packageFile!!).use { zip ->
            val manifestEntry = zip.getEntry("plugin.json")
            assertEquals("plugin.json 缺失", true, manifestEntry != null)
            val raw = zip.getInputStream(manifestEntry!!).bufferedReader().use { it.readText() }
            val manifest = Json { ignoreUnknownKeys = true }
                .decodeFromString(ModuleManifest.serializer(), raw)

            assertEquals(ModuleManifest.TYPE_NATIVE, manifest.type)
            assertEquals(ModuleManifest.API_V2, manifest.apiVersion)
            assertEquals("classes.dex", manifest.entry)
            assertTrue("entryClass 不合法", manifest.entryClass.contains('.'))
            ModuleManifestValidator.validate(manifest)

            val dexEntry = zip.getEntry(manifest.entry)
            assertEquals("dex 入口缺失", true, dexEntry != null)
            val header = zip.getInputStream(dexEntry!!).use { input ->
                ByteArray(4).also { input.read(it) }
            }
            // "dex\n" magic followed by the dex version digits.
            assertEquals("dex\n", String(header.copyOf(4), Charsets.US_ASCII))
        }
    }

    /** Walks up from the test working directory to find the packaged sample. */
    private fun findSamplePackage(): File? {
        var current = File("").absoluteFile
        repeat(6) {
            val candidate = File(
                current,
                "feature/plugins/src/main/assets/demo-native.osplugin",
            )
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return null
        }
        return null
    }
}
