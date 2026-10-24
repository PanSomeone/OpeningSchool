package com.openschedule.adapter.zhengfang.external

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.openschedule.adapter.zhengfang.ZhengfangImporter
import com.openschedule.core.model.ImportResult
import com.openschedule.core.model.Timetable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ZhengfangAdapterService : Service() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            handle(message)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(message: Message) {
        when (message.what) {
            1 -> sendReply(message, Reply(descriptor = DESCRIPTOR))
            2 -> {
                val raw = message.data.getString("request").orEmpty()
                val request = runCatching { json.decodeFromString<Request>(raw) }.getOrNull()
                if (request == null) {
                    sendReply(message, Reply(code = 2, message = "请求格式错误"))
                    return
                }
                val replyTo = message.replyTo
                scope.launch {
                    val result = ZhengfangImporter().import(request.config)
                    val reply = when (result) {
                        is ImportResult.Success -> Reply(
                            timetable = result.timetable,
                            warnings = result.warnings,
                        )

                        is ImportResult.Failure -> Reply(code = 3, message = result.message)
                    }
                    val data = Bundle().apply { putString("reply", json.encodeToString(reply)) }
                    Handler(Looper.getMainLooper()).post {
                        replyTo?.send(Message.obtain().apply { this.data = data })
                    }
                }
            }

            else -> sendReply(message, Reply(code = 2, message = "未知请求"))
        }
    }

    private fun sendReply(message: Message, reply: Reply) {
        val data = Bundle().apply { putString("reply", json.encodeToString(reply)) }
        message.replyTo?.send(Message.obtain().apply { this.data = data })
    }

    @Serializable
    private data class Request(
        val apiVersion: Int = 1,
        val config: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Reply(
        val code: Int = 0,
        val message: String = "",
        val descriptor: Descriptor? = null,
        val timetable: Timetable? = null,
        val warnings: List<String> = emptyList(),
    )

    @Serializable
    private data class Descriptor(
        val id: String,
        val name: String,
        val description: String,
        val apiVersion: Int = 1,
        val fields: List<Field>,
    )

    @Serializable
    private data class Field(
        val key: String,
        val label: String,
        val placeholder: String = "",
        val secret: Boolean = false,
        val required: Boolean = true,
        val defaultValue: String = "",
    )

    private companion object {
        val DESCRIPTOR = Descriptor(
            id = "zhengfang-external",
            name = "正方教务（独立版）",
            description = "独立 APK 适配器，账号密码登录新版正方教务",
            fields = listOf(
                Field("baseUrl", "教务系统地址", "https://jw.example.edu.cn"),
                Field("username", "学号"),
                Field("password", "密码", secret = true),
                Field("year", "学年", "2026-2027"),
                Field("term", "学期", "1 或 2", defaultValue = "1"),
                Field("startDate", "开学日期", "2026-09-01（可选）", required = false),
            ),
        )
    }
}
