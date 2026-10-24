package com.openschedule.feature.plugins

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
data class MarketCatalog(
    val version: Int = 1,
    val plugins: List<MarketPlugin> = emptyList(),
)

@Serializable
data class MarketPlugin(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "工具",
    val version: String = "",
    val url: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val packageName: String = "",
    val iconKey: String = "extension",
)

sealed interface MarketDownload {
    data object Idle : MarketDownload
    data object Queued : MarketDownload
    data class Downloading(val progress: Float) : MarketDownload
    data class Ready(val file: File, val verified: Boolean) : MarketDownload
    data class Failed(val message: String) : MarketDownload
}

class PluginMarketViewModel(
    private val context: Context,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("openschedule_market", Context.MODE_PRIVATE)

    private val _plugins = MutableStateFlow<List<MarketPlugin>>(emptyList())
    val plugins: StateFlow<List<MarketPlugin>> = _plugins.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, MarketDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, MarketDownload>> = _downloads.asStateFlow()

    private var catalogJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        catalogJob?.cancel()
        _loading.value = true
        _error.value = null
        catalogJob = viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) { loadCatalog() }
            if (catalog == null) {
                _error.value = "插件目录暂时不可用"
                _loading.value = false
                return@launch
            }
            _plugins.value = catalog.plugins
            refreshInstalled()
            _loading.value = false
        }
    }

    fun refreshInstalled() {
        val installed = _plugins.value.mapNotNullTo(mutableSetOf()) { plugin ->
            if (plugin.packageName.isBlank()) {
                null
            } else {
                plugin.id.takeIf {
                    runCatching {
                        context.packageManager.getPackageInfo(plugin.packageName, 0)
                    }.isSuccess
                }
            }
        }
        _installed.value = installed
    }

    fun download(ids: Collection<String>) {
        val targets = ids
            .distinct()
            .mapNotNull { id -> _plugins.value.firstOrNull { it.id == id } }
            .filter { plugin ->
                when (_downloads.value[plugin.id]) {
                    is MarketDownload.Ready,
                    is MarketDownload.Downloading,
                    is MarketDownload.Queued,
                    -> false

                    else -> true
                }
            }
        if (targets.isEmpty()) return

        _downloads.update { current ->
            current + targets.associate { it.id to MarketDownload.Queued }
        }
        viewModelScope.launch {
            targets.chunked(MAX_PARALLEL).forEach { batch ->
                batch.map { plugin ->
                    async(Dispatchers.IO) { downloadOne(plugin) }
                }.awaitAll()
            }
        }
    }

    private fun loadCatalog(): MarketCatalog? {
        val remote = runCatching { fetchCatalog(REMOTE_CATALOG) }.getOrNull()
        if (remote != null && remote.plugins.isNotEmpty()) {
            prefs.edit().putString(KEY_CACHE, json.encodeToString(remote)).apply()
            return remote
        }
        val cached = prefs.getString(KEY_CACHE, null)?.let { raw ->
            runCatching { json.decodeFromString<MarketCatalog>(raw) }.getOrNull()
        }
        if (cached != null && cached.plugins.isNotEmpty()) return cached
        return runCatching {
            val raw = context.assets.open(ASSET_CATALOG).bufferedReader().use { it.readText() }
            json.decodeFromString<MarketCatalog>(raw)
        }.getOrNull()
    }

    private fun fetchCatalog(url: String): MarketCatalog {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("catalog HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString<MarketCatalog>(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadOne(plugin: MarketPlugin) {
        _downloads.update { it + (plugin.id to MarketDownload.Downloading(0f)) }
        val dir = File(context.cacheDir, "market").apply { mkdirs() }
        val target = File(dir, "${plugin.id}-${plugin.version}.apk")
        val temp = File(dir, "${plugin.id}.part")
        var connection: HttpURLConnection? = null
        try {
            val http = URL(plugin.url).openConnection() as HttpURLConnection
            connection = http
            http.connectTimeout = 10_000
            http.readTimeout = 30_000
            http.requestMethod = "GET"
            http.instanceFollowRedirects = true
            http.setRequestProperty("User-Agent", "OpeningSchool/${plugin.version}")
            if (http.responseCode !in 200..299) {
                throw IOException("HTTP ${http.responseCode}")
            }
            val total = http.contentLengthLong.takeIf { it > 0 } ?: plugin.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var shown = -1
            http.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        val percent = if (total > 0) {
                            ((copied * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        if (percent != shown && (percent == 100 || percent % 2 == 0)) {
                            shown = percent
                            _downloads.update {
                                it + (plugin.id to MarketDownload.Downloading(percent / 100f))
                            }
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (plugin.sha256.isNotBlank() && !hash.equals(plugin.sha256, ignoreCase = true)) {
                temp.delete()
                _downloads.update {
                    it + (plugin.id to MarketDownload.Failed("SHA-256 校验失败"))
                }
                return
            }
            if (target.isFile) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            _downloads.update {
                it + (
                    plugin.id to MarketDownload.Ready(
                        file = target,
                        verified = plugin.sha256.isNotBlank(),
                    )
                    )
            }
            AppLog.i("Market", "下载完成 ${plugin.id} ($copied bytes)")
        } catch (error: Exception) {
            temp.delete()
            AppLog.w("Market", "下载失败 ${plugin.id}: ${error.message}")
            _downloads.update {
                it + (plugin.id to MarketDownload.Failed(error.message ?: "下载失败"))
            }
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val REMOTE_CATALOG =
            "https://raw.githubusercontent.com/PanSomeone/OpeningSchool/main/market/catalog.json"
        const val ASSET_CATALOG = "market_catalog.json"
        const val KEY_CACHE = "catalog_cache"
    }
}
