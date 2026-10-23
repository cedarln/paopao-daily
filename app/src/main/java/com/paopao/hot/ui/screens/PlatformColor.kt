package com.paopao.hot.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.paopao.hot.data.model.platformByRoute

/** 平台品牌色；深色模式下向白色提亮，避免深蓝/深红在暗底上看不清 */
@Composable
internal fun platformColor(route: String): Color {
    val base = platformByRoute(route)?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    return if (isSystemInDarkTheme()) lerp(base, Color.White, 0.35f) else base
}
