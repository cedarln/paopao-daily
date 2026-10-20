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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    // 自定义 scheme（zhihu:// 等）统一转成网页版链接；转不了走外部打开兜底
    val target = remember(rawUrl) { HotUrls.toWebUrl(rawUrl) }

    if (target == null) {
        UnopenableContent(onOpenExternal = { openInBrowser(context, rawUrl) })
        return
    }

    var progress by remember(target) { mutableIntStateOf(0) }
    var loadFailed by remember(target) { mutableStateOf(false) }

    if (progress in 1..99) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 去掉 WebView 默认 UA 中的 "; wv" 标记，
                // 抖音/虎扑等站点会据此识别 WebView 并返回空白反爬页
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // http(s) 页内跳转继续在应用内加载；
                        // zhihu:// 等自定义 scheme 是页面试图拉起自家 App 的深链，
                        // WebView 加载不了，也不主动拉起 App——直接拦截留在当前页面
                        return !(url.startsWith("http://") || url.startsWith("https://"))
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        // 只有主文档加载失败才提示，子资源失败忽略
                        if (request?.isForMainFrame == true) loadFailed = true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress
                    }
                }
                loadUrl(target)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    if (loadFailed) {
        LoadFailedContent(
            onOpenExternal = { openInBrowser(context, target) },
            onCopy = { copyLink(context, target) }
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
