package com.paopao.hot.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object Format {

    /** 热度值格式化：1.2亿 / 34.5万 / 678 */
    fun hot(value: Double?): String = when {
        value == null -> ""
        value >= 1e8 -> String.format("%.1f亿", value / 1e8)
        value >= 1e4 -> String.format("%.1f万", value / 1e4)
        else -> value.roundToInt().toString()
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
