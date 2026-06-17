package com.nextpage.domain.repository

import com.nextpage.domain.model.DailyReadingActivity
import kotlinx.coroutines.flow.Flow

interface ReadingStatsRepository {
    fun observeStats(bookId: String): Flow<ReadingStatsData?>

    fun observeTotalTime(): Flow<Long>

    fun observeBookStats(): Flow<List<ReadingStatsData>>

    fun observeDailyActivity(): Flow<List<DailyReadingActivity>>

    suspend fun updateReadingTime(bookId: String, additionalMinutes: Long)

    suspend fun deleteStats(bookId: String)
}

data class ReadingStatsData(
    val bookId: String,
    val totalMinutesRead: Long,
    val lastReadDateEpochMillis: Long,
    val sessionsCount: Int
)