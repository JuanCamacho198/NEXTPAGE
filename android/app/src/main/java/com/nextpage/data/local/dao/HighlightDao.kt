package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nextpage.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllHighlights(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeHighlightsForBook(bookId: String): Flow<List<HighlightEntity>>

    @Upsert
    suspend fun upsert(highlight: HighlightEntity)
}
