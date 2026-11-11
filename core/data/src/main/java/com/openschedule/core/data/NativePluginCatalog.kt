package com.openschedule.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.openschedule.plugin.sdk.NativePluginApi
import java.io.File

enum class NativePluginSource { APK, DEX_MODULE }

/** One installable native plugin: an APK package or a packaged `.dex` module. */
data class NativePluginDescriptor(
    /** Plugin id: package name for APKs, module id for DEX modules. */
    val id: String,
    val packageName: String,
    val label: String,
    val version: String,
    val iconKey: String,
    /** Class implementing `HostPlugin`. */
    val entry: String,
    val apiVersion: Int,
    /** APK path, or the `.dex` file path for DEX modules. */
    val sourceDir: String,
    val nativeLibraryDir: String?,
    val source: NativePluginSource = NativePluginSource.APK,
    /** Set for DEX modules so the host can relay enable/disable to the module store. */
    val moduleId: String? = null,
) {
    val isCompatible: Boolean get() = apiVersion in 1..NativePluginApi.VERSION
}

/**
 * Finds installed native plugins.
 *
 * Discovery uses the marker action declared in the host `<queries>` block, so no
 * `QUERY_ALL_PACKAGES` permission is needed. Everything advertised by the plugin
 * manifest is treated as untrusted input: ids, entry class names and API
 * versions are validated before anything is loaded.
 */
class NativePluginCatalog(context: Context) {

    private val appContext = context.applicationContext

    fun discover(): List<NativePluginDescriptor> = runCatching {
        val manager = appContext.packageManager
        val marker = Intent(NativePluginApi.ACTION_MARKER)
        val services = manager.queryIntentServices(marker, PackageManager.GET_META_DATA)
        services.mapNotNull { service ->
            runCatching { describe(manager, service.serviceInfo.packageName) }.getOrNull()
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrElse { emptyList() }

    /** Builds a descriptor for one package; null when it is not a valid plugin. */
    fun describeFor(packageName: String): NativePluginDescriptor? = runCatching {
        describe(appContext.packageManager, packageName)
    }.getOrNull()

    /**
     * Builds a descriptor for a `.osplugin` native module. The module lives in
     * `filesDir/modules/<id>/`; nothing is installed, so there is no launcher
     * entry or system app-list presence.
     */
    fun describeModule(module: InstalledModule): NativePluginDescriptor? {
        val manifest = module.manifest
        if (manifest.type != ModuleManifest.TYPE_NATIVE) return null
        val dex = File(module.directory, manifest.entry)
        if (!dex.isFile) return null
        return NativePluginDescriptor(
            id = manifest.id,
            packageName = manifest.id,
            label = manifest.name,
            version = manifest.version,
            iconKey = manifest.iconKey.ifBlank { "extension" },
            entry = manifest.entryClass,
            apiVersion = manifest.apiVersion,
            sourceDir = dex.absolutePath,
            nativeLibraryDir = null,
            source = NativePluginSource.DEX_MODULE,
            moduleId = manifest.id,
        )
    }

    private fun describe(
        manager: PackageManager,
        packageName: String,
    ): NativePluginDescriptor? {
        if (packageName == appContext.packageName) return null
        val info = manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val meta = info.metaData ?: return null
        val entry = meta.getString(NativePluginApi.META_ENTRY)?.trim().orEmpty()
        if (!isValidClassName(entry)) return null
        val apiVersion = runCatching {
            meta.getInt(NativePluginApi.META_API, 0)
        }.getOrDefault(0)
        if (apiVersion <= 0) return null
        val packageInfo = runCatching {
            manager.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return null
        val label = meta.getString(NativePluginApi.META_NAME)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: runCatching { manager.getApplicationLabel(info).toString() }.getOrDefault(packageName)
        return NativePluginDescriptor(
            id = packageName,
            packageName = packageName,
            label = label,
            version = packageInfo.versionName.orEmpty(),
            iconKey = meta.getString(NativePluginApi.META_ICON)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "extension",
            entry = entry,
            apiVersion = apiVersion,
            sourceDir = info.sourceDir,
            nativeLibraryDir = info.nativeLibraryDir,
        )
    }

    /** Rejects obviously invalid class names before handing them to a class loader. */
    private fun isValidClassName(name: String): Boolean =
        name.isNotEmpty() &&
            name.length <= 200 &&
            CLASS_PATTERN.matches(name) &&
            !name.contains("..")

    /** Per-plugin private directory, also used as the dex output directory. */
    fun dataDir(pluginId: String): File =
        File(appContext.filesDir, "native-plugins/$pluginId").apply { mkdirs() }

    fun dexDir(pluginId: String): File =
        File(appContext.codeCacheDir, "native-plugins/${pluginId.hashCode()}").apply { mkdirs() }

    private companion object {
        val CLASS_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    }
}
