package com.paopao.hot.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paopao.hot.PaopaoApp
import com.paopao.hot.data.Settings
import com.paopao.hot.data.SettingsDefaults
import com.paopao.hot.data.db.FavoriteEntity
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.data.model.PlatformDef
import com.paopao.hot.data.repo.AggregatedEntry
import com.paopao.hot.data.repo.HotEntry
import com.paopao.hot.data.repo.HotRepository
import com.paopao.hot.util.EntryKeys
import com.paopao.hot.work.DailySyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 页面周期 */
enum class Period(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("本月")
}

/** 「全部」视图里同题话题的一个平台来源 */
data class RowSource(
    val route: String,
    val name: String,
    val rank: Int,
    val hot: Double?,
    val url: String?,
    val mobileUrl: String?
)

/** 列表行统一展示模型（今日条目与周/月聚合条目共用） */
data class RowUi(
    val route: String,
    val name: String,
    val rank: Int,
    val title: String,
    val hot: Double?,
    val daysOnBoard: Int?,
    val bestRank: Int?,
    val url: String?,
    val mobileUrl: String? = null,
    val description: String? = null,
    val cover: String? = null,
    val timestamp: Long? = null,
    /** 「全部」视图里同一话题被几个平台收录，1 表示仅单平台 */
    val platformCount: Int = 1,
    val sources: List<RowSource> = emptyList(),
    /** 较昨日的名次变化，正数上升、负数下降、null 无对比数据 */
    val trend: Int? = null,
    /** 昨日榜单里没有这个话题（需要昨天有数据才成立） */
    val isNew: Boolean = false,
    /** 收藏时间，仅收藏列表使用 */
    val savedAt: Long? = null,
    /** 条目所属快照日期，仅搜索结果/历史回看使用 */
    val seenDay: Long? = null
)

/** 网页阅读请求：优先移动版链接，失败再退回规范链接 */
data class ReaderRequest(
    val title: String,
    val mobileUrl: String?,
    val url: String?
)

data class BoardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<RowUi> = emptyList(),
    val error: String? = null,
    val dataStartDay: Long? = null,
    val lastSyncAt: Long? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as PaopaoApp).container
    private val repo = container.repository

    var period by mutableStateOf(Period.TODAY)
        private set
    var route by mutableStateOf<String?>(null)
        private set

    /** 当前打开的话题详情，null 表示关闭 */
    var detail by mutableStateOf<RowUi?>(null)
        private set

    /** 当前打开的网页阅读器，null 表示关闭 */
    var reader by mutableStateOf<ReaderRequest?>(null)
        private set

    /** 列表关键词过滤（空串 = 不过滤） */
    var query by mutableStateOf("")
        private set

    /** 今日榜单只看未读 */
    var onlyUnread by mutableStateOf(false)
        private set

    /** 正在回看的历史日期（epochDay），null 表示看当前周期的实时榜单 */
    var archiveDay by mutableStateOf<Long?>(null)
        private set

    fun updateQuery(q: String) {
        query = q
        // 逐字输入时只跑最后一次查询，避免每敲一个字都扫一遍数据库
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (q.isNotBlank()) delay(220)
            loadBoard()
        }
    }

    private var searchJob: Job? = null

    fun updateOnlyUnread(enabled: Boolean) {
        onlyUnread = enabled
    }

    /** 回看某天的完整榜单；同时清掉搜索词，避免两个过滤条件互相打架 */
    fun openArchive(day: Long) {
        archiveDay = day
        if (query.isNotEmpty()) query = ""
        viewModelScope.launch { loadBoard() }
    }

    fun exitArchive() {
        if (archiveDay == null) return
        archiveDay = null
        viewModelScope.launch { loadBoard() }
    }

    fun openDetail(row: RowUi) {
        detail = row
        markReadToday(listOf(row))
    }

    fun closeDetail() { detail = null }

    private fun markReadToday(rows: List<RowUi>) {
        if (rows.isEmpty()) return
        val day = EntryKeys.todayEpochDay()
        val keys = rows.map { EntryKeys.read(it.route, it.title, day) }
        viewModelScope.launch {
            container.settingsStore.markRead(keys, EntryKeys.readDayPrefix(day))
        }
    }

    /** 把当前今日榜单（含筛选结果）全部标记为已读 */
    fun markAllTodayRead() {
        markReadToday(_board.value.items)
    }

    fun openReader(title: String, mobileUrl: String?, url: String?) {
        reader = ReaderRequest(title, mobileUrl, url)
    }

    fun closeReader() { reader = null }

    // ---- 收藏 ----

    private suspend fun reloadFavorites() {
        _favorites.value = repo.favorites().map { it.toRowUi() }
        _favoriteKeys.value = repo.favoriteKeys()
    }

    fun toggleFavorite(row: RowUi) {
        viewModelScope.launch {
            val key = EntryKeys.favorite(row.route, row.title)
            if (key in _favoriteKeys.value) {
                repo.removeFavorite(key)
            } else {
                repo.saveFavorite(
                    FavoriteEntity(
                        favoriteKey = key,
                        route = row.route,
                        name = row.name,
                        title = row.title,
                        description = row.description,
                        url = row.url,
                        mobileUrl = row.mobileUrl,
                        cover = row.cover,
                        hot = row.hot,
                        timestamp = row.timestamp,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
            reloadFavorites()
        }
    }

    private fun FavoriteEntity.toRowUi() = RowUi(
        route = route,
        name = name,
        rank = 0,
        title = title,
        hot = hot,
        daysOnBoard = null,
        bestRank = null,
        url = url,
        mobileUrl = mobileUrl,
        description = description,
        cover = cover,
        timestamp = timestamp,
        savedAt = savedAt
    )

    private val _board = MutableStateFlow(BoardUiState())
    val board: StateFlow<BoardUiState> = _board.asStateFlow()

    private val _favorites = MutableStateFlow<List<RowUi>>(emptyList())
    val favorites: StateFlow<List<RowUi>> = _favorites.asStateFlow()

    private val _favoriteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    val settings: StateFlow<Settings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings(SettingsDefaults.BASE_URL, true))

    init {
        viewModelScope.launch {
            // 清洗历史数据里的自定义 scheme / HTML 转义链接，再恢复浏览状态
            repo.sanitizeLocalUrls()
            val saved = container.settingsStore.settings.first()
            period = Period.entries.firstOrNull { it.name == saved.lastPeriod } ?: Period.TODAY
            route = saved.lastRoute?.takeIf { saved.isPlatformEnabled(it) }
            reloadFavorites()
            loadBoard()
            // 从旧版本升级上来时，本地快照还没有摘要/封面，静默补拉一次
            if (saved.contentVersion < SettingsDefaults.CONTENT_VERSION) {
                val hadData = _board.value.items.isNotEmpty()
                if (hadData) refresh()
                container.settingsStore.setContentVersion(SettingsDefaults.CONTENT_VERSION)
            }
        }
    }

    fun selectPeriod(p: Period) {
        if (p == period && archiveDay == null) return
        period = p
        archiveDay = null
        persistUiState()
        viewModelScope.launch { loadBoard() }
    }

    fun selectRoute(r: String?) {
        if (r == route) return
        route = r
        persistUiState()
        viewModelScope.launch { loadBoard() }
    }

    private fun persistUiState() {
        val p = period
        val r = route
        viewModelScope.launch { container.settingsStore.setUiState(p.name, r) }
    }

    /** 手动刷新：只拉取已订阅平台 */
    fun refresh() {
        viewModelScope.launch {
            val platforms = enabledPlatforms()
            if (platforms.isEmpty()) {
                _board.value = _board.value.copy(
                    refreshing = false,
                    error = NO_PLATFORM_HINT
                )
                return@launch
            }
            _board.value = _board.value.copy(refreshing = true, error = null)
            val results = repo.refreshAll(platforms)
            repo.trim()
            val failed = platforms.filter { results[it.route] != true }.map { it.name }
            _board.value = _board.value.copy(
                refreshing = false,
                error = if (failed.isNotEmpty()) "以下平台刷新失败：${failed.joinToString("、")}" else null
            )
            loadBoard(preserveError = true)
        }
    }

    private suspend fun loadBoard(preserveError: Boolean = false) {
        // 列表已有内容时只静默更新，避免切换周期/平台时整页闪 loading
        _board.value = _board.value.copy(
            loading = _board.value.items.isEmpty(),
            error = if (preserveError) _board.value.error else null
        )
        val platforms = enabledPlatforms()
        val dataStart = repo.earliestDay()
        val searching = query.isNotBlank()
        val archived = archiveDay
        val prevRanks = if (!searching && archived == null && period == Period.TODAY) {
            yesterdayRanks()
        } else emptyMap()
        val rows: List<RowUi> = try {
            when {
                // 搜索覆盖全部保留历史（45 天），不受当前周期/平台筛选限制
                searching -> searchHistory(query.trim())
                archived != null -> buildArchiveRows(archived, platforms)
                period == Period.TODAY -> buildTodayRows(platforms, prevRanks)
                period == Period.WEEK -> buildAggregatedRows(platforms, HotRepository.weekStart())
                else -> buildAggregatedRows(platforms, HotRepository.monthStart())
            }
        } catch (t: Throwable) {
            _board.value = _board.value.copy(
                loading = false,
                error = "读取本地数据失败：${t.message}"
            )
            return
        }
        _board.value = _board.value.copy(
            loading = false,
            items = rows,
            error = if (platforms.isEmpty()) NO_PLATFORM_HINT else _board.value.error,
            dataStartDay = dataStart,
            lastSyncAt = repo.lastSyncAt()
        )
        // 今日榜为空且不是用户主动筛选的问题 → 自动尝试联网刷新一次
        if (period == Period.TODAY && archived == null && rows.isEmpty() && platforms.isNotEmpty() &&
            query.isBlank() && !_board.value.refreshing
        ) {
            autoRefreshIfEmpty(platforms)
        }
    }

    /** 全量历史搜索：同题跨平台/跨天合并，按最近出现时间倒序 */
    private suspend fun searchHistory(keyword: String): List<RowUi> =
        repo.search(keyword)
            .groupBy { it.title }
            .map { (_, list) ->
                val best = list.minBy { it.rank }
                val richest = list.firstOrNull { !it.description.isNullOrBlank() } ?: best
                val sources = list
                    .groupBy { it.route }
                    .map { (_, group) ->
                        val g = group.minBy { it.rank }
                        RowSource(g.route, g.name, g.rank, g.hot, g.url, g.mobileUrl)
                    }
                    .sortedBy { it.rank }
                RowUi(
                    route = best.route,
                    name = best.name,
                    rank = best.rank,
                    title = best.title,
                    hot = best.hot,
                    daysOnBoard = list.mapNotNull { it.epochDay }.distinct().size,
                    bestRank = list.minOf { it.rank },
                    url = best.url ?: richest.url,
                    mobileUrl = best.mobileUrl ?: richest.mobileUrl,
                    description = richest.description,
                    cover = richest.cover,
                    timestamp = best.timestamp ?: richest.timestamp,
                    platformCount = sources.size,
                    sources = sources,
                    seenDay = list.maxOfOrNull { it.epochDay ?: 0L }
                )
            }
            .sortedByDescending { it.seenDay ?: 0L }
            .take(SEARCH_RESULT_LIMIT)

    private suspend fun buildTodayRows(
        platforms: List<PlatformDef>,
        prevRanks: Map<String, Int> = emptyMap()
    ): List<RowUi> {
        val target = route
        if (target != null) {
            if (platforms.none { it.route == target }) return emptyList()
            return repo.today(target).map { it.toRowUi(it.rank, null).withTrend(prevRanks) }
        }
        // 全部：跨平台同题合并（话题被多个平台同时收录则算一条），按热度排序取前 20
        return platforms.flatMap { p -> repo.today(p.route) }
            .groupBy { it.title }
            .map { (_, list) -> mergeToday(list) }
            .sortedByDescending { it.hot ?: 0.0 }
            .take(HotRepository.TOP_N)
            .mapIndexed { i, e -> e.copy(rank = i + 1).withTrend(prevRanks) }
    }

    /** 历史回看：某天所有平台的快照按标题合并，和今日榜同样的排序规则 */
    private suspend fun buildArchiveRows(day: Long, platforms: List<PlatformDef>): List<RowUi> {
        val enabled = platforms.mapTo(mutableSetOf()) { it.route }
        return repo.dayEntries(day)
            .filter { it.route in enabled }
            .filter { route == null || it.route == route }
            .groupBy { it.title }
            .map { (_, list) -> mergeToday(list) }
            .sortedByDescending { it.hot ?: 0.0 }
            .take(HotRepository.TOP_N)
            .mapIndexed { i, e -> e.copy(rank = i + 1, seenDay = day) }
    }

    private suspend fun yesterdayRanks(): Map<String, Int> =
        repo.bestRankByTitle(HotRepository.todayEpochDay() - 1)

    /**
     * 名次变化：正数表示上升。昨天没有这个话题时标记为新上榜；
     * 昨天整份榜单为空（首次使用/昨天没同步）时不给任何标记，避免满屏「新」。
     */
    private fun RowUi.withTrend(prev: Map<String, Int>): RowUi {
        if (prev.isEmpty()) return this
        val before = prev[title] ?: return copy(isNew = true, trend = null)
        return copy(trend = before - rank, isNew = false)
    }

    private suspend fun buildAggregatedRows(
        platforms: List<PlatformDef>,
        fromDay: Long
    ): List<RowUi> {
        val target = route
        if (target != null) {
            if (platforms.none { it.route == target }) return emptyList()
            return repo.aggregated(target, fromDay).map { it.toRowUi(it.bestRank) }
        }
        return platforms.flatMap { p -> repo.aggregated(p.route, fromDay) }
            .groupBy { it.title }
            .map { (_, list) -> mergeAggregated(list) }
            .sortedWith(
                compareByDescending<RowUi> { it.daysOnBoard ?: 0 }
                    .thenByDescending { it.hot ?: 0.0 }
            )
            .take(HotRepository.TOP_N)
            .mapIndexed { i, e -> e.copy(rank = i + 1) }
    }

    private fun mergeToday(list: List<HotEntry>): RowUi {
        val primary = list.maxBy { it.hot ?: 0.0 }
        val sources = list
            .groupBy { it.route }
            .map { (_, group) -> group.minBy { it.rank } }
            .map { RowSource(it.route, it.name, it.rank, it.hot, it.url, it.mobileUrl) }
            .sortedBy { it.rank }
        return primary.toRowUi(0, null)
            .copy(platformCount = sources.size, sources = sources)
    }

    private fun mergeAggregated(list: List<AggregatedEntry>): RowUi {
        val primary = list.sortedWith(
            compareByDescending<AggregatedEntry> { it.daysOnBoard }
                .thenByDescending { it.latestHot ?: 0.0 }
        ).first()
        val sources = list
            .map { RowSource(it.route, it.name, it.bestRank, it.latestHot, it.url, it.mobileUrl) }
            .distinctBy { it.route }
            .sortedBy { it.rank }
        return primary.toRowUi(primary.bestRank)
            .copy(platformCount = sources.size, sources = sources)
    }

    private fun autoRefreshIfEmpty(platforms: List<PlatformDef>) {
        viewModelScope.launch {
            _board.value = _board.value.copy(refreshing = true)
            repo.refreshAll(platforms)
            repo.trim()
            val prevRanks = if (period == Period.TODAY) yesterdayRanks() else emptyMap()
            val rows = when (period) {
                Period.TODAY -> buildTodayRows(platforms, prevRanks)
                Period.WEEK -> buildAggregatedRows(platforms, HotRepository.weekStart())
                Period.MONTH -> buildAggregatedRows(platforms, HotRepository.monthStart())
            }
            _board.value = _board.value.copy(
                refreshing = false,
                items = rows,
                lastSyncAt = repo.lastSyncAt(),
                error = if (rows.isEmpty()) "暂无数据：可能网络不可用或 API 服务异常，下拉可重试" else null
            )
        }
    }

    private suspend fun enabledPlatforms(): List<PlatformDef> {
        val enabled = container.settingsStore.settings.first().effectiveEnabledRoutes
        return PLATFORMS.filter { it.route in enabled }
    }

    private fun HotEntry.toRowUi(rank: Int, days: Int?) = RowUi(
        route = route,
        name = name,
        rank = rank,
        title = title,
        hot = hot,
        daysOnBoard = days,
        bestRank = null,
        url = url,
        mobileUrl = mobileUrl,
        description = description,
        cover = cover,
        timestamp = timestamp,
        sources = listOf(RowSource(route, name, rank, hot, url, mobileUrl))
    )

    private fun AggregatedEntry.toRowUi(rank: Int) = RowUi(
        route = route,
        name = name,
        rank = rank,
        title = title,
        hot = latestHot,
        daysOnBoard = daysOnBoard,
        bestRank = bestRank,
        url = url,
        mobileUrl = mobileUrl,
        description = description,
        cover = cover,
        timestamp = timestamp,
        sources = listOf(RowSource(route, name, bestRank, latestHot, url, mobileUrl))
    )

    // ---- 平台订阅 ----

    fun setPlatformSubscribed(routeId: String, subscribed: Boolean) {
        viewModelScope.launch {
            val current = container.settingsStore.settings.first()
                .effectiveEnabledRoutes
                .toMutableSet()
            if (subscribed) current.add(routeId) else current.remove(routeId)
            container.settingsStore.setEnabledRoutes(current)
            if (!subscribed && route == routeId) {
                route = null
                persistUiState()
            }
            if (subscribed) refresh() else loadBoard()
        }
    }

    fun setAllPlatformsSubscribed(subscribed: Boolean) {
        viewModelScope.launch {
            if (subscribed) {
                container.settingsStore.resetPlatformSubscriptions()
                refresh()
            } else {
                container.settingsStore.setEnabledRoutes(emptySet())
                route = null
                persistUiState()
                loadBoard()
            }
        }
    }

    // ---- 设置页 ----

    fun saveBaseUrl(raw: String) {
        viewModelScope.launch {
            container.updateBaseUrl(raw)
            container.settingsStore.setBaseUrl(container.baseUrl())
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setAutoSync(enabled)
            val context = getApplication<PaopaoApp>()
            if (enabled) DailySyncWorker.schedule(context) else DailySyncWorker.cancel(context)
        }
    }

    fun setReaderFontPercent(percent: Int) {
        viewModelScope.launch { container.settingsStore.setReaderFontPercent(percent) }
    }

    fun setReaderDark(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setReaderDark(enabled) }
    }

    fun setReaderNoImages(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setReaderNoImages(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setDynamicColor(enabled) }
    }

    fun setShowCovers(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setShowCovers(enabled) }
    }

    fun setCompactRows(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setCompactRows(enabled) }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            repo.clearAll()
            loadBoard()
        }
    }

    fun allPlatforms(): List<PlatformDef> = PLATFORMS

    companion object {
        const val NO_PLATFORM_HINT = "还没有订阅任何平台，去「设置 → 平台订阅」开启几个吧"
        private const val SEARCH_RESULT_LIMIT = 50
    }
}
