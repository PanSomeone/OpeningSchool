package com.openschedule.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Namespaced key-value storage for plugin modules. Each plugin sees only its
 * own `<id>.json` file under [directory]; values are strings (modules use
 * JSON.stringify for structured data) and both per-value and per-plugin sizes
 * are capped so a module cannot fill app storage.
 */
class PluginDataStore(private val directory: File) {

    private val json = Json
    private val cache = mutableMapOf<String, MutableMap<String, String>>()

    @Synchronized
    fun get(pluginId: String, key: String, fallback: String = ""): String =
        data(pluginId)[key] ?: fallback

    @Synchronized
    fun set(pluginId: String, key: String, value: String) {
        check(key.length <= MAX_KEY_LENGTH) { "存储键过长" }
        check(value.length <= MAX_VALUE_LENGTH) { "存储值过大" }
        val data = data(pluginId)
        val previous = data.put(key, value)
        if (previous != value && !persist(pluginId, data)) {
            if (previous == null) data.remove(key) else data[key] = previous
            error("存储写入失败")
        }
    }

    @Synchronized
    fun remove(pluginId: String, key: String) {
        val data = data(pluginId)
        if (data.remove(key) != null) {
            persist(pluginId, data)
        }
    }

    @Synchronized
    fun clear(pluginId: String) {
        cache.remove(pluginId)
        File(directory, "$pluginId.json").delete()
    }

    @Synchronized
    fun keys(pluginId: String): List<String> = data(pluginId).keys.sorted()

    private fun data(pluginId: String): MutableMap<String, String> =
        cache.getOrPut(pluginId) { read(pluginId) }

    private fun read(pluginId: String): MutableMap<String, String> {
        val file = File(directory, "$pluginId.json")
        if (!file.isFile) return mutableMapOf()
        val parsed = runCatching {
            val element = json.parseToJsonElement(file.readText())
            (element as? JsonObject)?.entries?.associate { (key, value) ->
                key to ((value as? JsonPrimitive)?.content ?: "")
            }
        }.getOrNull() ?: return mutableMapOf()
        val total = parsed.values.sumOf { it.length }
        if (total > MAX_PLUGIN_BYTES) return mutableMapOf()
        return parsed.toMutableMap()
    }

    private fun persist(pluginId: String, data: Map<String, String>): Boolean {
        if (data.values.sumOf { it.length } > MAX_PLUGIN_BYTES) {
            throw IllegalStateException("插件存储空间已满")
        }
        directory.mkdirs()
        val file = File(directory, "$pluginId.json")
        val temp = File(directory, "$pluginId.json.tmp")
        val payload = buildJsonObject {
            data.forEach { (key, value) -> put(key, value) }
        }.toString()
        return try {
            temp.writeText(payload)
            if (file.isFile) file.delete()
            temp.renameTo(file)
        } catch (error: Exception) {
            false
        }
    }

    companion object {
        const val MAX_KEY_LENGTH = 64
        const val MAX_VALUE_LENGTH = 16 * 1024
        const val MAX_PLUGIN_BYTES = 256 * 1024
    }
}
