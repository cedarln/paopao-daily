package com.paopao.hot

import android.app.Application
import androidx.work.Configuration
import com.paopao.hot.di.AppContainer
import com.paopao.hot.work.DailySyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PaopaoApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 若开启了自动同步，安排每日后台快照（为周/月榜积累数据）
        runBlocking {
            try {
                val autoSync = container.settingsStore.settings.first().autoSync
                if (autoSync) DailySyncWorker.schedule(this@PaopaoApp)
            } catch (_: Exception) {
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
