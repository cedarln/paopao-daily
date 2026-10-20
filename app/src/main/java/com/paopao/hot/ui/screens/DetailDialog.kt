package com.paopao.hot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paopao.hot.ui.RowUi
import com.paopao.hot.util.HotUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 桌面 UA：虎扑桌面版等需要伪装成桌面浏览器才返回正文的站点使用 */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 应用内详情页：WebView 直接阅读原文，不再跳出第三方浏览器。
 * 顶部提供复制链接 / 系统分享 / 外部浏览器三个出口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailDialog(row: RowUi, onClose: () -> Unit) {
    val context = LocalContext.current

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { copyLink(context, row.url) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制链接")
                    }
                    IconButton(onClick = { shareLink(context, row) }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = { openInBrowser(context, row.url) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "浏览器打开")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            WebViewContent(rawUrl = row.url)
        }
    }
}

@Composable
private fun WebViewContent(rawUrl: String?) {
    val context = LocalContext.current

    // 解析各平台的反爬转换（B站外嵌播放器 / 虎扑桌面版 / 自定义 scheme / http 升级），
    // b23.tv 短链解析涉及网络请求，放 IO 线程
    val target by produceState<HotUrls.WebTarget?>(initialValue = null, key1 = rawUrl) {
        if (rawUrl.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) { HotUrls.resolve(rawUrl) }
    }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        when {
            rawUrl.isNullOrBlank() -> UnopenableContent(
                onOpenExternal = { openInBrowser(context, rawUrl) }
            )
            // b23.tv 等短链解析中
            target == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            else -> ResolvedWebView(context = context, target = target!!)
        }
    }
}

@Composable
private fun ResolvedWebView(context: Context, target: HotUrls.WebTarget) {
    var progress by remember(target.url) { mutableIntStateOf(0) }
    var loadFailed by remember(target.url) { mutableStateOf(false) }

    if (progress in 1..99) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }

    key(target.url) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // 虎扑桌面版等站点用桌面 UA；其余去掉 WebView 默认 UA 中的 "; wv" 反爬标记
                    settings.userAgentString = if (target.desktop) {
                        DESKTOP_UA
                    } else {
                        settings.userAgentString.replace("; wv", "")
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            return when {
                                // 正常 https 页内跳转继续在应用内加载
                                url.startsWith("https://") -> false
                                // 明文 http 升级 https（Android 9+ 禁止明文流量，直接放行会报
                                // ERR_CLEARTEXT_NOT_PERMITTED，例如微博 m 站的跳转链）
                                url.startsWith("http://") -> {
                                    view?.post {
                                        view.loadUrl("https://${url.removePrefix("http://")}")
                                    }
                                    true
                                }
                                // zhihu:// 等自定义 scheme 是页面试图拉起自家 App 的深链，
                                // 拦截不跟随，留在当前页面
                                else -> true
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // 只有主文档加载失败才提示，子资源失败忽略；
                            // 服务端 302 到明文 http 不一定经过 shouldOverrideUrlLoading，
                            // 这里再做一次 https 升级兜底
                            if (request?.isForMainFrame != true) return
                            val url = request.url.toString()
                            if (url.startsWith("http://")) {
                                loadFailed = false
                                view?.post {
                                    view.loadUrl("https://${url.removePrefix("http://")}")
                                }
                            } else {
                                loadFailed = true
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(target.url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (loadFailed) {
        LoadFailedContent(
            onOpenExternal = { openInBrowser(context, target.url) },
            onCopy = { copyLink(context, target.url) }
        )
    }
}

@Composable
private fun UnopenableContent(onOpenExternal: () -> Unit) {
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
            text = "该链接无法在应用内打开",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpenExternal) {
            Text("用应用 / 浏览器打开")
        }
    }
}

@Composable
private fun LoadFailedContent(onOpenExternal: () -> Unit, onCopy: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🫧", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "页面加载失败，部分站点会拦截应用内浏览",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenExternal) {
                Text("用浏览器打开")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCopy) {
                Text("复制链接")
            }
        }
    }
}

internal fun copyLink(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("paopao", url))
}

internal fun shareLink(context: Context, row: RowUi) {
    val url = row.url ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${row.title}\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "分享到"))
}

fun openInBrowser(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}
