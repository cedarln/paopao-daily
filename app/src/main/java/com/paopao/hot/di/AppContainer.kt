package com.paopao.hot.di

import android.content.Context
import com.paopao.hot.data.SettingsStore
import com.paopao.hot.data.SettingsDefaults
import com.paopao.hot.data.api.HotApi
import com.paopao.hot.data.db.AppDatabase
import com.paopao.hot.data.repo.HotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** 轻量手写依赖容器，v1 不引入 Hilt */
class AppContainer(context: Context) {

    val settingsStore: SettingsStore = SettingsStore(context)
    val db: AppDatabase = AppDatabase.build(context)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentBaseUrl: String = SettingsDefaults.BASE_URL

    /** 主实例 + 备用实例，按顺序做故障转移 */
    @Volatile
    private var apiList: List<HotApi> = buildApiList(currentBaseUrl)

    val repository: HotRepository = HotRepository({ apiList }, db.snapshotDao())

    init {
        // 启动时读取用户自定义 API 地址（DataStore 首读很快，阻塞可接受）
        runBlocking {
            try {
                val url = settingsStore.settings.first().baseUrl
                if (url.isNotBlank()) {
                    currentBaseUrl = normalize(url)
                    apiList = buildApiList(currentBaseUrl)
                }
            } catch (_: Exception) {
                // 保持默认
            }
        }
    }

    fun baseUrl(): String = currentBaseUrl

    fun updateBaseUrl(raw: String) {
        val normalized = normalize(raw)
        currentBaseUrl = normalized
        apiList = buildApiList(normalized)
    }

    /** 主实例排最前，备用实例去重后跟在后面 */
    private fun buildApiList(primary: String): List<HotApi> =
        (listOf(primary) + SettingsDefaults.FALLBACK_BASE_URLS)
            .distinct()
            .map { buildApi(it) }

    private fun buildApi(baseUrl: String): HotApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HotApi::class.java)

    companion object {
        fun normalize(raw: String): String {
            var url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (!url.endsWith("/")) url = "$url/"
            return url
        }
    }
}
