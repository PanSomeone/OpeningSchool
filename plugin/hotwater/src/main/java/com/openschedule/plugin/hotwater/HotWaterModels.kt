package com.openschedule.plugin.hotwater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * The gateway contract, as used by the one-button screen.
 *
 * No secret is shipped. `cid` is generated per install by [HotWaterSession]
 * rather than borrowed from the official app.
 */
internal object ApiConstants {

    const val DEFAULT_BASE_URL = "https://i.ilife798.com/api/v1/"

    const val APP_VERSION_NAME = "3.1.9"
    const val APP_VERSION_CODE = "31009"
    const val USER_AGENT = "Android_ilife798_$APP_VERSION_NAME"

    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_APPLICATION_TYPE = "ApplicationType"
    const val HEADER_VERSION_CODE = "VersionCode"

    const val APPLICATION_TYPE_DEVICE = "1,1"

    /**
     * Opening and closing moved to the account scope in 2026-10; the device
     * scope answers the version-gate refusal on those two paths.
     */
    const val APPLICATION_TYPE_ACCOUNT = "1,5"

    fun applicationTypeFor(path: String): String = when (path) {
        PATH_CAPTCHA, PATH_SMS_CODE, PATH_LOGIN -> APPLICATION_TYPE_DEVICE
        PATH_DEVICE_START, PATH_DEVICE_END -> APPLICATION_TYPE_ACCOUNT
        else -> APPLICATION_TYPE_DEVICE
    }

    const val PATH_CAPTCHA = "captcha/"
    const val PATH_SMS_CODE = "acc/login/code"
    const val PATH_LOGIN = "acc/login"
    const val PATH_MASTER = "ui/app/master"
    const val PATH_DEVICE_STATUS = "ui/app/dev/status"
    const val PATH_DEVICE_START = "dev/start"
    const val PATH_DEVICE_END = "dev/end"
    const val PATH_QR_USE = "qr/use"
    const val PATH_DEVICE_FAVO = "dev/favo"

    const val PARAM_DID = "did"
    const val PARAM_MORE = "more"
    const val PARAM_UPGRADE = "upgrade"
    const val PARAM_PTYPE = "ptype"
    const val PARAM_ARGS = "args"
    const val PARAM_RCP = "rcp"
    const val PARAM_CNT = "cnt"
    const val PARAM_ID = "id"
    const val PARAM_REMOVE = "remove"

    const val FAVO_ADD = "0"

    const val PTYPE_WALLET = 91
    const val ARGS_EMPTY = ""

    const val CODE_SUCCESS = 0
    const val CODE_SESSION_EXPIRED = -99
    const val CODE_RATE_LIMITED = -98

    const val CODE_PAYMENT_REQUIRED = -82
    const val CODE_ARREARS = -52
    const val CODE_NO_AGREEMENT = -88
    const val CODE_AGREEMENT_EXPIRED = -87
    const val CODE_NO_CARD = -20
    const val CODE_DEVICE_NOT_READY = -19
    const val CODE_WRONG_SCOPE = -21
    const val CODE_DEVICE_IN_USE = -15
    const val CODE_SETTLE_ON_DEVICE = -2
}

@Serializable
internal data class Envelope<out T>(
    @SerialName("code") val code: Int = UNKNOWN,
    @SerialName("msg") val msg: String? = null,
    @SerialName("message") val messageField: String? = null,
    @SerialName("data") val data: T? = null,
) {
    val isSuccess: Boolean get() = code == ApiConstants.CODE_SUCCESS
    val isSessionExpired: Boolean get() = code == ApiConstants.CODE_SESSION_EXPIRED
    val isRateLimited: Boolean get() = code == ApiConstants.CODE_RATE_LIMITED
    val message: String? get() = msg ?: messageField

    companion object {
        const val UNKNOWN = Int.MIN_VALUE
    }
}

@Serializable
internal data class LoginData(
    @SerialName("al") val al: LoginToken? = null,
)

@Serializable
internal data class LoginToken(
    @SerialName("token") val token: String = "",
    @SerialName("uid") val uid: String = "",
)

@Serializable
internal data class MasterData(
    @SerialName("account") val account: Account? = null,
    @SerialName("favos") val favos: List<Favo> = emptyList(),
)

@Serializable
internal data class Account(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("pn") val phone: String? = null,
)

@Serializable
internal data class Favo(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("bm") val bm: Bm? = null,
    @SerialName("gene") val gene: Gene? = null,
)

@Serializable
internal data class Bm(
    @SerialName("dtype") val dtype: Int = 0,
)

@Serializable
internal data class Gene(
    @SerialName("status") val status: Int = 0,
)

@Serializable
internal data class DeviceStatusData(
    @SerialName("device") val device: DeviceStateNode? = null,
)

@Serializable
internal data class DeviceStateNode(
    @SerialName("status") val status: Int = 0,
    @SerialName("gene") val gene: Gene? = null,
    @SerialName("parts") val parts: List<Part> = emptyList(),
)

@Serializable
internal data class Part(
    @SerialName("maxT") val maxT: Int? = null,
)

@Serializable
internal data class QrUseData(
    @SerialName("dev") val device: QrDeviceRef? = null,
)

@Serializable
internal data class QrDeviceRef(
    @SerialName("id") val id: String = "",
)

@Serializable
internal data class SmsCodeRequest(
    @SerialName("un") val phone: String,
    @SerialName("authCode") val captchaText: String,
    @SerialName("s") val captchaKey: String,
)

@Serializable
internal data class LoginRequest(
    @SerialName("openCode") val openCode: String = "",
    @SerialName("authCode") val smsCode: String,
    @SerialName("un") val phone: String,
    @SerialName("cid") val clientId: String,
)

internal enum class DeviceKind(val dtype: Int, val label: String) {
    SHOWER(6, "淋浴器"),
    WATER_CONTROL(5, "直饮机"),
    WATER_HEATER(9, "热水主机"),
    OTHER(-1, "其他设备"),
    ;

    companion object {
        fun of(dtype: Int): DeviceKind = entries.firstOrNull { it.dtype == dtype } ?: OTHER
    }
}

internal data class Device(
    val deviceId: String,
    val deviceName: String? = null,
    val status: Int? = null,
    val dtype: Int = 0,
) {
    val kind: DeviceKind get() = DeviceKind.of(dtype)

    val displayName: String
        get() = deviceName?.takeIf { it.isNotBlank() } ?: kind.label

    /** `99` means the server has not said, so it is not evidence of running water. */
    val isRunning: Boolean
        get() = status != null && status != STATUS_IDLE && status != STATUS_UNREPORTED

    companion object {
        const val STATUS_IDLE = 0
        const val STATUS_UNREPORTED = 99

        fun from(favo: Favo): Device = Device(
            deviceId = favo.id,
            deviceName = favo.name,
            status = favo.gene?.status,
            dtype = favo.bm?.dtype ?: 0,
        )
    }
}

internal fun MasterData.toDevices(): List<Device> =
    favos.filter { it.id.isNotBlank() }.map(Device::from)

internal sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

internal sealed class AppError {
    data object Offline : AppError()
    data class Server(val code: Int, val message: String?) : AppError()
    data object Unauthorized : AppError()
    data object Timeout : AppError()
    data object RateLimited : AppError()
    data class Refused(val code: Int, val message: String) : AppError()
    data class Unexpected(val detail: String? = null) : AppError()

    fun toChinese(): String = when (this) {
        is Offline -> "网络连不上，检查一下连接再试"
        is Unauthorized -> "登录状态失效了，重新登录一次"
        is Timeout -> "服务器没有及时响应，稍后再试"
        is RateLimited -> "操作太频繁，被服务器限制了，等一会儿再点"
        is Refused -> message
        is Server -> message?.takeIf { it.isNotBlank() }
            ?.let { "服务器拒绝了：$it" }
            ?: "服务器拒绝了（错误码 $code）"
        is Unexpected -> detail?.takeIf { it.isNotBlank() } ?: "没预想到的问题"
    }
}

internal data class CaptchaChallenge(
    val key: String,
    val jpegBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is CaptchaChallenge && key == other.key && jpegBytes.contentEquals(other.jpegBytes)

    override fun hashCode(): Int = 31 * key.hashCode() + jpegBytes.contentHashCode()
}

internal sealed interface QrCandidate {
    data class DeviceId(val id: String) : QrCandidate
    data class OpaqueId(val id: String) : QrCandidate
    data object Unreadable : QrCandidate
}

/**
 * What the sticker on the machine may contain. A bare device id is used
 * directly; anything else is exchanged at `qr/use` first.
 */
internal object DeviceQr {

    private const val MIN_LENGTH = 6
    private const val MAX_LENGTH = 32

    private val digits = Regex("^\\d+$")
    private val idShape = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private val schemes = setOf("http", "https", "ilife")
    private val routeWords = setOf(
        "q", "qr", "bm", "b", "s", "billing", "vip", "wallet",
        "dev", "device", "devices", "did", "bind", "binding",
    )

    fun parse(raw: String): QrCandidate {
        val text = raw.trim()
        if (text.isEmpty()) return QrCandidate.Unreadable
        if (isDeviceId(text)) return QrCandidate.DeviceId(text)

        val uri = runCatching { URI(text) }.getOrNull() ?: return QrCandidate.Unreadable
        if (uri.scheme?.lowercase() !in schemes) return QrCandidate.Unreadable

        queryValue(uri.rawQuery, "id")?.let { return QrCandidate.OpaqueId(it) }

        val fromPath = uri.rawPath.orEmpty()
            .split('/')
            .map(::decode)
            .filter { it.lowercase() !in routeWords && isPlausibleId(it) }
            .lastOrNull()
        if (fromPath != null) {
            return if (isDeviceId(fromPath)) QrCandidate.DeviceId(fromPath) else QrCandidate.OpaqueId(fromPath)
        }

        val loose = decode(uri.rawQuery.orEmpty().trim('/'))
        if (isPlausibleId(loose)) return QrCandidate.OpaqueId(loose)

        return QrCandidate.Unreadable
    }

    private fun isDeviceId(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH && digits.matches(value)

    private fun isPlausibleId(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH && idShape.matches(value)

    private fun queryValue(rawQuery: String?, key: String): String? =
        rawQuery.orEmpty()
            .split('&')
            .mapNotNull { part ->
                val name = part.substringBefore('=', "")
                if (name.lowercase() != key) null else decode(part.substringAfter('=', "")).trim()
            }
            .firstOrNull { it.isNotEmpty() }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
