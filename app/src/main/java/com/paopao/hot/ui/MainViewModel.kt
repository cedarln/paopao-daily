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
    /** 「全部」视图里同一话题被几个平台收录，1 表示仅单平台 */
    val platformCount: Int = 1
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

    /** 当前应用内详情页正在查看的条目，null 表示关闭 */
    var detail by mutableStateOf<RowUi?>(null)
        private set

    /** 列表关键词过滤（空串 = 不过滤） */
    var query by mutableStateOf("")
        private set

    fun setQuery(q: String) {
        query = q
        viewModelScope.launch { loadBoard() }
    }

    fun openDetail(row: RowUi) { detail = row }
    fun closeDetail() { detail = null }

    private val _board = MutableStateFlow(BoardUiState())
    val board: StateFlow<BoardUiState> = _board.asStateFlow()

    val settings: StateFlow<Settings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings(SettingsDefaults.BASE_URL, true))

    init {
        viewModelScope.launch {
            // 恢复上次浏览的周期与平台，再加载榜单
            val saved = container.settingsStore.settings.first()
            period = Period.entries.firstOrNull { it.name == saved.lastPeriod } ?: Period.TODAY
            route = saved.lastRoute
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

    /** 手动刷新：全平台拉取 + 重新加载 */
    fun refresh() {
        viewModelScope.launch {
            _board.value = _board.value.copy(refreshing = true, error = null)
            val results = repo.refreshAll()
            repo.trim()
            val failed = PLATFORMS.filter { results[it.route] != true }.map { it.name }
            _board.value = _board.value.copy(
                refreshing = false,
                error = if (failed.isNotEmpty()) "以下平台刷新失败：${failed.joinToString("、")}" else null
            )
            loadBoard()
        }
    }

    private suspend fun loadBoard() {
        // 列表已有内容时只静默更新，避免切换周期/平台时整页闪 loading
        _board.value = _board.value.copy(loading = _board.value.items.isEmpty(), error = null)
        val dataStart = repo.earliestDay()
        val rows: List<RowUi> = try {
            when (period) {
                Period.TODAY -> buildTodayRows()
                Period.WEEK -> buildAggregatedRows(HotRepository.weekStart())
                Period.MONTH -> buildAggregatedRows(HotRepository.monthStart())
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
            dataStartDay = dataStart,
            lastSyncAt = repo.lastSyncAt()
        )
        // 今日榜为空且不是用户主动筛选的问题 → 自动尝试联网刷新一次
        if (period == Period.TODAY && rows.isEmpty() && !_board.value.refreshing) {
            autoRefreshIfEmpty()
        }
    }

    private suspend fun buildTodayRows(): List<RowUi> {
        val target = route
        return if (target == null) {
            // 全部：跨平台合并，按热度排序取前 20
            PLATFORMS.flatMap { p -> repo.today(p.route) }
                .sortedByDescending { it.hot ?: 0.0 }
                .take(HotRepository.TOP_N)
                .mapIndexed { i, e -> e.toRowUi(i + 1, null) }
        } else {
            repo.today(target).map { it.toRowUi(it.rank, null) }
        }
    }

    private suspend fun buildAggregatedRows(fromDay: Long): List<RowUi> {
        val target = route
        return if (target == null) {
            PLATFORMS.flatMap { p -> repo.aggregated(p.route, fromDay) }
                .sortedWith(
                    compareByDescending<com.paopao.hot.data.repo.AggregatedEntry> { it.daysOnBoard }
                        .thenByDescending { it.latestHot ?: 0.0 }
                )
                .take(HotRepository.TOP_N)
                .mapIndexed { i, e -> e.toRowUi(i + 1) }
        } else {
            repo.aggregated(target, fromDay).map { it.toRowUi(it.bestRank) }
        }
    }

    private fun autoRefreshIfEmpty() {
        viewModelScope.launch {
            _board.value = _board.value.copy(refreshing = true)
            repo.refreshAll()
            repo.trim()
            val rows = when (period) {
                Period.TODAY -> buildTodayRows()
                Period.WEEK -> buildAggregatedRows(HotRepository.weekStart())
                Period.MONTH -> buildAggregatedRows(HotRepository.monthStart())
            }
            _board.value = _board.value.copy(
                refreshing = false,
                items = rows,
                lastSyncAt = repo.lastSyncAt(),
                error = if (rows.isEmpty()) "暂无数据：可能网络不可用或 API 服务异常，下拉可重试" else null
            )
        }
    }

    private fun HotEntry.toRowUi(rank: Int, days: Int?) = RowUi(
        route = route, name = name, rank = rank, title = title,
        hot = hot, daysOnBoard = days, bestRank = null, url = url
    )

    private fun com.paopao.hot.data.repo.AggregatedEntry.toRowUi(rank: Int) = RowUi(
        route = route, name = name, rank = rank, title = title,
        hot = latestHot, daysOnBoard = daysOnBoard, bestRank = bestRank, url = url
    )

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

    fun currentPlatformList(): List<PlatformDef> = PLATFORMS
}
