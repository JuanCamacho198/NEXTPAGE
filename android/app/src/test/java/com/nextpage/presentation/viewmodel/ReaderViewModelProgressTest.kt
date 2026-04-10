package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelProgressTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateProgress_writesLocalProgressThroughRepository() = runTest {
        val repository = FakeReaderRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = "book-10",
            mainDispatcher = dispatcher
        )

        viewModel.updateProgress(
            bookId = "book-10",
            cfiLocation = "epubcfi(/6/2[chapter-1]!/4/1:0)",
            percentage = 25f
        )

        val saved = repository.lastUpserted
        assertNotNull(saved)
        assertEquals("progress-book-10", saved?.id)
        assertEquals("book-10", saved?.bookId)
        assertEquals("epubcfi(/6/2[chapter-1]!/4/1:0)", saved?.cfiLocation)
        assertEquals(25f, saved?.percentage)
        assertTrue((saved?.updatedAtEpochMillis ?: 0L) > 0L)
    }

    @Test
    fun goToPage_validPageNavigatesAndUpdatesProgress() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeReaderRepository()
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        setPdfState(viewModel, selectedBookId = "book-42", totalPdfPages = 10)
        viewModel.goToPage(3)
        advanceUntilIdle()

        val saved = repository.lastUpserted
        assertNotNull(saved)
        assertEquals("book-42", saved?.bookId)
        assertEquals("pdfpage:2", saved?.cfiLocation)
        assertEquals(30f, saved?.percentage ?: 0f, 0.001f)

        val uiState = viewModel.uiState.value
        assertEquals(10, uiState.totalPdfPages)
        assertEquals(2, uiState.currentPdfPage)
        val counterLabel = "Page ${3} of ${uiState.totalPdfPages}"
        assertEquals("Page 3 of 10", counterLabel)
    }

    @Test
    fun goToPdfPage_updatesCurrentPageAndProgress() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeReaderRepository()
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        setPdfState(viewModel, selectedBookId = "book-42", totalPdfPages = 10)
        viewModel.goToPdfPage(6)
        advanceUntilIdle()

        assertEquals(6, viewModel.uiState.value.currentPdfPage)
        assertEquals("pdfpage:6", repository.lastUpserted?.cfiLocation)
    }

    @Test
    fun nextAndPreviousPdfNavigation_keepsCurrentPageConsistent() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeReaderRepository()
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        setPdfState(
            viewModel,
            selectedBookId = "book-42",
            totalPdfPages = 10,
            currentPdfPage = 4
        )

        viewModel.goToNextPdfPage()
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.currentPdfPage)

        viewModel.goToPreviousPdfPage()
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.currentPdfPage)
        assertEquals("pdfpage:4", repository.lastUpserted?.cfiLocation)
    }

    @Test
    fun goToPage_invalidPageIgnored() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeReaderRepository()
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        setPdfState(viewModel, selectedBookId = "book-42", totalPdfPages = 10)
        viewModel.goToPage(0)
        viewModel.goToPage(11)
        advanceUntilIdle()

        assertEquals(null, repository.lastUpserted)
    }

    private fun setPdfState(
        viewModel: ReaderViewModel,
        selectedBookId: String,
        totalPdfPages: Int,
        currentPdfPage: Int = 0
    ) {
        val field = ReaderViewModel::class.java.getDeclaredField("mutableUiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(viewModel) as MutableStateFlow<ReaderUiState>
        state.value = state.value.copy(
            selectedBookId = selectedBookId,
            bookFormat = "pdf",
            currentPdfPage = currentPdfPage,
            totalPdfPages = totalPdfPages
        )
    }

    private class FakeReaderRepository : ReaderRepository {
        private val progressFlow = MutableStateFlow<ReadingProgress?>(null)
        var lastUpserted: ReadingProgress? = null

        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = progressFlow

        override suspend fun upsertProgress(progress: ReadingProgress) {
            lastUpserted = progress
            progressFlow.value = progress
        }

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
