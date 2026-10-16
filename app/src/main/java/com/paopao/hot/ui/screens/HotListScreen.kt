package com.paopao.hot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.ui.BoardUiState
import com.paopao.hot.ui.Period
import com.paopao.hot.ui.RowUi
import com.paopao.hot.util.Format

private val RankColors = listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListScreen(
    period: Period,
    state: BoardUiState,
    selectedRoute: String?,
    onRouteSelect: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PlatformFilterRow(
            selectedRoute = selectedRoute,
            onSelect = onRouteSelect
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
            when {
                state.loading && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.items.isEmpty() -> EmptyBoard(state.error)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    item { BoardSummary(state, period) }
                    itemsIndexedWithDivider(state.items) { row ->
                        HotItemRow(row = row, period = period, onClick = { }, onLongClick = { })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/** 紧凑列表：行与行之间用细分隔线，信息密度高 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedWithDivider(
    items: List<RowUi>,
    itemContent: @Composable (RowUi) -> Unit
) {
    items(items.size) { index ->
        val row = items[index]
        Box(Modifier.animateItem()) {
            itemContent(row)
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun BoardSummary(state: BoardUiState, period: Period) {
    val start = state.dataStartDay
    val note = when (period) {
        Period.TODAY -> "实时快照 · 最近同步：${Format.time(state.lastSyncAt)}"
        else -> if (start != null) {
            "数据自 ${Format.date(start)} 起由后台积累，打开 App 越多榜单越完整"
        } else "暂无历史数据，后台同步积累一天后这里会开始有内容"
    }
    Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
    if (state.error != null && state.items.isNotEmpty()) {
        Text(
            text = state.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PlatformFilterRow(selectedRoute: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChipUi(label = "全部", selected = selectedRoute == null) { onSelect(null) }
        }
        items(PLATFORMS) { p ->
            FilterChipUi(label = p.name, selected = selectedRoute == p.route) {
                onSelect(p.route)
            }
        }
    }
}

@Composable
private fun FilterChipUi(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        ),
        border = null
    )
}

/**
 * 紧凑榜单行：排名 + 单行标题 + 热度值右对齐。
 * 参考主流热榜应用（NewsNow / 今日热榜）的行式布局，替代松散卡片。
 */
@Composable
private fun HotItemRow(
    row: RowUi,
    period: Period,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = true, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankText(row.rank, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.hot != null && row.hot > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Format.hot(row.hot),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                if (period != Period.TODAY && row.daysOnBoard != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "上榜 ${row.daysOnBoard} 天",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 排名文字：前三名彩色加粗，其余弱化显示 */
@Composable
private fun RankText(rank: Int, modifier: Modifier = Modifier) {
    val color = RankColors.getOrNull(rank - 1)
    Text(
        text = rank.toString(),
        fontSize = 15.sp,
        fontWeight = if (color != null) FontWeight.Bold else FontWeight.Medium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    )
}

@Composable
private fun EmptyBoard(error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🫧", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = error ?: "这里还没有泡泡",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
