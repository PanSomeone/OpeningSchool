package com.openschedule.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.openschedule.core.model.AdapterConfigField
import com.openschedule.core.model.ImportResult
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.TimetableImporter
import com.openschedule.core.plugin.AdapterDescriptor
import com.openschedule.core.plugin.AdapterProtocol
import com.openschedule.core.plugin.AdapterReply
import com.openschedule.core.plugin.AdapterRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

data class AdapterService(
    val packageName: String,
    val serviceName: String,
    val label: String,
) {
    val component: ComponentName get() = ComponentName(packageName, serviceName)
}

/**
 * Finds adapters published by other installed apps and talks to them.
 *
 * The wire is deliberately boring: a bound [Messenger], a JSON string in a
 * Bundle, and a reply through `replyTo`. No code is loaded into this process,
 * and credentials are only ever put on the wire when the user presses import.
 */
class ExternalAdapterCatalog(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false },
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun discover(): List<AdapterService> {
        val pm = appContext.packageManager
        val intent = Intent(AdapterProtocol.ACTION)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val serviceInfo = info.serviceInfo ?: return@mapNotNull null
            if (!serviceInfo.exported) return@mapNotNull null
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.serviceInfo.packageName, 0)).toString()
            }.getOrDefault(info.serviceInfo.packageName)
            AdapterService(
                packageName = info.serviceInfo.packageName,
                serviceName = info.serviceInfo.name ?: return@mapNotNull null,
                label = appLabel,
            )
        }.distinctBy { it.component }
    }

    suspend fun loadDescriptors(): List<ExternalImporter> = discover().mapNotNull { service ->
        val reply = runCatching { describe(service) }.getOrNull() ?: return@mapNotNull null
        val descriptor = reply.descriptor ?: return@mapNotNull null
        ExternalImporter(this, service, descriptor)
    }

    private suspend fun describe(service: AdapterService): AdapterReply? {
        val raw = exchange(service.component, AdapterProtocol.MSG_DESCRIBE, "{}") ?: return null
        return runCatching { json.decodeFromString(AdapterReply.serializer(), raw) }.getOrNull()
    }

    internal suspend fun import(service: AdapterService, config: Map<String, String>): AdapterReply {
        val request = json.encodeToString(
            AdapterRequest.serializer(),
            AdapterRequest(config = config),
        )
        val raw = exchange(service.component, AdapterProtocol.MSG_IMPORT, request)
            ?: return AdapterReply(
                code = AdapterReply.INTERNAL_ERROR,
                message = "插件没有在 15 秒内回应",
            )
        return runCatching { json.decodeFromString(AdapterReply.serializer(), raw) }
            .getOrElse {
                AdapterReply(
                    code = AdapterReply.INTERNAL_ERROR,
                    message = "插件返回了无法解析的数据",
                )
            }
    }

    private suspend fun exchange(component: ComponentName, what: Int, request: String): String? =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                var bound = false
                var connectionRef: ServiceConnection? = null

                fun cleanup() {
                    val connection = connectionRef ?: return
                    if (bound) runCatching { appContext.unbindService(connection) }
                    bound = false
                    connectionRef = null
                }

                val replyHandler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        val payload = msg.data?.getString(AdapterProtocol.KEY_REPLY)
                        cleanup()
                        if (continuation.isActive) continuation.resume(payload)
                    }
                }
                val replyMessenger = Messenger(replyHandler)

                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder == null) {
                            cleanup()
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        val service = Messenger(binder)
                        val message = Message.obtain(null, what)
                        message.replyTo = replyMessenger
                        message.data = Bundle().apply {
                            putString(AdapterProtocol.KEY_REQUEST, request)
                        }
                        val sent = runCatching { service.send(message) }.isSuccess
                        if (!sent) {
                            cleanup()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        cleanup()
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        cleanup()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                connectionRef = connection

                val intent = Intent(AdapterProtocol.ACTION).setComponent(component)
                bound = runCatching {
                    appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)

                if (!bound) {
                    connectionRef = null
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation { cleanup() }
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}

class ExternalImporter(
    private val catalog: ExternalAdapterCatalog,
    private val service: AdapterService,
    val descriptor: AdapterDescriptor,
) : TimetableImporter {

    override val id: String get() = "external:${service.packageName}/${descriptor.id}"
    override val name: String get() = descriptor.name
    override val description: String
        get() = descriptor.description.ifBlank { service.label }

    override val configFields: List<AdapterConfigField> get() = descriptor.fields

    override suspend fun import(config: Map<String, String>): ImportResult {
        val missing = configFields
            .filter { it.required && config[it.key].isNullOrBlank() }
            .map { it.label }
        if (missing.isNotEmpty()) {
            return ImportResult.Failure("请填写：${missing.joinToString("、")}")
        }

        val reply = catalog.import(service, config)
        if (reply.code != AdapterReply.OK) {
            return ImportResult.Failure(reply.message.ifBlank { "插件导入失败（${reply.code}）" })
        }
        val timetable: Timetable = reply.timetable
            ?: return ImportResult.Failure("插件没有返回课表")
        return ImportResult.Success(timetable, reply.warnings)
    }
}
