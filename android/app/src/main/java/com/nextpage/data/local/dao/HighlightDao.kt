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

    @Query("SELECT * FROM highlights WHERE id = :id LIMIT 1")
    suspend fun getHighlightById(id: String): HighlightEntity?

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM highlights WHERE book_id = :bookId")
    suspend fun getHighlightsForBook(bookId: String): List<HighlightEntity>

    @Upsert
    suspend fun upsert(highlight: HighlightEntity)

    @Upsert
    suspend fun upsertAll(highlights: List<HighlightEntity>)

    @Query("SELECT COUNT(*) FROM highlights")
    suspend fun count(): Int
}
