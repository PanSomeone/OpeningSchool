package com.openschedule.core.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts one native plugin view inside Compose.
 *
 * The plugin builds an ordinary Android `View` tree; this wrapper only pins it to
 * the available width and gives it a scrollable container when requested. No
 * WebView is involved.
 */
@Composable
fun NativePluginView(
    factory: () -> View?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val container = if (scrollable) {
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    } else {
        modifier.fillMaxWidth()
    }
    Box(modifier = container) {
        AndroidView(
            factory = { context -> factory() ?: MissingView(context) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Convenience for plugin widget cells: fills the parent box. */
@Composable
fun NativePluginWidgetView(
    factory: () -> View?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> factory() ?: MissingView(context) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun MissingView(context: Context): View =
    TextView(context).apply {
        text = "插件视图不可用"
        textSize = 13f
        val pad = (12 * context.resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }
