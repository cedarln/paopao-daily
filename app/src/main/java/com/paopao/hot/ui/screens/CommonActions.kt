package com.paopao.hot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun copyText(context: Context, label: String, text: String?) {
    if (text.isNullOrBlank()) {
        Toast.makeText(context, "暂无可复制的链接", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
}

internal fun shareText(context: Context, title: String, url: String?) {
    val text = if (url.isNullOrBlank()) title else "$title\n$url"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享到")) }
}

/**
 * 用户主动选择「浏览器打开」时才离开 App。
 * ACTION_VIEW 没有指定浏览器包名，系统仍可能交给同名 App 处理，这是用户预期内的选择。
 */
fun openInBrowser(context: Context, url: String?) {
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "暂无原文链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show()
    }
}
