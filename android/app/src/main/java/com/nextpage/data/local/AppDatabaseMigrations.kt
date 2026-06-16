package com.nextpage.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN chapter_count INTEGER DEFAULT NULL")
        }
    }

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

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE highlights ADD COLUMN locator_json TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN locator_json TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN locator_json TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN description TEXT DEFAULT NULL")
        }
    }

    val ALL = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )
}
