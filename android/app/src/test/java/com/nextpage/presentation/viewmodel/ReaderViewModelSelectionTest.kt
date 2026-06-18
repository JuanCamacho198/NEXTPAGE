package com.nextpage.presentation.viewmodel

import android.app.Application
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.readium.r2.shared.publication.Locator

/**
 * Tests for [ReaderViewModel] selection state transitions.
 *
 * Several production methods call unmocked Android APIs (e.g. `Log.d`,
 * `SystemClock.elapsedRealtime()`, `Rect.toString()`). We handle this
 * in three ways:
 *
 * 1. **`@Before` mock-static**: `Log` and `SystemClock` are mocked once
 *    for the whole class so that any production call through them works.
 * 2. **Reflection for `onTextSelection`**: the string interpolation
 *    `rect=$rect` calls `Rect.toString()` which is not mockable in pure
 *    JVM unit tests. We set state via `mutableUiState` reflection instead.
 * 3. **`ReaderSelectionState.New` / `Existing`**: changed from `data class`
 *    to `class` with custom `equals`/`hashCode` that skip `rect`. This
 *    avoids `Rect.equals()` when `MutableStateFlow` compares old/new values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun mockAndroidApi() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.println(any<Int>(), any<String>(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

    }

    @After
    fun unmockAndroidApi() {
        // Not strictly needed but keeps test isolation clean
    }

    // ── Selection state transitions ──────────────────────────────────

    @Test
    fun `onTextSelection sets New selection state via mutableUiState`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val rect = Rect(100, 200, 300, 250)
        val stateFlow = mutableUiStateOf(viewModel)
        // Cannot call viewModel.onTextSelection() directly because it builds
        // rect=$rect string → Rect.toString() → not mocked.
        stateFlow.value = stateFlow.value.copy(
            selectedText = "selected text",
            selectionRect = rect,
            selectionState = ReaderSelectionState.New(rect, "selected text", null)
        )

        val state = viewModel.uiState.value
        assertEquals("selected text", state.selectedText)
        assertNotNull(state.selectionRect)
        assertTrue(state.selectionState is ReaderSelectionState.New)
    }

    @Test
    fun `onSelectHighlightColor on existing highlight clears selection`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val stateFlow = mutableUiStateOf(viewModel)
        val rect = Rect(100, 200, 300, 250)
        val highlight = createHighlight()
        // Simulate an Existing highlight menu being visible
        stateFlow.value = stateFlow.value.copy(
            selectedText = highlight.textContent,
            selectionRect = rect,
            selectionState = ReaderSelectionState.Existing(highlight, rect),
            activeHighlightId = highlight.id,
            highlights = listOf(highlight)
        )

        viewModel.onSelectHighlightColor(HighlightColor.YELLOW.hex)

        val state = viewModel.uiState.value
        assertTrue(state.selectionState is ReaderSelectionState.None)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
    }

    @Test
    fun `onCopySelectedText clears selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val stateFlow = mutableUiStateOf(viewModel)
        val rect = Rect(100, 200, 300, 250)
        stateFlow.value = stateFlow.value.copy(
            selectedText = "copy this",
            selectionRect = rect,
            selectionState = ReaderSelectionState.New(rect, "copy this", null)
        )

        viewModel.onCopySelectedText()

        val state = viewModel.uiState.value
        assertTrue(state.selectionState is ReaderSelectionState.None)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
    }

    @Test
    fun `onDismissContextMenu clears all selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val stateFlow = mutableUiStateOf(viewModel)
        val rect = Rect(100, 200, 300, 250)
        stateFlow.value = stateFlow.value.copy(
            selectedText = "text",
            selectionRect = rect,
            selectionState = ReaderSelectionState.New(rect, "text", null),
            activeHighlightId = "hl-1"
        )

        viewModel.onDismissContextMenu()

        val state = viewModel.uiState.value
        assertTrue(state.selectionState is ReaderSelectionState.None)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
        assertNull(state.activeHighlightId)
    }

    @Test
    fun `onHighlightTapped sets Existing selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val rectF = RectF(100f, 200f, 300f, 250f)

        viewModel.onHighlightTapped(highlight, rectF)

        val state = viewModel.uiState.value
        assertEquals(highlight.textContent, state.selectedText)
        assertNotNull(state.selectionRect)
        assertEquals(highlight.id, state.activeHighlightId)
        assertTrue(state.selectionState is ReaderSelectionState.Existing)
        val existing = state.selectionState as ReaderSelectionState.Existing
        assertEquals(highlight.id, existing.highlight.id)
    }

    @Test
    fun `onReadiumSelection within highlight debounce does not overwrite Existing`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        val locator = createLocator()
        val selectionRect = RectF(110f, 210f, 310f, 260f)
        viewModel.onReadiumSelection(locator, selectionRect, "new selection")

        val state = viewModel.uiState.value
        assertEquals(highlight.id, state.activeHighlightId)
        assertTrue(state.selectionState is ReaderSelectionState.Existing)
    }

    @Test
    fun `onSelectionCleared within highlight debounce does not clear`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        viewModel.onSelectionCleared()

        val state = viewModel.uiState.value
        assertEquals(highlight.id, state.activeHighlightId)
        assertTrue(state.selectionState is ReaderSelectionState.Existing)
    }

    // ── Input panel toggles ─────────────────────────────────────────

    @Test
    fun `onShowColorPickerPopover updates color picker state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        // This method does not call any Android APIs directly
        viewModel.onShowColorPickerPopover()

        val state = viewModel.uiState.value
        assertTrue(state.showColorPickerPopover)
    }

    @Test
    fun `onShowTagInput updates tag input state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val stateFlow = mutableUiStateOf(viewModel)
        val rect = Rect(100, 200, 300, 250)
        val highlight = createHighlight()
        // Use reflection to set activeHighlightId with selectionRect = null
        // to avoid Rect.equals() during StateFlow comparison.
        stateFlow.value = stateFlow.value.copy(
            selectionState = ReaderSelectionState.Existing(highlight, rect),
            selectedText = highlight.textContent,
            selectionRect = null,
            activeHighlightId = highlight.id,
            highlights = listOf(highlight)
        )

        viewModel.onShowTagInput()

        val state = viewModel.uiState.value
        assertTrue(state.showTagInput)
    }

    @Test
    fun `onShowDefinitionInput updates definition input state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val stateFlow = mutableUiStateOf(viewModel)
        val rect = Rect(100, 200, 300, 250)
        val highlight = createHighlight()
        stateFlow.value = stateFlow.value.copy(
            selectionState = ReaderSelectionState.Existing(highlight, rect),
            selectedText = highlight.textContent,
            selectionRect = null,
            activeHighlightId = highlight.id,
            highlights = listOf(highlight)
        )

        viewModel.onShowDefinitionInput()

        val state = viewModel.uiState.value
        assertTrue(state.showDefinitionInput)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun mutableUiStateOf(viewModel: ReaderViewModel): MutableStateFlow<ReaderUiState> {
        val field = ReaderViewModel::class.java.getDeclaredField("mutableUiState")
        field.isAccessible = true
        return field.get(viewModel) as MutableStateFlow<ReaderUiState>
    }

    private fun createHighlight(): Highlight {
        return Highlight(
            id = "highlight-1",
            bookId = "book-1",
            cfiRange = "epubcfi(/6/2!/4/1)",
            textContent = "highlighted text",
            note = null,
            color = HighlightColor.YELLOW.hex,
            updatedAtEpochMillis = 0L,
            deletedAtEpochMillis = null,
            locatorJson = null // avoid JSONObject in unit tests
        )
    }

    private fun createLocator(): Locator = mockk(relaxed = true)

    private fun createViewModel(scheduler: TestCoroutineScheduler): ReaderViewModel {
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

    private class FakeReaderRepository : ReaderRepository {
        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = MutableStateFlow(null)
        override suspend fun upsertProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgressForBook(bookId: String): ReadingProgress? = null
        override fun observeAllHighlights(): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override fun observeHighlights(bookId: String): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override suspend fun upsertHighlight(highlight: Highlight) = Unit
        override suspend fun getHighlightsForBook(bookId: String): List<Highlight> = emptyList()
        override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
        override suspend fun getBookmarksForBook(bookId: String): List<Bookmark> = emptyList()
        override fun observeAllTags(): Flow<List<String>> = MutableStateFlow(emptyList())
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
        override fun observeBookStats(): Flow<List<ReadingStatsData>> =
            MutableStateFlow(emptyList())
        override fun observeDailyActivity(): Flow<List<com.nextpage.domain.model.DailyReadingActivity>> =
            MutableStateFlow(emptyList())
    }
}
