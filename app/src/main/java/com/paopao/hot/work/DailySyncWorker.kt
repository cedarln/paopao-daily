package com.paopao.hot.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paopao.hot.data.model.PLATFORMS
import com.paopao.hot.di.AppContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** 每日后台同步：为周/月聚合积累快照数据（只同步已订阅的平台） */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        val enabled = container.settingsStore.settings.first().effectiveEnabledRoutes
        val platforms = PLATFORMS.filter { it.route in enabled }
        if (platforms.isNotEmpty()) {
            container.repository.refreshAll(platforms)
            container.repository.trim()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_hot_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
