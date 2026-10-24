package com.openschedule.plugin.hotwater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HotWaterUiState(
    val signedIn: Boolean = false,
    val accountName: String? = null,
    val sessionExpired: Boolean = false,

    val phoneInput: String = "",
    val captchaText: String = "",
    val smsCodeInput: String = "",
    val captcha: CaptchaChallenge? = null,
    val isLoadingCaptcha: Boolean = false,
    val isSmsSent: Boolean = false,
    val resendSeconds: Int = 0,
    val isAuthBusy: Boolean = false,
    val authError: String? = null,
    val showTokenLogin: Boolean = false,
    val tokenInput: String = "",

    val isLoadingDevices: Boolean = false,
    val devices: List<Device> = emptyList(),
    val selected: Device? = null,
    val isBusy: Boolean = false,
    val runningOverride: Boolean? = null,
    val startBlockedUntilMillis: Long = 0L,
    val settleRequired: Boolean = false,
    val errorMessage: String? = null,
    val deviceLoadFailed: Boolean = false,
    val isBinding: Boolean = false,
    val bindMessage: String? = null,
    val bindFailed: Boolean = false,
) {
    val isRunning: Boolean get() = runningOverride ?: (selected?.isRunning == true)

    fun isStartBlocked(now: Long): Boolean = now < startBlockedUntilMillis

    val needsSettling: Boolean get() = settleRequired && !isRunning

    val needsSetup: Boolean get() = devices.isEmpty()

    val canSendSms: Boolean
        get() = !isAuthBusy &&
            phoneInput.length >= PHONE_LENGTH &&
            captcha != null &&
            captchaText.isNotBlank()

    val canResendSms: Boolean get() = canSendSms && resendSeconds == 0

    val canSubmitSms: Boolean get() = !isAuthBusy && isSmsSent && smsCodeInput.isNotBlank()

    val canSubmitToken: Boolean get() = !isAuthBusy && tokenInput.trim().length >= MIN_TOKEN_LENGTH

    val statusLine: String
        get() = when {
            isBusy -> "正在处理"
            needsSetup -> "还没有可用的设备"
            isRunning -> "热水已开"
            needsSettling -> "上次没结算"
            else -> "热水已关"
        }

    val buttonLabel: String
        get() = when {
            needsSetup -> "添加设备"
            isRunning -> "关闭热水"
            else -> "一键启动热水"
        }

    companion object {
        const val PHONE_LENGTH = 11
        const val MIN_TOKEN_LENGTH = 16
        const val RESEND_COOLDOWN_SECONDS = 30
    }
}

internal class HotWaterViewModel(
    private val repository: HotWaterRepository,
    private val session: HotWaterSession,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HotWaterUiState(
            signedIn = session.token != null,
            accountName = session.accountName,
        ),
    )
    val state: StateFlow<HotWaterUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        if (_state.value.signedIn) refreshDevices() else refreshCaptcha()
    }

    // --------------------------------------------------------------- auth form

    fun onPhoneChange(value: String) {
        _state.update {
            it.copy(
                phoneInput = value.filter(Char::isDigit).take(HotWaterUiState.PHONE_LENGTH),
                authError = null,
            )
        }
    }

    fun onCaptchaTextChange(value: String) {
        _state.update { it.copy(captchaText = value.trim(), authError = null) }
    }

    fun onSmsCodeChange(value: String) {
        _state.update {
            it.copy(smsCodeInput = value.filter(Char::isDigit), authError = null)
        }
    }

    fun toggleTokenLogin() {
        _state.update { it.copy(showTokenLogin = !it.showTokenLogin, authError = null) }
    }

    fun onTokenChange(value: String) {
        _state.update { it.copy(tokenInput = value, authError = null) }
    }

    fun refreshCaptcha(reason: String? = null) {
        if (_state.value.isLoadingCaptcha) return
        _state.update { it.copy(isLoadingCaptcha = true, authError = reason) }

        viewModelScope.launch {
            when (val result = repository.loadCaptcha()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoadingCaptcha = false,
                        captcha = result.value,
                        captchaText = "",
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingCaptcha = false, authError = result.error.toChinese())
                }
            }
        }
    }

    fun sendSmsCode() {
        val current = _state.value
        val captcha = current.captcha ?: return
        if (!current.canSendSms) return

        _state.update { it.copy(isAuthBusy = true, authError = null) }
        viewModelScope.launch {
            val result = repository.sendSmsCode(
                phone = current.phoneInput,
                captchaText = current.captchaText,
                captchaKey = captcha.key,
            )
            when (result) {
                is Outcome.Success -> {
                    _state.update { it.copy(isAuthBusy = false, isSmsSent = true) }
                    startResendCooldown()
                }

                is Outcome.Failure -> {
                    val message = result.error.toChinese()
                    _state.update { it.copy(isAuthBusy = false, authError = message) }
                    if (result.error is AppError.Server) refreshCaptcha(reason = message)
                }
            }
        }
    }

    fun submitSmsCode() {
        val current = _state.value
        if (!current.canSubmitSms) return

        _state.update { it.copy(isAuthBusy = true, authError = null) }
        viewModelScope.launch {
            when (val result = repository.login(current.phoneInput, current.smsCodeInput)) {
                is Outcome.Success -> adoptSignedIn(result.value)
                is Outcome.Failure -> _state.update {
                    it.copy(isAuthBusy = false, authError = result.error.toChinese())
                }
            }
        }
    }

    fun submitToken() {
        val current = _state.value
        if (!current.canSubmitToken) return

        _state.update { it.copy(isAuthBusy = true, authError = null) }
        viewModelScope.launch {
            when (val result = repository.adoptToken(current.tokenInput)) {
                is Outcome.Success -> adoptSignedIn(result.value)
                is Outcome.Failure -> _state.update {
                    it.copy(isAuthBusy = false, authError = result.error.toChinese())
                }
            }
        }
    }

    private fun adoptSignedIn(account: Account) {
        _state.update {
            it.copy(
                signedIn = true,
                sessionExpired = false,
                isAuthBusy = false,
                accountName = session.accountName ?: account.displayNameFallback(),
                phoneInput = "",
                captchaText = "",
                smsCodeInput = "",
                tokenInput = "",
                captcha = null,
                authError = null,
            )
        }
        refreshDevices()
    }

    private fun Account.displayNameFallback(): String =
        name?.takeIf { it.isNotBlank() }
            ?: phone?.takeIf { it.isNotBlank() }
            ?: "已登录"

    private fun startResendCooldown() {
        countdownJob?.cancel()
        _state.update { it.copy(resendSeconds = HotWaterUiState.RESEND_COOLDOWN_SECONDS) }
        countdownJob = viewModelScope.launch {
            repeat(HotWaterUiState.RESEND_COOLDOWN_SECONDS) {
                delay(1_000)
                _state.update {
                    it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0))
                }
            }
            refreshCaptcha()
        }
    }

    fun signOut() {
        countdownJob?.cancel()
        repository.logout()
        _state.value = HotWaterUiState()
        refreshCaptcha()
    }

    // ---------------------------------------------------------------- devices

    fun refreshDevices() {
        if (_state.value.isLoadingDevices) return
        if (!_state.value.signedIn) return

        _state.update { it.copy(isLoadingDevices = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.loadDevices()) {
                is Outcome.Success -> _state.update { state ->
                    val selected = resolveSelection(
                        devices = result.value,
                        rememberedId = session.selectedDeviceId ?: state.selected?.deviceId,
                    )
                    session.selectedDeviceId = selected?.deviceId
                    state.copy(
                        isLoadingDevices = false,
                        devices = result.value,
                        selected = selected,
                        deviceLoadFailed = false,
                        sessionExpired = false,
                    )
                }

                is Outcome.Failure -> {
                    if (result.error is AppError.Unauthorized) {
                        expireSession()
                    } else {
                        _state.update {
                            it.copy(
                                isLoadingDevices = false,
                                deviceLoadFailed = true,
                                errorMessage = result.error.toChinese(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectDevice(device: Device) {
        if (_state.value.isBusy) return
        session.selectedDeviceId = device.deviceId
        _state.update {
            val same = it.selected?.deviceId == device.deviceId
            it.copy(
                selected = device,
                errorMessage = null,
                runningOverride = if (same) it.runningOverride else null,
                settleRequired = if (same) it.settleRequired else false,
            )
        }
    }

    fun toggleWater() {
        val current = _state.value
        val device = current.selected ?: return
        if (current.isBusy) return
        if (current.isRunning) {
            closeWater(device)
            return
        }
        if (current.isStartBlocked(now())) return
        openWater(device)
    }

    private fun openWater(device: Device) {
        _state.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.openWater(device)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            runningOverride = true,
                            settleRequired = false,
                            startBlockedUntilMillis = now() + ACTION_GAP_MILLIS,
                        )
                    }
                }

                is Outcome.Failure -> finishWithFailure(result.error)
            }
        }
    }

    private fun closeWater(device: Device) {
        _state.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.closeWater(device)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            runningOverride = false,
                            settleRequired = result.value.settleRequired,
                            selected = it.selected?.copy(status = Device.STATUS_IDLE),
                        )
                    }
                }

                is Outcome.Failure -> finishWithFailure(result.error)
            }
        }
    }

    private fun finishWithFailure(error: AppError) {
        if (error is AppError.Unauthorized) {
            expireSession()
            return
        }
        _state.update {
            it.copy(isBusy = false, errorMessage = error.toChinese())
        }
    }

    private fun expireSession() {
        repository.logout()
        _state.value = HotWaterUiState(
            signedIn = false,
            sessionExpired = true,
            authError = "登录状态失效了，重新登录一次",
        )
        refreshCaptcha()
    }

    fun bindDevice(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || _state.value.isBinding) return

        _state.update { it.copy(isBinding = true, bindMessage = null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.bindDevice(trimmed)) {
                is Outcome.Success -> {
                    session.selectedDeviceId = result.value.deviceId
                    _state.update {
                        it.copy(
                            isBinding = false,
                            selected = result.value,
                            devices = it.devices + result.value,
                            bindMessage = "已添加「${result.value.displayName}」",
                            bindFailed = false,
                            runningOverride = null,
                            settleRequired = false,
                        )
                    }
                    refreshDevices()
                }

                is Outcome.Failure -> {
                    if (result.error is AppError.Unauthorized) {
                        expireSession()
                    } else {
                        _state.update {
                            it.copy(
                                isBinding = false,
                                bindMessage = result.error.toChinese(),
                                bindFailed = true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissBindMessage() {
        _state.update { it.copy(bindMessage = null) }
    }

    private companion object {
        const val ACTION_GAP_MILLIS = 3_000L

        fun resolveSelection(devices: List<Device>, rememberedId: String?): Device? {
            if (devices.isEmpty()) return null
            return devices.firstOrNull { it.deviceId == rememberedId }
                ?: devices.singleOrNull()
                ?: devices.firstOrNull { it.isRunning }
                ?: devices.first()
        }
    }
}
