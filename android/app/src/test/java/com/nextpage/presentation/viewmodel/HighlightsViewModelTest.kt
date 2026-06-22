package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.model.ReadingStats
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [HighlightsViewModel].
 *
 * NOTE: [HighlightsViewModel.uiState] uses `stateIn(WhileSubscribed(5000))`,
 * which only emits AFTER the first subscriber starts collecting.  Every test
 * must launch a background collection before reading `.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HighlightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Start collecting uiState in the background so `stateIn` emits. */
    private fun <T> collectUiState(viewModel: HighlightsViewModel) {
        // backgroundScope in runTest is available — this is called inside runTest
    }

    @Test
    fun `initial state has no tag filter`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", tag = "cita"),
            createHighlight(id = "2", tag = "idea")
        )
        val viewModel = createViewModel(highlights = highlights)

        val state = viewModel.uiState.first()
        assertNull(state.tagFilter)
        assertEquals(2, state.filteredHighlights.size)
    }

    @Test
    fun `onTagFilterChanged filters highlights by tag`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", tag = "cita", textContent = "Quote one"),
            createHighlight(id = "2", tag = "idea", textContent = "Idea one"),
            createHighlight(id = "3", tag = "cita", textContent = "Quote two"),
            createHighlight(id = "4", tag = null, textContent = "No tag")
        )
        val viewModel = createViewModel(highlights = highlights)
        viewModel.uiState.first() // trigger stateIn subscription

        viewModel.onTagFilterChanged("cita")

        val state = viewModel.uiState.first()
        assertEquals("cita", state.tagFilter)
        assertEquals(2, state.filteredHighlights.size)
        assertTrue(state.filteredHighlights.all { it.tag == "cita" })
    }

    @Test
    fun `onTagFilterChanged with null clears filter`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", tag = "cita"),
            createHighlight(id = "2", tag = "idea")
        )
        val viewModel = createViewModel(highlights = highlights)
        viewModel.uiState.first() // trigger stateIn subscription

        viewModel.onTagFilterChanged("cita")
        viewModel.onTagFilterChanged(null)

        val state = viewModel.uiState.first()
        assertNull(state.tagFilter)
        assertEquals(2, state.filteredHighlights.size)
    }

    @Test
    fun `availableTags extracted from highlights`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", tag = "cita"),
            createHighlight(id = "2", tag = "idea"),
            createHighlight(id = "3", tag = "cita"),
            createHighlight(id = "4", tag = "ficci\u00F3n"),
            createHighlight(id = "5", tag = null)
        )
        val viewModel = createViewModel(highlights = highlights)

        val state = viewModel.uiState.first()
        assertEquals(listOf("cita", "ficci\u00F3n", "idea"), state.availableTags)
    }

    @Test
    fun `onTypeFilterChanged filters by highlight type`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", type = "quote"),
            createHighlight(id = "2", type = "idea"),
            createHighlight(id = "3", type = "passage")
        )
        val viewModel = createViewModel(highlights = highlights)
        viewModel.uiState.first() // trigger stateIn subscription

        viewModel.onTypeFilterChanged("quotes")

        val state = viewModel.uiState.first()
        assertEquals("quotes", state.typeFilter)
        assertEquals(1, state.filteredHighlights.size)
        assertEquals("quote", state.filteredHighlights.first().type)
    }

    @Test
    fun `onColorFilterChanged filters by color`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", color = HighlightColor.YELLOW.hex),
            createHighlight(id = "2", color = HighlightColor.BLUE.hex),
            createHighlight(id = "3", color = HighlightColor.YELLOW.hex)
        )
        val viewModel = createViewModel(highlights = highlights)
        viewModel.uiState.first() // trigger stateIn subscription

        viewModel.onColorFilterChanged(HighlightColor.YELLOW.hex)

        val state = viewModel.uiState.first()
        assertEquals(HighlightColor.YELLOW.hex, state.colorFilter)
        assertEquals(2, state.filteredHighlights.size)
    }

    @Test
    fun `combined tag and type filter`() = runTest {
        val highlights = listOf(
            createHighlight(id = "1", tag = "cita", type = "quote"),
            createHighlight(id = "2", tag = "cita", type = "idea"),
            createHighlight(id = "3", tag = "idea", type = "quote")
        )
        val viewModel = createViewModel(highlights = highlights)
        viewModel.uiState.first() // trigger stateIn subscription

        viewModel.onTagFilterChanged("cita")
        viewModel.onTypeFilterChanged("quotes")

        val state = viewModel.uiState.first()
        assertEquals(1, state.filteredHighlights.size)
        assertEquals("1", state.filteredHighlights.first().id)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createHighlight(
        id: String = "hl-${java.util.UUID.randomUUID()}",
        bookId: String = "book-1",
        tag: String? = null,
        color: String = HighlightColor.YELLOW.hex,
        textContent: String = "Sample text $id",
        type: String? = null,
        note: String? = null
    ): Highlight = Highlight(
        id = id,
        bookId = bookId,
        cfiRange = "epubcfi(/6/2)",
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = 0L,
        deletedAtEpochMillis = null,
        tag = tag,
        type = type
    )

    private fun createViewModel(
        highlights: List<Highlight> = emptyList(),
        books: List<Book> = emptyList()
    ): HighlightsViewModel {
        val readerRepo = FakeReaderRepository(highlights)
        val homeRepo = FakeHomeRepository(books)
        return HighlightsViewModel(readerRepo, homeRepo)
    }

    private class FakeReaderRepository(
        private val highlights: List<Highlight> = emptyList()
    ) : ReaderRepository {
        private val highlightsFlow = MutableStateFlow(highlights)

        override fun observeProgress(bookId: String): Flow<com.nextpage.domain.model.ReadingProgress?> =
            MutableStateFlow(null)
        override suspend fun upsertProgress(progress: com.nextpage.domain.model.ReadingProgress) = Unit
        override suspend fun getProgressForBook(bookId: String): com.nextpage.domain.model.ReadingProgress? = null
        override fun observeAllHighlights(): Flow<List<Highlight>> = highlightsFlow
        override fun observeAllHighlightsPaged(): Flow<androidx.paging.PagingData<Highlight>> =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
        override fun observeHighlights(bookId: String): Flow<List<Highlight>> = highlightsFlow
        override suspend fun upsertHighlight(highlight: Highlight) = Unit
        override suspend fun getHighlightsForBook(bookId: String): List<Highlight> = highlights
        override fun observeAllTags(): Flow<List<String>> = MutableStateFlow(
            highlights.mapNotNull { it.tag }.distinct().sorted()
        )
        override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
        override suspend fun getBookmarksForBook(bookId: String): List<Bookmark> = emptyList()
    }

    private class FakeHomeRepository(
        private val books: List<Book> = emptyList()
    ) : HomeRepository {
        private val booksFlow = MutableStateFlow(books)

        override fun observeBooks(): Flow<List<Book>> = booksFlow
        override fun observeRecentBooks(limit: Int): Flow<List<Book>> = booksFlow
        override fun observeCurrentBook(): Flow<Book?> = MutableStateFlow(books.firstOrNull())
        override fun observeCurrentBookProgress(): Flow<Float> = MutableStateFlow(0f)
        override fun observeDailyStats(userId: String?): Flow<ReadingStats> =
            MutableStateFlow(ReadingStats())
        override suspend fun deleteBook(bookId: String): Result<Unit> = Result.success(Unit)
    }
}
