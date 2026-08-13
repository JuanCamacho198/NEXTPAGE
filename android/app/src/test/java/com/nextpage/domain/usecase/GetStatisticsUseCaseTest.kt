package com.nextpage.domain.usecase

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.DailyReadingActivity
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Tests for the refresh trigger behavior of [GetStatisticsUseCase] (R5):
 * - `getDailyActivity()` is a suspend function, not a Flow
 * - The use case re-aggregates only when `refresh()` is called
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetStatisticsUseCaseTest {

    private val todayStart: Long = run {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    @Test
    fun refreshTrigger_picksUpNewDailyActivityOnRefresh() = runBlocking {
        val statsRepo = FakeReadingStatsRepository().apply {
            dailyActivity = listOf(DailyReadingActivity(dateEpochMillis = todayStart, minutesRead = 5))
        }
        val useCase = GetStatisticsUseCase(statsRepo, FakeHomeRepository())

        val first = useCase().first()
        val firstToday = first.weeklyActivity.find { it.dateEpochMillis == todayStart }
        assertEquals(5L, firstToday?.minutesRead?.toLong() ?: -1L)

        // Mutate the underlying "table" — without refresh(), the use case flow
        // does not re-aggregate (the getDailyActivity() call only fires when
        // the refreshTrigger emits, which happens on refresh()).
        statsRepo.dailyActivity = listOf(DailyReadingActivity(dateEpochMillis = todayStart, minutesRead = 99))
        useCase.refresh()
        val second = useCase().first()
        val secondToday = second.weeklyActivity.find { it.dateEpochMillis == todayStart }
        assertEquals(99L, secondToday?.minutesRead?.toLong() ?: -1L)
    }

    @Test
    fun weeklyActivity_coversSevenDays_evenWhenNoData() = runBlocking {
        val useCase = GetStatisticsUseCase(FakeReadingStatsRepository(), FakeHomeRepository())
        val stats = useCase().first()
        assertEquals(7, stats.weeklyActivity.size)
        // Each day's minutes should default to 0 when no data is present
        assertEquals(0, stats.weeklyActivity.sumOf { it.minutesRead })
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        var dailyActivity: List<DailyReadingActivity> = emptyList()
        private val stats = MutableStateFlow<ReadingStatsData?>(null)
        private val total = MutableStateFlow(0L)
        private val allStats = MutableStateFlow<List<ReadingStatsData>>(emptyList())

        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = stats
        override fun observeTotalTime(): Flow<Long> = total
        override fun observeBookStats(): Flow<List<ReadingStatsData>> = allStats
        override suspend fun getDailyActivity(): List<DailyReadingActivity> = dailyActivity
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
    }

    private class FakeHomeRepository : HomeRepository {
        override fun observeBooks(): Flow<List<Book>> = MutableStateFlow(emptyList())
        override fun observeRecentBooks(limit: Int): Flow<List<Book>> = MutableStateFlow(emptyList())
        override fun observeCurrentBook(): Flow<Book?> = MutableStateFlow(null)
        override fun observeCurrentBookProgress(): Flow<Float> = MutableStateFlow(0f)
        override fun observeCurrentBooks(): Flow<List<Book>> = MutableStateFlow(emptyList())
        override fun observeDailyStats(userId: String?): Flow<com.nextpage.domain.model.ReadingStats> =
            MutableStateFlow(com.nextpage.domain.model.ReadingStats(0, 0, 0f))
        override suspend fun deleteBook(bookId: String): Result<Unit> = Result.success(Unit)
    }
}

