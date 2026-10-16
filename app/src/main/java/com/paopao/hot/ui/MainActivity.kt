package com.paopao.hot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.paopao.hot.ui.screens.DetailDialog
import com.paopao.hot.ui.screens.HotListScreen
import com.paopao.hot.ui.screens.SettingsScreen
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

    var currentTab by remember { mutableStateOf(Tab.TODAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentTab == Tab.SETTINGS) "泡泡热榜 · 设置" else "泡泡热榜",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {
                            currentTab = tab
                            if (tab != Tab.SETTINGS) viewModel.selectPeriod(tab.toPeriod())
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
                    selectedRoute = viewModel.route,
                    onRouteSelect = { viewModel.selectRoute(it) },
                    onRefresh = { viewModel.refresh() },
                    onOpenDetail = { viewModel.openDetail(it) }
                )
            }
        }

        // 应用内详情页（WebView）
        viewModel.detail?.let { row ->
            DetailDialog(row = row, onClose = { viewModel.closeDetail() })
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
