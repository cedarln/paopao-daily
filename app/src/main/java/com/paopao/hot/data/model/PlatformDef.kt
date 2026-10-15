package com.paopao.hot.data.model

/** 内置支持的平台清单（route 对应 DailyHotApi 路由） */
data class PlatformDef(
    val route: String,
    val name: String,
    val emoji: String
)

val PLATFORMS: List<PlatformDef> = listOf(
    PlatformDef("zhihu", "知乎", "知"),
    PlatformDef("weibo", "微博", "微"),
    PlatformDef("tieba", "贴吧", "贴"),
    PlatformDef("hupu", "虎扑", "虎"),
    PlatformDef("douyin", "抖音", "抖"),
    PlatformDef("bilibili", "B站", "B"),
    PlatformDef("baidu", "百度", "百"),
    PlatformDef("douban-group", "豆瓣", "豆"),
    PlatformDef("juejin", "掘金", "掘"),
    PlatformDef("ithome", "IT之家", "IT")
)

fun platformByRoute(route: String): PlatformDef? = PLATFORMS.firstOrNull { it.route == route }
