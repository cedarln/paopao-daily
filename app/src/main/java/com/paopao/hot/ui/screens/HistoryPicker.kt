package com.paopao.hot.ui.screens

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 历史榜单日期选择。数据只保留最近 45 天，超出范围的日期直接禁用，
 * 避免用户选到空榜单后以为功能坏了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPickerDialog(
    initialDay: Long,
    earliestDay: Long,
    latestDay: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val minDay = earliestDay.coerceAtMost(latestDay)
    val selectable = remember(minDay, latestDay) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = utcMillisToEpochDay(utcTimeMillis)
                return day in minDay..latestDay
            }

            override fun isSelectableYear(year: Int): Boolean =
                year in utcDayToLocalDate(minDay).year..utcDayToLocalDate(latestDay).year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay.coerceIn(minDay, latestDay))
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        yearRange = utcDayToLocalDate(minDay).year..utcDayToLocalDate(latestDay).year,
        selectableDates = selectable
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(utcMillisToEpochDay(it)) }
                    onDismiss()
                }
            ) { Text("查看这一天") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun utcMillisToEpochDay(utcTimeMillis: Long): Long =
    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

private fun utcDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
