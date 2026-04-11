package com.nextpage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingStatsDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.dao.SyncFileMappingDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.data.local.entity.ReadingStatsEntity
import com.nextpage.data.local.entity.SyncFileMappingEntity
import com.nextpage.data.local.entity.SyncOutboxEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        ReadingStatsEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class,
        SyncOutboxEntity::class,
        SyncFileMappingEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readingStatsDao(): ReadingStatsDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncFileMappingDao(): SyncFileMappingDao
}
