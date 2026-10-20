package com.paopao.hot.util

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/**
 * 热榜链接处理：部分平台的 mobileUrl 是自定义 scheme（如知乎的 zhihu://），
 * WebView 无法加载。这里统一转成网页版 https 链接，转不了的返回 null 走外部打开。
 */
object HotUrls {

    private val ZhihuQuestion = Regex("^zhihu://questions?/(\\d+)")
    private val BvidPattern = Regex("BV[0-9A-Za-z]{8,}")

    fun toWebUrl(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url

        // zhihu://questions/123456 → https://www.zhihu.com/question/123456
        ZhihuQuestion.find(url)?.let {
            return "https://www.zhihu.com/question/${it.groupValues[1]}"
        }
        return null
    }

    /** 判断链接是否为 WebView 可直接加载的 http(s) 链接 */
    fun isHttpUrl(raw: String?): Boolean =
        raw?.let { it.startsWith("http://") || it.startsWith("https://") } == true

    /** WebView 加载目标：url + 是否使用桌面 UA */
    data class WebTarget(val url: String, val desktop: Boolean = false)

    /**
     * 把条目链接解析成 WebView 可用的目标（针对各平台反爬策略定点转换）：
     * - 非 http(s) 的自定义 scheme（zhihu:// 等）转网页版链接
     * - B站视频：转官方外嵌播放器（B站 H5 播放器在 WebView 里拒绝初始化，只出封面）
     *   b23.tv 短链先跟随 302 重定向解析出 BV 号
     * - 虎扑移动页：转桌面版并使用桌面 UA（移动页正文依赖被反爬拦截的前端 JS，桌面版服务端直出）
     * - 明文 http 统一升级 https（Android 9+ 默认禁止明文流量）
     *
     * 阻塞网络调用（仅 b23.tv 短链需要），请在 IO 线程执行。
     */
    fun resolve(raw: String?): WebTarget? {
        if (raw.isNullOrBlank()) return null
        var url = raw.trim()
        if (!isHttpUrl(url)) {
            url = toWebUrl(url) ?: return null
            if (!isHttpUrl(url)) return null
        }
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null

        // B站视频 → 官方外嵌播放器
        if (host == "b23.tv") {
            val finalUrl = followRedirects(url)
            val bv = finalUrl?.let { BvidPattern.find(it) }
            if (bv != null) return WebTarget(embedBilibili(bv.value))
            // 解析失败退化为最终地址继续加载
            if (finalUrl != null && isHttpUrl(finalUrl)) url = finalUrl
        }
        if (host.endsWith("bilibili.com") && url.contains("/video")) {
            BvidPattern.find(url)?.let { return WebTarget(embedBilibili(it.value)) }
        }

        // 虎扑移动页 → 桌面版（正文服务端直出）
        if (host == "m.hupu.com") {
            return WebTarget(
                url.replaceFirst("m.hupu.com", "bbs.hupu.com"),
                desktop = true
            )
        }

        return WebTarget(url)
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
