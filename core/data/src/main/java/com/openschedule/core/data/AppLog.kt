package com.openschedule.core.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Logs are kept in-process and mirrored to a rotating file so a user can attach
 * them to a report even when the app has already been killed by a crash.
 */
object AppLog {

    private const val ROOT_TAG = "OpeningSchool"
    private const val FILE_NAME = "app.log"
    private const val ARCHIVE_NAME = "app.log.1"
    private const val MAX_BUFFER_LINES = 600
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val MAX_EXPORT_CHARS = 512 * 1024

    private val buffer = ArrayDeque<String>()
    private val lock = Any()
    private val timestamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var logFile: File? = null

    fun install(context: Context) {
        synchronized(lock) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            appContext = app
            val dir = File(app.filesDir, "logs").apply { mkdirs() }
            logFile = File(dir, FILE_NAME)
            installCrashHandler()
        }
        i(
            "App",
            "启动 v${versionName(requireContext())} (${versionCode(requireContext())}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                "(SDK ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
        )
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** Writes a report containing device metadata and all retained log lines. */
    fun export(context: Context): File {
        val app = context.applicationContext
        val report = buildString {
            appendLine("开源大学 · 运行日志")
            appendLine("导出时间  ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine("版本      ${versionName(app)} (${versionCode(app)})")
            appendLine("包名      ${app.packageName}")
            appendLine("设备      ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统      Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI       ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("区域      ${Locale.getDefault().toLanguageTag()}")
            appendLine()
            appendLine("---------------- 日志 ----------------")
            val logs = readPersistedLogs().ifBlank { snapshotLocked() }
            append(logs)
            if (logs.isNotBlank() && !logs.endsWith("\n")) appendLine()
        }
        val dir = File(app.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "OpeningSchool-${LocalDateTime.now().format(fileTimestamp)}.txt")
        file.writeText(report)
        return file
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val now = LocalDateTime.now().format(timestamp)
        val line = buildString {
            append(now).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (error != null) {
                append('\n').append(Log.getStackTraceString(error).trimEnd())
            }
        }
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_BUFFER_LINES) buffer.removeFirst()
            runCatching { appendLocked(line) }
        }
        when (level) {
            "E" -> Log.e(ROOT_TAG, "[$tag] $message", error)
            "W" -> Log.w(ROOT_TAG, "[$tag] $message", error)
            else -> Log.i(ROOT_TAG, "[$tag] $message")
        }
    }

    private fun appendLocked(line: String) {
        val file = logFile ?: return
        if (file.length() > MAX_FILE_BYTES) {
            val archive = File(file.parentFile, ARCHIVE_NAME)
            archive.delete()
            file.renameTo(archive)
        }
        file.appendText(line + "\n")
    }

    private fun readPersistedLogs(): String {
        val file = logFile ?: return ""
        val archive = File(file.parentFile, ARCHIVE_NAME)
        val text = buildString {
            if (archive.isFile) append(archive.readText())
            if (file.isFile) append(file.readText())
        }
        return if (text.length > MAX_EXPORT_CHARS) text.takeLast(MAX_EXPORT_CHARS) else text
    }

    private fun snapshotLocked(): String = synchronized(lock) {
        buffer.joinToString(separator = "\n", postfix = "\n")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                e("Crash", "未捕获异常 · ${thread.name}", error)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun requireContext(): Context =
        appContext ?: error("AppLog.install() must be called before logging")

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "?" }

    private fun versionCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrNull() ?: 0L
}
