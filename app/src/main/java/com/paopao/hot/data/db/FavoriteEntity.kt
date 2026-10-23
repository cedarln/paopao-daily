package com.paopao.hot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * 收藏 / 稍后读。key 用「平台 + 标题」稳定标识，
 * 即使榜单快照被新数据替换，收藏内容仍然完整保留。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val favoriteKey: String,
    val route: String,
    val name: String,
    val title: String,
    val description: String? = null,
    val url: String?,
    val mobileUrl: String? = null,
    val cover: String? = null,
    val hot: Double? = null,
    val timestamp: Long? = null,
    val savedAt: Long
)

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT favoriteKey FROM favorites")
    suspend fun keys(): List<String>

    @Upsert
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE favoriteKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
