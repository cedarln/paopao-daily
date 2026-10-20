package com.paopao.hot.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object Format {

    /** 热度值格式化：1.2亿 / 34.5万 / 678（整数位去掉无意义的 .0） */
    fun hot(value: Double?): String = when {
        value == null -> ""
        value >= 1e8 -> trimZero(String.format("%.1f亿", value / 1e8))
        value >= 1e4 -> trimZero(String.format("%.1f万", value / 1e4))
        else -> value.roundToInt().toString()
    }

    private fun trimZero(s: String): String =
        if (s.endsWith(".0")) s.dropLast(2) else s

    /** 文件大小格式化：1.2 MB / 856 KB */
    fun size(bytes: Long): String = when {
        bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
        bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun date(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MM-dd"))

    fun time(millis: Long?): String {
        if (millis == null || millis <= 0) return "暂无记录"
        val d = java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
        val today = LocalDate.now()
        return when (d.toLocalDate()) {
            today -> String.format("今天 %02d:%02d", d.hour, d.minute)
            today.minusDays(1) -> String.format("昨天 %02d:%02d", d.hour, d.minute)
            else -> d.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        }
    }
}
