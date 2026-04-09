package com.nextpage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao
}
