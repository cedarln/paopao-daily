package com.paopao.hot.util

import java.time.LocalDate

/** 条目在本地存储里的稳定键：平台 + 标题（用不可见分隔符避免标题里出现竖线冲突） */
object EntryKeys {

    private const val SEP = "\u001F"

    /** 平台 + 标题：同一天内同一话题的稳定标识（跨天变化不影响收藏） */
    fun entry(route: String, title: String): String = "$route$SEP$title"

    fun favorite(route: String, title: String): String = entry(route, title)

    fun read(route: String, title: String, epochDay: Long): String = "$epochDay$SEP$route$SEP$title"

    fun isRead(readKeys: Set<String>, route: String, title: String, epochDay: Long): Boolean =
        read(route, title, epochDay) in readKeys

    fun readDayPrefix(epochDay: Long): String = "$epochDay$SEP"

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
