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
    val contentVersion: Int = 0,
    /** system / light / dark */
    val themeMode: String = THEME_SYSTEM,
    /** Android 12+ 动态取色 */
    val dynamicColor: Boolean = true,
    /** 阅读器字号百分比：100 / 115 / 130 / 150 */
    val readerFontPercent: Int = 100,
    /** 阅读器深色网页 */
    val readerDark: Boolean = false,
    /** 阅读器隐藏图片 */
    val readerNoImages: Boolean = false,
    /** 列表是否显示封面缩略图 */
    val showCovers: Boolean = true,
    /** 紧凑列表：隐藏封面、减小行高 */
    val compactRows: Boolean = false,
    /** 今日已读条目标记，随日期自动清理 */
    val readKeys: Set<String> = emptySet()
) {
    val effectiveEnabledRoutes: Set<String>
        get() = enabledRoutes ?: PLATFORMS.map { it.route }.toSet()

    fun isPlatformEnabled(route: String): Boolean = route in effectiveEnabledRoutes

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val READER_FONT_CHOICES = listOf(100, 115, 130, 150)
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val LAST_PERIOD = stringPreferencesKey("last_period")
        val LAST_ROUTE = stringPreferencesKey("last_route")
        val ENABLED_ROUTES = stringSetPreferencesKey("enabled_routes")
        val CONTENT_VERSION = intPreferencesKey("content_version")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val READER_FONT_PERCENT = intPreferencesKey("reader_font_percent")
        val READER_DARK = booleanPreferencesKey("reader_dark")
        val READER_NO_IMAGES = booleanPreferencesKey("reader_no_images")
        val SHOW_COVERS = booleanPreferencesKey("show_covers")
        val COMPACT_ROWS = booleanPreferencesKey("compact_rows")
        val READ_KEYS = stringSetPreferencesKey("read_keys")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[Keys.BASE_URL] ?: SettingsDefaults.BASE_URL,
            autoSync = prefs[Keys.AUTO_SYNC] ?: true,
            lastPeriod = prefs[Keys.LAST_PERIOD],
            lastRoute = prefs[Keys.LAST_ROUTE],
            enabledRoutes = prefs[Keys.ENABLED_ROUTES],
            contentVersion = prefs[Keys.CONTENT_VERSION] ?: 0,
            themeMode = prefs[Keys.THEME_MODE] ?: Settings.THEME_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            readerFontPercent = prefs[Keys.READER_FONT_PERCENT] ?: 100,
            readerDark = prefs[Keys.READER_DARK] ?: false,
            readerNoImages = prefs[Keys.READER_NO_IMAGES] ?: false,
            showCovers = prefs[Keys.SHOW_COVERS] ?: true,
            compactRows = prefs[Keys.COMPACT_ROWS] ?: false,
            readKeys = prefs[Keys.READ_KEYS].orEmpty()
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

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setReaderFontPercent(percent: Int) {
        context.dataStore.edit { it[Keys.READER_FONT_PERCENT] = percent }
    }

    suspend fun setReaderDark(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READER_DARK] = enabled }
    }

    suspend fun setReaderNoImages(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READER_NO_IMAGES] = enabled }
    }

    suspend fun setShowCovers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_COVERS] = enabled }
    }

    suspend fun setCompactRows(enabled: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_ROWS] = enabled }
    }

    /** 已读标记只保留 [dayPrefix] 当天，避免集合无限增长 */
    suspend fun markRead(keys: Collection<String>, dayPrefix: String) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val kept = prefs[Keys.READ_KEYS].orEmpty()
                .filterTo(mutableSetOf()) { it.startsWith(dayPrefix) }
            kept.addAll(keys)
            prefs[Keys.READ_KEYS] = kept
        }
    }
}

object SettingsDefaults {
    const val BASE_URL = "https://hot-api.liiiu.cn/"

    /** 2 = 快照开始带摘要/封面/移动链接；旧版本升级后需要补一次快照 */
    const val CONTENT_VERSION = 2

    /** 备用公共实例：主实例失败时按顺序故障转移 */
    val FALLBACK_BASE_URLS = listOf("https://api-hot.imsyy.top/")
}
