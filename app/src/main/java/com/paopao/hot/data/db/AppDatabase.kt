package com.paopao.hot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SnapshotEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snapshotDao(): SnapshotDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        /** v1 → v2：补充话题摘要、封面、发布时间与移动版链接 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE snapshots ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE snapshots ADD COLUMN mobileUrl TEXT")
                db.execSQL("ALTER TABLE snapshots ADD COLUMN cover TEXT")
                db.execSQL("ALTER TABLE snapshots ADD COLUMN timestamp INTEGER")
            }
        }

        /** v2 → v3：新增收藏表，收藏内容独立于每日快照保留 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`favoriteKey` TEXT NOT NULL, " +
                        "`route` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`url` TEXT, " +
                        "`mobileUrl` TEXT, " +
                        "`cover` TEXT, " +
                        "`hot` REAL, " +
                        "`timestamp` INTEGER, " +
                        "`savedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`favoriteKey`))"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "paopao.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
