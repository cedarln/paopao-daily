package com.paopao.hot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.paopao.hot.ui.RowSource
import com.paopao.hot.ui.RowUi
import com.paopao.hot.util.Format
import com.paopao.hot.util.HotUrls

/**
 * 话题详情：完全原生渲染，点击条目不再立刻加载平台网页。
 * 用户先看到摘要和来源，想深入时再主动进入阅读器或浏览器。
 */
@Composable
fun HotDetailDialog(
    row: RowUi,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onReadOriginal: (title: String, mobileUrl: String?, url: String?) -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    var selected by remember(row.title) {
        mutableStateOf(row.sources.maxByOrNull { it.hot ?: 0.0 })
    }
    var descExpanded by remember(row.title) { mutableStateOf(false) }

    val source: RowSource? = selected
    val readMobile = source?.mobileUrl ?: row.mobileUrl
    val readUrl = source?.url ?: row.url
    val canonical = HotUrls.canonical(readUrl, readMobile)
    val appOnly = HotUrls.isAppOnly(readMobile ?: readUrl)
    val hasLink = canonical != null
    // 没有原文链接时退化为站内搜索，保证详情页永远有可点的操作
    val searchUrl = HotUrls.searchUrl(source?.route ?: row.route, row.title)
    val fallbackUrl = canonical ?: searchUrl

    val brand = platformColor(source?.route ?: row.route)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onClose = onClose,
                    onCopy = { copyText(context, "paopao", fallbackUrl) },
                    onShare = { shareText(context, row.title, fallbackUrl) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 34.sp
                        )
                    }

                    item { MetaFlow(row = row, source = source, brand = brand) }

                    if (!row.cover.isNullOrBlank()) {
                        item {
                            AsyncImage(
                                model = row.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }

                    item {
                        DescriptionBlock(
                            description = row.description,
                            hasLink = hasLink,
                            expanded = descExpanded,
                            onToggle = { descExpanded = !descExpanded }
                        )
                    }

                    // 摘要下方紧跟一个主操作：提示里说「点下方」，按钮就真的在下方
                    item {
                        FilledTonalButton(
                            onClick = {
                                if (hasLink) onReadOriginal(row.title, readMobile, readUrl)
                                else openInBrowser(context, fallbackUrl)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = if (hasLink) Icons.AutoMirrored.Filled.MenuBook
                                else Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasLink) "阅读原文" else "在浏览器搜索这个话题")
                        }
                    }

                    if (appOnly) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "该平台网页版通常要求登录，应用内阅读可能显示空白或只剩导航栏，建议直接用下方的「浏览器打开」。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (row.sources.size > 1) {
                        item {
                            SourcePicker(
                                sources = row.sources,
                                selectedRoute = source?.route,
                                onSelect = { selected = it }
                            )
                        }
                    }

                }

                DetailActionBar(
                    hasLink = hasLink,
                    appOnly = appOnly,
                    onRead = { onReadOriginal(row.title, readMobile, readUrl) },
                    onBrowser = { openInBrowser(context, fallbackUrl) },
                    onCopyTitle = { copyText(context, "paopao", row.title) }
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "话题详情",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制链接", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "分享", modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaFlow(row: RowUi, source: RowSource?, brand: Color) {
    val platformName = source?.name ?: row.name
    val rank = source?.rank ?: row.rank
    val hot = source?.hot ?: row.hot

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetaPill(platformName, brand)
        if (row.daysOnBoard != null) {
            MetaPill("上榜 ${row.daysOnBoard} 天", MaterialTheme.colorScheme.primary)
            MetaPill("最高第 ${row.bestRank ?: rank} 名", MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (rank > 0) {
            MetaPill("第 $rank 名", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hot != null && hot > 0) {
            MetaPill("热度 ${Format.hot(hot)}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            row.isNew -> MetaPill("今日新上榜", MaterialTheme.colorScheme.primary)
            row.trend != null && row.trend > 0 ->
                MetaPill("较昨日 ↑${row.trend}", MaterialTheme.colorScheme.primary)
            row.trend != null && row.trend < 0 ->
                MetaPill("较昨日 ↓${-row.trend}", MaterialTheme.colorScheme.error)
        }
        if (row.platformCount > 1) {
            MetaPill("${row.platformCount} 个平台在榜", MaterialTheme.colorScheme.primary)
        }
        if (row.timestamp != null && row.timestamp > 0) {
            MetaPill("发布 ${Format.time(row.timestamp)}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.11f)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DescriptionBlock(
    description: String?,
    hasLink: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val desc = description?.trim().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "摘要",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (desc.isEmpty()) {
            Text(
                text = if (hasLink) {
                    "该平台暂未提供摘要，可以点下方「阅读原文」查看完整内容。"
                } else {
                    "该平台暂未提供摘要，也没有可直接打开的原文链接，可以按标题去平台内搜索。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 27.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 10,
                overflow = TextOverflow.Ellipsis
            )
            if (desc.length > 180) {
                TextButton(
                    onClick = onToggle,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(if (expanded) "收起" else "展开全文")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcePicker(
    sources: List<RowSource>,
    selectedRoute: String?,
    onSelect: (RowSource) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "也在这些平台上榜",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sources.forEach { s ->
                val tint = platformColor(s.route)
                FilterChip(
                    selected = s.route == selectedRoute,
                    onClick = { onSelect(s) },
                    label = { Text("${s.name} #${s.rank}", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tint.copy(alpha = 0.18f),
                        selectedLabelColor = tint
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailActionBar(
    hasLink: Boolean,
    appOnly: Boolean,
    onRead: () -> Unit,
    onBrowser: () -> Unit,
    onCopyTitle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                // 明确只能在 App 内看的平台，把「浏览器打开」设为主操作
                appOnly -> {
                    Button(
                        onClick = onBrowser,
                        enabled = hasLink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("浏览器打开")
                    }
                    OutlinedButton(onClick = onRead, enabled = hasLink) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("应用内试试")
                    }
                }
                hasLink -> {
                    Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("阅读原文")
                    }
                    OutlinedButton(onClick = onBrowser) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("浏览器")
                    }
                }
                // 连原文链接都没有时也要给出可执行操作，而不是灰按钮
                else -> {
                    Button(onClick = onBrowser, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("搜索话题")
                    }
                    OutlinedButton(onClick = onCopyTitle) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制标题")
                    }
                }
            }
        }
    }
}
