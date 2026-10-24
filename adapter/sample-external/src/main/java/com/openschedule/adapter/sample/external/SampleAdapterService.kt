package com.openschedule.adapter.sample.external

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.openschedule.core.model.Course
import com.openschedule.core.model.Timetable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SampleAdapterService : Service() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                1 -> sendReply(message, Reply(descriptor = DESCRIPTOR))
                2 -> sendReply(message, Reply(timetable = sampleTimetable()))
                else -> sendReply(message, Reply(code = 2, message = "未知请求"))
            }
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun sendReply(message: Message, reply: Reply) {
        val data = Bundle().apply { putString("reply", json.encodeToString(reply)) }
        message.replyTo?.send(Message.obtain().apply { this.data = data })
    }

    private fun sampleTimetable(): Timetable = Timetable(
        name = "示例课表",
        totalWeeks = 20,
        courses = listOf(
            Course(
                id = UUID.randomUUID().toString(),
                name = "高等数学",
                teacher = "张老师",
                location = "三教 101",
                day = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = (1..16).toSet(),
                color = 0,
            ),
            Course(
                id = UUID.randomUUID().toString(),
                name = "大学英语",
                teacher = "李老师",
                location = "外语楼 201",
                day = 3,
                startPeriod = 3,
                endPeriod = 4,
                weeks = (1..16).toSet(),
                color = 1,
            ),
            Course(
                id = UUID.randomUUID().toString(),
                name = "程序设计",
                teacher = "王老师",
                location = "实验楼 302",
                day = 5,
                startPeriod = 5,
                endPeriod = 6,
                weeks = (1..12).toSet(),
                color = 2,
            ),
        ),
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
        val fields: List<String> = emptyList(),
    )

    private companion object {
        val DESCRIPTOR = Descriptor(
            id = "sample-external",
            name = "示例课表适配器",
            description = "无需登录，用于验证外部适配器下载与导入流程",
        )
    }
}
