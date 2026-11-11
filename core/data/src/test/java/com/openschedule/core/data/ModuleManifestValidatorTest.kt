package com.openschedule.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleManifestValidatorTest {

    @Test
    fun acceptsV1WebImporter() {
        val manifest = ModuleManifest(
            id = "demo.web-importer",
            name = "示例导入",
            startUrl = "https://example.com",
        )
        ModuleManifestValidator.validate(manifest)
    }

    @Test
    fun acceptsV2AppModule() {
        val manifest = ModuleManifest(
            id = "demo.app",
            name = "示例应用",
            apiVersion = ModuleManifest.API_V2,
            type = ModuleManifest.TYPE_APP,
            requires = listOf("demo.other"),
        )
        ModuleManifestValidator.validate(manifest)
    }

    @Test
    fun rejectsUnknownApiVersion() {
        val manifest = ModuleManifest(id = "demo.bad", name = "x", apiVersion = 3)
        val error = runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()
        assertEquals("当前应用版本不支持 API 3，请更新应用", error?.message)
    }

    @Test
    fun rejectsAppTypeOnV1() {
        val manifest = ModuleManifest(
            id = "demo.bad",
            name = "x",
            apiVersion = ModuleManifest.API_V1,
            type = ModuleManifest.TYPE_APP,
        )
        val error = runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()
        assertEquals("当前应用版本不支持模块类型 app，请更新应用", error?.message)
    }

    @Test
    fun rejectsImporterWithoutStartUrl() {
        val manifest = ModuleManifest(id = "demo.bad", name = "x", startUrl = "ftp://x")
        val error = runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()
        assertEquals("模块 startUrl 必须是 http(s) 地址", error?.message)
    }

    @Test
    fun rejectsSelfAndDuplicateDependencies() {
        val selfDep = ModuleManifest(
            id = "demo.bad",
            name = "x",
            apiVersion = 2,
            type = ModuleManifest.TYPE_APP,
            requires = listOf("demo.bad"),
        )
        assertEquals(
            "模块不能依赖自己",
            runCatching { ModuleManifestValidator.validate(selfDep) }.exceptionOrNull()?.message,
        )
        val duplicated = ModuleManifest(
            id = "demo.bad",
            name = "x",
            apiVersion = 2,
            type = ModuleManifest.TYPE_APP,
            requires = listOf("demo.a", "demo.a"),
        )
        assertEquals(
            "模块依赖重复",
            runCatching { ModuleManifestValidator.validate(duplicated) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun rejectsBadEntryPath() {
        val manifest = ModuleManifest(
            id = "demo.bad",
            name = "x",
            apiVersion = 2,
            type = ModuleManifest.TYPE_APP,
            entry = "../escape.js",
        )
        val error = runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()
        assertEquals("模块入口路径不合法", error?.message)
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun acceptsNativeModuleWithDexEntry() {
        ModuleManifestValidator.validate(
            ModuleManifest(
                id = "demo.native",
                name = "原生插件",
                apiVersion = ModuleManifest.API_V2,
                type = ModuleManifest.TYPE_NATIVE,
                entry = "classes.dex",
                entryClass = "com.example.Plugin",
            ),
        )
    }

    @Test
    fun rejectsNativeModuleWithoutEntryClass() {
        val manifest = ModuleManifest(
            id = "demo.native",
            name = "原生插件",
            apiVersion = ModuleManifest.API_V2,
            type = ModuleManifest.TYPE_NATIVE,
            entry = "classes.dex",
        )
        assertEquals(
            "原生模块必须声明合法的 entryClass",
            runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun rejectsNativeModuleWithNonDexEntry() {
        val manifest = ModuleManifest(
            id = "demo.native",
            name = "原生插件",
            apiVersion = ModuleManifest.API_V2,
            type = ModuleManifest.TYPE_NATIVE,
            entry = "main.js",
            entryClass = "com.example.Plugin",
        )
        assertEquals(
            "原生模块 entry 必须指向 .dex",
            runCatching { ModuleManifestValidator.validate(manifest) }.exceptionOrNull()?.message,
        )
    }
}
