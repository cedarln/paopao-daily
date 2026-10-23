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
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.data.model.PlatformDef
import com.paopao.hot.data.repo.AggregatedEntry
import com.paopao.hot.data.repo.HotEntry
import com.paopao.hot.data.repo.HotRepository
import com.paopao.hot.work.DailySyncWorker
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
    val sources: List<RowSource> = emptyList()
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

    fun updateQuery(q: String) {
        query = q
        viewModelScope.launch { loadBoard() }
    }

    fun openDetail(row: RowUi) { detail = row }
    fun closeDetail() { detail = null }

    fun openReader(title: String, mobileUrl: String?, url: String?) {
        reader = ReaderRequest(title, mobileUrl, url)
    }

    fun closeReader() { reader = null }

    private val _board = MutableStateFlow(BoardUiState())
    val board: StateFlow<BoardUiState> = _board.asStateFlow()

    val settings: StateFlow<Settings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings(SettingsDefaults.BASE_URL, true))

    init {
        viewModelScope.launch {
            // 清洗历史数据里的自定义 scheme / HTML 转义链接，再恢复浏览状态
            repo.sanitizeLocalUrls()
            val saved = container.settingsStore.settings.first()
            period = Period.entries.firstOrNull { it.name == saved.lastPeriod } ?: Period.TODAY
            route = saved.lastRoute?.takeIf { saved.isPlatformEnabled(it) }
            loadBoard()
        }
    }

    fun selectPeriod(p: Period) {
        if (p == period) return
        period = p
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
        val rows: List<RowUi> = try {
            applyQuery(
                when (period) {
                    Period.TODAY -> buildTodayRows(platforms)
                    Period.WEEK -> buildAggregatedRows(platforms, HotRepository.weekStart())
                    Period.MONTH -> buildAggregatedRows(platforms, HotRepository.monthStart())
                }
            )
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
        if (period == Period.TODAY && rows.isEmpty() && platforms.isNotEmpty() &&
            query.isBlank() && !_board.value.refreshing
        ) {
            autoRefreshIfEmpty(platforms)
        }
    }

    /** 关键词过滤（标题包含即命中，忽略大小写） */
    private fun applyQuery(rows: List<RowUi>): List<RowUi> =
        if (query.isBlank()) rows
        else rows.filter { it.title.contains(query, ignoreCase = true) }

    private suspend fun buildTodayRows(platforms: List<PlatformDef>): List<RowUi> {
        val target = route
        if (target != null) {
            if (platforms.none { it.route == target }) return emptyList()
            return repo.today(target).map { it.toRowUi(it.rank, null) }
        }
        // 全部：跨平台同题合并（话题被多个平台同时收录则算一条），按热度排序取前 20
        return platforms.flatMap { p -> repo.today(p.route) }
            .groupBy { it.title }
            .map { (_, list) -> mergeToday(list) }
            .sortedByDescending { it.hot ?: 0.0 }
            .take(HotRepository.TOP_N)
            .mapIndexed { i, e -> e.copy(rank = i + 1) }
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
            val rows = when (period) {
                Period.TODAY -> buildTodayRows(platforms)
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

    fun clearLocalData() {
        viewModelScope.launch {
            repo.clearAll()
            loadBoard()
        }
    }

    fun allPlatforms(): List<PlatformDef> = PLATFORMS

    companion object {
        const val NO_PLATFORM_HINT = "还没有订阅任何平台，去「设置 → 平台订阅」开启几个吧"
    }
}
