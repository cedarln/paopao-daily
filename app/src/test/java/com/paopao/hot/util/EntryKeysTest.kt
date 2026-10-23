package com.paopao.hot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryKeysTest {

    @Test
    fun `same title on different platforms is a different entry`() {
        assertNotEquals(
            EntryKeys.entry("zhihu", "同一话题"),
            EntryKeys.entry("weibo", "同一话题")
        )
    }

    @Test
    fun `read key is scoped to a single day`() {
        val day = 20_000L
        val keys = setOf(EntryKeys.read("zhihu", "标题", day))
        assertTrue(EntryKeys.isRead(keys, "zhihu", "标题", day))
        assertFalse(EntryKeys.isRead(keys, "zhihu", "标题", day + 1))
        assertFalse(EntryKeys.isRead(keys, "weibo", "标题", day))
    }

    @Test
    fun `day prefix only matches keys of that day`() {
        val key = EntryKeys.read("zhihu", "标题", 20_000L)
        assertTrue(key.startsWith(EntryKeys.readDayPrefix(20_000L)))
        assertFalse(key.startsWith(EntryKeys.readDayPrefix(20_001L)))
    }

    @Test
    fun `titles containing separator-like characters stay distinct`() {
        assertEquals(
            "zhihu\u001F标题|带竖线",
            EntryKeys.entry("zhihu", "标题|带竖线")
        )
    }
}
