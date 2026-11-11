package com.openschedule.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Guards the published 校历 plugin package (dist/hzau.calendar-*.osplugin):
 * manifest must satisfy the host validator and the dex must really contain the
 * declared entry class.
 */
class HzauCalendarPackageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun publishedCalendarPluginMatchesHostRules() {
        val packageFile = findPackage()
        assumeTrue("未找到校历插件包，跳过", packageFile != null)

        ZipFile(packageFile).use { zip ->
            val manifestEntry = zip.getEntry("plugin.json")
            requireNotNull(manifestEntry) { "缺少 plugin.json" }
            val manifest = json.decodeFromString(
                ModuleManifest.serializer(),
                zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() },
            )
            ModuleManifestValidator.validate(manifest)

            assertEquals(ModuleManifest.TYPE_NATIVE, manifest.type)
            assertEquals("hzau.calendar", manifest.id)
            assertEquals("classes.dex", manifest.entry)
            assertTrue(manifest.entryClass.endsWith(".CalendarPlugin"))

            val dexEntry = zip.getEntry(manifest.entry)
            requireNotNull(dexEntry) { "缺少 ${manifest.entry}" }
            val dex = zip.getInputStream(dexEntry).use { it.readBytes() }
            assertEquals("dex\n", String(dex.copyOf(4), Charsets.US_ASCII))

            val dexText = String(dex, Charsets.ISO_8859_1)
            assertTrue(
                "dex 内没有入口类",
                dexText.contains(manifest.entryClass.replace('.', '/')),
            )
        }
    }

    private fun findPackage(): File? {
        var current = File("").absoluteFile
        repeat(6) {
            val direct = File(current, "dist/hzau.calendar-1.0.0.osplugin")
            if (direct.isFile) return direct
            current = current.parentFile ?: return null
        }
        return null
    }
}
