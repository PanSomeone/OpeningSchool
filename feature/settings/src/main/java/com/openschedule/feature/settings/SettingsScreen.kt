package com.openschedule.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openschedule.core.data.AnnouncementStore
import com.openschedule.core.data.AppLog
import com.openschedule.core.model.MAX_NAV_SLOTS
import com.openschedule.core.model.NavSlot
import com.openschedule.core.model.ThemeMode
import com.openschedule.core.ui.IconPickerDialog
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle
import com.openschedule.core.ui.SettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    hiddenPluginIds: Set<String> = emptySet(),
    announcementStore: AnnouncementStore,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val announcements by announcementStore.announcements.collectAsStateWithLifecycle()
    val announcementReadIds by announcementStore.readIds.collectAsStateWithLifecycle()
    val announcementUnread = announcements.count { it.id !in announcementReadIds }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val nav = settings.nav
        .sortedBy { it.order }
        .filterNot { it.id in hiddenPluginIds }
    val visibleCount = nav.count { it.visible }

    var iconPickerFor by remember { mutableStateOf<NavSlot?>(null) }
    var renaming by remember { mutableStateOf<NavSlot?>(null) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showAnnouncements by remember { mutableStateOf(false) }
    var exportingLog by remember { mutableStateOf(false) }
    var showSponsor by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val updateViewModel: UpdateViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UpdateViewModel(context.applicationContext) as T
                }
            }
        },
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val wallpaperStatus = when {
        settings.pageWallpapers.isNotEmpty() && settings.appWallpaper != null ->
            "全局 + ${settings.pageWallpapers.size} 页"

        settings.pageWallpapers.isNotEmpty() -> "按页面"
        settings.appWallpaper != null -> "全局"
        else -> "未设置"
    }

    if (showWallpaper) {
        WallpaperSettingsScreen(
            viewModel = viewModel,
            hiddenPluginIds = hiddenPluginIds,
            onBack = { showWallpaper = false },
        )
        return
    }

    if (showAnnouncements) {
        AnnouncementHistoryScreen(
            store = announcementStore,
            onBack = { showAnnouncements = false },
        )
        return
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "设置")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            SectionTitle("外观")
            SettingsCard {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text("主题", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeChoice("系统", Icons.Filled.BrightnessAuto, settings.themeMode == ThemeMode.SYSTEM) {
                            viewModel.update { it.copy(themeMode = ThemeMode.SYSTEM) }
                        }
                        ThemeChoice("浅色", Icons.Filled.LightMode, settings.themeMode == ThemeMode.LIGHT) {
                            viewModel.update { it.copy(themeMode = ThemeMode.LIGHT) }
                        }
                        ThemeChoice("深色", Icons.Filled.DarkMode, settings.themeMode == ThemeMode.DARK) {
                            viewModel.update { it.copy(themeMode = ThemeMode.DARK) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWallpaper = true }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "壁纸",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                    Text(
                        text = wallpaperStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("底部导航 $visibleCount/$MAX_NAV_SLOTS")
            SettingsCard {
                if (nav.isEmpty()) {
                    Text(
                        text = "暂无可配置项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                nav.forEachIndexed { index, slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { iconPickerFor = slot }) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = NavIcons.icon(slot.iconKey),
                                        contentDescription = "更换图标",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { renaming = slot }
                                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = slot.label.ifBlank { slot.id },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "重命名",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(14.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveNav(slot.id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "上移",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveNav(slot.id, 1) },
                            enabled = index < nav.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "下移",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (slot.id == "settings") {
                            Text(
                                text = "固定",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                            )
                        } else {
                            Switch(
                                checked = slot.visible,
                                onCheckedChange = { viewModel.setNavVisible(slot.id, it) },
                                enabled = slot.visible || visibleCount < MAX_NAV_SLOTS,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                var opacity by remember(settings.navOpacity) {
                    mutableFloatStateOf(settings.navOpacity)
                }
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("透明度", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(opacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        onValueChangeFinished = {
                            viewModel.update { it.copy(navOpacity = opacity) }
                        },
                        valueRange = com.openschedule.core.model.AppSettings.MIN_NAV_OPACITY..1f,
                    )
                }
                }

            SectionTitle("公告")
            SettingsCard {
                AboutRow(
                    icon = Icons.Filled.Campaign,
                    title = "历史公告",
                    value = when {
                        announcementUnread > 0 -> "$announcementUnread 条未读"
                        announcements.isNotEmpty() -> "全部已读"
                        else -> "暂无"
                    },
                    onClick = { showAnnouncements = true },
                )
            }

            SectionTitle("反馈与日志")
            SettingsCard {
                AboutRow(
                    icon = Icons.Filled.Feedback,
                    title = "反馈",
                    value = "GitHub Issues",
                    onClick = {
                        val opened = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL)))
                        }.isSuccess
                        if (!opened) {
                            scope.launch { snackbar.showSnackbar("没有可用的浏览器") }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.Groups,
                    title = "反馈群",
                    value = FEEDBACK_GROUP,
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("反馈群", FEEDBACK_GROUP),
                        )
                        scope.launch { snackbar.showSnackbar("群号已复制") }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.Description,
                    title = "导出日志",
                    value = if (exportingLog) "正在导出" else null,
                    onClick = {
                        if (!exportingLog) {
                            exportingLog = true
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    runCatching { AppLog.export(context) }.getOrNull()
                                }
                                if (file == null) {
                                    exportingLog = false
                                    snackbar.showSnackbar("日志导出失败")
                                    return@launch
                                }
                                AppLog.i("Settings", "导出日志 ${file.name}")
                                val uri = runCatching {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                }.getOrNull()
                                exportingLog = false
                                if (uri == null) {
                                    snackbar.showSnackbar("日志导出失败")
                                    return@launch
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "开源大学运行日志")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val opened = runCatching {
                                    context.startActivity(Intent.createChooser(send, "分享日志"))
                                }.isSuccess
                                if (!opened) snackbar.showSnackbar("没有可用的分享应用")
                            }
                        }
                    },
                )
            }

            SectionTitle("关于")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开源大学", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "OpeningSchool",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "v$versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.Person,
                    title = "作者",
                    value = AUTHOR,
                    onClick = null,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "检查更新",
                    value = when (val state = updateState) {
                        is UpdateState.Available -> state.info.version
                        is UpdateState.Downloading -> "下载中"
                        is UpdateState.Ready -> "待安装"
                        is UpdateState.UpToDate -> "已是最新"
                        else -> null
                    },
                    onClick = {
                        showUpdate = true
                        when (updateState) {
                            is UpdateState.Idle,
                            is UpdateState.UpToDate,
                            is UpdateState.Failed,
                            -> updateViewModel.check()

                            else -> Unit
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.Favorite,
                    title = "赞助",
                    value = null,
                    onClick = { showSponsor = true },
                )
            }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalFloatingNavSpace.current + 16.dp),
        )
    }

    iconPickerFor?.let { slot ->
        IconPickerDialog(
            currentKey = slot.iconKey,
            onPick = { key ->
                viewModel.setNavIcon(slot.id, key)
                iconPickerFor = null
            },
            onDismiss = { iconPickerFor = null },
        )
    }

    renaming?.let { slot ->
        NavLabelDialog(
            initial = slot.label.ifBlank { slot.id },
            onSave = { label ->
                viewModel.setNavLabel(slot.id, label)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    if (showUpdate) {
        UpdateSheet(
            state = updateState,
            currentVersion = versionName,
            onCheck = updateViewModel::check,
            onDownload = updateViewModel::download,
            onInstall = {
                if (!updateViewModel.install()) {
                    scope.launch {
                        snackbar.showSnackbar("无法启动安装器，请允许安装未知应用")
                    }
                }
            },
            onOpenRelease = updateViewModel::openReleasePage,
            onDismiss = { showUpdate = false },
        )
    }

    if (showSponsor) {
        SponsorSheet(onDismiss = { showSponsor = false })
    }
}

private enum class SponsorPlatform { WECHAT, ALIPAY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsorSheet(onDismiss: () -> Unit) {
    var platform by remember { mutableStateOf(SponsorPlatform.WECHAT) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("赞助开源大学", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "感谢支持。赞助用于持续维护和更新，纯属自愿，不影响任何功能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = platform == SponsorPlatform.WECHAT,
                    onClick = { platform = SponsorPlatform.WECHAT },
                    label = { Text("微信") },
                )
                FilterChip(
                    selected = platform == SponsorPlatform.ALIPAY,
                    onClick = { platform = SponsorPlatform.ALIPAY },
                    label = { Text("支付宝") },
                )
            }
            Crossfade(
                targetState = platform,
                animationSpec = tween(durationMillis = 180),
                label = "sponsorQr",
            ) { selected ->
                val (resource, description, ratio) = when (selected) {
                    SponsorPlatform.WECHAT -> Triple(
                        R.drawable.sponsor_wechat,
                        "微信收款二维码",
                        800f / 1090f,
                    )

                    SponsorPlatform.ALIPAY -> Triple(
                        R.drawable.sponsor_alipay,
                        "支付宝收款二维码",
                        800f / 1201f,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Image(
                        painter = painterResource(resource),
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .padding(10.dp),
                    )
                }
            }
            Text(
                text = if (platform == SponsorPlatform.WECHAT) "微信扫一扫" else "支付宝扫一扫",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("软件更新", style = MaterialTheme.typography.titleLarge)
            when (state) {
                UpdateState.Idle -> {
                    Text("检查是否有新的测试版", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("检查更新")
                    }
                }

                UpdateState.Checking -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("正在检查更新…", style = MaterialTheme.typography.bodyMedium)
                }

                is UpdateState.UpToDate -> {
                    Text("已是最新版本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "当前 v${state.currentVersion} · 最新 v${state.latestVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCheck) { Text("重新检查") }
                }

                is UpdateState.Available -> {
                    Text(
                        text = "发现新版本 ${state.info.version}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "当前 v$currentVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.info.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = "安装包 ${formatUpdateSize(state.info.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("下载更新")
                    }
                    TextButton(onClick = onCheck) { Text("重新检查") }
                    TextButton(onClick = onOpenRelease) { Text("在浏览器查看") }
                }

                is UpdateState.Downloading -> {
                    Text("正在下载更新", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is UpdateState.Ready -> {
                    Text("下载完成", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.verified) "SHA-256 校验通过" else "未校验",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.verified) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text("立即安装")
                    }
                    Text(
                        text = "Android 会弹出系统安装确认",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCheck) { Text("重新检查") }
                }

                is UpdateState.Failed -> {
                    Text("更新失败", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("重试")
                    }
                    TextButton(onClick = onOpenRelease) { Text("在浏览器打开") }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes <= 0 -> "未知大小"
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> "${bytes / 1024} KB"
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun appVersionName(context: Context): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull().orEmpty().ifBlank { "1.0.0-beta18" }

private const val AUTHOR = "PanSomeone"

private const val FEEDBACK_URL = "https://github.com/PanSomeone/OpeningSchool/issues"

private const val FEEDBACK_GROUP = "1126108304"


@Composable
private fun ThemeChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text(label) },
    )
}

@Composable
private fun NavLabelDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val trimmed = value.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= MAX_NAV_LABEL) value = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = trimmed.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private const val MAX_NAV_LABEL = 6
