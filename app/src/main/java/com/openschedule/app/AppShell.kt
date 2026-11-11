package com.openschedule.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openschedule.core.data.Announcement
import com.openschedule.core.data.InstalledModule
import com.openschedule.core.data.PluginModuleStore
import com.openschedule.core.data.NativePluginRuntime
import com.openschedule.core.data.PluginRuntime
import com.openschedule.core.data.SettingsStore
import com.openschedule.core.model.MAX_NAV_SLOTS
import com.openschedule.core.model.NavSlot
import com.openschedule.core.plugin.PluginDestination
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.LocalIsDarkTheme
import com.openschedule.core.ui.LocalWallpaperActive
import com.openschedule.core.ui.NativePluginView
import com.openschedule.core.ui.NativePluginWidgetView
import com.openschedule.core.ui.NavIcons
import com.openschedule.core.ui.PluginViewContent
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.feature.home.HomeScreen
import com.openschedule.feature.home.HomeShortcut
import com.openschedule.feature.home.HomeViewModel
import com.openschedule.feature.home.HomeWidgetSpec
import com.openschedule.feature.plugins.BuiltInPluginUi
import com.openschedule.feature.plugins.PluginsScreen
import com.openschedule.feature.plugins.PluginsViewModel
import com.openschedule.feature.settings.AnnouncementDialog
import com.openschedule.feature.settings.SettingsScreen
import com.openschedule.feature.settings.SettingsViewModel
import com.openschedule.feature.settings.UpdateState
import com.openschedule.feature.settings.UpdateViewModel
import com.openschedule.feature.settings.WallpaperSettingsScreen
import com.openschedule.feature.timetable.TimetableScreen
import com.openschedule.feature.timetable.TimetableSettingsScreen
import com.openschedule.feature.timetable.TimetableViewModel
import com.openschedule.plugin.hotwater.HotWaterHomeWidget
import com.openschedule.plugin.hotwater.HotWaterScreen
import com.openschedule.plugin.hotwater.HotWaterSettingsScreen
import com.openschedule.plugin.scores.ScoresHomeWidget
import com.openschedule.plugin.scores.ScoresScreen
import com.openschedule.plugin.scores.ScoresSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppShell(graph: AppGraph) {
    val settings by graph.settingsStore.settings.collectAsStateWithLifecycle()
    val announcements by graph.announcementStore.announcements.collectAsStateWithLifecycle()
    val announcementReadIds by graph.announcementStore.readIds.collectAsStateWithLifecycle()
    val unreadAnnouncements = announcements.count { it.id !in announcementReadIds }
    var announcementToShow by remember { mutableStateOf<Announcement?>(null) }
    val context = LocalContext.current

    val updateViewModel = rememberedViewModel(UpdateViewModel::class.java, "update") {
        UpdateViewModel(context.applicationContext)
    }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val ignoredVersion by updateViewModel.ignoredVersion.collectAsStateWithLifecycle()
    val ignoredUntil by updateViewModel.ignoredUntil.collectAsStateWithLifecycle()
    val availableUpdate = (updateState as? UpdateState.Available)?.info
    val showUpdateDot = availableUpdate != null &&
        (availableUpdate.version != ignoredVersion ||
            System.currentTimeMillis() >= ignoredUntil)

    LaunchedEffect(Unit) {
        graph.announcementStore.refresh()
        updateViewModel.autoCheck()
    }

    // Host event bridge: plugins can subscribe to timetable changes.
    LaunchedEffect(Unit) {
        graph.timetableStore.timetable.collect {
            graph.pluginRuntime.emitHostEvent("timetable.updated")
            graph.nativePlugins.emitHostEvent("timetable.updated")
        }
    }

    val pluginStates by graph.pluginRuntime.states.collectAsStateWithLifecycle()
    val moduleWidgets by graph.pluginRuntime.homeWidgets.collectAsStateWithLifecycle()
    val nativePlugins by graph.nativePlugins.plugins.collectAsStateWithLifecycle()

    val homeViewModel = rememberedViewModel(HomeViewModel::class.java, "home") {
        HomeViewModel(graph.timetableStore, graph.weatherStore)
    }
    var selectedId by rememberSaveable { mutableStateOf("home") }
    var pluginSettingsId by rememberSaveable { mutableStateOf<String?>(null) }
    var wallpaperOpen by rememberSaveable { mutableStateOf(false) }
    var openModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var openNativeId by rememberSaveable { mutableStateOf<String?>(null) }

    val moduleScreens by graph.pluginRuntime.screens.collectAsStateWithLifecycle()
    val openModuleScreen = openModuleId?.let { moduleScreens[it] }

    val openDestination: (String) -> Unit = { id ->
        selectedId = id
        pluginSettingsId = null
        wallpaperOpen = false
        openModuleId = null
        openNativeId = null
    }

    val homeShortcuts = listOf(
        HomeShortcut(
            id = "timetable",
            label = "课表",
            iconKey = "calendar",
            installed = "timetable" !in settings.disabledPlugins,
        ),
        HomeShortcut(
            id = "hotwater",
            label = "热水",
            iconKey = "water",
            installed = "hotwater" !in settings.disabledPlugins,
        ),
        HomeShortcut(
            id = "scores",
            label = "成绩",
            iconKey = "school",
            installed = "scores" !in settings.disabledPlugins,
        ),
        HomeShortcut(id = "plugins", label = "插件", iconKey = "extension", installed = true),
        HomeShortcut(id = "settings", label = "设置", iconKey = "settings", installed = true),
    )
    val externalHomeWidgets = listOf(
        HomeWidgetSpec(
            id = "hotwater",
            title = "热水",
            iconKey = "water",
            available = "hotwater" !in settings.disabledPlugins,
            content = { modifier ->
                HotWaterHomeWidget(
                    modifier = modifier,
                    onOpen = { openDestination("hotwater") },
                )
            },
        ),
        HomeWidgetSpec(
            id = "scores",
            title = "GPA",
            iconKey = "school",
            available = "scores" !in settings.disabledPlugins,
            content = { modifier ->
                ScoresHomeWidget(
                    modifier = modifier,
                    onOpen = { openDestination("scores") },
                )
            },
        ),
    ) + moduleWidgets.map { widget ->
        HomeWidgetSpec(
            id = widget.widgetId,
            title = widget.spec.title.ifBlank { "插件组件" },
            iconKey = widget.spec.iconKey,
            available = pluginStates[widget.pluginId]?.status == PluginRuntime.Status.Active,
            content = { modifier ->
                PluginViewContent(
                    node = widget.spec.view,
                    onAction = { action ->
                        graph.pluginRuntime.dispatchAction(widget.pluginId, action)
                    },
                    modifier = modifier,
                )
            },
        )
    } + nativePlugins
        .filter { it.status == NativePluginRuntime.Status.Active }
        .flatMap { plugin ->
            plugin.widgets.map { widget ->
                HomeWidgetSpec(
                    id = plugin.widgetType(widget.id),
                    title = widget.title,
                    iconKey = widget.iconKey,
                    available = true,
                    content = { modifier ->
                        NativePluginWidgetView(
                            factory = {
                                graph.nativePlugins.createWidgetView(
                                    plugin.id,
                                    widget.id,
                                    context,
                                )
                            },
                            modifier = modifier,
                        )
                    },
                )
            }
        }

    val destinations = listOf(
        PluginDestination(
            id = "home",
            label = "主页",
            iconKey = "home",
            defaultOrder = 0,
            content = {
                HomeScreen(
                    viewModel = homeViewModel,
                    layout = settings.homeLayout,
                    onLayoutChange = { layout ->
                        graph.settingsStore.update { it.copy(homeLayout = layout) }
                    },
                    externalWidgets = externalHomeWidgets,
                    shortcuts = homeShortcuts,
                    timetableEnabled = "timetable" !in settings.disabledPlugins,
                    onOpen = openDestination,
                    unreadAnnouncements = unreadAnnouncements,
                    onOpenAnnouncements = {
                        announcementToShow = announcements.firstOrNull {
                            it.id !in announcementReadIds
                        }
                    },
                )
            },
        ),
        PluginDestination(
            id = "timetable",
            label = "课表",
            iconKey = "calendar",
            defaultOrder = 1,
            pluginId = "timetable",
            content = {
                val viewModel = rememberedViewModel(TimetableViewModel::class.java, "timetable") {
                    TimetableViewModel(
                        timetableStore = graph.timetableStore,
                        settingsStore = graph.settingsStore,
                        builtInImporters = graph.builtInImporters,
                        externalAdapters = graph.externalAdapters,
                        moduleStore = graph.moduleStore,
                    )
                }
                TimetableScreen(
                    viewModel = viewModel,
                    onOpenSettings = { pluginSettingsId = "timetable" },
                )
            },
        ),
        PluginDestination(
            id = "hotwater",
            label = "热水",
            iconKey = "water",
            defaultOrder = 2,
            pluginId = "hotwater",
            content = { HotWaterScreen() },
        ),
        PluginDestination(
            id = "scores",
            label = "成绩",
            iconKey = "school",
            defaultOrder = 5,
            visibleByDefault = false,
            pluginId = "scores",
            content = { ScoresScreen() },
        ),
        PluginDestination(
            id = "plugins",
            label = "插件",
            iconKey = "extension",
            defaultOrder = 3,
            content = {
                val viewModel = rememberedViewModel(PluginsViewModel::class.java, "plugins") {
                    PluginsViewModel(graph.builtInImporters, graph.externalAdapters)
                }
                PluginsRoute(
                    viewModel = viewModel,
                    settingsStore = graph.settingsStore,
                    moduleStore = graph.moduleStore,
                    pluginStates = pluginStates,
                    onOpenPlugin = { id ->
                        selectedId = id
                        pluginSettingsId = null
                        wallpaperOpen = false
                        openModuleId = null
                    },
                    onOpenModule = { id -> openModuleId = id },
                    onCanOpenModule = { id -> moduleScreens.containsKey(id) },
                    nativePlugins = nativePlugins,
                    onToggleNative = { id, enabled ->
                        graph.nativePlugins.setEnabled(id, enabled)
                    },
                    onOpenNative = { id -> openNativeId = id },
                    onRefreshNative = { graph.nativePlugins.refresh() },
                    onOpenPluginSettings = { pluginSettingsId = it },
                    onOpenSettings = {
                        selectedId = "settings"
                        pluginSettingsId = null
                    },
                )
            },
        ),
        PluginDestination(
            id = "settings",
            label = "设置",
            iconKey = "settings",
            defaultOrder = 4,
            content = {
                val viewModel = rememberedViewModel(SettingsViewModel::class.java, "settings") {
                    SettingsViewModel(graph.settingsStore)
                }
                SettingsScreen(
                    viewModel = viewModel,
                    hiddenPluginIds = settings.disabledPlugins,
                    announcementStore = graph.announcementStore,
                    updateViewModel = updateViewModel,
                )
            },
        ),
    )

    // Merges the destinations compiled into this build into the saved nav, so a
    // new plugin appears once and a user's order/hidden flags survive updates.
    LaunchedEffect(Unit) {
        graph.settingsStore.update { current ->
            val saved = current.nav.associateBy { it.id }
            val merged = destinations.map { destination ->
                saved[destination.id] ?: NavSlot(
                    id = destination.id,
                    label = destination.label,
                    visible = destination.visibleByDefault,
                    order = destination.defaultOrder,
                    iconKey = destination.iconKey,
                )
            }.sortedBy { it.order }
                .mapIndexed { index, slot ->
                    val normalized = slot.copy(order = index)
                    if (normalized.id == "settings") normalized.copy(visible = true) else normalized
                }

            if (merged == current.nav) current else current.copy(nav = merged)
        }
    }

    val enabledDestinations = destinations.filter { destination ->
        destination.pluginId == null || destination.pluginId !in settings.disabledPlugins
    }
    val visibleSlots = settings.nav
        .sortedBy { it.order }
        .filter { slot -> slot.visible && enabledDestinations.any { it.id == slot.id } }
        .take(MAX_NAV_SLOTS)
    val navSlots = if (visibleSlots.any { it.id == "settings" }) {
        visibleSlots
    } else {
        // Settings is the app's way back to every toggle; never let it disappear.
        val settingsSlot = settings.nav.firstOrNull { it.id == "settings" }
            ?.copy(visible = true)
            ?: NavSlot(id = "settings", label = "设置", iconKey = "settings")
        if (visibleSlots.size >= MAX_NAV_SLOTS) {
            visibleSlots.dropLast(1) + settingsSlot
        } else {
            visibleSlots + settingsSlot
        }
    }

    val selectedDestination = destinations.firstOrNull { it.id == selectedId }
    val selectedAvailable = selectedDestination != null &&
        (selectedDestination.pluginId == null || selectedDestination.pluginId !in settings.disabledPlugins)
    val active = if (selectedAvailable) {
        selectedDestination!!
    } else {
        destinations.firstOrNull { it.id == navSlots.first().id } ?: destinations.first()
    }

    BackHandler(enabled = pluginSettingsId != null) {
        pluginSettingsId = null
    }
    BackHandler(enabled = openModuleId != null) {
        openModuleId = null
    }
    BackHandler(enabled = openNativeId != null) {
        openNativeId = null
    }
    BackHandler(
        enabled = pluginSettingsId == null &&
            navSlots.none { it.id == active.id } &&
            active.id != "home",
    ) {
        selectedId = "home"
    }
    BackHandler(enabled = wallpaperOpen) {
        wallpaperOpen = false
    }

    val wallpaperFile = remember(active.id, settings.appWallpaper, settings.pageWallpapers, context) {
        val pageWallpaper = settings.pageWallpapers[active.id]
        val appWallpaper = settings.appWallpaper
        listOfNotNull(pageWallpaper, appWallpaper).firstOrNull { fileName ->
            File(context.filesDir, fileName).exists()
        }
    }

    CompositionLocalProvider(
        LocalFloatingNavSpace provides 92.dp,
        LocalWallpaperActive provides (wallpaperFile != null),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WallpaperBackground(wallpaperFile)
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) {
            Scaffold(
                containerColor = Color.Transparent,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when {
                        openNativeId != null -> {
                            val plugin = nativePlugins.firstOrNull { it.id == openNativeId }
                            NativePluginScreen(
                                plugin = plugin,
                                onCreateView = { screenId ->
                                    graph.nativePlugins.createScreenView(
                                        openNativeId!!,
                                        screenId,
                                        context,
                                    )
                                },
                                onRetry = { graph.nativePlugins.refresh() },
                                onBack = { openNativeId = null },
                            )
                        }

                        openModuleId != null -> {
                            ModuleScreen(
                                pluginId = openModuleId!!,
                                screen = openModuleScreen?.screen,
                                onAction = { action ->
                                    graph.pluginRuntime.dispatchAction(openModuleId!!, action)
                                },
                                onBack = { openModuleId = null },
                            )
                        }

                        wallpaperOpen -> {
                            val viewModel = rememberedViewModel(
                                SettingsViewModel::class.java,
                                "settings",
                            ) { SettingsViewModel(graph.settingsStore) }
                            WallpaperSettingsScreen(
                                viewModel = viewModel,
                                hiddenPluginIds = settings.disabledPlugins,
                                onBack = { wallpaperOpen = false },
                            )
                        }

                        pluginSettingsId == "timetable" -> {
                            val viewModel = rememberedViewModel(
                                TimetableViewModel::class.java,
                                "timetable",
                            ) {
                                TimetableViewModel(
                                    timetableStore = graph.timetableStore,
                                    settingsStore = graph.settingsStore,
                                    builtInImporters = graph.builtInImporters,
                                    externalAdapters = graph.externalAdapters,
                                    moduleStore = graph.moduleStore,
                                )
                            }
                            TimetableSettingsScreen(
                                viewModel = viewModel,
                                onBack = { pluginSettingsId = null },
                                onOpenWallpaper = { wallpaperOpen = true },
                            )
                        }

                        pluginSettingsId == "hotwater" ->
                            HotWaterSettingsScreen(onBack = { pluginSettingsId = null })

                        pluginSettingsId == "scores" ->
                            ScoresSettingsScreen(onBack = { pluginSettingsId = null })
                        else -> active.content()
                    }
                }
            }

            FloatingNav(
                slots = navSlots,
                destinations = destinations,
                activeId = active.id,
                opacity = settings.navOpacity,
                badgedIds = if (showUpdateDot) setOf("settings") else emptySet(),
                onSelect = { id ->
                    selectedId = id
                    pluginSettingsId = null
                    wallpaperOpen = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
            )
            }
        }
    }

    announcementToShow?.let { announcement ->
        AnnouncementDialog(
            announcement = announcement,
            onDismiss = {
                graph.announcementStore.markRead(announcement.id)
                announcementToShow = null
            },
        )
    }
}

@Composable
private fun NativePluginScreen(
    plugin: NativePluginRuntime.PluginInfo?,
    onCreateView: (String) -> android.view.View?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val screens = plugin?.screens.orEmpty()
    var selected by rememberSaveable(plugin?.id) { mutableStateOf(0) }
    val index = selected.coerceIn(0, (screens.size - 1).coerceAtLeast(0))

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = plugin?.label ?: "原生插件",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
        if (screens.size > 1) {
            TabRow(selectedTabIndex = index) {
                screens.forEachIndexed { i, screen ->
                    Tab(
                        selected = i == index,
                        onClick = { selected = i },
                        text = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = LocalFloatingNavSpace.current + 24.dp),
        ) {
            if (screens.isEmpty()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = when (plugin?.status) {
                                null -> "插件不存在"
                                NativePluginRuntime.Status.Active -> "该插件没有注册页面"
                                NativePluginRuntime.Status.Disabled -> "插件已停用"
                                NativePluginRuntime.Status.Incompatible -> "插件与当前应用版本不兼容"
                                NativePluginRuntime.Status.Error -> "插件加载失败"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (plugin != null) {
                            if (plugin.message.isNotBlank()) {
                                Text(
                                    text = plugin.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Text(
                                text = "${plugin.id} · v${plugin.version} · 来源：${plugin.sourceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(
                            text = "可在上一页用开关停用/启用，或从插件市场重新下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (plugin?.status == NativePluginRuntime.Status.Error) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 10.dp),
                            ) {
                                Text("重试加载")
                            }
                        }
                    }
                }
            } else {
                // Keyed on the screen id so switching tabs rebuilds a fresh view.
                androidx.compose.runtime.key(screens[index].id) {
                    NativePluginView(
                        factory = { onCreateView(screens[index].id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleScreen(
    pluginId: String,
    screen: com.openschedule.core.plugin.PluginScreen?,
    onAction: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = screen?.title?.ifBlank { null } ?: "插件页面",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (screen == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "「$pluginId」没有提供页面。模块需要在运行时调用 ctx.ui.screen({...})。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                PluginViewContent(node = screen.view, onAction = onAction)
            }
        }
    }
}

@Composable
private fun PluginsRoute(
    viewModel: PluginsViewModel,
    settingsStore: SettingsStore,
    moduleStore: PluginModuleStore,
    pluginStates: Map<String, PluginRuntime.PluginState>,
    onOpenPlugin: (String) -> Unit,
    onOpenPluginSettings: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModule: (String) -> Unit,
    onCanOpenModule: (String) -> Boolean,
    nativePlugins: List<NativePluginRuntime.PluginInfo>,
    onToggleNative: (String, Boolean) -> Unit,
    onOpenNative: (String) -> Unit,
    onRefreshNative: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val modules by moduleStore.modules.collectAsStateWithLifecycle()
    val catalog = listOf(
        Triple("timetable", "课程表", "课表、作息与导入") to "calendar",
        Triple("hotwater", "热水", "宿舍热水控制") to "water",
        Triple("scores", "成绩", "GPA 与体测算分") to "school",
    )
    val plugins = catalog.map { (info, iconKey) ->
        val (id, name, description) = info
        BuiltInPluginUi(
            id = id,
            name = name,
            description = description,
            iconKey = iconKey,
            installed = id !in settings.disabledPlugins,
            inNav = settings.nav.any { it.id == id && it.visible },
            hasSettings = true,
        )
    }

    PluginsScreen(
        viewModel = viewModel,
        plugins = plugins,
        moduleStore = moduleStore,
        modules = modules,
        moduleStates = pluginStates,
        onInstall = { id ->
            settingsStore.update { it.copy(disabledPlugins = it.disabledPlugins - id) }
        },
        onUninstall = { id ->
            settingsStore.update { current ->
                current.copy(
                    disabledPlugins = current.disabledPlugins + id,
                    nav = current.nav.map { slot ->
                        if (slot.id == id) slot.copy(visible = false) else slot
                    },
                )
            }
        },
        onOpenPlugin = onOpenPlugin,
        onOpenPluginSettings = onOpenPluginSettings,
        onOpenSettings = onOpenSettings,
        canOpenModule = onCanOpenModule,
        onOpenModule = onOpenModule,
        nativePlugins = nativePlugins,
        onToggleNative = onToggleNative,
        onOpenNative = onOpenNative,
        onRefreshNative = onRefreshNative,
    )
}

@Composable
private fun WallpaperBackground(fileName: String?) {
    val context = LocalContext.current
    val dark = LocalIsDarkTheme.current
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileName) {
        if (fileName == null) {
            frame = null
        } else {
            val loaded = withContext(Dispatchers.IO) {
                WallpaperCache.load(context, fileName)
            }
            if (loaded != null) frame = loaded
        }
    }

    Crossfade(
        targetState = frame,
        animationSpec = tween(durationMillis = 280),
        label = "wallpaper",
        modifier = Modifier.fillMaxSize(),
    ) { image ->
        if (image == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (dark) {
                                Color.Black.copy(alpha = 0.34f)
                            } else {
                                Color.White.copy(alpha = 0.18f)
                            },
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

private object WallpaperCache {
    private val cache = object : LruCache<String, ImageBitmap>(3) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    fun load(context: Context, fileName: String): ImageBitmap? {
        cache.get(fileName)?.let { return it }
        val file = File(context.filesDir, fileName)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?.asImageBitmap()
            ?: return null
        cache.put(fileName, bitmap)
        return bitmap
    }
}

@Composable
private fun FloatingNav(
    slots: List<NavSlot>,
    destinations: List<PluginDestination>,
    activeId: String,
    opacity: Float,
    badgedIds: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 64.dp
    val itemGap = 4.dp
    val selectedIndex = slots.indexOfFirst { it.id == activeId }
    val targetOffset = if (selectedIndex >= 0) {
        (itemWidth + itemGap) * selectedIndex.toFloat()
    } else {
        0.dp
    }
    val highlightOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navHighlight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(
                alpha = opacity.coerceIn(0.55f, 1f),
            ),
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = highlightOffset)
                            .width(itemWidth)
                            .height(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(24.dp),
                            ),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slots.forEach { slot ->
                        val destination = destinations.firstOrNull { it.id == slot.id }
                            ?: return@forEach
                        val selected = slot.id == activeId
                        val contentColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 260),
                            label = "navContent",
                        )
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .width(itemWidth)
                                .height(56.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onSelect(slot.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box {
                                Icon(
                                    imageVector = NavIcons.icon(
                                        slot.iconKey.ifBlank { destination.iconKey },
                                    ),
                                    contentDescription = buildString {
                                        append(slot.label.ifBlank { destination.label })
                                        if (slot.id in badgedIds) append("，有可用更新")
                                    },
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                if (slot.id in badgedIds) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .background(
                                                MaterialTheme.colorScheme.error,
                                                CircleShape,
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.surface,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = slot.label.ifBlank { destination.label },
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                textAlign = TextAlign.Center,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : ViewModel> rememberedViewModel(modelClass: Class<T>, key: String, create: () -> T): T {
    val factory = remember(key) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
    }
    return viewModel(modelClass = modelClass, key = key, factory = factory)
}
