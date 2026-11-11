package com.openschedule.plugin.sdk

import android.content.Context
import android.view.View
import java.io.File

/**
 * Contract implemented by a native plugin APK.
 *
 * A plugin is a normal Android application (installed by the user, visible in
 * the launcher or not) that declares a marker service and an entry class
 * implementing this interface. The host loads that class with a
 * [dalvik.system.DexClassLoader] whose parent is the host classloader, calls
 * [onCreate] once when the plugin becomes enabled, and asks for its screens and
 * widgets.
 *
 * Rules that keep this stable:
 *
 * - Only Android framework types are used ([Context], [View], [File]). There is
 *   no Compose, no app-internal model and no reflection on host classes, so a
 *   plugin keeps working after the host is minified.
 * - UI is built with ordinary Android views. The host embeds them with
 *   `AndroidView`; nothing is loaded from the network and no WebView is used.
 * - [onCreate], [onDestroy] and every `createView` run on the main thread.
 *
 * Build the plugin against the `plugin-sdk` AAR (`:core:plugin-sdk:assembleRelease`).
 */
interface HostPlugin {

    /**
     * Called once on the main thread when the plugin starts. Keep it cheap;
     * register widgets/screens afterwards through [screens] and [widgets].
     */
    fun onCreate(host: PluginHost)

    /** Called on the main thread when the plugin is disabled or the host exits. */
    fun onDestroy() {}

    /** Pages this plugin contributes. The host shows them after a tap on the plugin row. */
    fun screens(): List<NativeScreen> = emptyList()

    /** Home-canvas widgets this plugin contributes. */
    fun widgets(): List<NativeWidget> = emptyList()
}

/**
 * A page declared by a plugin. [createView] is called on the main thread each
 * time the host needs a fresh view; returning the same instance later is not
 * supported.
 */
class NativeScreen(
    /** Unique within the plugin; used as a stable tab/id key. */
    val id: String,
    val title: String,
    val iconKey: String = NATIVE_ICON_DEFAULT,
    val createView: (Context) -> View,
)

/** A home-canvas widget declared by a plugin. */
class NativeWidget(
    val id: String,
    val title: String,
    val iconKey: String = NATIVE_ICON_DEFAULT,
    /** Nominal grid size, 1..2 columns by 1..2 rows. */
    val columns: Int = 1,
    val rows: Int = 1,
    val createView: (Context) -> View,
)

/**
 * Host services handed to a plugin. Everything is scoped to the calling plugin:
 * storage is a private namespace, events are delivered on the main thread, and
 * files live under the host's private directory for this plugin only.
 */
interface PluginHost {

    /** Stable plugin id: the plugin's package name. */
    val pluginId: String

    /** Host version name, for compatibility checks and log lines. */
    val hostVersion: String

    /** Private directory owned by this plugin; created on first access. */
    fun dataDir(): File

    /** Writes to the app log (visible in 设置 → 反馈与日志 → 导出日志). */
    fun log(message: String)

    fun getString(key: String, fallback: String?): String?

    fun putString(key: String, value: String)

    fun remove(key: String)

    /**
     * Subscribes to a channel. The handler runs on the main thread and receives
     * the payload as a JSON string (`"null"` when there is none).
     */
    fun onEvent(channel: String, handler: (payloadJson: String) -> Unit)

    /** Broadcasts to other running plugins; the sender does not receive its own event. */
    fun emit(channel: String, payloadJson: String)
}

/** Icon key used when a plugin does not pick one. */
const val NATIVE_ICON_DEFAULT: String = "extension"

/** Reserved channels emitted by the host. */
object NativeHostEvents {
    const val TIMETABLE_UPDATED = "timetable.updated"
    const val PLUGIN_LOADED = "plugin.loaded"
    const val PLUGIN_UNLOADED = "plugin.unloaded"
}

/** Manifest contract shared by the host and plugin APKs. */
object NativePluginApi {

    /** Bump when [HostPlugin]/[PluginHost] change incompatibly. */
    const val VERSION = 2

    /**
     * Marker action. A plugin declares it on an exported, no-op service so the
     * host can discover installed plugins through `queryIntentServices` without
     * asking for `QUERY_ALL_PACKAGES`.
     */
    const val ACTION_MARKER = "com.openschedule.plugin.NATIVE"

    /** `<meta-data>` on `<application>`: fully qualified class implementing [HostPlugin]. */
    const val META_ENTRY = "com.openschedule.plugin.ENTRY"

    /** Optional `<meta-data>`: display name override. */
    const val META_NAME = "com.openschedule.plugin.NAME"

    /** Optional `<meta-data>`: icon key from the host icon catalog. */
    const val META_ICON = "com.openschedule.plugin.ICON"

    /** Optional `<meta-data>`: SDK version the plugin was built against. */
    const val META_API = "com.openschedule.plugin.API"
}
