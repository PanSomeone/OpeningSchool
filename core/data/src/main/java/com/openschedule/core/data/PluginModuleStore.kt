package com.openschedule.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

@Serializable
data class ModuleManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val apiVersion: Int = 1,
    val type: String = TYPE_WEB_IMPORTER,
    val entry: String = "main.js",
    val startUrl: String = "",
    val iconKey: String = "extension",
    val author: String = "",
) {
    companion object {
        const val TYPE_WEB_IMPORTER = "web-importer"
        const val SUPPORTED_API = 1
    }
}

data class InstalledModule(
    val manifest: ModuleManifest,
    val directory: File,
    val enabled: Boolean,
)

/**
 * Installs signed plugin packages into app storage. A module is a zip with a
 * `plugin.json` manifest and assets; nothing is executed during install.
 */
class PluginModuleStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, "modules")

    private val _modules = MutableStateFlow(loadModules())
    val modules: StateFlow<List<InstalledModule>> = _modules.asStateFlow()

    fun enabledWebImporters(): List<InstalledModule> = _modules.value.filter {
        it.enabled &&
            it.manifest.type == ModuleManifest.TYPE_WEB_IMPORTER &&
            it.manifest.startUrl.isNotBlank()
    }

    suspend fun install(
        zipFile: File,
        expectedSha256: String = "",
    ): Result<InstalledModule> = withContext(Dispatchers.IO) {
        runCatching {
            if (expectedSha256.isNotBlank()) {
                val actual = sha256(zipFile)
                check(actual.equals(expectedSha256, ignoreCase = true)) {
                    "模块包 SHA-256 校验失败"
                }
            }
            root.mkdirs()
            val temp = File(root, ".install-${System.currentTimeMillis()}")
            temp.mkdirs()
            try {
                val manifest = extract(zipFile, temp)
                val target = File(root, manifest.id)
                if (target.exists() && !target.deleteRecursively()) {
                    error("无法替换旧模块")
                }
                if (!temp.renameTo(target)) error("无法保存模块")
                enableInPrefs(manifest.id, true)
                val installed = InstalledModule(manifest, target, enabled = true)
                _modules.value = (loadModules() + installed)
                    .distinctBy { it.manifest.id }
                    .sortedBy { it.manifest.name }
                AppLog.i("Module", "已安装模块 ${manifest.id} ${manifest.version}")
                installed
            } finally {
                if (temp.exists()) temp.deleteRecursively()
            }
        }.onFailure { error ->
            AppLog.w("Module", "模块安装失败: ${error.message}")
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        enableInPrefs(id, enabled)
        _modules.value = _modules.value.map {
            if (it.manifest.id == id) it.copy(enabled = enabled) else it
        }
    }

    fun remove(id: String) {
        val module = _modules.value.firstOrNull { it.manifest.id == id } ?: return
        module.directory.deleteRecursively()
        enableInPrefs(id, false)
        _modules.value = _modules.value.filterNot { it.manifest.id == id }
        AppLog.i("Module", "已移除模块 $id")
    }

    private fun loadModules(): List<InstalledModule> {
        val enabledIds = prefs.getStringSet(KEY_ENABLED, null)
        return root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { dir ->
                val manifestFile = File(dir, "plugin.json")
                if (!manifestFile.isFile) return@mapNotNull null
                val manifest = runCatching {
                    json.decodeFromString(
                        ModuleManifest.serializer(),
                        manifestFile.readText(),
                    )
                }.getOrNull() ?: return@mapNotNull null
                if (!isValid(manifest)) return@mapNotNull null
                InstalledModule(
                    manifest = manifest,
                    directory = dir,
                    enabled = enabledIds?.contains(manifest.id) ?: true,
                )
            }
            .sortedBy { it.manifest.name }
    }

    private fun extract(zipFile: File, target: File): ModuleManifest {
        ZipFile(zipFile).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_NAME)
                ?: error("模块缺少 plugin.json")
            check(manifestEntry.size <= MAX_MANIFEST_BYTES) { "plugin.json 过大" }
            val manifestRaw = zip.getInputStream(manifestEntry)
                .bufferedReader()
                .use { it.readText() }
            val manifest = runCatching {
                json.decodeFromString(ModuleManifest.serializer(), manifestRaw)
            }.getOrElse { error("plugin.json 格式错误: ${it.message}") }
            validate(manifest)
            check(zip.getEntry(manifest.entry) != null) { "模块入口 ${manifest.entry} 不存在" }

            var total = 0L
            var count = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (++count > MAX_FILES) error("模块文件过多")
                val output = File(target, entry.name)
                val canonicalTarget = target.canonicalPath + File.separator
                check(output.canonicalPath.startsWith(canonicalTarget)) {
                    "模块包含非法路径"
                }
                output.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { stream ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= MAX_MODULE_BYTES) { "模块解压后过大" }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
            }
            return manifest
        }
    }

    private fun validate(manifest: ModuleManifest) {
        check(ID_PATTERN.matches(manifest.id)) { "模块 id 不合法" }
        check(manifest.name.isNotBlank()) { "模块名称不能为空" }
        check(manifest.apiVersion == ModuleManifest.SUPPORTED_API) {
            "模块 API ${manifest.apiVersion} 不受支持"
        }
        check(manifest.type == ModuleManifest.TYPE_WEB_IMPORTER) {
            "暂不支持模块类型 ${manifest.type}"
        }
        check(
            manifest.startUrl.startsWith("https://") ||
                manifest.startUrl.startsWith("http://"),
        ) { "模块 startUrl 必须是 http(s) 地址" }
        check(!manifest.entry.startsWith("/") && !manifest.entry.contains("..")) {
            "模块入口路径不合法"
        }
    }

    private fun isValid(manifest: ModuleManifest): Boolean = runCatching {
        validate(manifest)
    }.isSuccess

    private fun enableInPrefs(id: String, enabled: Boolean) {
        val current = prefs.getStringSet(KEY_ENABLED, null)?.toMutableSet()
            ?: _modules.value.mapTo(mutableSetOf()) { it.manifest.id }
        if (enabled) current += id else current -= id
        prefs.edit().putStringSet(KEY_ENABLED, current).apply()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val PREFS_NAME = "openschedule_modules"
        const val KEY_ENABLED = "enabled_ids"
        const val MANIFEST_NAME = "plugin.json"
        const val MAX_MANIFEST_BYTES = 64L * 1024
        const val MAX_MODULE_BYTES = 8L * 1024 * 1024
        const val MAX_FILES = 200
        val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
