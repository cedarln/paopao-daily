package com.paopao.hot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.hot.data.Settings
import com.paopao.hot.data.SettingsDefaults
import com.paopao.hot.ui.BoardUiState
import com.paopao.hot.ui.MainViewModel
import com.paopao.hot.util.Format

@Composable
fun SettingsScreen(
    settings: Settings,
    board: BoardUiState,
    viewModel: MainViewModel
) {
    var urlInput by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 数据源
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("数据源", fontWeight = FontWeight.SemiBold)
                Text(
                    "热榜数据来自 DailyHotApi 兼容服务，默认使用社区公共实例。你可以替换为自己部署的实例地址（例如用 Vercel / Docker 自建）。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("API 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveBaseUrl(urlInput) }) {
                        Text("保存")
                    }
                    OutlinedButton(onClick = { urlInput = SettingsDefaults.BASE_URL }) {
                        Text("恢复默认")
                    }
                }
            }
        }

        // 同步
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("同步", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每日后台同步", fontSize = 15.sp)
                        Text(
                            "每天自动抓取一次各平台热榜快照，周/月榜依赖这些历史数据",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.autoSync, onCheckedChange = { viewModel.setAutoSync(it) })
                }
                HorizontalDivider()
                Text(
                    "最近同步：${Format.time(board.lastSyncAt)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showClearDialog = true }) {
                    Text("清除本地历史数据", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 关于
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于", fontWeight = FontWeight.SemiBold)
                Text(
                    "泡泡热榜 v1.0 · 开源项目\n\n" +
                        "· 数据聚合基于开源项目 DailyHotApi（GPL-3.0），感谢原作者 imsyy 与社区维护者\n" +
                        "· 小红书暂无公开热榜接口，后续版本视可行方案支持\n" +
                        "· 榜单内容版权归各平台所有，仅供个人阅读参考\n" +
                        "· 全部数据仅保存在本机，App 不上传任何信息",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除本地历史数据？") },
            text = { Text("将删除所有已积累的热榜快照，周/月榜会从零开始重新积累。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLocalData()
                    showClearDialog = false
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}
