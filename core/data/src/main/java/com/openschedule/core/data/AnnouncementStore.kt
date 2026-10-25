package com.openschedule.core.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val content: String = "",
    val date: String = "",
    val pinned: Boolean = false,
)

@Serializable
private data class AnnouncementFeed(
    val announcements: List<Announcement> = emptyList(),
)

class AnnouncementStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheFile = File(appContext.filesDir, CACHE_FILE)

    private val _announcements = MutableStateFlow(loadInitial())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private val _readIds = MutableStateFlow(
        prefs.getStringSet(KEY_READ_IDS, emptySet()).orEmpty(),
    )
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Keeps the last good feed when the network fails; returns false on failure. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (_refreshing.value) return@withContext false
        _refreshing.value = true
        try {
            val url = URL("$FEED_URL?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext false
                val raw = connection.inputStream.bufferedReader().use { it.readText() }
                val list = parseFeed(raw)
                if (list.isEmpty()) return@withContext false
                cacheFile.writeText(raw)
                _announcements.value = list
                AppLog.i("Announcement", "公告已更新 ${list.size} 条")
                true
            } finally {
                connection.disconnect()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLog.w("Announcement", "公告更新失败: ${error.message}")
            false
        } finally {
            _refreshing.value = false
        }
    }

    fun markRead(id: String) {
        if (id.isBlank() || id in _readIds.value) return
        val next = _readIds.value + id
        prefs.edit().putStringSet(KEY_READ_IDS, next).apply()
        _readIds.value = next
    }

    fun markAllRead() {
        val next = _announcements.value.map { it.id }.toSet()
        prefs.edit().putStringSet(KEY_READ_IDS, next).apply()
        _readIds.value = next
    }

    private fun loadInitial(): List<Announcement> {
        cached()?.let { return it }
        val bundled = runCatching {
            appContext.resources.openRawResource(R.raw.announcements)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return runCatching { parseFeed(bundled) }.getOrDefault(emptyList())
    }

    private fun cached(): List<Announcement>? {
        if (!cacheFile.isFile) return null
        return runCatching { parseFeed(cacheFile.readText()) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseFeed(raw: String): List<Announcement> =
        json.decodeFromString(AnnouncementFeed.serializer(), raw)
            .announcements
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .sortedWith(
                compareByDescending<Announcement> { it.pinned }
                    .thenByDescending { it.date },
            )

    private companion object {
        const val PREFS_NAME = "openschedule_announcements"
        const val KEY_READ_IDS = "read_ids"
        const val CACHE_FILE = "announcements.json"
        const val FEED_URL =
            "https://raw.githubusercontent.com/PanSomeone/OpeningSchool/main/announcements.json"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
