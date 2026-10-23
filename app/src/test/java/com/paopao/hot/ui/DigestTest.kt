package com.paopao.hot.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestTest {

    private fun row(title: String, name: String) = RowUi(
        route = "zhihu",
        name = name,
        rank = 1,
        title = title,
        hot = null,
        daysOnBoard = null,
        bestRank = null,
        url = null
    )

    private val date = LocalDate.of(2026, 10, 23)

    @Test
    fun `empty board yields no text`() {
        assertEquals("", Digest.build(emptyList(), date))
    }

    @Test
    fun `digest numbers the entries and keeps platform names`() {
        val text = Digest.build(
            listOf(row("第一条", "知乎"), row("第二条", "微博")),
            date
        )
        assertTrue(text.startsWith("🔥 泡泡热榜 · 10月23日 今日速览"))
        assertTrue(text.contains("1. 第一条（知乎）"))
        assertTrue(text.contains("2. 第二条（微博）"))
        assertTrue(text.endsWith("来自「泡泡热榜」App"))
    }

    @Test
    fun `topN limits the number of entries`() {
        val rows = (1..12).map { row("话题$it", "知乎") }
        val text = Digest.build(rows, date, topN = 5)
        assertTrue(text.contains("5. 话题5"))
        assertTrue(!text.contains("6. 话题6"))
    }
}
