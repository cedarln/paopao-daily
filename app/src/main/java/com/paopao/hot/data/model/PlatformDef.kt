package com.paopao.hot.data.model

/** 内置支持的平台清单（route 对应 DailyHotApi 路由） */
data class PlatformDef(
    val route: String,
    val name: String,
    val emoji: String,
    /** 品牌色，用于列表和详情页的小面积点缀 */
    val color: Long
)

val PLATFORMS: List<PlatformDef> = listOf(
    PlatformDef("zhihu", "知乎", "知", 0xFF0066FF),
    PlatformDef("weibo", "微博", "微", 0xFFE6162D),
    PlatformDef("tieba", "贴吧", "贴", 0xFF3385FF),
    PlatformDef("hupu", "虎扑", "虎", 0xFFD9311F),
    PlatformDef("douyin", "抖音", "抖", 0xFFFE2C55),
    PlatformDef("bilibili", "B站", "B", 0xFFFB7299),
    PlatformDef("baidu", "百度", "百", 0xFF2932E1),
    PlatformDef("douban-group", "豆瓣", "豆", 0xFF00B51D),
    PlatformDef("juejin", "掘金", "掘", 0xFF1E80FF),
    PlatformDef("ithome", "IT之家", "IT", 0xFFD22222)
)

fun platformByRoute(route: String): PlatformDef? = PLATFORMS.firstOrNull { it.route == route }
