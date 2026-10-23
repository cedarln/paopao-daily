package com.paopao.hot.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paopao.hot.data.Settings
import com.paopao.hot.ui.ReaderRequest
import com.paopao.hot.util.HotUrls
import com.paopao.hot.util.ReaderCss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** 标准 Android Chrome UA：去掉 WebView 默认 UA 里的 "; wv" 标记，减少站点反爬误伤 */
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** 虎扑桌面版等需要伪装成桌面浏览器才返回正文的站点使用 */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private sealed interface ReaderTargetState {
    data object Loading : ReaderTargetState
    data object NoLink : ReaderTargetState
    data class Ready(val target: HotUrls.WebTarget) : ReaderTargetState
}

/**
 * 应用内网页阅读器（用户主动进入的二级页面）：
 * - 只有 48dp 工具条，站点自己的顶栏/下载横幅由注入 CSS 隐藏
 * - 所有非 http(s) 深链一律拦截，转为「用浏览器打开」的可选操作
 * - 返回键优先网页后退，关闭时销毁 WebView
 */
@Composable
fun WebReaderDialog(
    request: ReaderRequest,
    readerFontPercent: Int,
    readerDark: Boolean,
    readerNoImages: Boolean,
    onFontPercentChange: (Int) -> Unit,
    onReaderDarkChange: (Boolean) -> Unit,
    onReaderNoImagesChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var prefsOpen by remember { mutableStateOf(false) }
    var lastBlockedAt by remember { mutableLongStateOf(0L) }
    var blockedHint by remember { mutableStateOf(false) }
    var blockedHintSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(blockedHintSeq) {
        if (blockedHintSeq == 0) return@LaunchedEffect
        delay(8_000)
        blockedHint = false
    }

    // 字号与网页样式可实时调整，不需要重新加载页面
    LaunchedEffect(webView, readerFontPercent) {
        webView?.settings?.textZoom = readerFontPercent
    }
    LaunchedEffect(webView, readerDark, readerNoImages) {
        webView?.evaluateJavascript(prefsScript(readerDark, readerNoImages), null)
    }

    val canonical = remember(request) { HotUrls.canonical(request.url, request.mobileUrl) }

    val targetState by produceState<ReaderTargetState>(ReaderTargetState.Loading, request) {
        value = withContext(Dispatchers.IO) {
            val target = HotUrls.resolveForReading(request.mobileUrl, request.url)
            if (target == null) ReaderTargetState.NoLink else ReaderTargetState.Ready(target)
        }
    }

    fun goBack() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    BackHandler { goBack() }

    fun showBlockedHint() {
        // 页面可能连续尝试多个深链，2 秒内只提示一次
        val now = System.currentTimeMillis()
        if (now - lastBlockedAt < 2_000) return
        lastBlockedAt = now
        blockedHint = true
        blockedHintSeq++
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    ReaderTopBar(
                        title = request.title,
                        onBack = { goBack() },
                        onRefresh = { webView?.reload() },
                        refreshEnabled = targetState is ReaderTargetState.Ready,
                        onCopy = { copyText(context, "paopao", canonical) },
                        onBrowser = { openInBrowser(context, canonical) },
                        onReaderSettings = { prefsOpen = true },
                        menuOpen = menuOpen,
                        onMenuOpenChange = { menuOpen = it }
                    )
                    if (progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    } else {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .navigationBarsPadding()
                    ) {
                        when (val state = targetState) {
                            ReaderTargetState.Loading -> ReaderLoading()
                            ReaderTargetState.NoLink -> ReaderNoLink(onBrowser = {
                                openInBrowser(context, canonical)
                            })
                            is ReaderTargetState.Ready -> key(state.target.url) {
                                ReaderWebView(
                                    target = state.target,
                                    fontPercent = readerFontPercent,
                                    dark = readerDark,
                                    noImages = readerNoImages,
                                    onReady = { webView = it },
                                    onProgress = { progress = it },
                                    onStart = {
                                        failure = null
                                        progress = 0
                                    },
                                    onFailed = {
                                        progress = 100
                                        failure = it
                                    },
                                    onBlocked = { showBlockedHint() }
                                )
                            }
                        }

                        failure?.let { message ->
                            ReaderFailure(
                                message = message,
                                onRetry = {
                                    failure = null
                                    progress = 0
                                    webView?.reload()
                                },
                                onBrowser = { openInBrowser(context, canonical) }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = blockedHint,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 }
                ) {
                    BlockedHintBar(
                        onDismiss = { blockedHint = false },
                        onBrowser = {
                            blockedHint = false
                            openInBrowser(context, canonical)
                        }
                    )
                }
            }
        }
    }

    if (prefsOpen) {
        AlertDialog(
            onDismissRequest = { prefsOpen = false },
            title = { Text("阅读设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "正文字号",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Settings.READER_FONT_CHOICES.forEach { percent ->
                                FilterChip(
                                    selected = percent == readerFontPercent,
                                    onClick = { onFontPercentChange(percent) },
                                    label = {
                                        Text(
                                            "$percent%",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("深色网页", fontSize = 15.sp)
                            Text(
                                "夜间阅读时反转页面配色",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = readerDark,
                            onCheckedChange = onReaderDarkChange
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("隐藏图片", fontSize = 15.sp)
                            Text(
                                "只保留文字，省流量也更快",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = readerNoImages,
                            onCheckedChange = onReaderNoImagesChange
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { prefsOpen = false }) { Text("完成") }
            }
        )
    }
}

/** 把阅读偏好写进独立 style 标签，开关切换不需要重新加载页面 */
private fun prefsScript(dark: Boolean, noImages: Boolean): String {
    val css = ReaderCss.prefsCss(dark, noImages)
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")
    return """
        (function() {
          var id = 'paopao-reader-prefs';
          var style = document.getElementById(id);
          if (!style) {
            style = document.createElement('style');
            style.id = id;
            (document.head || document.documentElement).appendChild(style);
          }
          style.textContent = '$css';
        })();
    """.trimIndent()
}

/**
 * 深链拦截提示条。不用 Snackbar：系统字体放大后「文案 + 操作」挤在一行会超出屏幕，
 * 这里文案独占一行、按钮另起一行，任何字号都能完整显示。
 */
@Composable
private fun BlockedHintBar(
    onDismiss: () -> Unit,
    onBrowser: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "网页想跳转到第三方 App，已拦截",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                ) {
                    Text("知道了")
                }
                TextButton(
                    onClick = onBrowser,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    Text("用浏览器打开")
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
    onCopy: () -> Unit,
    onBrowser: () -> Unit,
    onReaderSettings: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        IconButton(
            onClick = onRefresh,
            enabled = refreshEnabled,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text("阅读设置") },
                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onReaderSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("复制链接") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onCopy()
                    }
                )
                DropdownMenuItem(
                    text = { Text("浏览器打开") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onBrowser()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebView(
    target: HotUrls.WebTarget,
    fontPercent: Int,
    dark: Boolean,
    noImages: Boolean,
    onReady: (WebView) -> Unit,
    onProgress: (Int) -> Unit,
    onStart: () -> Unit,
    onFailed: (String) -> Unit,
    onBlocked: () -> Unit
) {
    val context = LocalContext.current
    val currentDark by rememberUpdatedState(dark)
    val currentNoImages by rememberUpdatedState(noImages)

    val webView = remember(target.url) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                // 视频/音频必须用户点击后才播放，避免退出后还在响
                mediaPlaybackRequiresUserGesture = true
                // https 页面里的 http 图片/资源尽量继续加载
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
                textZoom = fontPercent
                userAgentString = if (target.desktop) DESKTOP_UA else MOBILE_UA
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    return when (uri.scheme?.lowercase(Locale.ROOT)) {
                        // 正常网页跳转留在应用内
                        "https" -> false
                        // 明文 http 升级 https：Android 9+ 直接加载会报 CLEARTEXT_NOT_PERMITTED
                        "http" -> {
                            view.post {
                                view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                            }
                            true
                        }
                        // zhihu://、sinaweibo://、snssdk1128://、intent:// 等一律不跟随，
                        // 否则会拉起对应 App 并可能直接报错
                        else -> {
                            onBlocked()
                            true
                        }
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    onStart()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view != null && url != null) {
                        injectReaderCleanup(view, url, currentDark, currentNoImages)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // 只有主文档失败才提示，子资源失败忽略
                    if (request?.isForMainFrame != true) return
                    val failedUrl = request.url.toString()
                    if (failedUrl.startsWith("http://")) {
                        // 服务端 302 到明文 http 不一定经过 shouldOverrideUrlLoading，这里兜底升级
                        view?.post {
                            view.loadUrl("https://${failedUrl.removePrefix("http://")}")
                        }
                    } else {
                        onFailed(error?.description?.toString().orEmpty().ifBlank { "页面加载失败" })
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.cancel()
                    onFailed("证书校验失败，已停止加载")
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    // 知乎等平台会对应用内访问直接返回 403，页面本身是反爬提示，展示自己的兜底更清楚
                    if (request?.isForMainFrame != true) return
                    val code = errorResponse?.statusCode ?: return
                    if (code >= 400) {
                        onFailed("页面返回 HTTP $code，平台可能限制了应用内访问")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgress(newProgress)
                }
            }

            loadUrl(target.url)
        }
    }

    DisposableEffect(webView) {
        onReady(webView)
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}

/** 只留正文：隐藏站点顶栏、App 下载/打开横幅，并应用阅读偏好 */
private fun injectReaderCleanup(webView: WebView, url: String, dark: Boolean, noImages: Boolean) {
    val css = ReaderCss.forUrl(url)
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")
    val js = """
        (function() {
          var id = 'paopao-reader-style';
          var style = document.getElementById(id);
          if (!style) {
            style = document.createElement('style');
            style.id = id;
            (document.head || document.documentElement).appendChild(style);
          }
          style.textContent = '$css';
        })();
        ${prefsScript(dark, noImages)}
        ${ReaderCss.cleanupScript()}
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

@Composable
private fun ReaderLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "正在准备页面…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReaderNoLink(onBrowser: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🫧", fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "这条内容没有可用的原文链接",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onBrowser) {
            Text("尝试用浏览器打开")
        }
    }
}

@Composable
private fun ReaderFailure(
    message: String,
    onRetry: () -> Unit,
    onBrowser: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🫧", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "应用内页面没能打开",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onRetry) { Text("重试") }
                    OutlinedButton(onClick = onBrowser) { Text("浏览器打开") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "部分平台会限制应用内浏览，用浏览器打开通常可以正常阅读",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
