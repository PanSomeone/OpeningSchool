package com.openschedule.feature.plugins

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openschedule.core.data.InstalledModule
import com.openschedule.core.data.ModuleManifest
import com.openschedule.core.data.NativePluginRuntime
import com.openschedule.core.data.NativePluginSource
import com.openschedule.core.data.PluginModuleStore
import com.openschedule.core.data.PluginRuntime
import com.openschedule.core.model.TimetableImporter
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle
import java.io.File
import java.util.Locale

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
    moduleStore: PluginModuleStore,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenPlugin: (String) -> Unit,
    onOpenPluginSettings: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modules: List<InstalledModule> = emptyList(),
    moduleStates: Map<String, PluginRuntime.PluginState> = emptyMap(),
    canOpenModule: (String) -> Boolean = { false },
    onOpenModule: (String) -> Unit = {},
    nativePlugins: List<NativePluginRuntime.PluginInfo> = emptyList(),
    onToggleNative: (String, Boolean) -> Unit = { _, _ -> },
    onOpenNative: (String) -> Unit = {},
    onRefreshNative: () -> Unit = {},
) {
    var showMarket by remember { mutableStateOf(false) }
    var uninstalling by remember { mutableStateOf<BuiltInPluginUi?>(null) }
    var removingModule by remember { mutableStateOf<InstalledModule?>(null) }
    val importers by viewModel.importers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    BackHandler(enabled = showMarket) { showMarket = false }

    if (showMarket) {
        PluginMarket(
            plugins = plugins,
            importers = importers,
            moduleStore = moduleStore,
            loading = loading,
            onRefresh = viewModel::refresh,
            onBack = { showMarket = false },
            onInstall = onInstall,
            onUninstall = { uninstalling = it },
            onOpenPluginSettings = onOpenPluginSettings,
        )
    } else {
        val installed = plugins.filter { it.installed }
        val installedModules = modules.filter {
            it.manifest.apiVersion == ModuleManifest.API_V2 &&
                it.manifest.type != ModuleManifest.TYPE_NATIVE
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "插件") {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }

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
                        if (installed.isEmpty() && installedModules.isEmpty()) {
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
                                onOpen = { onOpenPlugin(plugin.id) },
                                onSettings = { onOpenPluginSettings(plugin.id) },
                                onRemove = { uninstalling = plugin },
                            )
                            if (index != installed.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (installedModules.isNotEmpty()) {
                            if (installed.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Text(
                                text = "模块",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        installedModules.forEachIndexed { index, module ->
                            InstalledModuleRow(
                                module = module,
                                state = moduleStates[module.manifest.id],
                                openable = canOpenModule(module.manifest.id),
                                onOpen = { onOpenModule(module.manifest.id) },
                                onToggle = {
                                    moduleStore.setEnabled(module.manifest.id, !module.enabled)
                                },
                                onRemove = { removingModule = module },
                            )
                            if (index != installedModules.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                SectionTitle("原生插件")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (nativePlugins.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "未发现原生插件。安装插件 APK 或从插件市场添加模块后点按刷新。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onRefreshNative) { Text("刷新") }
                            }
                        }
                        nativePlugins.forEachIndexed { index, plugin ->
                            NativePluginRow(
                                plugin = plugin,
                                onOpen = { onOpenNative(plugin.id) },
                                onToggle = { onToggleNative(plugin.id, !plugin.enabled) },
                                onRetry = onRefreshNative,
                            )
                            if (index != nativePlugins.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (nativePlugins.isNotEmpty()) {
                            TextButton(onClick = onRefreshNative) { Text("重新扫描已安装插件") }
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

    removingModule?.let { module ->
        AlertDialog(
            onDismissRequest = { removingModule = null },
            title = { Text("移除「${module.manifest.name}」？") },
            text = { Text("模块会从设备删除，主页组件与依赖它的模块会一起停止") },
            confirmButton = {
                TextButton(onClick = {
                    moduleStore.remove(module.manifest.id)
                    removingModule = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removingModule = null }) { Text("取消") }
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
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
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
            Text(
                text = "点按进入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
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
private fun InstalledModuleRow(
    module: InstalledModule,
    state: PluginRuntime.PluginState?,
    openable: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val typeLabel = if (module.manifest.type == ModuleManifest.TYPE_APP) "应用模块" else "导入模块"
    val statusText = when {
        !module.enabled -> "已停用"
        state == null -> "待启动"
        state.status == PluginRuntime.Status.Active -> "运行中"
        state.status == PluginRuntime.Status.Blocked -> state.message
        state.status == PluginRuntime.Status.Error -> "出错：${state.message}"
        state.status == PluginRuntime.Status.Loading -> "启动中"
        else -> ""
    }
    val statusColor = when {
        !module.enabled || state?.status == PluginRuntime.Status.Blocked ->
            MaterialTheme.colorScheme.onSurfaceVariant

        state?.status == PluginRuntime.Status.Error -> MaterialTheme.colorScheme.error
        state?.status == PluginRuntime.Status.Active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (openable) base.clickable(onClick = onOpen) else base
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginIcon(module.manifest.iconKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(module.manifest.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(8.dp))
                Tag(typeLabel)
                if (module.manifest.version.isNotBlank() && module.manifest.version != "1.0.0") {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        module.manifest.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (module.manifest.requires.isNotEmpty()) {
                Text(
                    text = "依赖：${module.manifest.requires.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            if (openable) {
                Text(
                    text = "点按进入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = module.enabled,
            onCheckedChange = { onToggle() },
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "移除模块",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NativePluginRow(
    plugin: NativePluginRuntime.PluginInfo,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
) {
    val statusText = when (plugin.status) {
        NativePluginRuntime.Status.Active -> "运行中"
        NativePluginRuntime.Status.Disabled -> "已停用"
        NativePluginRuntime.Status.Incompatible -> plugin.message
        NativePluginRuntime.Status.Error -> if (plugin.message.isBlank()) "加载失败" else plugin.message
    }
    val statusColor = when (plugin.status) {
        NativePluginRuntime.Status.Active -> MaterialTheme.colorScheme.primary
        NativePluginRuntime.Status.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
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
                Text(
                    text = plugin.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(8.dp))
                Tag(if (plugin.source == NativePluginSource.DEX_MODULE) "模块" else "APK")
                if (plugin.version.isNotBlank()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        plugin.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (plugin.status == NativePluginRuntime.Status.Error) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            }
            val slots = buildList {
                if (plugin.screens.isNotEmpty()) add("${plugin.screens.size} 个页面")
                if (plugin.widgets.isNotEmpty()) add("${plugin.widgets.size} 个组件")
            }
            if (slots.isNotEmpty()) {
                Text(
                    text = slots.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (plugin.screens.isNotEmpty()) "点按进入" else "点按查看状态",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = plugin.enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun PluginMarket(
    plugins: List<BuiltInPluginUi>,
    importers: List<TimetableImporter>,
    moduleStore: PluginModuleStore,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onInstall: (String) -> Unit,
    onUninstall: (BuiltInPluginUi) -> Unit,
    onOpenPluginSettings: (String) -> Unit,
) {
    val context = LocalContext.current
    val marketViewModel: PluginMarketViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PluginMarketViewModel(
                        context.applicationContext,
                        moduleStore,
                    ) as T
                }
            }
        },
    )
    val marketPlugins by marketViewModel.plugins.collectAsStateWithLifecycle()
    val marketLoading by marketViewModel.loading.collectAsStateWithLifecycle()
    val marketError by marketViewModel.error.collectAsStateWithLifecycle()
    val installedPackages by marketViewModel.installed.collectAsStateWithLifecycle()
    val downloads by marketViewModel.downloads.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val needle = query.trim()
    fun matches(vararg values: String): Boolean =
        needle.isBlank() || values.any { it.contains(needle, ignoreCase = true) }

    val visibleBuiltins = plugins.filter { matches(it.name, it.description) }
    val visibleImporters = importers.filter { matches(it.name, it.description, it.id) }
    val onlineForCategory = marketPlugins.filter { plugin ->
        when (category) {
            CATEGORY_BUILTIN -> false
            CATEGORY_ADAPTER -> !plugin.category.contains("工具")
            CATEGORY_TOOL -> plugin.category.contains("工具")
            else -> true
        }
    }
    val visibleOnline = onlineForCategory.filter {
        matches(it.name, it.description, it.category)
    }
    val showBuiltins = category == CATEGORY_ALL || category == CATEGORY_BUILTIN
    val showAdapters = category == CATEGORY_ALL || category == CATEGORY_ADAPTER
    val showProtocol = showAdapters && needle.isBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "插件市场",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        ) {
            IconButton(
                onClick = {
                    onRefresh()
                    marketViewModel.refresh()
                },
                enabled = !loading && !marketLoading,
            ) {
                if (loading || marketLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新插件目录")
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("搜索插件") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(CATEGORIES) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) },
                    )
                }
            }

            if (category != CATEGORY_BUILTIN) {
                OnlinePluginsSection(
                    plugins = visibleOnline,
                    loading = marketLoading,
                    error = marketError,
                    installed = installedPackages,
                    downloads = downloads,
                    selected = selected,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onSelectAll = { ids ->
                        selected = if (ids.isNotEmpty() && selected.containsAll(ids)) {
                            emptySet()
                        } else {
                            ids
                        }
                    },
                    onDownload = { ids ->
                        marketViewModel.download(ids)
                        selected = emptySet()
                    },
                    onRetryCatalog = marketViewModel::refresh,
                    onInstall = { plugin, file ->
                        if (plugin.isModule) {
                            marketViewModel.installModule(plugin, file)
                        } else {
                            installApk(context, file)
                        }
                    },
                    onRemoveModule = marketViewModel::uninstallModule,
                )
            }

            if (showBuiltins) {
            SectionTitle("内置插件")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (visibleBuiltins.isEmpty()) {
                        Text(
                            text = if (needle.isBlank()) "暂无内置插件" else "没有匹配的内置插件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                    visibleBuiltins.forEachIndexed { index, plugin ->
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
                        if (index != visibleBuiltins.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            }

            if (showAdapters) {
            SectionTitle("导入适配器")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (loading && visibleImporters.isEmpty()) {
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
                    if (visibleImporters.isEmpty() && !loading) {
                        Text(
                            text = if (needle.isBlank()) "暂无适配器" else "没有匹配的适配器",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                    visibleImporters.forEachIndexed { index, importer ->
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
                        if (index != visibleImporters.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            }

            if (showProtocol) {
            SectionTitle("模块开发")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "插件以 .osplugin 应用内模块分发，不安装 APK。开发、审核与上架见仓库 docs/PLUGIN_DEVELOPMENT.md。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            }
        }
    }
}

private const val CATEGORY_ALL = "全部"
private const val CATEGORY_BUILTIN = "内置"
private const val CATEGORY_ADAPTER = "适配器"
private const val CATEGORY_TOOL = "工具"
private val CATEGORIES = listOf(CATEGORY_ALL, CATEGORY_BUILTIN, CATEGORY_ADAPTER, CATEGORY_TOOL)

@Composable
private fun OnlinePluginsSection(
    plugins: List<MarketPlugin>,
    loading: Boolean,
    error: String?,
    installed: Set<String>,
    downloads: Map<String, MarketDownload>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Set<String>) -> Unit,
    onDownload: (Set<String>) -> Unit,
    onRetryCatalog: () -> Unit,
    onInstall: (MarketPlugin, File) -> Unit,
    onRemoveModule: (String) -> Unit,
) {
    SectionTitle("在线插件")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (loading && plugins.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (error != null && plugins.isEmpty() && !loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryCatalog) { Text("重试") }
                }
            }
            if (plugins.isEmpty() && !loading && error == null) {
                Text(
                    text = "没有匹配的在线插件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }

            val selectable = plugins.filter { plugin ->
                plugin.id !in installed && when (downloads[plugin.id]) {
                    is MarketDownload.Downloading,
                    is MarketDownload.Queued,
                    is MarketDownload.Ready,
                    -> false

                    else -> true
                }
            }
            val selectedHere = plugins.map { it.id }.filter { it in selected }.toSet()
            if (plugins.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选 ${selectedHere.size} 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onSelectAll(selectable.map { it.id }.toSet()) },
                        enabled = selectable.isNotEmpty(),
                    ) {
                        Text(
                            if (selectable.isNotEmpty() && selectedHere.containsAll(selectable.map { it.id })) {
                                "取消全选"
                            } else {
                                "全选"
                            },
                        )
                    }
                    Button(
                        onClick = { onDownload(selectedHere) },
                        enabled = selectedHere.isNotEmpty(),
                    ) {
                        Text("并行下载", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }
                Text(
                    text = "最多 3 个同时下载；安装完成后点右上角刷新",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            plugins.forEachIndexed { index, plugin ->
                val download = downloads[plugin.id] ?: MarketDownload.Idle
                OnlinePluginRow(
                    plugin = plugin,
                    installed = plugin.id in installed,
                    download = download,
                    selected = plugin.id in selected,
                    selectable = selectable.any { it.id == plugin.id },
                    onToggle = { onToggle(plugin.id) },
                    onDownload = { onDownload(setOf(plugin.id)) },
                    onInstall = onInstall,
                    onRemoveModule = onRemoveModule,
                )
                if (index != plugins.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun OnlinePluginRow(
    plugin: MarketPlugin,
    installed: Boolean,
    download: MarketDownload,
    selected: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (MarketPlugin, File) -> Unit,
    onRemoveModule: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!installed) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    enabled = selectable,
                )
            }
        }
        PluginIcon(plugin.iconKey)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (installed) {
                    Tag("已安装")
                } else if (plugin.version.isNotBlank()) {
                    Tag("v${plugin.version}")
                }
                if (installed && plugin.isModule) {
                    TextButton(onClick = { onRemoveModule(plugin.id) }) {
                        Text("移除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (plugin.description.isNotBlank()) {
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = plugin.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plugin.sizeBytes > 0) {
                    Text(
                        text = "  ${formatSize(plugin.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (download) {
                is MarketDownload.Downloading -> {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Text(
                        text = "下载中 ${(download.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MarketDownload.Queued -> Text(
                    text = "排队中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                is MarketDownload.Ready -> Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (download.verified) "已下载 · 校验通过" else "已下载 · 未校验",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (download.verified) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { onInstall(plugin, download.file) }) {
                        Icon(
                            if (plugin.isModule) Icons.Filled.Extension else Icons.Filled.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (plugin.isModule) "启用模块" else "安装")
                    }
                }

                is MarketDownload.Failed -> Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = download.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDownload) { Text("重试") }
                }

                else -> if (!installed) {
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("下载")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb) else "${bytes / 1024} KB"
}

private fun installApk(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_LONG).show()
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(context, "无法读取安装包", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(context, "无法启动安装器，请允许安装未知应用", Toast.LENGTH_LONG).show()
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

