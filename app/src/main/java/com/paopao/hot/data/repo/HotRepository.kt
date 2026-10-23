package com.paopao.hot.data.repo

import com.paopao.hot.data.api.HotApi
import com.paopao.hot.data.db.SnapshotDao
import com.paopao.hot.data.db.SnapshotEntity
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.data.model.PlatformDef
import com.paopao.hot.util.HotUrls
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
    val description: String?,
    val url: String?,
    val mobileUrl: String?,
    val cover: String?,
    val hot: Double?,
    val timestamp: Long?
)

/** 周/月聚合条目：一条话题在周期内「上榜天数 / 最佳名次」 */
data class AggregatedEntry(
    val route: String,
    val name: String,
    val title: String,
    val description: String?,
    val url: String?,
    val mobileUrl: String?,
    val cover: String?,
    val timestamp: Long?,
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
                // 空列表通常意味着实例异常，保留上一次成功的数据
                if (items.isEmpty()) continue
                val now = System.currentTimeMillis()
                val entities = items.mapIndexed { index, dto ->
                    val url = HotUrls.normalize(dto.url)
                    val mobileUrl = HotUrls.normalize(dto.mobileUrl) ?: url
                    SnapshotEntity(
                        route = platform.route,
                        name = platform.name,
                        dateEpochDay = day,
                        rank = index + 1,
                        title = dto.title.orEmpty().trim(),
                        description = dto.desc
                            ?.trim()
                            ?.replace(HTML_TAG, "")
                            ?.takeIf { it.isNotEmpty() }
                            ?.take(MAX_DESC_CHARS),
                        // 自定义 scheme（zhihu:// 等）统一转成网页版，转不了就置空
                        url = url ?: mobileUrl,
                        mobileUrl = mobileUrl ?: url,
                        cover = HotUrls.normalize(dto.cover),
                        hot = dto.hot,
                        timestamp = dto.timestamp?.takeIf { it > 0 },
                        fetchedAt = now
                    )
                }.filter { it.title.isNotEmpty() }
                if (entities.isEmpty()) continue
                // 整日替换而不是逐条 upsert：榜单变短时不会残留上一次的高名次旧条目
                dao.deleteDay(platform.route, day)
                dao.upsertAll(entities)
                preferredApiIndex = apis.indexOf(api).coerceAtLeast(0)
                return true
            } catch (_: Throwable) {
                // 换下一个实例重试
            }
        }
        return false
    }

    /** 刷新指定平台（并行拉取），返回 route -> 成功与否 */
    suspend fun refreshAll(
        platforms: List<PlatformDef> = PLATFORMS,
        day: Long = todayEpochDay()
    ): Map<String, Boolean> = coroutineScope {
        platforms
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
                // 摘要/封面可能来自更早的快照，尽量保留信息量最大的一条
                val richest = list
                    .filter { !it.description.isNullOrBlank() || !it.cover.isNullOrBlank() }
                    .maxByOrNull { it.dateEpochDay } ?: latest
                AggregatedEntry(
                    route = latest.route,
                    name = latest.name,
                    title = latest.title,
                    description = richest.description,
                    url = latest.url ?: richest.url,
                    mobileUrl = latest.mobileUrl ?: richest.mobileUrl,
                    cover = richest.cover,
                    timestamp = latest.timestamp ?: richest.timestamp,
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
        description = description,
        url = url,
        mobileUrl = mobileUrl,
        cover = cover,
        hot = hot,
        timestamp = timestamp
    )

    /**
     * 清洗历史快照里的脏链接（自定义 scheme、&amp; 转义）：
     * 能转换成网页版链接的就地改写，转不了的置空（条目变为仅可看摘要）。
     * App 启动时执行一次。
     */
    suspend fun sanitizeLocalUrls() {
        for (row in dao.getDirtyUrls()) {
            val url = HotUrls.normalize(row.url)
            val mobileUrl = HotUrls.normalize(row.mobileUrl) ?: url
            dao.updateUrls(row.id, url ?: mobileUrl, mobileUrl ?: url)
        }
    }

    companion object {
        const val TOP_N = 20
        const val RETENTION_DAYS = 45L
        private const val MAX_DESC_CHARS = 2000
        private val HTML_TAG = Regex("<[^>]+>")

        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

        /** 本周榜起点：含今天的最近 7 天 */
        fun weekStart(): Long = todayEpochDay() - 6

        /** 本月榜起点：含今天的最近 30 天 */
        fun monthStart(): Long = todayEpochDay() - 29
    }
}
