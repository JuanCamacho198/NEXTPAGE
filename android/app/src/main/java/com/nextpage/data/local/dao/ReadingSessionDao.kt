package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextpage.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT COALESCE(SUM(duration_minutes), 0) FROM reading_sessions WHERE date = :date")
    fun getTotalMinutesForDate(date: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration_minutes), 0) FROM reading_sessions")
    fun getTotalMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reading_sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE date = :date")
    fun getSessionCountForDate(date: Long): Flow<Int>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY start_time DESC")
    fun observeSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("DELETE FROM reading_sessions WHERE book_id = :bookId")
    suspend fun deleteSessionsForBook(bookId: String)

    @Query("SELECT COUNT(*) FROM reading_sessions")
    suspend fun count(): Int
}