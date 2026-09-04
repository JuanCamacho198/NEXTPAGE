package com.nextpage.testutil

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared fakes for reader ViewModel slice tests (SDD reader-facade-split).
 *
 * One fake serves every slice test (T1 harness through T8 closure) so new
 * slice tests add assertions, not fixture copies. Pass a custom
 * [highlightsFlow] when the test drives highlight emissions (ordering pins).
 */
class FakeReaderRepository(
    private val highlightsFlow: MutableStateFlow<List<Highlight>> = MutableStateFlow(emptyList())
) : ReaderRepository {
    override fun observeProgress(bookId: String): Flow<ReadingProgress?> = MutableStateFlow(null)
    override suspend fun upsertProgress(progress: ReadingProgress) = Unit
    override suspend fun updateBookReadingState(bookId: String, progressPercent: Float, updatedAt: Long) = Unit
    override fun observeAllHighlights(): Flow<List<Highlight>> = highlightsFlow
    override fun observeAllHighlightsPaged(): Flow<androidx.paging.PagingData<Highlight>> =
        flowOf(androidx.paging.PagingData.empty())
    override fun observeHighlights(bookId: String): Flow<List<Highlight>> = highlightsFlow
    override suspend fun upsertHighlight(highlight: Highlight) = Unit
    override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
    override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
    override suspend fun getProgressForBook(bookId: String): ReadingProgress? = null
    override suspend fun getHighlightsForBook(bookId: String): List<Highlight> = emptyList()
    override suspend fun getBookmarksForBook(bookId: String): List<Bookmark> = emptyList()
}

class FakeReadingStatsRepository : ReadingStatsRepository {
    override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
    override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
    override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
    override suspend fun deleteStats(bookId: String) = Unit
    override fun observeBookStats(): Flow<List<ReadingStatsData>> = MutableStateFlow(emptyList())
    override suspend fun getDailyActivity(userId: String?): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
}
