package com.openschedule.core.plugin

import com.openschedule.core.model.AdapterConfigField
import com.openschedule.core.model.Timetable
import kotlinx.serialization.Serializable

/**
 * Contract for an out-of-process timetable adapter.
 *
 * A third-party APK publishes a [android.app.Service] with [ACTION] and
 * requires [PERMISSION]. The host binds to it and exchanges JSON through
 * [android.os.Messenger]. Nothing is dynamically loaded: the adapter is a
 * normal app, installed by the user, and the host only ever sees strings.
 *
 * Request: `Message.obj` is unused; `data.getString(KEY_REQUEST)` holds JSON.
 * Reply: the adapter sends a message whose `data.getString(KEY_REPLY)` holds
 * JSON, using `msg.replyTo` as the destination.
 *
 * See `docs/ADAPTER_API.md` for the schema and a skeleton service.
 */
object AdapterProtocol {
    const val ACTION = "com.openschedule.plugin.ADAPTER"
    const val PERMISSION = "com.openschedule.permission.USE_ADAPTER"
    const val API_VERSION = 1

    const val MSG_DESCRIBE = 1
    const val MSG_IMPORT = 2

    const val KEY_REQUEST = "request"
    const val KEY_REPLY = "reply"
}

@Serializable
data class AdapterDescriptor(
    val id: String,
    val name: String,
    val description: String = "",
    val apiVersion: Int = AdapterProtocol.API_VERSION,
    val fields: List<AdapterConfigField> = emptyList(),
)

@Serializable
data class AdapterRequest(
    val apiVersion: Int = AdapterProtocol.API_VERSION,
    val config: Map<String, String> = emptyMap(),
)

@Serializable
data class AdapterReply(
    val code: Int = 0,
    val message: String = "",
    val descriptor: AdapterDescriptor? = null,
    val timetable: Timetable? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        const val OK = 0
        const val BAD_REQUEST = 2
        const val INTERNAL_ERROR = 3
    }
}
