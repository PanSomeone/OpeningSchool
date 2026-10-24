package com.openschedule.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.MAX_NAV_SLOTS
import com.openschedule.core.model.NavSlot
import com.openschedule.core.model.ThemeMode
import com.openschedule.core.ui.IconPickerDialog
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle
import com.openschedule.core.ui.SettingsCard
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    hiddenPluginIds: Set<String> = emptySet(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val nav = settings.nav
        .sortedBy { it.order }
        .filterNot { it.id in hiddenPluginIds }
    val visibleCount = nav.count { it.visible }

    var iconPickerFor by remember { mutableStateOf<NavSlot?>(null) }
    var renaming by remember { mutableStateOf<NavSlot?>(null) }
    var aboutDialog by remember { mutableStateOf<AboutDialog?>(null) }
    var showWallpaper by remember { mutableStateOf(false) }
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
                        Switch(
                            checked = slot.visible,
                            onCheckedChange = { viewModel.setNavVisible(slot.id, it) },
                            enabled = slot.visible || visibleCount < MAX_NAV_SLOTS,
                            modifier = Modifier.padding(start = 4.dp),
                        )
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
                    value = null,
                    onClick = {
                        if (UPDATE_URL.isBlank()) {
                            aboutDialog = AboutDialog.UPDATE
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_URL)))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow(
                    icon = Icons.Filled.Favorite,
                    title = "赞助",
                    value = null,
                    onClick = {
                        if (SPONSOR_URL.isBlank()) {
                            aboutDialog = AboutDialog.SPONSOR
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_URL)))
                        }
                    },
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

    aboutDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { aboutDialog = null },
            title = {
                Text(
                    when (dialog) {
                        AboutDialog.UPDATE -> "检查更新"
                        AboutDialog.SPONSOR -> "赞助开源大学"
                    },
                )
            },
            text = {
                Text(
                    when (dialog) {
                        AboutDialog.UPDATE -> "更新地址尚未配置；代码仓库建立后会接入。"
                        AboutDialog.SPONSOR -> "赞助方式即将补充，感谢支持开源大学。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutDialog = null }) { Text("知道了") }
            },
        )
    }
}

private enum class AboutDialog { UPDATE, SPONSOR }

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
}.getOrNull().orEmpty().ifBlank { "1.0.0" }

private const val AUTHOR = "PanSomeone"

/** Set when the repository exists. */
private const val UPDATE_URL = ""

/** Set to a sponsorship page when available. */
private const val SPONSOR_URL = ""

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
