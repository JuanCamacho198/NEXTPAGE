package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nextpage.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId LIMIT 1")
    fun observeProgressForBook(bookId: String): Flow<ReadingProgressEntity?>

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}
