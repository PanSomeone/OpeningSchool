package com.openschedule.feature.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.model.AppSettings
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.ScreenHeader
import com.openschedule.core.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class WallpaperPage(val id: String, val label: String)

private val WALLPAPER_PAGES = listOf(
    WallpaperPage("home", "主页"),
    WallpaperPage("timetable", "课表"),
    WallpaperPage("hotwater", "热水"),
    WallpaperPage("scores", "成绩"),
    WallpaperPage("plugins", "插件"),
    WallpaperPage("settings", "设置"),
)

private sealed interface WallpaperTarget {
    data object Global : WallpaperTarget
    data class Page(val id: String) : WallpaperTarget
}

private data class PendingWallpaper(
    val uri: Uri,
    val target: WallpaperTarget,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsScreen(
    viewModel: SettingsViewModel,
    hiddenPluginIds: Set<String>,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingTarget by remember { mutableStateOf<WallpaperTarget?>(null) }
    var pendingCrop by remember { mutableStateOf<PendingWallpaper?>(null) }
    var pageMenu by remember { mutableStateOf<WallpaperPage?>(null) }
    val pages = WALLPAPER_PAGES.filterNot { it.id in hiddenPluginIds }

    fun labelFor(target: WallpaperTarget): String = when (target) {
        WallpaperTarget.Global -> "全局壁纸"
        is WallpaperTarget.Page -> {
            val page = WALLPAPER_PAGES.firstOrNull { it.id == target.id }
            "${page?.label ?: "页面"}壁纸"
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (uri != null && target != null) {
            pendingCrop = PendingWallpaper(uri, target, labelFor(target))
        }
    }

    fun choose(target: WallpaperTarget) {
        pendingTarget = target
        pickImage.launch("image/*")
    }

    fun removePageOverride(pageId: String) {
        val current = settings
        val oldName = current.pageWallpapers[pageId] ?: return
        val next = current.copy(pageWallpapers = current.pageWallpapers - pageId)
        viewModel.update { next }
        deleteIfUnused(context, oldName, next)
    }

    pendingCrop?.let { crop ->
        Dialog(
            onDismissRequest = { pendingCrop = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            WallpaperCropScreen(
                uri = crop.uri,
                targetLabel = crop.label,
                onCancel = { pendingCrop = null },
                onApply = { name ->
                    val current = viewModel.settings.value
                    val target = crop.target
                    val oldName = when (target) {
                        WallpaperTarget.Global -> current.appWallpaper
                        is WallpaperTarget.Page -> current.pageWallpapers[target.id]
                    }
                    val next = when (target) {
                        WallpaperTarget.Global -> current.copy(appWallpaper = name)
                        is WallpaperTarget.Page -> current.copy(
                            pageWallpapers = current.pageWallpapers + (target.id to name),
                        )
                    }
                    viewModel.update { next }
                    deleteIfUnused(context, oldName, next)
                    pendingCrop = null
                    scope.launch { snackbar.showSnackbar("壁纸已更新") }
                },
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "壁纸",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalFloatingNavSpace.current + 24.dp),
            ) {
                SectionTitle("全局壁纸")
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        WallpaperThumb(
                            fileName = settings.appWallpaper,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (settings.appWallpaper == null) "未设置" else "已设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { choose(WallpaperTarget.Global) }) {
                                Text("选择图片")
                            }
                            if (settings.appWallpaper != null) {
                                TextButton(
                                    onClick = {
                                        val oldName = settings.appWallpaper
                                        val next = settings.copy(appWallpaper = null)
                                        viewModel.update { next }
                                        deleteIfUnused(context, oldName, next)
                                    },
                                ) {
                                    Text("移除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Text(
                            text = "所有未单独设置的页面使用这张壁纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionTitle("页面壁纸")
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                        pages.forEachIndexed { index, page ->
                            val custom = settings.pageWallpapers[page.id]
                            val effective = custom ?: settings.appWallpaper
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pageMenu = page }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WallpaperThumb(
                                    fileName = effective,
                                    modifier = Modifier.size(48.dp),
                                    dimmed = custom == null,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                ) {
                                    Text(page.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = when {
                                            custom != null -> "自定义"
                                            settings.appWallpaper != null -> "跟随全局"
                                            else -> "未设置"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index != pages.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                Text(
                    text = "页面壁纸优先于全局壁纸；启用壁纸时课表内部背景会暂时隐藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalFloatingNavSpace.current + 16.dp),
        )
    }

    pageMenu?.let { page ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pageMenu = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    text = "${page.label}壁纸",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                TextButton(
                    onClick = {
                        pageMenu = null
                        choose(WallpaperTarget.Page(page.id))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("选择图片")
                }
                TextButton(
                    onClick = {
                        removePageOverride(page.id)
                        pageMenu = null
                    },
                    enabled = settings.pageWallpapers.containsKey(page.id),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("跟随全局壁纸")
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumb(
    fileName: String?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, fileName) {
        value = withContext(Dispatchers.IO) {
            if (fileName == null) {
                null
            } else {
                val file = File(context.filesDir, fileName)
                if (!file.exists()) {
                    null
                } else {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (dimmed) 0.5f else 1f),
                )
            } else {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun deleteIfUnused(context: Context, fileName: String?, settings: AppSettings) {
    if (fileName == null) return
    if (fileName == settings.appWallpaper) return
    if (fileName in settings.pageWallpapers.values) return
    File(context.filesDir, fileName).delete()
}
