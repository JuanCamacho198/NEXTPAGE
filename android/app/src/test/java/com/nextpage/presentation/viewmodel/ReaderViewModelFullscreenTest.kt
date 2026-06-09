package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
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
    fun `fullscreen resets on new book load`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        // Enter fullscreen
        viewModel.onToggleFullscreen()
        assertTrue(viewModel.uiState.value.isFullscreen)

        // Load a new book (simulate)
        viewModel.loadBook("new-book", "/path/book.epub", "epub")

        // Fullscreen should reset to false
        assertFalse(viewModel.uiState.value.isFullscreen)
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
        override fun observeAllHighlights(): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override fun observeHighlights(bookId: String): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override suspend fun upsertHighlight(highlight: Highlight) = Unit
        override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
    }
}
