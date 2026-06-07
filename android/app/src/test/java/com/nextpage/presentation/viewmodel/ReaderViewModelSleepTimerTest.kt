package com.nextpage.presentation.viewmodel

import com.nextpage.data.epub.EpubContentLoader
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSleepTimerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startSleepTimer with MIN_VALUE activates end-of-chapter mode`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapter = EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        setEpubState(viewModel, chapters = listOf(chapter, chapter, chapter))

        viewModel.startSleepTimer(Int.MIN_VALUE)

        val state = viewModel.uiState.value
        assertTrue("End-of-chapter mode should be active", state.sleepTimerEndOfChapterMode)
        assertTrue("Sleep timer should be active", state.sleepTimerActive)
        assertEquals("No countdown in end-of-chapter mode", 0, state.sleepTimerRemainingSecs)
    }

    @Test
    fun `goToNextChapter triggers end-of-chapter and finishes timer`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapter = EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml"),
            EpubContentLoader.Chapter("chap-2", "Chapter 2", "chapter2.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 0)

        // Start end-of-chapter timer
        viewModel.startSleepTimer(Int.MIN_VALUE)
        assertTrue("Timer should be active before chapter change", viewModel.uiState.value.sleepTimerActive)

        // Navigate to next chapter
        viewModel.goToNextChapter()

        val state = viewModel.uiState.value
        assertFalse("Timer should be inactive after chapter change", state.sleepTimerActive)
        assertTrue("Finished overlay should show", state.sleepTimerFinished)
        assertFalse("End-of-chapter mode should be reset", state.sleepTimerEndOfChapterMode)
        assertEquals("Chapter should have advanced", 1, state.currentChapterIndex)
    }

    @Test
    fun `goToPreviousChapter triggers end-of-chapter and finishes timer`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml"),
            EpubContentLoader.Chapter("chap-2", "Chapter 2", "chapter2.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 1)

        viewModel.startSleepTimer(Int.MIN_VALUE)
        assertTrue("Timer should be active", viewModel.uiState.value.sleepTimerActive)

        viewModel.goToPreviousChapter()

        val state = viewModel.uiState.value
        assertFalse("Timer should be inactive after chapter change", state.sleepTimerActive)
        assertTrue("Finished overlay should show", state.sleepTimerFinished)
        assertEquals("Chapter should have gone back", 0, state.currentChapterIndex)
    }

    @Test
    fun `goToChapter with different index triggers end-of-chapter`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml"),
            EpubContentLoader.Chapter("chap-2", "Chapter 2", "chapter2.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 0)

        viewModel.startSleepTimer(Int.MIN_VALUE)

        // Go to specific chapter (index 2)
        viewModel.goToChapter(2)

        val state = viewModel.uiState.value
        assertFalse("Timer should be inactive", state.sleepTimerActive)
        assertTrue("Finished overlay should show", state.sleepTimerFinished)
        assertEquals("Should be on chapter 2", 2, state.currentChapterIndex)
    }

    @Test
    fun `goToChapter with same index does NOT trigger end-of-chapter`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 0)

        viewModel.startSleepTimer(Int.MIN_VALUE)
        assertTrue("Timer should be active", viewModel.uiState.value.sleepTimerActive)

        // Same chapter - should NOT trigger
        viewModel.goToChapter(0)

        val state = viewModel.uiState.value
        assertTrue("Timer should still be active when staying on same chapter", state.sleepTimerActive)
        assertFalse("Finished overlay should NOT show", state.sleepTimerFinished)
    }

    @Test
    fun `onTapZone with left zone triggers previous chapter and ends timer`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 1)

        viewModel.startSleepTimer(Int.MIN_VALUE)
        viewModel.onTapZone(isLeftZone = true)

        val state = viewModel.uiState.value
        assertFalse("Timer should be inactive after tap zone", state.sleepTimerActive)
        assertTrue("Finished overlay should show", state.sleepTimerFinished)
    }

    @Test
    fun `cancelSleepTimer resets all timer state including end-of-chapter mode`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapter = EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        setEpubState(viewModel, chapters = listOf(chapter, chapter))

        viewModel.startSleepTimer(Int.MIN_VALUE)
        assertTrue("Timer should be active", viewModel.uiState.value.sleepTimerActive)

        viewModel.cancelSleepTimer()

        val state = viewModel.uiState.value
        assertFalse("Timer should be cancelled", state.sleepTimerActive)
        assertFalse("End-of-chapter mode should be reset", state.sleepTimerEndOfChapterMode)
        assertFalse("Finished overlay should not show", state.sleepTimerFinished)
        assertEquals("Remaining should be 0", 0, state.sleepTimerRemainingSecs)
    }

    @Test
    fun `dismissSleepTimerOverlay clears finished flag without affecting other state`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapters = listOf(
            EpubContentLoader.Chapter("chap-0", "Intro", "intro.xhtml"),
            EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        )
        setEpubState(viewModel, chapters = chapters, currentChapterIndex = 0)

        viewModel.startSleepTimer(Int.MIN_VALUE)
        viewModel.goToNextChapter()
        assertTrue("Finished should be set after chapter change", viewModel.uiState.value.sleepTimerFinished)

        viewModel.dismissSleepTimerOverlay()

        assertFalse("Finished should be cleared after dismiss", viewModel.uiState.value.sleepTimerFinished)
    }

    @Test
    fun `formatSleepTimerRemaining formats correctly`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )

        assertEquals("0:00", viewModel.formatSleepTimerRemaining(0))
        assertEquals("0:05", viewModel.formatSleepTimerRemaining(5))
        assertEquals("1:00", viewModel.formatSleepTimerRemaining(60))
        assertEquals("5:30", viewModel.formatSleepTimerRemaining(330))
        assertEquals("10:00", viewModel.formatSleepTimerRemaining(600))
    }

    @Test
    fun `startSleepTimer with normal minutes starts countdown`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            mainDispatcher = dispatcher
        )
        val chapter = EpubContentLoader.Chapter("chap-1", "Chapter 1", "chapter1.xhtml")
        setEpubState(viewModel, chapters = listOf(chapter, chapter))

        viewModel.startSleepTimer(5)

        val state = viewModel.uiState.value
        assertTrue("Timer should be active", state.sleepTimerActive)
        assertFalse("Not end-of-chapter mode", state.sleepTimerEndOfChapterMode)
        assertEquals("Should be 5 min", 5 * 60, state.sleepTimerRemainingSecs)
        assertEquals("Preset should be 5", 5, state.sleepTimerPresetMinutes)
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun setEpubState(
        viewModel: ReaderViewModel,
        chapters: List<EpubContentLoader.Chapter>,
        currentChapterIndex: Int = 0
    ) {
        val field = ReaderViewModel::class.java.getDeclaredField("mutableUiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(viewModel) as MutableStateFlow<ReaderUiState>
        state.value = state.value.copy(
            bookFormat = "epub",
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            isLoading = false
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
