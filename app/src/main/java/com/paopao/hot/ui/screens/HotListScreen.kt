package com.paopao.hot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.hot.data.model.PlatformDef
import com.paopao.hot.data.repo.HotRepository
import com.paopao.hot.ui.BoardUiState
import com.paopao.hot.ui.Period
import com.paopao.hot.ui.RowUi
import com.paopao.hot.util.EntryKeys
import com.paopao.hot.util.Format
import com.paopao.hot.util.HotUrls
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val RankColors = listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListScreen(
    period: Period,
    state: BoardUiState,
    platforms: List<PlatformDef>,
    selectedRoute: String?,
    favoriteKeys: Set<String>,
    readKeys: Set<String>,
    onlyUnread: Boolean,
    onOnlyUnreadChange: (Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
    onRouteSelect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (RowUi) -> Unit,
    onToggleFavorite: (RowUi) -> Unit,
    query: String = "",
    archiveDay: Long? = null,
    onExitArchive: () -> Unit = {},
    showCovers: Boolean = true,
    compactRows: Boolean = false
) {
    val today = EntryKeys.todayEpochDay()
    val searching = query.isNotBlank()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 5 } }
    // 切换周期/平台/回到今天后回到顶部，否则会停在上一个榜单的中段
    LaunchedEffect(period, selectedRoute, archiveDay, searching) {
        listState.scrollToItem(0)
    }
    val unreadIds = remember(state.items, readKeys, today) {
        state.items.filterNot { EntryKeys.isRead(readKeys, it.route, it.title, today) }
            .mapTo(mutableSetOf()) { EntryKeys.entry(it.route, it.title) }
    }
    // 只有今日榜有「已读」概念，周/月是聚合视图；搜索/历史回看跨天也不需要已读态
    val trackRead = period == Period.TODAY && !searching && archiveDay == null
    val visible = if (trackRead && onlyUnread) {
        state.items.filter { EntryKeys.entry(it.route, it.title) in unreadIds }
    } else state.items

    Column(modifier = Modifier.fillMaxSize()) {
        PlatformFilterRow(
            platforms = platforms,
            selectedRoute = selectedRoute,
            unreadCount = if (trackRead) unreadIds.size else null,
            onlyUnread = onlyUnread,
            onOnlyUnreadChange = onOnlyUnreadChange,
            onSelect = onRouteSelect
        )
        if (archiveDay != null && !searching) {
            ArchiveBanner(day = archiveDay, onExitArchive = onExitArchive)
        }
        Box(Modifier.weight(1f)) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.loading && state.items.isEmpty() -> BoardSkeleton()
                visible.isEmpty() && onlyUnread && trackRead -> AllReadState(
                    onShowAll = { onOnlyUnreadChange(false) }
                )
                state.items.isEmpty() -> EmptyBoard(
                    error = state.error,
                    query = query,
                    onRetry = onRefresh
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    item {
                        BoardSummary(
                            state = state,
                            period = period,
                            platformCount = platforms.size,
                            unreadCount = if (trackRead && !onlyUnread) unreadIds.size else null,
                            onMarkAllRead = onMarkAllRead,
                            query = query,
                            archiveDay = archiveDay.takeUnless { searching },
                            onRetry = onRefresh
                        )
                    }
                    itemsIndexedWithDivider(visible) { row ->
                        HotItemRow(
                            row = row,
                            period = period,
                            favorited = EntryKeys.favorite(row.route, row.title) in favoriteKeys,
                            unread = if (trackRead) {
                                EntryKeys.entry(row.route, row.title) in unreadIds
                            } else null,
                            query = query,
                            showCover = showCovers && !compactRows,
                            compact = compactRows,
                            onClick = { onOpenDetail(row) },
                            onToggleFavorite = { onToggleFavorite(row) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
            androidx.compose.animation.AnimatedVisibility(
                visible = showToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "回到顶部")
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
private fun BoardSummary(
    state: BoardUiState,
    period: Period,
    platformCount: Int,
    unreadCount: Int? = null,
    onMarkAllRead: () -> Unit = {},
    query: String = "",
    archiveDay: Long? = null,
    onRetry: () -> Unit = {}
) {
    val start = state.dataStartDay
    val note = when {
        archiveDay != null ->
            "历史快照 · ${Format.date(archiveDay)} · 共 ${state.items.size} 条"
        query.isNotBlank() ->
            "在最近 ${HotRepository.RETENTION_DAYS} 天快照里找到 ${state.items.size} 条相关话题"
        period == Period.TODAY ->
            "实时快照 · $platformCount 个平台 · 最近同步：${Format.time(state.lastSyncAt)}"
        start != null ->
            "数据自 ${Format.date(start)} 起由后台积累，打开 App 越多榜单越完整"
        else -> "暂无历史数据，后台同步积累一天后这里会开始有内容"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (unreadCount != null && unreadCount > 0) {
            Text(
                text = "全部已读",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onMarkAllRead)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    if (state.error != null && state.items.isNotEmpty()) {
        OfflineBar(message = state.error, onRetry = onRetry)
    }
}

/** 刷新失败但还有本地缓存：明确告知在看缓存，并给一个重试入口 */
@Composable
private fun OfflineBar(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PlatformFilterRow(
    platforms: List<PlatformDef>,
    selectedRoute: String?,
    unreadCount: Int? = null,
    onlyUnread: Boolean = false,
    onOnlyUnreadChange: (Boolean) -> Unit = {},
    onSelect: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChipUi(label = "全部", selected = selectedRoute == null) { onSelect(null) }
        }
        if (unreadCount != null && unreadCount > 0) {
            item {
                FilterChipUi(
                    label = if (onlyUnread) "未读 $unreadCount" else "只看未读",
                    selected = onlyUnread,
                    onClick = { onOnlyUnreadChange(!onlyUnread) }
                )
            }
        }
        items(platforms) { p ->
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
 * 点击进入原生话题详情，长按弹出快捷操作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HotItemRow(
    row: RowUi,
    period: Period,
    favorited: Boolean,
    unread: Boolean?,
    query: String,
    showCover: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val canonical = HotUrls.canonical(row.url, row.mobileUrl)
    val brand = platformColor(row.route)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
                .padding(horizontal = 4.dp, vertical = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankText(row.rank, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unread == true) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = highlight(row.title, query),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread == false) FontWeight.Normal else FontWeight.Medium,
                        color = if (unread == false) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        } else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(brand)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (row.platformCount > 1) "${row.name} 等${row.platformCount}个平台" else row.name,
                        fontSize = 11.sp,
                        color = if (row.platformCount > 1) MaterialTheme.colorScheme.primary else brand
                    )
                    TrendBadge(row, Modifier.padding(start = 6.dp))
                    if (row.seenDay != null && query.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Format.date(row.seenDay),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
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
            if (showCover && !row.cover.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = row.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            if (favorited) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("话题详情") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                onClick = { menuOpen = false; onClick() }
            )
            DropdownMenuItem(
                text = { Text(if (favorited) "取消收藏" else "收藏") },
                leadingIcon = {
                    Icon(
                        if (favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        null
                    )
                },
                onClick = { menuOpen = false; onToggleFavorite() }
            )
            DropdownMenuItem(
                text = { Text("复制链接") },
                enabled = canonical != null,
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menuOpen = false; copyText(context, "paopao", canonical) }
            )
            DropdownMenuItem(
                text = { Text("分享") },
                enabled = canonical != null,
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { menuOpen = false; shareText(context, row.title, canonical) }
            )
            DropdownMenuItem(
                text = { Text("浏览器打开") },
                enabled = canonical != null,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                onClick = { menuOpen = false; openInBrowser(context, canonical) }
            )
        }
    }
}

/** 历史回看状态条：明确当前看到的不是今天，并给一键回到今天的出口 */
@Composable
private fun ArchiveBanner(day: Long, onExitArchive: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "正在回看 ${Format.date(day)} 的榜单",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onExitArchive,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) { Text("回到今天", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** 搜索结果里把命中的关键词标成主题色 */
private fun highlight(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val start = text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = HighlightColor, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(start, start + query.length))
        }
        append(text.substring(start + query.length))
    }
}

private val HighlightColor = Color(0xFF2563EB)

/** 较昨日趋势：↑上升 ↓下降「新」为昨日榜上没有的话题；持平不显示 */
@Composable
internal fun TrendBadge(row: RowUi, modifier: Modifier = Modifier) {
    val trend = row.trend
    val text: String
    val color: Color
    when {
        row.isNew -> {
            text = "新"
            color = MaterialTheme.colorScheme.primary
        }
        trend == null || trend == 0 -> return
        trend > 0 -> {
            text = "↑$trend"
            color = MaterialTheme.colorScheme.primary
        }
        else -> {
            text = "↓${-trend}"
            color = MaterialTheme.colorScheme.error
        }
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}

/** 排名文字：前三名彩色加粗，其余弱化显示 */
@Composable
private fun RankText(rank: Int, modifier: Modifier = Modifier) {    val color = RankColors.getOrNull(rank - 1)
    Text(
        text = rank.toString(),
        fontSize = 15.sp,
        fontWeight = if (color != null) FontWeight.Bold else FontWeight.Medium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    )
}

/** 首屏骨架屏：比转圈更能表达「马上会出来一列内容」 */
@Composable
private fun BoardSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(9) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.25f))
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (index % 3 == 0) 0.72f else 0.88f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.22f))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(9.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.14f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AllReadState(onShowAll: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "🎉", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "今日内容都读完啦",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onShowAll) {
                Text("看全部")
            }
        }
    }
}

@Composable
private fun EmptyBoard(error: String?, query: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "🫧", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = error ?: if (query.isNotBlank()) "没有找到「$query」相关话题" else "这里还没有泡泡",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) {
                Text("刷新试试")
            }
        }
    }
}
