package com.openschedule.plugin.hotwater

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.ScreenHeader
import kotlinx.coroutines.delay

@Composable
fun HotWaterScreen(modifier: Modifier = Modifier) {
    val viewModel = rememberHotWaterViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "热水") {
                if (state.signedIn) {
                    IconButton(onClick = viewModel::refreshDevices) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新设备")
                    }
                    IconButton(onClick = viewModel::signOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出登录")
                    }
                }
            }

            if (state.signedIn) {
                WaterPanel(state = state, viewModel = viewModel)
            } else {
                LoginPanel(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
internal fun rememberHotWaterViewModel(): HotWaterViewModel {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val session = HotWaterSession(context)
                return HotWaterViewModel(
                    repository = HotWaterRepository(HotWaterGateway(session), session),
                    session = session,
                ) as T
            }
        }
    }
    return viewModel(factory = factory)
}

@Composable
private fun LoginPanel(state: HotWaterUiState, viewModel: HotWaterViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = LocalFloatingNavSpace.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "登录惠生活账号",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = state.phoneInput,
            onValueChange = viewModel::onPhoneChange,
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.captchaText,
                onValueChange = viewModel::onCaptchaTextChange,
                label = { Text("验证码") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            CaptchaImage(
                captcha = state.captcha,
                loading = state.isLoadingCaptcha,
                onRefresh = { viewModel.refreshCaptcha() },
            )
        }

        Button(
            onClick = viewModel::sendSmsCode,
            enabled = state.canSendSms,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(if (state.isSmsSent) "重新发送" else "发送验证码", fontWeight = FontWeight.Medium)
        }
        if (state.resendSeconds > 0) {
            Text(
                text = "${state.resendSeconds} 秒后可重新发送",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isSmsSent) {
            OutlinedTextField(
                value = state.smsCodeInput,
                onValueChange = viewModel::onSmsCodeChange,
                label = { Text("短信验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::submitSmsCode,
                enabled = state.canSubmitSms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.isAuthBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        state.authError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TextButton(onClick = viewModel::toggleTokenLogin) {
            Text(if (state.showTokenLogin) "用短信验证码登录" else "用 token 登录")
        }

        if (state.showTokenLogin) {
            OutlinedTextField(
                value = state.tokenInput,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Authorization token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::submitToken,
                enabled = state.canSubmitToken,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isAuthBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录")
                }
            }
        }
    }
}

@Composable
private fun CaptchaImage(
    captcha: CaptchaChallenge?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val image: ImageBitmap? = remember(captcha) {
        captcha?.jpegBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = 112.dp, height = 56.dp)
            .clickable(onClick = onRefresh),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                image != null -> Image(
                    bitmap = image,
                    contentDescription = "验证码，点击刷新",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Text(
                    text = "刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterPanel(state: HotWaterUiState, viewModel: HotWaterViewModel) {
    var showBind by remember { mutableStateOf(false) }
    var bindCode by remember { mutableStateOf("") }

    var startBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(state.startBlockedUntilMillis) {
        val remaining = state.startBlockedUntilMillis - System.currentTimeMillis()
        startBlocked = remaining > 0
        if (remaining > 0) {
            delay(remaining)
            startBlocked = false
        }
    }
    val gateShut = startBlocked && !state.isRunning

    LaunchedEffect(state.bindMessage, state.bindFailed) {
        if (!state.bindFailed && state.bindMessage != null) {
            showBind = false
            bindCode = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.devices.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                state.devices.forEach { device ->
                    FilterChip(
                        selected = state.selected?.deviceId == device.deviceId,
                        onClick = { viewModel.selectDevice(device) },
                        label = { Text(device.displayName) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (state.isLoadingDevices && state.devices.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = state.selected?.displayName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.statusLine,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    state.isRunning -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                if (state.needsSetup) {
                    showBind = true
                } else {
                    viewModel.toggleWater()
                }
            },
            enabled = !state.isBusy && !gateShut,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = state.buttonLabel,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.bindMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.bindMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.bindFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
            )
        }

        if (state.deviceLoadFailed) {
            TextButton(onClick = viewModel::refreshDevices) { Text("重试") }
        }

        Spacer(Modifier.weight(0.7f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            state.accountName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.devices.isNotEmpty()) {
                TextButton(onClick = { showBind = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加设备")
                }
            }
        }
        Spacer(Modifier.height(LocalFloatingNavSpace.current + 8.dp))
    }

    if (showBind) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isBinding) {
                    showBind = false
                    viewModel.dismissBindMessage()
                }
            },
            icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
            title = { Text("添加设备") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bindCode,
                        onValueChange = { bindCode = it },
                        label = { Text("设备号 / 二维码内容") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.bindMessage?.takeIf { state.bindFailed }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.bindDevice(bindCode) },
                    enabled = bindCode.isNotBlank() && !state.isBinding,
                ) {
                    if (state.isBinding) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("添加")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBind = false
                        viewModel.dismissBindMessage()
                    },
                    enabled = !state.isBinding,
                ) { Text("取消") }
            },
        )
    }
}
