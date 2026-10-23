package com.paopao.hot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.ui.screens.HotDetailDialog
import com.paopao.hot.ui.screens.HotListScreen
import com.paopao.hot.ui.screens.SettingsScreen
import com.paopao.hot.ui.screens.WebReaderDialog
import com.paopao.hot.ui.theme.PaopaoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaopaoTheme {
                PaopaoAppUi()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaopaoAppUi(viewModel: MainViewModel = viewModel()) {
    val board by viewModel.board.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var settingsTab by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val enabledPlatforms = PLATFORMS.filter { settings.isPlatformEnabled(it.route) }
    // 以 ViewModel 恢复的周期为准，避免「tab 显示今日、列表却是本周」的不一致
    val currentTab = if (settingsTab) {
        Tab.SETTINGS
    } else {
        when (viewModel.period) {
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
                            text = if (currentTab == Tab.SETTINGS) "泡泡热榜 · 设置" else "泡泡热榜",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (currentTab != Tab.SETTINGS) {
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
                            if (tab == Tab.SETTINGS) {
                                settingsTab = true
                            } else {
                                settingsTab = false
                                viewModel.selectPeriod(tab.toPeriod())
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
            Tab.SETTINGS -> SettingsScreen(
                settings = settings,
                board = board,
                viewModel = viewModel
            )
            else -> Box(contentModifier) {
                HotListScreen(
                    period = currentTab.toPeriod(),
                    state = board,
                    platforms = enabledPlatforms,
                    selectedRoute = viewModel.route,
                    onRouteSelect = { viewModel.selectRoute(it) },
                    onRefresh = { viewModel.refresh() },
                    onOpenDetail = { viewModel.openDetail(it) }
                )
            }
        }
        // 一级：原生话题详情（不联网、不跳转）
        viewModel.detail?.let { row ->
            HotDetailDialog(
                row = row,
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
                onClose = { viewModel.closeReader() }
            )
        }
    }
}

private enum class Tab(val label: String) {
    TODAY("今日"), WEEK("本周"), MONTH("本月"), SETTINGS("设置");

    fun toPeriod(): Period = when (this) {
        TODAY -> Period.TODAY
        WEEK -> Period.WEEK
        else -> Period.MONTH
    }

    fun icon() = when (this) {
        TODAY -> Icons.Filled.Today
        WEEK -> Icons.Filled.ViewWeek
        MONTH -> Icons.Filled.CalendarMonth
        SETTINGS -> Icons.Filled.Settings
    }
}
