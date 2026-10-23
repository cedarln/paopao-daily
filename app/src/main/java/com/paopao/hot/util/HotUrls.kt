package com.paopao.hot.util

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/**
 * 热榜链接处理：
 * - 清洗 HTML 实体转义（贴吧等接口会返回 &amp;）
 * - 把自定义 scheme（zhihu://、intent://）转换成网页链接
 * - 针对各平台挑选 WebView 能读的页面（B站播放器 / 虎扑桌面版）
 */
object HotUrls {

    private val ZhihuQuestion = Regex("^zhihu://questions?/(\\d+)")
    private val ZhihuAnswer = Regex("^zhihu://answers?/(\\d+)")
    private val BilibiliSchemeVideo = Regex("^bilibili://video/(BV[0-9A-Za-z]{8,})")
    private val BilibiliVideoPath = Regex("BV[0-9A-Za-z]{8,}")
    private val IntentUri = Regex("""^intent://([^#]+)#Intent;.*?scheme=(https?);""", RegexOption.IGNORE_CASE)

    /** 去除首尾空白、还原 HTML 实体，结果可能仍是非 http(s) 链接 */
    fun clean(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return s
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&", ignoreCase = true)
    }

    /**
     * 归一化为可直接加载的 http(s) 链接：
     * 非 http(s) 的自定义 scheme 尽力转成网页版，转不了的返回 null。
     */
    fun normalize(raw: String?): String? {
        val url = clean(raw) ?: return null
        if (url.startsWith("//")) return "https:$url"
        if (isHttpUrl(url)) return url
        return toWebUrl(url)?.takeIf { isHttpUrl(it) }
    }

    /** 判断链接是否为 WebView 可直接加载的 http(s) 链接 */
    fun isHttpUrl(raw: String?): Boolean {
        val s = clean(raw) ?: return false
        return s.startsWith("http://") || s.startsWith("https://")
    }

    /** 自定义 scheme → 网页版链接 */
    fun toWebUrl(raw: String?): String? {
        val url = clean(raw) ?: return null
        if (isHttpUrl(url)) return url

        // zhihu://questions/123456 → https://www.zhihu.com/question/123456
        ZhihuQuestion.find(url)?.let {
            return "https://www.zhihu.com/question/${it.groupValues[1]}"
        }
        ZhihuAnswer.find(url)?.let {
            return "https://www.zhihu.com/answer/${it.groupValues[1]}"
        }
        BilibiliSchemeVideo.find(url)?.let {
            return "https://www.bilibili.com/video/${it.groupValues[1]}"
        }
        // intent://host/path#Intent;scheme=https;... → https://host/path
        IntentUri.find(url)?.let {
            return "${it.groupValues[2]}://${it.groupValues[1]}"
        }
        return null
    }

    /** 分享 / 复制 / 浏览器打开优先使用的规范链接 */
    fun canonical(url: String?, mobileUrl: String?): String? =
        normalize(url) ?: normalize(mobileUrl)

    /** WebView 加载目标：url + 是否使用桌面 UA */
    data class WebTarget(val url: String, val desktop: Boolean = false)

    /**
     * 解析阅读用链接。优先移动版（排版更适合手机），失败再退回规范链接。
     * 阻塞网络调用（仅 b23.tv 短链需要），请在 IO 线程执行。
     */
    fun resolveForReading(mobileUrl: String?, url: String?): WebTarget? =
        resolve(mobileUrl) ?: resolve(url)

    fun resolve(raw: String?): WebTarget? {
        var url = normalize(raw) ?: return null
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null

        // B站视频 → 官方外嵌播放器（B站 H5 播放器在 WebView 里拒绝初始化）
        if (host == "b23.tv") {
            val finalUrl = followRedirects(url)
            val bv = finalUrl?.let { BilibiliVideoPath.find(it) }
            if (bv != null) return WebTarget(embedBilibili(bv.value))
            // 解析失败退化为最终地址继续加载
            if (finalUrl != null && isHttpUrl(finalUrl)) url = finalUrl
        }
        if (host.endsWith("bilibili.com") && url.contains("/video")) {
            BilibiliVideoPath.find(url)?.let { return WebTarget(embedBilibili(it.value)) }
        }

        // 虎扑移动页 → 桌面版（正文服务端直出，移动页正文依赖被拦截的前端 JS）
        if (host == "m.hupu.com") {
            // m.hupu.com/bbs/123.html → bbs.hupu.com/123.html
            val desktopUrl = url
                .replaceFirst("m.hupu.com/bbs/", "bbs.hupu.com/")
                .replaceFirst("m.hupu.com", "bbs.hupu.com")
            return WebTarget(desktopUrl, desktop = true)
        }

        return WebTarget(url)
    }

    /** 抖音等站点网页版强制登录、几乎无法在 WebView 内阅读，详情页据此给出提示 */
    fun isAppOnly(raw: String?): Boolean {
        val host = raw?.let { runCatching { Uri.parse(normalize(it) ?: it).host }.getOrNull() }
            ?.lowercase() ?: return false
        return host.endsWith("douyin.com") || host.endsWith("iesdouyin.com")
    }

    /** 返回被拦截的自定义 scheme 名称（如 zhihu、snssdk1128），用于提示用户 */
    fun blockedScheme(raw: String?): String? {
        val url = clean(raw) ?: return null
        if (isHttpUrl(url)) return null
        val scheme = url.substringBefore(':', "").ifEmpty { return null }
        return scheme
    }

    private fun embedBilibili(bvid: String): String =
        "https://player.bilibili.com/player.html?bvid=$bvid&autoplay=0&danmaku=0"

    /** 跟随最多 5 次 302，返回最终 URL；失败返回 null */
    private fun followRedirects(url: String): String? = runCatching {
        var current = url
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            try {
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                if (code in 300..399 && !loc.isNullOrBlank()) {
                    current = if (loc.startsWith("http")) loc
                    else URL(URL(current), loc).toString()
                } else {
                    return conn.url.toString()
                }
            } finally {
                conn.disconnect()
            }
        }
        current
    }.getOrNull()
}
