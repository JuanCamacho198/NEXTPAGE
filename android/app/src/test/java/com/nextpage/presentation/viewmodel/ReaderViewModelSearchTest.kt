package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.SearchResult
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import android.app.Application
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSearchTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onToggleSearch flips isSearchActive`() = runTest {
        val viewModel = createViewModel(testScheduler)

        assertFalse(viewModel.uiState.value.isSearchActive)

        viewModel.onToggleSearch()
        assertTrue(viewModel.uiState.value.isSearchActive)

        viewModel.onToggleSearch()
        assertFalse(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `onClearSearch resets search state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.onToggleSearch()
        viewModel.onSearchQuery("test")

        viewModel.onClearSearch()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `onDismissSearch resets search state and hides sheet`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.onToggleSearch()
        viewModel.onSearchQuery("test")

        viewModel.onDismissSearch()

        val state = viewModel.uiState.value
        assertFalse(state.isSearchActive)
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `onSearchResultSelected with same chapter dismisses without navigation`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        setEpubState(
            viewModel,
            chapters = listOf(
                BookChapter(0, "c1", "Ch 1", "ch1.xhtml"),
                BookChapter(1, "c2", "Ch 2", "ch2.xhtml")
            ),
            currentChapterIndex = 1,
            bookFilePath = "/test/book.epub"
        )

        val result = SearchResult(
            text = "...sample text...",
            offset = 0,
            chapterIndex = 1
        )
        viewModel.onSearchResultSelected(result)

        val state = viewModel.uiState.value
        assertEquals(1, state.currentChapterIndex)
        assertFalse(state.isSearchActive)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createViewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): ReaderViewModel {
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

    private fun setEpubState(
        viewModel: ReaderViewModel,
        chapters: List<BookChapter>,
        currentChapterIndex: Int,
        bookFilePath: String
    ) {
        val field = ReaderViewModel::class.java.getDeclaredField("mutableUiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(viewModel) as MutableStateFlow<ReaderUiState>
        state.value = state.value.copy(
            bookFormat = "epub",
            bookFilePath = bookFilePath,
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            totalPdfPages = 0
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
        override suspend fun getDailyActivity(): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
    }
}
