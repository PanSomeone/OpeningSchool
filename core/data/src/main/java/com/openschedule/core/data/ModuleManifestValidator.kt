package com.openschedule.core.data

/**
 * Pure manifest validation shared by install and load. Kept free of Android
 * types so it can be unit tested directly.
 *
 * API v1: `web-importer` modules with a `parse()` entry, unchanged.
 * API v2: `app` modules with an `apply(ctx)` lifecycle, dependency tree and
 * declarative UI slots.
 */
object ModuleManifestValidator {

    val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
    val ENTRY_CLASS_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")

    fun validate(manifest: ModuleManifest) {
        check(ID_PATTERN.matches(manifest.id)) { "模块 id 不合法" }
        check(manifest.name.isNotBlank()) { "模块名称不能为空" }
        when (manifest.apiVersion) {
            ModuleManifest.API_V1 ->
                check(manifest.type == ModuleManifest.TYPE_WEB_IMPORTER) {
                    "当前应用版本不支持模块类型 ${manifest.type}，请更新应用"
                }

            ModuleManifest.API_V2 ->
                check(
                    manifest.type == ModuleManifest.TYPE_APP ||
                        manifest.type == ModuleManifest.TYPE_NATIVE,
                ) { "当前应用版本不支持模块类型 ${manifest.type}，请更新应用" }

            else -> check(false) {
                "当前应用版本不支持 API ${manifest.apiVersion}，请更新应用"
            }
        }
        if (manifest.type == ModuleManifest.TYPE_NATIVE) {
            check(ENTRY_CLASS_PATTERN.matches(manifest.entryClass)) {
                "原生模块必须声明合法的 entryClass"
            }
            check(manifest.entry.lowercase().endsWith(".dex")) {
                "原生模块 entry 必须指向 .dex"
            }
        }
        if (manifest.type == ModuleManifest.TYPE_WEB_IMPORTER) {
            check(
                manifest.startUrl.startsWith("https://") ||
                    manifest.startUrl.startsWith("http://"),
            ) { "模块 startUrl 必须是 http(s) 地址" }
        }
        check(!manifest.entry.startsWith("/") && !manifest.entry.contains("..")) {
            "模块入口路径不合法"
        }
        check(manifest.requires.size == manifest.requires.toSet().size) {
            "模块依赖重复"
        }
        manifest.requires.forEach { dependency ->
            check(ID_PATTERN.matches(dependency)) { "依赖 id 不合法: $dependency" }
            check(dependency != manifest.id) { "模块不能依赖自己" }
        }
    }
}
