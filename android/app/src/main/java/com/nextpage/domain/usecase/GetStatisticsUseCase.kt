package com.nextpage.domain.usecase

import com.nextpage.domain.model.DailyReadingActivity
import com.nextpage.domain.model.Statistics
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Calendar
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GetStatisticsUseCase(
    private val readingStatsRepository: ReadingStatsRepository,
    private val homeRepository: HomeRepository
) {
    private val refreshTrigger = MutableStateFlow(Unit)

    fun refresh() {
        refreshTrigger.value = Unit
    }

    operator fun invoke(): Flow<Statistics> = combine(
        readingStatsRepository.observeTotalTime(),
        readingStatsRepository.observeBookStats(),
        refreshTrigger.flatMapLatest { flow { emit(readingStatsRepository.getDailyActivity()) } },
        homeRepository.observeBooks()
    ) { totalMinutes, bookStats, dailyActivity, books ->
        val todayStart = getTodayStartMillis()
        val todayMinutes = dailyActivity
            .filter { it.dateEpochMillis == todayStart }
            .sumOf { it.minutesRead }
            .toLong()

        Statistics(
            totalMinutesRead = totalMinutes,
            currentStreak = calculateStreak(dailyActivity, todayStart),
            booksRead = bookStats.count { it.totalMinutesRead >= BOOKS_READ_MINUTES },
            weeklyActivity = lastSevenDaysActivity(dailyActivity, todayStart),
            goalProgress = (todayMinutes.toFloat() / DAILY_GOAL_MINUTES).coerceIn(0f, 1f),
            favoriteGenres = books.mapNotNull { it.description?.split(",")?.firstOrNull() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(5)
        )
    }

    private fun calculateStreak(dailyActivity: List<DailyReadingActivity>, todayStart: Long): Int {
        val activeDates = dailyActivity
            .filter { it.minutesRead > 0 }
            .map { it.dateEpochMillis }
            .toSortedSet()

        if (activeDates.isEmpty()) return 0

        val calendar = Calendar.getInstance()
        var currentDate = todayStart
        var streak = 0

        // If no activity today, start checking from yesterday
        if (!activeDates.contains(currentDate)) {
            currentDate -= TimeUnit.DAYS.toMillis(1)
        }

        while (activeDates.contains(currentDate)) {
            streak++
            currentDate -= TimeUnit.DAYS.toMillis(1)
        }

        return streak
    }

    private fun lastSevenDaysActivity(
        dailyActivity: List<DailyReadingActivity>,
        todayStart: Long
    ): List<DailyReadingActivity> {
        val activityByDate = dailyActivity.associateBy { it.dateEpochMillis }
        val days = mutableListOf<DailyReadingActivity>()
        repeat(7) { offset ->
            val date = todayStart - TimeUnit.DAYS.toMillis((6 - offset).toLong())
            days.add(activityByDate[date] ?: DailyReadingActivity(dateEpochMillis = date, minutesRead = 0))
        }
        return days
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

    companion object {
        private const val DAILY_GOAL_MINUTES = 30
        private const val BOOKS_READ_MINUTES = 300L
    }
}
