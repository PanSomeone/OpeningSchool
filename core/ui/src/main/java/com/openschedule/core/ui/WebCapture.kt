package com.openschedule.core.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.serialization.json.Json

private val pageJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * A browser the user drives themselves, plus one explicit capture action.
 *
 * Nothing is read until the button is pressed, and the captured HTML is handed
 * to the caller; this component never stores page contents or credentials.
 */
@Composable
fun WebCaptureDialog(
    title: String,
    startUrl: String,
    hint: String,
    actionLabel: String,
    busy: Boolean,
    error: String?,
    onErrorConsumed: () -> Unit,
    onCapture: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        ) {
            WebCaptureContent(
                title = title,
                startUrl = startUrl,
                hint = hint,
                actionLabel = actionLabel,
                busy = busy,
                error = error,
                onErrorConsumed = onErrorConsumed,
                onCapture = onCapture,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun WebCaptureContent(
    title: String,
    startUrl: String,
    hint: String,
    actionLabel: String,
    busy: Boolean,
    error: String?,
    onErrorConsumed: () -> Unit,
    onCapture: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalIsDarkTheme.current
    val view = LocalView.current
    var pageTitle by remember { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
        )
    }

    val webView = remember(startUrl) {
        WebView(context).apply {
            val mainWebView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString = settings.userAgentString.replace("; wv", "")

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                    localError = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    canGoBack = view?.canGoBack() == true
                    pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: title
                    view?.evaluateJavascript(POPUP_HOOK_JS, null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame != true) return
                    loading = false
                    localError = error?.description?.toString() ?: "页面打不开"
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme.orEmpty()
                    if (scheme == "http" || scheme == "https") return false
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
                }

                /**
                 * Some SSO pages open the verification step with window.open.
                 * Load that URL in this same WebView so cookies and the session
                 * stay in one place.
                 */
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val popup = WebView(view?.context ?: context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }
                    popup.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (url.isNullOrBlank() || url.startsWith("about:")) return
                            mainWebView.loadUrl(url)
                            view?.stopLoading()
                            view?.post { view.destroy() }
                        }
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }

            loadUrl(startUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    fun goBack() {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    fun capture() {
        if (busy) return
        localError = null
        onErrorConsumed()
        webView.evaluateJavascript(COLLECT_PAGES_JS) { raw ->
            val pages = runCatching {
                pageJson.decodeFromString<List<String>>(raw)
            }.getOrDefault(emptyList())
            if (pages.isEmpty()) {
                localError = "没有读到页面内容，请等页面加载完再试"
            } else {
                onCapture(pages)
            }
        }
    }

    BackHandler(onBack = ::goBack)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = pageTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
        if (loading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val visibleError = error ?: localError
                if (visibleError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = visibleError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    localError = null
                                    onErrorConsumed()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "关闭提示",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = ::capture,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    text = { Text(if (busy) "处理中" else actionLabel) },
                    expanded = true,
                    modifier = Modifier.height(52.dp),
                )
            }
        }
    }
}

private const val POPUP_HOOK_JS = """
(function() {
  try {
    if (window.__openSchedulePopupHook) return;
    window.__openSchedulePopupHook = true;
    window.open = function(url) {
      if (url) window.location.href = url;
      return null;
    };
    document.addEventListener('click', function(event) {
      try {
        var link = event.target && event.target.closest
          ? event.target.closest('a[target="_blank"], a[target=_blank]')
          : null;
        if (link && link.href) {
          event.preventDefault();
          window.location.href = link.href;
        }
      } catch (e) {}
    }, true);
    document.querySelectorAll('a[target="_blank"], a[target=_blank]').forEach(function(link) {
      link.target = '_self';
    });
  } catch (e) {}
})()
"""

private const val COLLECT_PAGES_JS = """
(function() {
  try {
    var pages = [document.documentElement.outerHTML];
    var frames = document.querySelectorAll('iframe, frame');
    for (var i = 0; i < frames.length; i++) {
      try {
        var doc = frames[i].contentDocument;
        if (doc && doc.documentElement) pages.push(doc.documentElement.outerHTML);
      } catch (e) {}
    }
    return pages;
  } catch (e) {
    return [];
  }
})()
"""
