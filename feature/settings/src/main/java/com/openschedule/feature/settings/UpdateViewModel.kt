package com.openschedule.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openschedule.core.data.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max

data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val fallbackApkUrl: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(
        val info: UpdateInfo,
        val file: File,
        val verified: Boolean,
    ) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(
    private val context: Context,
) : ViewModel() {

    val currentVersion: String = appVersionName(context)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (_state.value is UpdateState.Downloading) return
        job?.cancel()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { fetchManifest() }
                val current = parseAppVersion(currentVersion)
                val latest = parseAppVersion(info.version)
                if (current == null || latest == null) {
                    _state.value = UpdateState.Failed("版本号格式无法识别")
                } else if (latest > current) {
                    _state.value = UpdateState.Available(info)
                } else {
                    _state.value = UpdateState.UpToDate(currentVersion, info.version)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.w("Update", "检查更新失败: ${error.message}")
                _state.value = UpdateState.Failed(
                    error.message ?: "检查更新失败，请检查网络",
                )
            }
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        if (_state.value is UpdateState.Downloading) return
        job?.cancel()
        _state.value = UpdateState.Downloading(0f)
        job = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadApk(info) }
                _state.value = UpdateState.Ready(
                    info = info,
                    file = file.first,
                    verified = file.second,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.w("Update", "下载更新失败: ${error.message}")
                _state.value = UpdateState.Failed(
                    error.message ?: "下载失败，请检查网络",
                )
            }
        }
    }

    /** Opens the system installer; a confirmation dialog is always required by Android. */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        if (!ready.file.isFile) return false
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
        }.isSuccess
    }

    fun openReleasePage() {
        val url = (state.value as? UpdateState.Available)?.info?.pageUrl
            ?: RELEASES_PAGE
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun fetchManifest(): UpdateInfo {
        var lastError: Throwable? = null
        for (baseUrl in MANIFEST_URLS) {
            try {
                return fetchManifestFrom(baseUrl)
            } catch (error: Throwable) {
                lastError = error
                AppLog.w("Update", "更新清单获取失败 $baseUrl: ${error.message}")
            }
        }
        throw lastError ?: IOException("更新清单获取失败")
    }

    private fun fetchManifestFrom(baseUrl: String): UpdateInfo {
        val url = "$baseUrl?t=${System.currentTimeMillis()}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "OpeningSchool-Update")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Pragma", "no-cache")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("更新服务 HTTP ${connection.responseCode}")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            val version = json.optString("version").trim()
            val apkUrl = json.optString("apkUrl").trim()
            if (version.isBlank() || apkUrl.isBlank()) {
                throw IOException("更新清单不完整")
            }
            if (!apkUrl.startsWith("https://")) {
                throw IOException("更新地址不是 HTTPS")
            }
            return UpdateInfo(
                version = version,
                title = json.optString("title").ifBlank { "开源大学 $version" },
                notes = json.optString("notes"),
                pageUrl = json.optString("pageUrl").ifBlank { RELEASES_PAGE },
                apkUrl = apkUrl,
                sha256 = json.optString("sha256").trim(),
                sizeBytes = json.optLong("sizeBytes"),
                fallbackApkUrl = json.optString("fallbackApkUrl").trim()
                    .takeIf { it.startsWith("https://") }
                    .orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(info: UpdateInfo): Pair<File, Boolean> {
        val urls = listOf(info.apkUrl, info.fallbackApkUrl)
            .filter { it.startsWith("https://") }
            .distinct()
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                return downloadApkFrom(info, url)
            } catch (error: Throwable) {
                lastError = error
                AppLog.w("Update", "下载失败 $url: ${error.message}")
                _state.value = UpdateState.Downloading(0f)
            }
        }
        throw lastError ?: IOException("没有可用的下载地址")
    }

    private fun downloadApkFrom(info: UpdateInfo, url: String): Pair<File, Boolean> {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val safeVersion = info.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "OpeningSchool-$safeVersion.apk")
        val temp = File(dir, "update.part")
        var connection: HttpURLConnection? = null
        try {
            val http = URL(url).openConnection() as HttpURLConnection
            connection = http
            http.connectTimeout = 10_000
            http.readTimeout = 30_000
            http.requestMethod = "GET"
            http.instanceFollowRedirects = true
            http.setRequestProperty("User-Agent", "OpeningSchool-Update")
            if (http.responseCode !in 200..299) {
                throw IOException("下载 HTTP ${http.responseCode}")
            }
            val total = http.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
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
                            max(0, ((copied * 100) / total).toInt()).coerceAtMost(100)
                        } else {
                            0
                        }
                        if (percent != shown && (percent == 100 || percent % 2 == 0)) {
                            shown = percent
                            _state.value = UpdateState.Downloading(percent / 100f)
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val verified = info.sha256.isNotBlank()
            if (verified && !hash.equals(info.sha256, ignoreCase = true)) {
                throw IOException("更新包校验失败")
            }
            if (target.isFile) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target to verified
        } finally {
            connection?.disconnect()
            if (temp.isFile) temp.delete()
        }
    }

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "1.0.0-beta17" }

    private companion object {
        const val RELEASES_PAGE = "https://gitee.com/PanSomeone/OpeningSchool/releases"
        val MANIFEST_URLS = listOf(
            "https://gitee.com/PanSomeone/OpeningSchool/raw/main/update.json",
            "https://raw.githubusercontent.com/PanSomeone/OpeningSchool/main/update.json",
        )
    }
}

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preLabel: String = "",
    val preNumber: Int = 0,
) : Comparable<AppVersion> {

    val stable: Boolean get() = preLabel.isBlank()

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (stable != other.stable) return if (stable) 1 else -1
        if (stable) return 0
        val rank = preRank(preLabel).compareTo(preRank(other.preLabel))
        if (rank != 0) return rank
        return preNumber.compareTo(other.preNumber)
    }

    private fun preRank(label: String): Int = when (label.lowercase()) {
        "alpha", "a" -> 0
        "beta", "b" -> 1
        "rc" -> 2
        else -> 3
    }
}

internal fun parseAppVersion(raw: String): AppVersion? {
    val match = Regex(
        "^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]+)(\\d*))?.*$",
    ).matchEntire(raw.trim()) ?: return null
    return AppVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
        preLabel = match.groupValues[4],
        preNumber = match.groupValues[5].toIntOrNull() ?: 0,
    )
}
