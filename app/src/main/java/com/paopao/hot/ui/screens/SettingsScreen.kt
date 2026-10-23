package com.paopao.hot.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paopao.hot.data.Settings
import com.paopao.hot.data.SettingsDefaults
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.ui.BoardUiState
import com.paopao.hot.ui.MainViewModel
import com.paopao.hot.util.Format
import com.paopao.hot.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        // 外观
        AppearanceCard(settings = settings, viewModel = viewModel)

        // 列表显示
        ListDisplayCard(settings = settings, viewModel = viewModel)

        // 平台订阅
        PlatformSubscriptionCard(settings = settings, viewModel = viewModel)

        // 数据源
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("数据源", fontWeight = FontWeight.SemiBold)
                Text(
                    "热榜数据来自 DailyHotApi 兼容服务，默认使用社区公共实例（不可用时自动切换备用实例）。你可以替换为自己部署的实例地址（例如用 Vercel / Docker 自建）。",
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

        UpdateCard()

        // 关于
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于", fontWeight = FontWeight.SemiBold)
                Text(
                    "泡泡热榜 · 开源项目（github.com/cedarln/${UpdateChecker.REPO}）\n\n" +
                        "· 数据聚合基于开源项目 DailyHotApi（GPL-3.0），感谢原作者 imsyy 与社区维护者\n" +
                        "· 点击条目先看原生摘要，点「阅读原文」才会加载网页\n" +
                        "· 阅读器会拦截平台 App 深链，并隐藏站点顶栏和下载横幅；字号、深色网页、隐藏图片都可在阅读器菜单里调整\n" +
                        "· 收藏、已读标记和浏览历史只存在本机；搜索覆盖最近 45 天的全部快照\n" +
                        "· 网页体验不佳的平台，可在上方「平台订阅」中关闭\n" +
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

// ---------- 外观 ----------

@Composable
private fun AppearanceCard(settings: Settings, viewModel: MainViewModel) {
    val dynamicSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("外观", fontWeight = FontWeight.SemiBold)
            Text(
                "选择浅色或深色界面；跟随系统时随系统深色模式自动切换",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Settings.THEME_SYSTEM to "跟随系统",
                    Settings.THEME_LIGHT to "浅色",
                    Settings.THEME_DARK to "深色"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动态取色", fontSize = 15.sp)
                    Text(
                        if (dynamicSupported) "Android 12+ 跟随系统壁纸配色"
                        else "当前系统版本不支持，将使用品牌靛蓝配色",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }
        }
    }
}

// ---------- 列表显示 ----------

@Composable
private fun ListDisplayCard(settings: Settings, viewModel: MainViewModel) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("榜单列表", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("显示封面缩略图", fontSize = 15.sp)
                    Text(
                        "摘要自带封面时在标题右侧显示小图，流量紧张可以关掉",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showCovers,
                    onCheckedChange = { viewModel.setShowCovers(it) }
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("紧凑模式", fontSize = 15.sp)
                    Text(
                        "减小行高并隐藏封面，一屏能多看好几条",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.compactRows,
                    onCheckedChange = { viewModel.setCompactRows(it) }
                )
            }
        }
    }
}

// ---------- 平台订阅 ----------

/**
 * 订阅哪些平台就只抓哪些平台：既减少无效请求，
 * 也能让用户直接避开网页版体验差的平台（如必须登录的抖音）。
 */
@Composable
private fun PlatformSubscriptionCard(settings: Settings, viewModel: MainViewModel) {
    val enabledCount = PLATFORMS.count { settings.isPlatformEnabled(it.route) }
    val allEnabled = enabledCount == PLATFORMS.size

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("平台订阅", fontWeight = FontWeight.SemiBold)
                    Text(
                        "已订阅 $enabledCount / ${PLATFORMS.size} 个平台",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { viewModel.setAllPlatformsSubscribed(!allEnabled) }) {
                    Text(if (allEnabled) "全部关闭" else "全部开启")
                }
            }
            Text(
                "只刷新和展示已订阅的平台。「全部」榜、平台筛选和后台同步都会遵循这里的选择；网页版限制多、阅读体验差的平台可以关掉。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            PLATFORMS.forEachIndexed { index, platform ->
                val checked = settings.isPlatformEnabled(platform.route)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = { viewModel.setPlatformSubscribed(platform.route, it) }
                        )
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(platformColor(platform.route))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(platform.name, fontSize = 15.sp)
                        if (platform.route == "douyin") {
                            Text(
                                "网页版通常要求登录，应用内阅读体验一般",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = checked, onCheckedChange = null)
                }
                if (index != PLATFORMS.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// ---------- 应用内更新 ----------

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val info: UpdateChecker.ReleaseInfo, val current: String) : UpdateState
    data class Downloading(val fileName: String, val percent: Int) : UpdateState
    data class Ready(val fileName: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** 设置页里的「版本更新」卡片：检查 -> 下载（进度）-> 拉起系统安装器 */
@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateChecker.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("版本更新", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前版本 v$currentVersion", fontSize = 15.sp)
                    Text(
                        text = when (val s = state) {
                            UpdateState.Idle -> "查询 GitHub 最新 Release"
                            UpdateState.Checking -> "正在检查更新…"
                            is UpdateState.UpToDate -> "已是最新版本"
                            is UpdateState.Available ->
                                "发现新版本 v${s.info.version}（${Format.size(s.info.apkSize)}）"
                            is UpdateState.Downloading -> "下载中 ${s.percent}%"
                            is UpdateState.Ready -> "更新包已就绪，点击「安装」完成更新"
                            is UpdateState.Failed -> "操作失败：${s.message}"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (val s = state) {
                    is UpdateState.Available -> Button(
                        onClick = { startDownload(context, scope, s.info) { state = it } }
                    ) { Text("下载更新") }
                    is UpdateState.Downloading -> CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(2.dp)
                    )
                    is UpdateState.Ready -> Button(
                        onClick = { installApk(context, s.fileName) }
                    ) { Text("安装") }
                    else -> OutlinedButton(
                        onClick = {
                            checkUpdate(context, scope, currentVersion) { state = it }
                        },
                        enabled = state !is UpdateState.Checking
                    ) { Text(if (state is UpdateState.Checking) "检查中…" else "检查更新") }
                }
            }
            val downloading = state as? UpdateState.Downloading
            if (downloading != null) {
                LinearProgressIndicator(
                    progress = { downloading.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val available = state as? UpdateState.Available
    if (available != null) {
        AlertDialog(
            onDismissRequest = { state = UpdateState.Idle },
            title = { Text("发现新版本 v${available.info.version}") },
            text = {
                Column {
                    Text(
                        "当前版本 v${available.current}，更新包 ${Format.size(available.info.apkSize)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (available.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            available.info.notes.take(500),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    state = UpdateState.Idle
                    startDownload(context, scope, available.info) { state = it }
                }) { Text("下载更新") }
            },
            dismissButton = {
                TextButton(onClick = { state = UpdateState.Idle }) { Text("以后再说") }
            }
        )
    }
}

private fun checkUpdate(
    context: android.content.Context,
    scope: CoroutineScope,
    current: String,
    onUpdate: (UpdateState) -> Unit
) {
    onUpdate(UpdateState.Checking)
    scope.launch {
        try {
            val info = UpdateChecker.fetchLatest()
            onUpdate(
                if (UpdateChecker.hasNewer(info.tag, current)) UpdateState.Available(info, current)
                else UpdateState.UpToDate(current)
            )
        } catch (e: Exception) {
            onUpdate(UpdateState.Failed(e.message ?: "网络错误"))
        }
    }
}

private fun startDownload(
    context: android.content.Context,
    scope: CoroutineScope,
    info: UpdateChecker.ReleaseInfo,
    onUpdate: (UpdateState) -> Unit
) {
    val fileName = "paopao-daily-${info.version}.apk"
    val id = UpdateChecker.download(context, info.apkUrl, fileName)
    if (id == -1L) {
        onUpdate(UpdateState.Failed("无法开始下载"))
        return
    }
    onUpdate(UpdateState.Downloading(fileName, 0))
    scope.launch {
        while (true) {
            delay(800)
            val p = UpdateChecker.queryDownload(context, id)
            if (p == null) {
                onUpdate(UpdateState.Failed("下载任务丢失，请重新检查更新"))
                return@launch
            }
            when (p.status) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                    onUpdate(UpdateState.Ready(fileName))
                    return@launch
                }
                android.app.DownloadManager.STATUS_FAILED -> {
                    onUpdate(UpdateState.Failed("下载失败，请重试"))
                    return@launch
                }
                else -> onUpdate(UpdateState.Downloading(fileName, p.percent))
            }
        }
    }
}

private fun installApk(context: android.content.Context, fileName: String) {
    val activity = context as? Activity ?: return
    val apk = UpdateChecker.downloadedFile(context, fileName)
    if (!apk.exists() || apk.length() == 0L) return
    // 权限未开时 promptInstall 会跳转「安装未知应用」授权页，用户回来后再点一次即可
    UpdateChecker.promptInstall(activity, apk)
}
