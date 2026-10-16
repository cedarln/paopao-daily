package com.paopao.hot.util

/**
 * 热榜链接处理：部分平台的 mobileUrl 是自定义 scheme（如知乎的 zhihu://），
 * WebView 无法加载。这里统一转成网页版 https 链接，转不了的返回 null 走外部打开。
 */
object HotUrls {

    private val ZhihuQuestion = Regex("^zhihu://questions?/(\\d+)")

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
}
