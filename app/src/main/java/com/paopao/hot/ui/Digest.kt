package com.paopao.hot.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 今日速览：把榜单前几条拼成一段可直接粘贴到聊天窗口的纯文本 */
object Digest {

    private val DATE = DateTimeFormatter.ofPattern("M月d日")

    fun today(rows: List<RowUi>, topN: Int = 10): String = build(rows, LocalDate.now(), topN)

    fun build(rows: List<RowUi>, date: LocalDate, topN: Int = 10): String {
        if (rows.isEmpty()) return ""
        val top = rows.take(topN)
        return buildString {
            append("🔥 泡泡热榜 · ${date.format(DATE)} 今日速览\n\n")
            top.forEachIndexed { i, row ->
                append(i + 1)
                append(". ")
                append(row.title)
                append("（")
                append(row.name)
                append("）")
                append('\n')
            }
            append("\n来自「泡泡热榜」App")
        }.trimEnd()
    }
}
