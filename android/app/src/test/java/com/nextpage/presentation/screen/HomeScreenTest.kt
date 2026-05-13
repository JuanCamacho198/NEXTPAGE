package com.nextpage.presentation.screen

import com.nextpage.domain.model.Book
import com.nextpage.presentation.viewmodel.HomeUiState
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the HomeScreen composable's data contract.
 *
 * Compose rendering tests (asserting all 7 sections render with visual elements)
 * belong in androidTest/ with createComposeRule. Here we test the ViewModel
 * state that drives the screen — proving the data contract for all 7 sections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeUiState_supportsAllSevenSections() = runTest {
        // This test verifies the HomeUiState data class has ALL fields
        // needed to render the 7 sections of the Pencil design:
        //
        // 1. Header — static "NextPage" title (no data field needed)
        // 2. Greeting — userName
        // 3. TodaySummary — minutesReadToday, sessionsToday, dailyProgressPercent
        // 4. ContinueReading — currentBook
        // 5. MyBookshelf — recentBooks
        // 6. QuickAccess — static icons (no data field needed)
        // 7. Bottom spacer — structural (no data field needed)

        val state = HomeUiState(
            userName = "TestUser",
            minutesReadToday = 42,
            sessionsToday = 3,
            dailyProgressPercent = 0.75f,
            currentBook = null,
            recentBooks = emptyList(),
            isLoading = false
        )

        // Section 2: Greeting
        assertEquals("TestUser", state.userName)

        // Section 3: TodaySummary — 3 stat cards
        assertEquals(42, state.minutesReadToday)
        assertEquals(3, state.sessionsToday)
        assertEquals(0.75f, state.dailyProgressPercent, 0.001f)

        // Section 4: ContinueReading
        assertNull(state.currentBook)

        // Section 5: MyBookshelf
        assertTrue(state.recentBooks.isEmpty())
    }

    @Test
    fun homeUiState_supportsCurrentBookForContinueReading() = runTest {
        val state = HomeUiState(
            currentBook = Book(
                id = "book-1",
                title = "Test Book",
                author = "Test Author",
                coverPath = null,
                filePath = "/path/to/book.epub",
                format = "epub",
                updatedAtEpochMillis = 1000L
            )
        )

        assertNotNull(state.currentBook)
        assertEquals("Test Book", state.currentBook!!.title)
        assertEquals("Test Author", state.currentBook!!.author)
    }

    @Test
    fun homeUiState_supportsRecentBooksForMyBookshelf() = runTest {
        val books = listOf(
            Book("b1", "Book 1", "Author 1", null, "/p1", "epub", 1000L),
            Book("b2", "Book 2", "Author 2", null, "/p2", "epub", 2000L)
        )
        val state = HomeUiState(recentBooks = books)

        assertEquals(2, state.recentBooks.size)
        assertEquals("Book 1", state.recentBooks[0].title)
        assertEquals("Book 2", state.recentBooks[1].title)
    }
}
