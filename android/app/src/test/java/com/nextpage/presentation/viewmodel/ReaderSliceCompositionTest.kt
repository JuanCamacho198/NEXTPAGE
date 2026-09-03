package com.nextpage.presentation.viewmodel

import android.app.Application
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T1 slice-composition harness (SDD reader-facade-split).
 *
 * Each slice test asserts the VM re-export mirrors the holder-owned state.
 * T1 ships the harness plus the Search slice against the current
 * merge-collector pipeline (passes before the split); T2 re-points the
 * Search assertions at `searchUiState`, and T3-T6 fill in the remaining
 * slices (chrome, settings, sleepTimer, session, annotation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSliceCompositionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search slice mirrors holder state through VM re-export`() = runTest {
        val viewModel = createViewModel(testScheduler)

        viewModel.onToggleSearch()
        assertSearchMirror(
            viewModel,
            expectedActive = true,
            expectedQuery = "",
            message = "toggle must mirror into the VM re-export"
        )

        viewModel.onSearchQuery("odisea")
        advanceTimeBy(400)
        runCurrent()

        // Null publication: query mirrors, search settles with no results.
        assertSearchMirror(
            viewModel,
            expectedActive = true,
            expectedQuery = "odisea",
            message = "query must mirror into the VM re-export"
        )
        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    // ── Harness (per-slice mirror assertions; T3-T6 extend here) ────

    private fun assertSearchMirror(
        viewModel: ReaderViewModel,
        expectedActive: Boolean,
        expectedQuery: String,
        message: String
    ) {
        // T2 re-points this at viewModel.searchUiState; the assertion shape stays.
        val reExport = viewModel.uiState.value
        assertEquals(message, expectedActive, reExport.isSearchActive)
        assertEquals(message, expectedQuery, reExport.searchQuery)
    }

    // TODO T3: assertChromeMirror / assertSettingsMirror (slice re-exports)
    // TODO T4: assertSleepTimerMirror (chapter forwarding wiring)
    // TODO T5: assertSessionMirror (pending-CFI + progress via sessionUiState)
    // TODO T6: assertAnnotationMirror (highlights latest-wins via annotationUiState)

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createViewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): ReaderViewModel {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val fake = FakeReaderRepository()
        return ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = fake,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(fake),
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
        override suspend fun getProgressForBook(bookId: String): ReadingProgress? = null
        override suspend fun getHighlightsForBook(bookId: String): List<Highlight> = emptyList()
        override suspend fun getBookmarksForBook(bookId: String): List<Bookmark> = emptyList()
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
        override fun observeBookStats(): Flow<List<ReadingStatsData>> = MutableStateFlow(emptyList())
        override suspend fun getDailyActivity(userId: String?): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
    }
}
