package com.nextpage.presentation.viewmodel

import android.app.Application
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.FakeReaderRepository
import com.nextpage.testutil.FakeReadingStatsRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * T2 points the Search assertions at `searchUiState` (slice 1 shipped);
 * T3-T6 fill in the remaining slices (chrome, settings, sleepTimer,
 * session, annotation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSliceCompositionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search slice mirrors holder state through VM re-export`() = runTest {
        val viewModel = createViewModel(testScheduler)

        viewModel.searchStateHolder.onToggleSearch()
        assertSearchMirror(
            viewModel,
            expectedActive = true,
            expectedQuery = "",
            message = "toggle must mirror into the VM re-export"
        )

        viewModel.searchStateHolder.onSearchQuery("odisea", null, null)
        advanceTimeBy(400)
        runCurrent()

        // Null publication: query mirrors, search settles with no results.
        assertSearchMirror(
            viewModel,
            expectedActive = true,
            expectedQuery = "odisea",
            message = "query must mirror into the VM re-export"
        )
        assertFalse(viewModel.searchUiState.value.isSearching)
        assertTrue(viewModel.searchUiState.value.searchResults.isEmpty())
    }

    // ── Harness (per-slice mirror assertions; T3-T6 extend here) ────

    private fun assertSearchMirror(
        viewModel: ReaderViewModel,
        expectedActive: Boolean,
        expectedQuery: String,
        message: String
    ) {
        val reExport = viewModel.searchUiState.value
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
}
