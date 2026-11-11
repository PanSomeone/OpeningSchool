package com.openschedule.core.data

import com.openschedule.plugin.sdk.HostPlugin

/**
 * Instantiates a plugin entry class from an arbitrary [ClassLoader].
 *
 * Split out from the runtime so it can be unit tested on the JVM with a plain
 * class loader; on device the runtime passes a `DexClassLoader` over the plugin
 * APK. The class must implement [HostPlugin] and expose a public no-arg
 * constructor — anything else is rejected before any plugin code runs.
 */
internal object NativePluginLoader {

    fun instantiate(loader: ClassLoader, entry: String): HostPlugin {
        require(entry.isNotBlank()) { "插件入口类为空" }
        val type = loader.loadClass(entry)
        require(HostPlugin::class.java.isAssignableFrom(type)) {
            "$entry 没有实现 HostPlugin"
        }
        require(!type.isInterface && !java.lang.reflect.Modifier.isAbstract(type.modifiers)) {
            "$entry 不能是接口或抽象类"
        }
        val constructor = type.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()
        return instance as HostPlugin
    }
}
