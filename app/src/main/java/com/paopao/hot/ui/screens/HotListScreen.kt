package com.paopao.hot.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.data.model.PlatformDef
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
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> EmptyBoard(state.error)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        BoardSummary(state, period)
                    }
                    items(state.items, key = { "${it.route}-${it.title}" }) { row ->
                        HotItemCard(row, period)
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
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
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChipUi(label = "全部", emoji = "🫧", selected = selectedRoute == null) { onSelect(null) }
        }
        items(PLATFORMS) { p ->
            FilterChipUi(label = p.name, emoji = p.emoji, selected = selectedRoute == p.route) {
                onSelect(p.route)
            }
        }
    }
}

@Composable
private fun FilterChipUi(label: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (selected) "$emoji $label" else label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        ),
        border = null
    )
}

@Composable
private fun HotItemCard(row: RowUi, period: Period) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.url != null) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.url)))
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RankBadge(row.rank)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformTag(row.name, row.route)
                    if (row.hot != null && row.hot > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "🔥 ${Format.hot(row.hot)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (period != Period.TODAY && row.daysOnBoard != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "上榜 ${row.daysOnBoard} 天",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = RankColors.getOrNull(rank - 1)
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = color ?: MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlatformTag(name: String, route: String) {
    val def: PlatformDef? = PLATFORMS.firstOrNull { it.route == route }
    Text(
        text = "${def?.emoji ?: ""} $name",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
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
