package com.openschedule.plugin.hotwater

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

internal data class CloseResult(val settleRequired: Boolean)

internal class HotWaterRepository(
    private val gateway: HotWaterGateway,
    private val session: HotWaterSession,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun loadCaptcha(): Outcome<CaptchaChallenge> = withContext(ioDispatcher) {
        val key = buildString(CAPTCHA_KEY_LENGTH) {
            repeat(CAPTCHA_KEY_LENGTH) {
                append(ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.length)])
            }
        }
        runCatching { mapBytes(gateway.captcha(key)) }
            .getOrElse(::mapThrown)
            .let { outcome ->
                when (outcome) {
                    is Outcome.Success -> Outcome.Success(CaptchaChallenge(key, outcome.value))
                    is Outcome.Failure -> outcome
                }
            }
    }

    suspend fun sendSmsCode(
        phone: String,
        captchaText: String,
        captchaKey: String,
    ): Outcome<Unit> = withContext(ioDispatcher) {
        val body = SmsCodeRequest(
            phone = phone,
            captchaText = captchaText,
            captchaKey = captchaKey,
        )
        runCatching { mapAction(gateway.sendSmsCode(body)) }
            .getOrElse(::mapThrown)
    }

    suspend fun login(phone: String, smsCode: String): Outcome<Account> =
        withContext(ioDispatcher) {
            val body = LoginRequest(
                smsCode = smsCode,
                phone = phone,
                clientId = session.clientId,
            )
            val token = runCatching { map(gateway.login(body)) }
                .getOrElse(::mapThrown)
                .let { outcome ->
                    when (outcome) {
                        is Outcome.Success -> outcome.value.al?.token.orEmpty()
                        is Outcome.Failure -> return@withContext outcome
                    }
                }
            if (token.isBlank()) {
                return@withContext Outcome.Failure(
                    AppError.Unexpected("登录成功但服务器没有返回 token"),
                )
            }
            adoptToken(token)
        }

    /**
     * The gateway answers `code: 0` with an anonymous payload for any token it
     * does not know, so success is only readable from the account being present.
     */
    suspend fun adoptToken(token: String): Outcome<Account> = withContext(ioDispatcher) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            return@withContext Outcome.Failure(AppError.Unexpected("token 不能为空"))
        }
        session.token = trimmed

        runCatching { map(gateway.master()) }
            .getOrElse(::mapThrown)
            .let { outcome ->
                when (outcome) {
                    is Outcome.Success -> {
                        val account = outcome.value.account
                        val id = account?.id?.takeIf { it.isNotBlank() }
                        if (id == null) {
                            clearSession()
                            Outcome.Failure(
                                AppError.Unexpected("服务器不认识这个 token"),
                            )
                        } else {
                            session.uid = id
                            session.accountName = account.displayName()
                            Outcome.Success(account)
                        }
                    }

                    is Outcome.Failure -> {
                        clearSession()
                        outcome
                    }
                }
            }
    }

    fun logout() = clearSession()

    suspend fun loadDevices(): Outcome<List<Device>> = withContext(ioDispatcher) {
        runCatching { map(gateway.master()) }
            .getOrElse(::mapThrown)
            .let { outcome ->
                when (outcome) {
                    is Outcome.Success -> {
                        val data = outcome.value
                        val hadToken = !session.token.isNullOrBlank()
                        if (hadToken && data.account?.id.isNullOrBlank()) {
                            Outcome.Failure(AppError.Unauthorized)
                        } else {
                            Outcome.Success(data.toDevices())
                        }
                    }

                    is Outcome.Failure -> outcome
                }
            }
    }

    suspend fun openWater(device: Device): Outcome<Unit> = withContext(ioDispatcher) {
        runCatching { mapStartAction(gateway.start(device.deviceId)) }
            .getOrElse(::mapThrown)
    }

    suspend fun closeWater(device: Device): Outcome<CloseResult> = withContext(ioDispatcher) {
        runCatching { mapCloseAction(gateway.stop(device.deviceId)) }
            .getOrElse(::mapThrown)
    }

    /**
     * A pasted code from the sticker, tried in the order its shape suggests and
     * falling back to the other interpretation when the first is refused.
     */
    suspend fun bindDevice(code: String): Outcome<Device> = withContext(ioDispatcher) {
        when (val candidate = DeviceQr.parse(code)) {
            QrCandidate.Unreadable -> Outcome.Failure(
                AppError.Unexpected("这个二维码里没有设备编号"),
            )

            is QrCandidate.DeviceId -> {
                val direct = favourite(candidate.id).mapToDevice(candidate.id)
                if (direct is Outcome.Success) return@withContext direct
                val resolved = resolveThenFavourite(candidate.id)
                if (resolved is Outcome.Success) resolved else direct
            }

            is QrCandidate.OpaqueId -> {
                val resolved = resolveThenFavourite(candidate.id)
                if (resolved is Outcome.Success) return@withContext resolved
                val direct = favourite(candidate.id).mapToDevice(candidate.id)
                if (direct is Outcome.Success) direct else resolved
            }
        }
    }

    private suspend fun favourite(deviceId: String): Outcome<Unit> =
        runCatching { mapAction(gateway.favourite(deviceId)) }
            .getOrElse(::mapThrown)

    private suspend fun resolveThenFavourite(id: String): Outcome<Device> =
        when (val resolved = resolveViaQrUse(id)) {
            is Outcome.Success -> favourite(resolved.value).mapToDevice(resolved.value)
            is Outcome.Failure -> resolved
        }

    private suspend fun resolveViaQrUse(id: String): Outcome<String> =
        runCatching { map(gateway.qrUse(id)) }
            .getOrElse(::mapThrown)
            .let { outcome ->
                when (outcome) {
                    is Outcome.Success -> {
                        val deviceId = outcome.value.device?.id.orEmpty()
                        if (deviceId.isBlank()) {
                            Outcome.Failure(AppError.Unexpected("服务器没有认出这个二维码"))
                        } else {
                            Outcome.Success(deviceId)
                        }
                    }

                    is Outcome.Failure -> outcome
                }
            }

    private suspend fun Outcome<Unit>.mapToDevice(deviceId: String): Outcome<Device> = when (this) {
        is Outcome.Failure -> this
        is Outcome.Success -> when (val devices = loadDevices()) {
            is Outcome.Failure -> devices
            is Outcome.Success -> devices.value.firstOrNull { it.deviceId == deviceId }
                ?.let { Outcome.Success(it) }
                ?: Outcome.Failure(
                    AppError.Unexpected("服务器接受了这台设备，但列表里还没有它，请稍后刷新"),
                )
        }
    }

    private fun clearSession() {
        session.token = null
        session.uid = null
        session.accountName = null
        session.selectedDeviceId = null
    }

    private fun Account.displayName(): String =
        name?.takeIf { it.isNotBlank() }
            ?: phone?.takeIf { it.isNotBlank() }
            ?: "已登录"

    private companion object {
        const val CAPTCHA_KEY_LENGTH = 10
        const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val HTTP_UNAUTHORIZED = 401
    }
}

// ------------------------------------------------------------------- mapping

private fun <T : Any> map(response: Response<out Envelope<T>>): Outcome<T> {
    envelopeError(response)?.let { return Outcome.Failure(it) }
    val data = response.body()?.data
        ?: return Outcome.Failure(AppError.Unexpected("响应缺少 data 字段"))
    return Outcome.Success(data)
}

private fun mapAction(response: Response<out Envelope<*>>): Outcome<Unit> {
    envelopeError(response)?.let { return Outcome.Failure(it) }
    return Outcome.Success(Unit)
}

/** Opening tolerates "already open": the state the request wanted is the state. */
private fun mapStartAction(response: Response<out Envelope<*>>): Outcome<Unit> {
    val error = envelopeError(response)
    if (error != null && !isAlreadyOpen(error)) return Outcome.Failure(error)
    return Outcome.Success(Unit)
}

/** Closing tolerates only `-2`, which means the valve is shut but a session needs settling. */
private fun mapCloseAction(response: Response<out Envelope<*>>): Outcome<CloseResult> {
    val error = envelopeError(response)
    val settled = error is AppError.Refused && error.code == ApiConstants.CODE_SETTLE_ON_DEVICE
    if (error != null && !settled) return Outcome.Failure(error)
    return Outcome.Success(CloseResult(settleRequired = settled))
}

private fun mapBytes(response: Response<ResponseBody>): Outcome<ByteArray> {
    if (!response.isSuccessful) {
        return Outcome.Failure(AppError.Server(response.code(), null))
    }
    val body = response.body()
        ?: return Outcome.Failure(AppError.Unexpected("验证码响应为空"))
    return runCatching { body.bytes() }.fold(
        onSuccess = { bytes ->
            if (bytes.isEmpty()) {
                Outcome.Failure(AppError.Unexpected("验证码图片为空"))
            } else {
                Outcome.Success(bytes)
            }
        },
        onFailure = { Outcome.Failure(toAppError(it)) },
    )
}

private fun mapThrown(throwable: Throwable): Outcome<Nothing> =
    Outcome.Failure(toAppError(throwable))

private fun toAppError(throwable: Throwable): AppError = when (throwable) {
    is UnknownHostException -> AppError.Offline
    is SocketTimeoutException -> AppError.Timeout
    is SerializationException -> AppError.Unexpected("响应格式无法解析")
    is IOException -> AppError.Offline
    else -> AppError.Unexpected(throwable.message?.take(160))
}

private fun envelopeError(response: Response<out Envelope<Any?>>): AppError? {
    if (!response.isSuccessful) {
        if (response.code() == 401) return AppError.Unauthorized
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val message = raw?.takeIf { it.isNotBlank() }?.let { body ->
            runCatching {
                hotWaterJson.decodeFromString(EnvelopeError.serializer(), body).message
            }.getOrNull()
        }
        return AppError.Server(response.code(), message)
    }

    val envelope = response.body()
        ?: return AppError.Unexpected("服务器返回了空响应体（HTTP ${response.code()}）")

    if (envelope.isSuccess) return null
    if (envelope.isSessionExpired) return AppError.Unauthorized
    if (envelope.isRateLimited) return AppError.RateLimited

    val known = KNOWN_REFUSALS[envelope.code]
    return if (known != null) {
        AppError.Refused(envelope.code, known)
    } else {
        AppError.Server(envelope.code, envelope.message)
    }
}

private fun isAlreadyOpen(error: AppError): Boolean {
    if (error is AppError.Refused && error.code == ApiConstants.CODE_SETTLE_ON_DEVICE) return true
    val message = when (error) {
        is AppError.Server -> error.message
        is AppError.Refused -> error.message
        else -> null
    } ?: return false
    if (!message.contains("运行")) return false
    return message.contains("设备") || message.contains("already")
}

private val KNOWN_REFUSALS: Map<Int, String> = mapOf(
    ApiConstants.CODE_PAYMENT_REQUIRED to "余额不足或这一单还没付钱。",
    ApiConstants.CODE_ARREARS to "这个账号欠费了。",
    ApiConstants.CODE_NO_AGREEMENT to "还没有签约自动扣费。",
    ApiConstants.CODE_AGREEMENT_EXPIRED to "自动扣费签约过期了。",
    ApiConstants.CODE_NO_CARD to "这个账号还没绑定校园卡。",
    ApiConstants.CODE_DEVICE_NOT_READY to "设备还没准备好，等一下再试。",
    ApiConstants.CODE_WRONG_SCOPE to "登录的权限不对，需要设备控制登录。",
    ApiConstants.CODE_DEVICE_IN_USE to "这台设备有人正在用，等它空下来再试。",
    ApiConstants.CODE_SETTLE_ON_DEVICE to "上一次的使用还没结算，需要在设备上点一下结算。",
)

@kotlinx.serialization.Serializable
private data class EnvelopeError(
    @kotlinx.serialization.SerialName("msg") val msg: String? = null,
    @kotlinx.serialization.SerialName("message") val messageField: String? = null,
) {
    val message: String? get() = msg ?: messageField
}
