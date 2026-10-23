package com.paopao.hot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotUrlsTest {

    @Test
    fun `HTML entity in query is decoded`() {
        assertEquals(
            "https://tieba.baidu.com/hottopic/browse?topic_id=1&topic_name=x",
            HotUrls.normalize("https://tieba.baidu.com/hottopic/browse?topic_id=1&amp;topic_name=x")
        )
    }

    @Test
    fun `zhihu question scheme becomes web url`() {
        assertEquals(
            "https://www.zhihu.com/question/123456",
            HotUrls.normalize("zhihu://questions/123456")
        )
        assertEquals(
            "https://www.zhihu.com/answer/9988",
            HotUrls.normalize("zhihu://answer/9988")
        )
    }

    @Test
    fun `bilibili scheme becomes web url`() {
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            HotUrls.normalize("bilibili://video/BV1xx411c7mD")
        )
    }

    @Test
    fun `intent uri is converted to its http scheme`() {
        assertEquals(
            "https://www.zhihu.com/question/42",
            HotUrls.normalize(
                "intent://www.zhihu.com/question/42#Intent;scheme=https;package=com.zhihu.android;end"
            )
        )
    }

    @Test
    fun `protocol relative image url becomes https`() {
        assertEquals(
            "https://pic1.zhimg.com/v2-abc.png",
            HotUrls.normalize("//pic1.zhimg.com/v2-abc.png")
        )
    }

    @Test
    fun `unknown app scheme is not loadable`() {
        assertNull(HotUrls.normalize("snssdk1128://aweme/detail/123"))
        assertNull(HotUrls.normalize("weixin://dl/business"))
        assertFalse(HotUrls.isHttpUrl("snssdk1128://aweme/detail/123"))
    }

    @Test
    fun `blocked scheme is reported for hint`() {
        assertEquals("zhihu", HotUrls.blockedScheme("zhihu://questions/1"))
        assertNull(HotUrls.blockedScheme("https://www.zhihu.com/question/1"))
    }

    @Test
    fun `canonical prefers web url and falls back to mobile`() {
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            HotUrls.canonical(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                "https://m.bilibili.com/video/BV1xx411c7mD"
            )
        )
        assertEquals(
            "https://m.example.com/a",
            HotUrls.canonical(null, "https://m.example.com/a")
        )
    }

    @Test
    fun `plain http is still considered loadable`() {
        assertTrue(HotUrls.isHttpUrl("http://example.com/a"))
    }

    @Test
    fun `search url encodes the title`() {
        val url = HotUrls.searchUrl("zhihu", "黄海 实弹射击")
        assertEquals(
            "https://www.zhihu.com/search?type=content&q=%E9%BB%84%E6%B5%B7+%E5%AE%9E%E5%BC%B9%E5%B0%84%E5%87%BB",
            url
        )
        assertTrue(HotUrls.isHttpUrl(url))
    }

    @Test
    fun `search url falls back to bing for unknown route`() {
        assertTrue(
            HotUrls.searchUrl("unknown", "話題").startsWith("https://www.bing.com/search?q=")
        )
    }
}
