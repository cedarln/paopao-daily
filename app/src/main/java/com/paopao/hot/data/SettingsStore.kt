package com.paopao.hot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paopao.hot.data.model.PLATFORMS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val baseUrl: String,
    val autoSync: Boolean,
    val lastPeriod: String? = null,
    val lastRoute: String? = null,
    /** null 表示从未调整过，默认全部订阅 */
    val enabledRoutes: Set<String>? = null,
    /** 本地快照的数据版本，低于当前版本说明是旧版写入的、缺少摘要/封面 */
    val contentVersion: Int = 0
) {
    val effectiveEnabledRoutes: Set<String>
        get() = enabledRoutes ?: PLATFORMS.map { it.route }.toSet()

    fun isPlatformEnabled(route: String): Boolean = route in effectiveEnabledRoutes
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val LAST_PERIOD = stringPreferencesKey("last_period")
        val LAST_ROUTE = stringPreferencesKey("last_route")
        val ENABLED_ROUTES = stringSetPreferencesKey("enabled_routes")
        val CONTENT_VERSION = intPreferencesKey("content_version")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[Keys.BASE_URL] ?: SettingsDefaults.BASE_URL,
            autoSync = prefs[Keys.AUTO_SYNC] ?: true,
            lastPeriod = prefs[Keys.LAST_PERIOD],
            lastRoute = prefs[Keys.LAST_ROUTE],
            enabledRoutes = prefs[Keys.ENABLED_ROUTES],
            contentVersion = prefs[Keys.CONTENT_VERSION] ?: 0
        )
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }

    /** 记住用户上次浏览的周期与平台，下次打开原样恢复 */
    suspend fun setUiState(period: String, route: String?) {
        context.dataStore.edit {
            it[Keys.LAST_PERIOD] = period
            if (route != null) it[Keys.LAST_ROUTE] = route else it.remove(Keys.LAST_ROUTE)
        }
    }

    /** 替换订阅集合；空集合表示一个都不订阅 */
    suspend fun setEnabledRoutes(routes: Set<String>) {
        context.dataStore.edit { it[Keys.ENABLED_ROUTES] = routes }
    }

    /** 恢复默认：全部订阅（删除偏好，而不是写死当前平台列表） */
    suspend fun resetPlatformSubscriptions() {
        context.dataStore.edit { it.remove(Keys.ENABLED_ROUTES) }
    }

    suspend fun setContentVersion(version: Int) {
        context.dataStore.edit { it[Keys.CONTENT_VERSION] = version }
    }
}

object SettingsDefaults {
    const val BASE_URL = "https://hot-api.liiiu.cn/"

    /** 2 = 快照开始带摘要/封面/移动链接；旧版本升级后需要补一次快照 */
    const val CONTENT_VERSION = 2

    /** 备用公共实例：主实例失败时按顺序故障转移 */
    val FALLBACK_BASE_URLS = listOf("https://api-hot.imsyy.top/")
}
