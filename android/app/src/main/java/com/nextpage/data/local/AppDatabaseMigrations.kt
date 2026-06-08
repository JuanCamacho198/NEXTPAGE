package com.nextpage.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN deleted_at INTEGER")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN total_pages INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE books ADD COLUMN user_rating INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN current_page INTEGER DEFAULT NULL")
        }
    }

    val ALL = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6
    )
}
