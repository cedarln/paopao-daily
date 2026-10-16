package com.paopao.hot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val baseUrl: String,
    val autoSync: Boolean,
    val lastPeriod: String? = null,
    val lastRoute: String? = null
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val LAST_PERIOD = stringPreferencesKey("last_period")
        val LAST_ROUTE = stringPreferencesKey("last_route")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[Keys.BASE_URL] ?: SettingsDefaults.BASE_URL,
            autoSync = prefs[Keys.AUTO_SYNC] ?: true,
            lastPeriod = prefs[Keys.LAST_PERIOD],
            lastRoute = prefs[Keys.LAST_ROUTE]
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
}

object SettingsDefaults {
    const val BASE_URL = "https://hot-api.liiiu.cn/"

    /** 备用公共实例：主实例失败时按顺序故障转移 */
    val FALLBACK_BASE_URLS = listOf("https://api-hot.imsyy.top/")
}
