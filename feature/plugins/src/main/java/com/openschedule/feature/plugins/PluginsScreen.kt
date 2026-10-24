package com.openschedule.feature.plugins

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.TimetableImporter
import com.openschedule.core.plugin.AdapterProtocol
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle

data class BuiltInPluginUi(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val installed: Boolean,
    val inNav: Boolean,
    val hasSettings: Boolean,
)

@Composable
fun PluginsScreen(
    viewModel: PluginsViewModel,
    plugins: List<BuiltInPluginUi>,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenPluginSettings: (String) -> Unit,
) {
    var showMarket by remember { mutableStateOf(false) }
    var uninstalling by remember { mutableStateOf<BuiltInPluginUi?>(null) }
    val importers by viewModel.importers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    BackHandler(enabled = showMarket) { showMarket = false }

    if (showMarket) {
        PluginMarket(
            plugins = plugins,
            importers = importers,
            loading = loading,
            onRefresh = viewModel::refresh,
            onBack = { showMarket = false },
            onInstall = onInstall,
            onUninstall = { uninstalling = it },
            onOpenPluginSettings = onOpenPluginSettings,
        )
    } else {
        val installed = plugins.filter { it.installed }

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "插件")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
            ) {
                MarketButton(onClick = { showMarket = true })

                SectionTitle("已安装")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (installed.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "还没有插件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { showMarket = true }) { Text("打开插件市场") }
                            }
                        }
                        installed.forEachIndexed { index, plugin ->
                            PluginInstalledRow(
                                plugin = plugin,
                                onSettings = { onOpenPluginSettings(plugin.id) },
                                onRemove = { uninstalling = plugin },
                            )
                            if (index != installed.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

            }
        }
    }

    uninstalling?.let { plugin ->
        AlertDialog(
            onDismissRequest = { uninstalling = null },
            title = { Text("移除「${plugin.name}」？") },
            text = { Text("插件会从列表和导航栏移除，数据会保留，可再次添加") },
            confirmButton = {
                TextButton(onClick = {
                    onUninstall(plugin.id)
                    uninstalling = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { uninstalling = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MarketButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "插件市场",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "添加或移除功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PluginInstalledRow(
    plugin: BuiltInPluginUi,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginIcon(plugin.iconKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                if (plugin.inNav) {
                    Spacer(Modifier.size(8.dp))
                    Tag("导航")
                }
            }
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (plugin.hasSettings) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "移除插件",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PluginMarket(
    plugins: List<BuiltInPluginUi>,
    importers: List<TimetableImporter>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onInstall: (String) -> Unit,
    onUninstall: (BuiltInPluginUi) -> Unit,
    onOpenPluginSettings: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "插件市场",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        ) {
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新扫描适配器")
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    plugins.forEachIndexed { index, plugin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PluginIcon(plugin.iconKey)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                                    if (plugin.installed) {
                                        Spacer(Modifier.size(8.dp))
                                        Tag("已安装")
                                    }
                                }
                                Text(
                                    text = plugin.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (plugin.installed) {
                                if (plugin.hasSettings) {
                                    IconButton(onClick = { onOpenPluginSettings(plugin.id) }) {
                                        Icon(
                                            Icons.Filled.Settings,
                                            contentDescription = "设置",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                TextButton(onClick = { onUninstall(plugin) }) {
                                    Text("移除", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                FilledTonalButton(onClick = { onInstall(plugin.id) }) {
                                    Text("添加")
                                }
                            }
                        }
                        if (index != plugins.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            SectionTitle("导入适配器")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (loading && importers.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    if (importers.isEmpty() && !loading) {
                        Text(
                            text = "暂无适配器",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                    importers.forEachIndexed { index, importer ->
                        val external = importer.id.startsWith("external:")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = importer.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Tag(if (external) "外部" else "内置")
                                }
                                if (importer.description.isNotBlank()) {
                                    Text(
                                        text = importer.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = importer.id.removePrefix("external:"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        if (index != importers.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            SectionTitle("第三方接入")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "适配器以独立 APK 安装，通过绑定 Service 返回 JSON 课表，无需动态加载代码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProtocolRow("Action", AdapterProtocol.ACTION)
                    ProtocolRow("Permission", AdapterProtocol.PERMISSION)
                    ProtocolRow("API", AdapterProtocol.API_VERSION.toString())
                }
            }
        }
    }
}

@Composable
private fun PluginIcon(iconKey: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NavIcons.icon(iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProtocolRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
