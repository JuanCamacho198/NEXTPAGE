package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import android.app.Application
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelFullscreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onToggleFullscreen flips isFullscreen`() = runTest {
        val viewModel = createViewModel(testScheduler)

        assertFalse(viewModel.uiState.value.isFullscreen)

        viewModel.onToggleFullscreen()
        assertTrue(viewModel.uiState.value.isFullscreen)

        viewModel.onToggleFullscreen()
        assertFalse(viewModel.uiState.value.isFullscreen)
    }

    @Test
    fun `fullscreen state persists across multiple toggles within session`() = runTest {
        val viewModel = createViewModel(testScheduler)

        // Toggle on
        viewModel.onToggleFullscreen()
        assertTrue(viewModel.uiState.value.isFullscreen)

        // Toggle off
        viewModel.onToggleFullscreen()
        assertFalse(viewModel.uiState.value.isFullscreen)

        // Toggle on again
        viewModel.onToggleFullscreen()
        assertTrue(viewModel.uiState.value.isFullscreen)

        // Toggle off again
        viewModel.onToggleFullscreen()
        assertFalse(viewModel.uiState.value.isFullscreen)
    }

    @Test
    fun `fullscreen enters immersive on new book load`() = runTest {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        // Load a new book (simulate)
        viewModel.loadBook("new-book", "/path/book.epub", "epub")

        // Reader should auto-enter immersive (fullscreen) reading mode.
        assertTrue(viewModel.uiState.value.isFullscreen)
    }

    @Test
    fun `default state is not fullscreen`() = runTest {
        val viewModel = createViewModel(testScheduler)
        assertFalse(viewModel.uiState.value.isFullscreen)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createViewModel(scheduler: TestCoroutineScheduler): ReaderViewModel {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        return ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
    }

    private class FakeReaderRepository : ReaderRepository {
        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = MutableStateFlow(null)
        override suspend fun upsertProgress(progress: ReadingProgress) = Unit
        override suspend fun updateBookReadingState(bookId: String, progressPercent: Float, updatedAt: Long) = Unit
        override fun observeAllHighlights(): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override fun observeAllHighlightsPaged(): Flow<androidx.paging.PagingData<Highlight>> =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
        override fun observeHighlights(bookId: String): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override suspend fun upsertHighlight(highlight: Highlight) = Unit
        override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
        override suspend fun getProgressForBook(bookId: String): com.nextpage.domain.model.ReadingProgress? = null
        override suspend fun getHighlightsForBook(bookId: String): List<com.nextpage.domain.model.Highlight> = emptyList()
        override suspend fun getBookmarksForBook(bookId: String): List<com.nextpage.domain.model.Bookmark> = emptyList()
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
        override fun observeBookStats(): kotlinx.coroutines.flow.Flow<List<com.nextpage.domain.repository.ReadingStatsData>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override suspend fun getDailyActivity(userId: String?): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
    }
}
