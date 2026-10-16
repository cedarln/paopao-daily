package com.paopao.hot.data.repo

import com.paopao.hot.data.api.HotApi
import com.paopao.hot.data.api.HotItemDto
import com.paopao.hot.data.db.SnapshotDao
import com.paopao.hot.data.db.SnapshotEntity
import com.paopao.hot.data.model.PlatformDef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/** UI 层用的热榜条目 */
data class HotEntry(
    val route: String,
    val name: String,
    val rank: Int,
    val title: String,
    val desc: String?,
    val url: String?,
    val hot: Double?
)

/** 周/月聚合条目：一条话题在周期内「上榜天数 / 最佳名次」 */
data class AggregatedEntry(
    val route: String,
    val name: String,
    val title: String,
    val url: String?,
    val daysOnBoard: Int,
    val bestRank: Int,
    val latestHot: Double?,
    val lastSeenEpochDay: Long
)

class HotRepository(
    private val apiProvider: () -> List<HotApi>,
    private val dao: SnapshotDao
) {

    /** 上次成功的 API 实例下标，粘性优先复用，减少无谓的失败重试 */
    @Volatile
    private var preferredApiIndex = 0

    /** 拉取单个平台并写入快照，主实例失败自动切换备用实例，返回是否成功 */
    suspend fun refreshPlatform(platform: PlatformDef, day: Long): Boolean {
        val apis = apiProvider()
        // 从上次成功的实例开始尝试，形成粘性路由
        val ordered = apis.drop(preferredApiIndex.coerceAtMost(apis.lastIndex)) +
            apis.take(preferredApiIndex.coerceAtMost(apis.lastIndex))
        for (api in ordered) {
            try {
                val resp = api.getHot(platform.route)
                val items = resp.data.take(TOP_N)
                val now = System.currentTimeMillis()
                val entities = items.mapIndexed { index, dto ->
                    SnapshotEntity(
                        route = platform.route,
                        name = platform.name,
                        dateEpochDay = day,
                        rank = index + 1,
                        title = dto.title.orEmpty().trim(),
                        // 部分平台（如知乎）的 mobileUrl 是 zhihu:// 自定义协议，
                        // WebView 加载不了，优先存 http(s) 链接
                        url = pickHttpUrl(dto.mobileUrl, dto.url),
                        hot = dto.hot,
                        fetchedAt = now
                    )
                }.filter { it.title.isNotEmpty() }
                dao.upsertAll(entities)
                preferredApiIndex = apis.indexOf(api).coerceAtLeast(0)
                return true
            } catch (_: Throwable) {
                // 换下一个实例重试
            }
        }
        return false
    }

    /** 刷新全部平台（并行拉取），返回 route -> 成功与否 */
    suspend fun refreshAll(day: Long = todayEpochDay()): Map<String, Boolean> = coroutineScope {
        com.paopao.hot.data.model.PLATFORMS
            .map { p -> async { p.route to refreshPlatform(p, day) } }
            .awaitAll()
            .toMap()
    }

    /** 今日榜（读本地快照，需先 refresh） */
    suspend fun today(route: String): List<HotEntry> {
        val day = todayEpochDay()
        return dao.getDay(route, day).map { it.toEntry() }
    }

    /**
     * 周/月聚合榜：对 [fromDay, today] 区间内的快照按标题去重聚合，
     * 排序规则：上榜天数多者优先，其次看最新热度。
     */
    suspend fun aggregated(route: String, fromDay: Long): List<AggregatedEntry> {
        val today = todayEpochDay()
        val rows = dao.getRange(fromDay, today).filter { it.route == route }
        return rows
            .groupBy { it.title }
            .map { (_, list) ->
                val latest = list.maxBy { it.dateEpochDay }
                AggregatedEntry(
                    route = latest.route,
                    name = latest.name,
                    title = latest.title,
                    url = latest.url,
                    daysOnBoard = list.map { it.dateEpochDay }.distinct().size,
                    bestRank = list.minOf { it.rank },
                    latestHot = latest.hot,
                    lastSeenEpochDay = latest.dateEpochDay
                )
            }
            .sortedWith(
                compareByDescending<AggregatedEntry> { it.daysOnBoard }
                    .thenByDescending { it.latestHot ?: Double.MIN_VALUE }
            )
            .take(TOP_N)
    }

    suspend fun lastSyncAt(): Long? = dao.lastSyncAt()
    suspend fun earliestDay(): Long? = dao.earliestDay()
    suspend fun clearAll() = dao.clearAll()

    /** 只保留最近 45 天快照，防止库无限膨胀 */
    suspend fun trim() {
        dao.trimBefore(todayEpochDay() - RETENTION_DAYS)
    }

    private fun SnapshotEntity.toEntry() = HotEntry(
        route = route,
        name = name,
        rank = rank,
        title = title,
        desc = null,
        url = url,
        hot = hot
    )

    /** 优先返回 http(s) 链接：mobileUrl 若为自定义 scheme 则回落到标准 url */
    private fun pickHttpUrl(mobileUrl: String?, url: String?): String? = when {
        com.paopao.hot.util.HotUrls.isHttpUrl(mobileUrl) -> mobileUrl
        com.paopao.hot.util.HotUrls.isHttpUrl(url) -> url
        else -> url ?: mobileUrl
    }

    /**
     * 清洗历史快照里的自定义 scheme 链接（如备用实例曾返回 zhihu://）：
     * 能转换成网页版链接的就地改写，转不了的置空（条目变为不可点击）。
     * App 启动时执行一次。
     */
    suspend fun sanitizeLocalUrls() {
        val bad = dao.getNonHttpUrls()
        for (row in bad) {
            val fixed = com.paopao.hot.util.HotUrls.toWebUrl(row.url)
            dao.updateUrl(row.id, fixed)
        }
    }

    companion object {
        const val TOP_N = 20
        const val RETENTION_DAYS = 45L

        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

        /** 本周榜起点：含今天的最近 7 天 */
        fun weekStart(): Long = todayEpochDay() - 6

        /** 本月榜起点：含今天的最近 30 天 */
        fun monthStart(): Long = todayEpochDay() - 29
    }
}
