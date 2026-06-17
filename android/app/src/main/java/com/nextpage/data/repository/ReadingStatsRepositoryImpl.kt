package com.nextpage.data.repository

import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.local.dao.ReadingStatsDao
import com.nextpage.data.local.entity.ReadingStatsEntity
import com.nextpage.domain.model.DailyReadingActivity
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class ReadingStatsRepositoryImpl(
    private val readingStatsDao: ReadingStatsDao,
    private val readingSessionDao: ReadingSessionDao
) : ReadingStatsRepository {
    override fun observeStats(bookId: String): Flow<ReadingStatsData?> =
        readingStatsDao.observeStatsForBook(bookId).map { entity ->
            entity?.let {
                ReadingStatsData(
                    bookId = it.bookId,
                    totalMinutesRead = it.totalMinutesRead,
                    lastReadDateEpochMillis = it.lastReadDateEpochMillis,
                    sessionsCount = it.sessionsCount
                )
            }
        }

    override fun observeTotalTime(): Flow<Long> =
        readingStatsDao.observeTotalMinutesRead().map { it ?: 0L }

    override fun observeBookStats(): Flow<List<ReadingStatsData>> =
        readingStatsDao.observeAllStats().map { entities ->
            entities.map {
                ReadingStatsData(
                    bookId = it.bookId,
                    totalMinutesRead = it.totalMinutesRead,
                    lastReadDateEpochMillis = it.lastReadDateEpochMillis,
                    sessionsCount = it.sessionsCount
                )
            }
        }

    override fun observeDailyActivity(): Flow<List<DailyReadingActivity>> =
        readingSessionDao.observeDailyMinutes().map { list ->
            list.map { DailyReadingActivity(dateEpochMillis = it.dateEpochMillis, minutesRead = it.totalMinutes) }
        }

    override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) {
        val now = System.currentTimeMillis()
        val existingEntity = readingStatsDao.observeStatsForBook(bookId).firstOrNull()

        if (existingEntity != null) {
            readingStatsDao.upsert(
                existingEntity.copy(
                    totalMinutesRead = existingEntity.totalMinutesRead + additionalMinutes,
                    lastReadDateEpochMillis = now,
                    sessionsCount = existingEntity.sessionsCount + 1
                )
            )
        } else {
            readingStatsDao.upsert(
                ReadingStatsEntity(
                    bookId = bookId,
                    totalMinutesRead = additionalMinutes,
                    lastReadDateEpochMillis = now,
                    sessionsCount = 1
                )
            )
        }
    }

    override suspend fun deleteStats(bookId: String) {
        readingStatsDao.deleteForBook(bookId)
    }
}