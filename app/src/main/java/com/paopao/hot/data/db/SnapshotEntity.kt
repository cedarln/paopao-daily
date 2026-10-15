package com.paopao.hot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * 每日快照：每次同步把各平台当日 Top N 存一份，
 * 周/月榜就是对这些快照做跨天聚合得到的。
 */
@Entity(
    tableName = "snapshots",
    indices = [Index(value = ["route", "dateEpochDay", "rank"], unique = true)]
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val route: String,
    val name: String,
    val dateEpochDay: Long,
    val rank: Int,
    val title: String,
    val url: String?,
    val hot: Double?,
    val fetchedAt: Long
)

@Dao
interface SnapshotDao {

    @Upsert
    suspend fun upsertAll(items: List<SnapshotEntity>)

    @Query("SELECT * FROM snapshots WHERE route = :route AND dateEpochDay = :day ORDER BY rank ASC")
    suspend fun getDay(route: String, day: Long): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE dateEpochDay >= :fromDay AND dateEpochDay <= :toDay ORDER BY rank ASC")
    suspend fun getRange(fromDay: Long, toDay: Long): List<SnapshotEntity>

    @Query("SELECT MAX(fetchedAt) FROM snapshots")
    suspend fun lastSyncAt(): Long?

    @Query("SELECT MIN(dateEpochDay) FROM snapshots")
    suspend fun earliestDay(): Long?

    @Query("DELETE FROM snapshots WHERE dateEpochDay < :beforeDay")
    suspend fun trimBefore(beforeDay: Long)

    @Query("DELETE FROM snapshots")
    suspend fun clearAll()
}
