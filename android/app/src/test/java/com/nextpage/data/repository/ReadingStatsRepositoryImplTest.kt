package com.nextpage.data.repository

import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.local.dao.ReadingStatsDao
import com.nextpage.data.local.entity.ReadingSessionEntity
import com.nextpage.data.local.entity.ReadingStatsEntity
import com.nextpage.data.local.model.DailyReadingMinutes
import com.nextpage.domain.model.DailyReadingActivity
import com.nextpage.domain.repository.ReadingStatsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the refactored ReadingStatsRepository — verifies the suspend
 * `getDailyActivity()` path (R5) and the book_stats → ReadingStatsData mapping.
 */
class ReadingStatsRepositoryImplTest {

    @Test
    fun getDailyActivity_aggregatesSessionsByDateFromSuspendDao() = runBlocking {
        val fakeSessionDao = FakeReadingSessionDao().apply {
            getDailyMinutesResult = listOf(
                DailyReadingMinutes(dateEpochMillis = 20240101L, totalMinutes = 25),
                DailyReadingMinutes(dateEpochMillis = 20240102L, totalMinutes = 10)
            )
        }
        val repository = ReadingStatsRepositoryImpl(
            readingStatsDao = FakeReadingStatsDao(),
            readingSessionDao = fakeSessionDao
        )

        val result: List<DailyReadingActivity> = repository.getDailyActivity()

        assertEquals(2, result.size)
        assertEquals(DailyReadingActivity(dateEpochMillis = 20240101L, minutesRead = 25), result[0])
        assertEquals(DailyReadingActivity(dateEpochMillis = 20240102L, minutesRead = 10), result[1])
    }

    @Test
    fun getDailyActivity_isSuspendNotFlow_doesNotEmitOnSessionInsert() = runBlocking {
        val fakeSessionDao = FakeReadingSessionDao().apply {
            getDailyMinutesResult = listOf(
                DailyReadingMinutes(dateEpochMillis = 20240101L, totalMinutes = 5)
            )
        }
        val repository = ReadingStatsRepositoryImpl(
            readingStatsDao = FakeReadingStatsDao(),
            readingSessionDao = fakeSessionDao
        )

        val first = repository.getDailyActivity()
        assertEquals(1, first.size)
        assertEquals(5, first.first().minutesRead)

        // Mutate the underlying "table" — since getDailyActivity is a suspend
        // function (not a Flow), observers of the old call site do not receive
        // automatic updates. A fresh suspend call re-reads the data.
        fakeSessionDao.getDailyMinutesResult = listOf(
            DailyReadingMinutes(dateEpochMillis = 20240101L, totalMinutes = 99)
        )

        val second = repository.getDailyActivity()
        assertEquals(99, second.first().minutesRead)
        // First snapshot is unchanged — no auto re-emission.
        assertEquals(5, first.first().minutesRead)
    }

    @Test
    fun getDailyActivity_returnsEmptyListWhenNoSessions() = runBlocking {
        val fakeSessionDao = FakeReadingSessionDao().apply {
            getDailyMinutesResult = emptyList()
        }
        val repository = ReadingStatsRepositoryImpl(
            readingStatsDao = FakeReadingStatsDao(),
            readingSessionDao = fakeSessionDao
        )

        val result = repository.getDailyActivity()
        assertTrue(result.isEmpty())
    }

    @Test
    fun observeBookStats_mapsReadingStatsEntityToDomain() = runBlocking {
        val fakeStatsDao = FakeReadingStatsDao().apply {
            allStatsState.value = listOf(
                ReadingStatsEntity(
                    bookId = "book-1",
                    totalMinutesRead = 60,
                    lastReadDateEpochMillis = 1000L,
                    sessionsCount = 4
                ),
                ReadingStatsEntity(
                    bookId = "book-2",
                    totalMinutesRead = 30,
                    lastReadDateEpochMillis = 2000L,
                    sessionsCount = 2
                )
            )
        }
        val repository = ReadingStatsRepositoryImpl(
            readingStatsDao = fakeStatsDao,
            readingSessionDao = FakeReadingSessionDao()
        )

        val result: List<ReadingStatsData> = repository.observeBookStats().first()

        assertEquals(2, result.size)
        assertEquals("book-1", result[0].bookId)
        assertEquals(60L, result[0].totalMinutesRead)
        assertEquals(4, result[0].sessionsCount)
        assertEquals("book-2", result[1].bookId)
        assertEquals(30L, result[1].totalMinutesRead)
    }

    // ── Fakes ───────────────────────────────────────────────────────

    private class FakeReadingSessionDao : ReadingSessionDao {
        var getDailyMinutesResult: List<DailyReadingMinutes> = emptyList()
        var getDailyMinutesFromDateResult: List<DailyReadingMinutes> = emptyList()

        override suspend fun insert(session: ReadingSessionEntity) = Unit
        override fun getTotalMinutesForDate(date: Long): Flow<Int> = MutableStateFlow(0)
        override fun getTotalMinutesForDateAndUser(date: Long, userId: String): Flow<Int> = MutableStateFlow(0)
        override fun getTotalMinutes(): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCount(): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCountForDate(date: Long): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCountForDateAndUser(date: Long, userId: String): Flow<Int> = MutableStateFlow(0)
        override fun observeSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>> =
            MutableStateFlow(emptyList())
        override suspend fun deleteSessionsForBook(bookId: String) = Unit
        override suspend fun count(): Int = 0
        override suspend fun getDailyMinutes(): List<DailyReadingMinutes> = getDailyMinutesResult
        override suspend fun getDailyMinutesFromDate(startDate: Long): List<DailyReadingMinutes> =
            getDailyMinutesFromDateResult
    }

    private class FakeReadingStatsDao : ReadingStatsDao {
        val allStatsState = MutableStateFlow<List<ReadingStatsEntity>>(emptyList())
        val totalMinutesState = MutableStateFlow<Long?>(0L)
        val statsForBook = MutableStateFlow<ReadingStatsEntity?>(null)

        override fun observeStatsForBook(bookId: String): Flow<ReadingStatsEntity?> = statsForBook
        override fun observeAllStats(): Flow<List<ReadingStatsEntity>> = allStatsState
        override suspend fun upsert(stats: ReadingStatsEntity) {
            allStatsState.value = allStatsState.value
                .filterNot { it.bookId == stats.bookId }
                .plus(stats)
        }
        override fun observeTotalMinutesRead(): Flow<Long?> = totalMinutesState
        override fun observeTotalMinutesReadForUser(userId: String): Flow<Long?> = totalMinutesState
        override suspend fun deleteForBook(bookId: String) {
            allStatsState.value = allStatsState.value.filterNot { it.bookId == bookId }
        }
    }
}
