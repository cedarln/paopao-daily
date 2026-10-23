package com.paopao.hot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SnapshotEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snapshotDao(): SnapshotDao

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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "paopao.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
