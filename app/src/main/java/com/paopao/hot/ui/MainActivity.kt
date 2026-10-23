package com.paopao.hot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.hot.data.Settings
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.ui.screens.shareRaw
import com.paopao.hot.ui.screens.FavoritesScreen
import com.paopao.hot.ui.screens.HotDetailDialog
import com.paopao.hot.ui.screens.HotListScreen
import com.paopao.hot.ui.screens.HistoryPickerDialog
import com.paopao.hot.ui.screens.SettingsScreen
import com.paopao.hot.ui.screens.WebReaderDialog
import com.paopao.hot.ui.theme.PaopaoTheme
import com.paopao.hot.util.EntryKeys

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                Settings.THEME_LIGHT -> false
                Settings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            PaopaoTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor
            ) {
                PaopaoAppUi(viewModel = viewModel, settings = settings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaopaoAppUi(
    viewModel: MainViewModel,
    settings: Settings
) {
    val board by viewModel.board.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteKeys by viewModel.favoriteKeys.collectAsState()
    val context = LocalContext.current

    var settingsTab by remember { mutableStateOf(false) }
    var favoritesTab by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val todayEpochDay = EntryKeys.todayEpochDay()
    val historyStart = board.dataStartDay
    val hasHistory = historyStart != null && historyStart < todayEpochDay
    val enabledPlatforms = PLATFORMS.filter { settings.isPlatformEnabled(it.route) }
    // 以 ViewModel 恢复的周期为准，避免「tab 显示今日、列表却是本周」的不一致
    val currentTab = when {
        settingsTab -> Tab.SETTINGS
        favoritesTab -> Tab.FAVORITES
        else -> when (viewModel.period) {
            Period.TODAY -> Tab.TODAY
            Period.WEEK -> Tab.WEEK
            Period.MONTH -> Tab.MONTH
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen && currentTab != Tab.SETTINGS) {
                        OutlinedTextField(
                            value = viewModel.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            placeholder = { Text("搜索热榜关键词", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = when (currentTab) {
                                Tab.SETTINGS -> "泡泡热榜 · 设置"
                                Tab.FAVORITES -> "我的收藏"
                                else -> "泡泡热榜"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (currentTab != Tab.SETTINGS && currentTab != Tab.FAVORITES) {
                        if (hasHistory) {
                            IconButton(onClick = { historyOpen = true }) {
                                Icon(Icons.Filled.History, contentDescription = "历史榜单")
                            }
                        }
                        if (currentTab == Tab.TODAY && viewModel.archiveDay == null &&
                            viewModel.query.isBlank() && board.items.isNotEmpty()
                        ) {
                            IconButton(
                                onClick = { shareRaw(context, Digest.today(board.items)) }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "分享今日速览")
                            }
                        }
                        IconButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen && viewModel.query.isNotEmpty()) {
                                    viewModel.updateQuery("")
                                }
                            }
                        ) {
                            Icon(
                                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchOpen) "关闭搜索" else "搜索"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {
                            when (tab) {
                                Tab.SETTINGS -> {
                                    settingsTab = true
                                    favoritesTab = false
                                }
                                Tab.FAVORITES -> {
                                    favoritesTab = true
                                    settingsTab = false
                                }
                                else -> {
                                    settingsTab = false
                                    favoritesTab = false
                                    viewModel.selectPeriod(tab.toPeriod())
                                }
                            }
                        },
                        icon = { Icon(tab.icon(), contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (currentTab) {
            Tab.SETTINGS -> Box(contentModifier) {
                SettingsScreen(
                    settings = settings,
                    board = board,
                    viewModel = viewModel
                )
            }
            Tab.FAVORITES -> Box(contentModifier) {
                FavoritesScreen(
                    favorites = favorites,
                    onOpenDetail = { viewModel.openDetail(it) },
                    onRemove = { viewModel.toggleFavorite(it) },
                    onBrowse = { favoritesTab = false }
                )
            }
            else -> Box(contentModifier) {
                HotListScreen(
                    period = currentTab.toPeriod(),
                    state = board,
                    platforms = enabledPlatforms,
                    selectedRoute = viewModel.route,
                    favoriteKeys = favoriteKeys,
                    readKeys = settings.readKeys,
                    onlyUnread = viewModel.onlyUnread,
                    onOnlyUnreadChange = { viewModel.updateOnlyUnread(it) },
                    onMarkAllRead = { viewModel.markAllTodayRead() },
                    archiveDay = viewModel.archiveDay,
                    onExitArchive = { viewModel.exitArchive() },
                    onRouteSelect = { viewModel.selectRoute(it) },
                    onRefresh = { viewModel.refresh() },
                    onOpenDetail = { viewModel.openDetail(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    query = viewModel.query,
                    showCovers = settings.showCovers,
                    compactRows = settings.compactRows
                )
            }
        }
        // 一级：原生话题详情（不联网、不跳转）
        viewModel.detail?.let { row ->
            HotDetailDialog(
                row = row,
                isFavorite = EntryKeys.favorite(row.route, row.title) in favoriteKeys,
                onToggleFavorite = { viewModel.toggleFavorite(row) },
                onClose = { viewModel.closeDetail() },
                onReadOriginal = { title, mobileUrl, url ->
                    viewModel.openReader(title, mobileUrl, url)
                }
            )
        }

        // 二级：用户主动进入的网页阅读器
        viewModel.reader?.let { request ->
            WebReaderDialog(
                request = request,
                readerFontPercent = settings.readerFontPercent,
                readerDark = settings.readerDark,
                readerNoImages = settings.readerNoImages,
                onFontPercentChange = { viewModel.setReaderFontPercent(it) },
                onReaderDarkChange = { viewModel.setReaderDark(it) },
                onReaderNoImagesChange = { viewModel.setReaderNoImages(it) },
                onClose = { viewModel.closeReader() }
            )
        }

        if (historyOpen) {
            HistoryPickerDialog(
                initialDay = viewModel.archiveDay ?: todayEpochDay,
                earliestDay = historyStart ?: todayEpochDay,
                latestDay = todayEpochDay,
                onPick = { viewModel.openArchive(it) },
                onDismiss = { historyOpen = false }
            )
        }
    }
}

private enum class Tab(val label: String) {
    TODAY("今日"), WEEK("本周"), MONTH("本月"), FAVORITES("收藏"), SETTINGS("设置");

    fun toPeriod(): Period = when (this) {
        TODAY -> Period.TODAY
        WEEK -> Period.WEEK
        else -> Period.MONTH
    }

    fun icon() = when (this) {
        TODAY -> Icons.Filled.Today
        WEEK -> Icons.Filled.ViewWeek
        MONTH -> Icons.Filled.CalendarMonth
        FAVORITES -> Icons.Filled.Star
        SETTINGS -> Icons.Filled.Settings
    }
}
