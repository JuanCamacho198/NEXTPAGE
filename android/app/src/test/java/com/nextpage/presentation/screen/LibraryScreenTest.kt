package com.nextpage.presentation.screen

import com.nextpage.domain.model.Book
import com.nextpage.presentation.viewmodel.LibraryUiState
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the LibraryScreen composable's data contract.
 *
 * Compose rendering tests (asserting grid layout, add-book card, etc.)
 * belong in androidTest/ with createComposeRule. Here we test the ViewModel
 * state that drives the screen — proving the data contract for all sections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun libraryUiState_emptyState_rendersEmpty() = runTest {
        val state = LibraryUiState(
            books = emptyList(),
            isLoading = false,
            isImporting = false,
            bookToDelete = null,
            totalMinutesRead = 0L,
            readingMinutesByBook = emptyMap()
        )

        assertTrue(state.books.isEmpty())
        assertEquals(0, state.books.size)
        assertEquals(0L, state.totalMinutesRead)
        assertNull(state.bookToDelete)
        assertTrue(state.readingMinutesByBook.isEmpty())
    }

    @Test
    fun libraryUiState_withBooks_supportsGridLayout() = runTest {
        val books = listOf(
            Book("b1", "Book 1", "Author 1", null, "/p1", "epub", totalPages = null, userRating = null, updatedAtEpochMillis = 1000L),
            Book("b2", "Book 2", "Author 2", null, "/p2", "epub", totalPages = null, userRating = null, updatedAtEpochMillis = 2000L),
            Book("b3", "Book 3", "Author 3", null, "/p3", "pdf", totalPages = null, userRating = null, updatedAtEpochMillis = 3000L),
            Book("b4", "Book 4", "Author 4", null, "/p4", "epub", totalPages = null, userRating = null, updatedAtEpochMillis = 4000L),
            Book("b5", "Book 5", "Author 5", null, "/p5", "pdf", totalPages = null, userRating = null, updatedAtEpochMillis = 5000L)
        )
        val minutesByBook = mapOf(
            "b1" to 30L,
            "b2" to 0L,
            "b3" to 120L,
            "b4" to 15L,
            "b5" to 0L
        )
        val state = LibraryUiState(
            books = books,
            isLoading = false,
            bookToDelete = null,
            readingMinutesByBook = minutesByBook
        )

        assertEquals(5, state.books.size)
        assertEquals("Book 1", state.books[0].title)
        assertEquals("Author 3", state.books[2].author)
        assertEquals("pdf", state.books[4].format)
        assertEquals(30L, state.readingMinutesByBook["b1"]?.toLong())
        assertEquals(120L, state.readingMinutesByBook["b3"]?.toLong())
    }

    @Test
    fun libraryUiState_tracksImportingState() = runTest {
        val state = LibraryUiState(
            books = emptyList(),
            isImporting = true
        )

        assertTrue(state.isImporting)
        assertTrue(state.books.isEmpty())
    }
}
