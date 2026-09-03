package com.nextpage.presentation.viewmodel

import android.app.Application
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.FakeReaderRepository
import com.nextpage.testutil.FakeReadingStatsRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * T1 pre-work pin (SDD reader-facade-split, design s5).
 *
 * The VM observes highlights through exactly one site — a direct
 * `flatMapLatest` over `observeHighlights(bookId)` — giving latest-wins
 * merge timing. These tests pin that guarantee BEFORE the facade split
 * touches any code, so slices T2-T6 (annotation last) cannot regress it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderHighlightsOrderingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `rapid consecutive highlight updates resolve latest-wins`() = runTest {
        val highlightsFlow = MutableStateFlow<List<Highlight>>(emptyList())
        val viewModel = createViewModel(testScheduler, highlightsFlow)
        runCurrent()

        highlightsFlow.value = listOf(highlight("h1"))
        runCurrent()
        highlightsFlow.value = listOf(highlight("h1"), highlight("h2"))
        runCurrent()

        assertEquals(listOf("h1", "h2"), viewModel.uiState.value.highlights.map { it.id })
    }

    @Test
    fun `highlight emissions never duplicate across merge paths`() = runTest {
        val highlightsFlow = MutableStateFlow<List<Highlight>>(emptyList())
        val viewModel = createViewModel(testScheduler, highlightsFlow)
        runCurrent()

        highlightsFlow.value = listOf(highlight("h1"), highlight("h2"))
        runCurrent()
        runCurrent()

        // Both the Cluster B merge and the direct flatMapLatest write into
        // uiState — the visible list must equal exactly the latest emission.
        val visible = viewModel.uiState.value.highlights
        assertEquals(listOf("h1", "h2"), visible.map { it.id })
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun highlight(id: String): Highlight = Highlight(
        id = id,
        bookId = "book-1",
        cfiRange = "cfi-$id",
        textContent = "text $id",
        note = null,
        color = "#FFEB3B",
        updatedAtEpochMillis = 0L,
        deletedAtEpochMillis = null
    )

    private fun createViewModel(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        highlightsFlow: MutableStateFlow<List<Highlight>>
    ): ReaderViewModel {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val fake = FakeReaderRepository(highlightsFlow)
        return ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = fake,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(fake),
            defaultBookId = "book-1",
            mainDispatcher = dispatcher
        )
    }
}
