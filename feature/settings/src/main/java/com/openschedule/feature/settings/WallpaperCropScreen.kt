package com.openschedule.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.openschedule.core.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_IMAGE_SIDE = 2560
private const val MAX_ZOOM = 4f

private data class CropImage(val bitmap: Bitmap, val sample: Int)

@Composable
fun WallpaperCropScreen(
    uri: Uri,
    targetLabel: String,
    onCancel: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decoding by remember(uri) { mutableStateOf(true) }
    val crop by produceState<CropImage?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { loadCropBitmap(context, uri) }
        decoding = false
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val source = crop?.bitmap
    val image = remember(source) { source?.asImageBitmap() }

    BackHandler(onBack = onCancel)

    LaunchedEffect(source, containerSize) {
        if (source != null && containerSize != IntSize.Zero) {
            zoom = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(decoding, source) {
        if (!decoding && source == null && errorMessage == null) {
            errorMessage = "图片读取失败，换一张试试"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (image != null && source != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(source, containerSize) {
                        detectTransformGestures(panZoomLock = true) { _, pan, gestureZoom, _ ->
                            val nextZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            zoom = nextZoom
                            offset = clampOffset(offset + pan, containerSize, source, nextZoom)
                        }
                    },
            ) {
                val base = max(
                    size.width / source.width.toFloat(),
                    size.height / source.height.toFloat(),
                )
                val scale = base * zoom
                val width = source.width * scale
                val height = source.height * scale
                val left = (size.width - width) / 2f + offset.x
                val top = (size.height - height) / 2f + offset.y
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(source.width, source.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
        } else {
            Text(
                text = if (errorMessage != null) errorMessage!! else "正在读取图片…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消", tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "壁纸预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "拖动调整位置 · 双指或滑杆缩放",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "缩放",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Slider(
                    value = zoom,
                    onValueChange = { value ->
                        zoom = value
                        if (source != null) {
                            offset = clampOffset(offset, containerSize, source, value)
                        }
                    },
                    valueRange = 1f..MAX_ZOOM,
                    enabled = source != null && !saving,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text(
                    text = "${(zoom * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
            if (errorMessage != null && source != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = {
                        zoom = 1f
                        offset = Offset.Zero
                    },
                    enabled = source != null && !saving,
                ) {
                    Text("重置", color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel, enabled = !saving) {
                    Text("取消", color = Color.White.copy(alpha = 0.85f))
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                ) {
                    Button(
                        onClick = {
                            val current = crop ?: return@Button
                            if (saving) return@Button
                            saving = true
                            errorMessage = null
                            val applyZoom = zoom
                            val applyOffset = offset
                            val size = containerSize
                            scope.launch {
                                val name = withContext(Dispatchers.IO) {
                                    saveCrop(context, uri, current, size, applyZoom, applyOffset)
                                }
                                saving = false
                                if (name == null) {
                                    errorMessage = "保存失败，请重试"
                                } else {
                                    onApply(name)
                                }
                            }
                        },
                        enabled = source != null && !saving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black,
                            )
                        } else {
                            Text("使用这张")
                        }
                    }
                }
            }
        }
    }
}

private fun loadCropBitmap(context: Context, uri: Uri): CropImage? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val readable = resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
        true
    } ?: false
    if (!readable || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        AppLog.w("WallpaperCrop", "图片尺寸无效 $uri ${bounds.outWidth}x${bounds.outHeight}")
        return null
    }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) {
        sample *= 2
    }
    val bitmap = decodeCropBitmap(context, uri, sample) ?: return null
    CropImage(bitmap, sample)
}.onFailure { error ->
    AppLog.w("WallpaperCrop", "壁纸读取异常: ${error.message}", error)
}.getOrNull()

private fun decodeCropBitmap(context: Context, uri: Uri, sample: Int): Bitmap? {
    val resolver = context.contentResolver
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    var bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val rotated = rotateBitmap(bitmap, orientation)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

private fun clampOffset(
    offset: Offset,
    container: IntSize,
    bitmap: Bitmap,
    zoom: Float,
): Offset {
    if (container.width <= 0 || container.height <= 0) return Offset.Zero
    val base = max(
        container.width / bitmap.width.toFloat(),
        container.height / bitmap.height.toFloat(),
    )
    val scale = base * zoom
    val maxX = ((bitmap.width * scale - container.width) / 2f).coerceAtLeast(0f)
    val maxY = ((bitmap.height * scale - container.height) / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

private fun saveCrop(
    context: Context,
    uri: Uri,
    source: CropImage,
    container: IntSize,
    zoom: Float,
    offset: Offset,
): String? {
    if (container.width <= 0 || container.height <= 0) return null
    val preview = source.bitmap
    val base = max(
        container.width / preview.width.toFloat(),
        container.height / preview.height.toFloat(),
    )
    val scale = base * zoom
    val previewWidth = (container.width / scale)
        .roundToInt()
        .coerceIn(1, preview.width)
    val previewHeight = (container.height / scale)
        .roundToInt()
        .coerceIn(1, preview.height)
    val previewX = ((preview.width - previewWidth) / 2f - offset.x / scale)
        .roundToInt()
        .coerceIn(0, preview.width - previewWidth)
    val previewY = ((preview.height - previewHeight) / 2f - offset.y / scale)
        .roundToInt()
        .coerceIn(0, preview.height - previewHeight)

    val imageX = previewX.toLong() * source.sample
    val imageY = previewY.toLong() * source.sample
    val imageWidth = previewWidth.toLong() * source.sample
    val imageHeight = previewHeight.toLong() * source.sample

    // Decode at a sample that still covers the screen, so landscape photos are
    // not saved smaller than the display and then upscaled into a soft blur.
    var saveSample = source.sample
    while (saveSample > 1 &&
        (imageWidth / saveSample < container.width ||
            imageHeight / saveSample < container.height)
    ) {
        saveSample /= 2
    }
    var working = preview
    var workingSample = source.sample
    if (saveSample != source.sample) {
        decodeCropBitmap(context, uri, saveSample)?.let {
            working = it
            workingSample = saveSample
        }
    }

    val cropX = (imageX.toDouble() / workingSample)
        .roundToInt()
        .coerceIn(0, working.width - 1)
    val cropY = (imageY.toDouble() / workingSample)
        .roundToInt()
        .coerceIn(0, working.height - 1)
    val cropWidth = (imageWidth.toDouble() / workingSample)
        .roundToInt()
        .coerceIn(1, working.width - cropX)
    val cropHeight = (imageHeight.toDouble() / workingSample)
        .roundToInt()
        .coerceIn(1, working.height - cropY)

    val cropped = runCatching {
        Bitmap.createBitmap(working, cropX, cropY, cropWidth, cropHeight)
    }.getOrNull() ?: return null
    val output = if (cropped.width == container.width && cropped.height == container.height) {
        cropped
    } else {
        runCatching {
            Bitmap.createScaledBitmap(cropped, container.width, container.height, true)
        }.getOrNull() ?: cropped
    }

    val name = "wallpaper_${System.currentTimeMillis()}.img"
    val file = File(context.filesDir, name)
    val saved = runCatching {
        FileOutputStream(file).use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream))
        }
    }.isSuccess
    if (output !== cropped) output.recycle()
    if (cropped !== working) cropped.recycle()
    if (working !== preview) working.recycle()
    if (!saved) {
        file.delete()
        return null
    }
    return name
}
