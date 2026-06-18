package com.nextpage.data.repository

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingStats
import com.nextpage.domain.repository.HomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRepositoryImpl(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingSessionDao: ReadingSessionDao
) : HomeRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAllBooks().map { entities ->
            entities.map { it.toBook() }
        }

    override fun observeRecentBooks(limit: Int): Flow<List<Book>> =
        bookDao.observeAllBooks().map { entities ->
            entities.take(limit).map { it.toBook() }
        }

    override fun observeCurrentBook(): Flow<Book?> =
        // Current book is the most recently read book
        // (simplified: use the most recently updated book)
        bookDao.observeAllBooks().map { entities ->
            entities.maxByOrNull { it.updatedAtEpochMillis }?.toBook()
        }

    override fun observeCurrentBookProgress(): Flow<Float> =
        observeCurrentBook().flatMapLatest { book ->
            if (book != null) {
                readingProgressDao.observeProgressForBook(book.id)
                    .map { it?.percentage ?: 0f }
            } else {
                flowOf(0f)
            }
        }

    override fun observeDailyStats(userId: String?): Flow<ReadingStats> {
        val todayStart = getTodayStartMillis()
        return if (userId != null && userId.isNotBlank()) {
            combine(
                readingSessionDao.getTotalMinutesForDateAndUser(todayStart, userId),
                readingSessionDao.getSessionCountForDateAndUser(todayStart, userId)
            ) { minutes, sessions ->
                ReadingStats(
                    minutesRead = minutes,
                    sessionCount = sessions,
                    dailyProgressPercent = calculateProgress(minutes)
                )
            }
        } else {
            combine(
                readingSessionDao.getTotalMinutesForDate(todayStart),
                readingSessionDao.getSessionCountForDate(todayStart)
            ) { minutes, sessions ->
                ReadingStats(
                    minutesRead = minutes,
                    sessionCount = sessions,
                    dailyProgressPercent = calculateProgress(minutes)
                )
            }
        }
    }

    override suspend fun deleteBook(bookId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        bookDao.deleteBook(bookId, now)
        readingSessionDao.deleteSessionsForBook(bookId)
    }

    private fun getTodayStartMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun calculateProgress(minutesRead: Int): Float {
        // Daily goal is 30 minutes by default
        val dailyGoal = 30
        return (minutesRead.toFloat() / dailyGoal).coerceIn(0f, 1f)
    }

    private fun com.nextpage.data.local.entity.BookEntity.toBook(): Book = Book(
        id = id,
        title = title,
        author = author,
        description = description,
        coverPath = coverPath,
        filePath = filePath,
        format = format,
        totalPages = totalPages,
        userRating = userRating,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}