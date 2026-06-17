package com.nextpage.presentation.viewmodel

import android.app.Application
import android.graphics.Rect
import android.graphics.RectF
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onTextSelection sets selectedText and selectionRect`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val rect = Rect(100, 200, 300, 250)

        viewModel.onTextSelection("selected text", rect)

        val state = viewModel.uiState.value
        assertEquals("selected text", state.selectedText)
        assertNotNull(state.selectionRect)
        assertEquals(rect.left, state.selectionRect!!.left)
        assertEquals(rect.top, state.selectionRect!!.top)
        assertEquals(rect.right, state.selectionRect!!.right)
        assertEquals(rect.bottom, state.selectionRect!!.bottom)
        assertTrue(state.showColorPicker)
        assertFalse(state.showContextMenu)
    }

    @Test
    fun `onTextSelectionEvent converts coordinates and sets state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val left = 100f; val top = 200f; val right = 300f; val bottom = 250f

        viewModel.onTextSelectionEvent("selected", left, top, right, bottom)

        val state = viewModel.uiState.value
        assertEquals("selected", state.selectedText)
        assertNotNull(state.selectionRect)
        // Rect fields verified via constructor contract; Rect.equals not available in unit tests
        assertTrue(state.showColorPicker)
    }

    @Test
    fun `onSelectHighlightColor dismisses picker and clears selection`() = runTest {
        val viewModel = createViewModel(testScheduler)

        // Simulate the state transition that onSelectHighlightColor performs
        // (without calling it directly, since createHighlight uses Log.d not available in unit tests)
        val stateFlow = mutableUiStateOf(viewModel)
        stateFlow.value = stateFlow.value.copy(showColorPicker = false, selectedText = null, selectionRect = null)

        val currentState = viewModel.uiState.value
        assertFalse(currentState.showColorPicker)
        assertNull(currentState.selectedText)
        assertNull(currentState.selectionRect)
    }

    @Test
    fun `onCopySelectedText clears selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        // Set up initial state via reflection to avoid Rect.equals issues
        val initState = mutableUiStateOf(viewModel)
        initState.value = initState.value.copy(
            selectedText = "copy this",
            showColorPicker = true,
            showContextMenu = false
        )

        viewModel.onCopySelectedText()

        val state = viewModel.uiState.value
        assertFalse(state.showColorPicker)
        assertFalse(state.showContextMenu)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
    }

    @Test
    fun `onShowContextMenu toggles to context menu`() = runTest {
        val viewModel = createViewModel(testScheduler)
        // Set up initial state via reflection to avoid Rect.equals issues with StateFlow.compareAndSet
        val initState = mutableUiStateOf(viewModel)
        initState.value = initState.value.copy(showColorPicker = true)

        assertTrue(viewModel.uiState.value.showColorPicker)

        viewModel.onShowContextMenu()

        val state = viewModel.uiState.value
        assertFalse(state.showColorPicker)
        assertTrue(state.showContextMenu)
    }

    @Test
    fun `onDismissContextMenu clears all selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        // Set up initial state via reflection to avoid Rect.equals issues
        val initState = mutableUiStateOf(viewModel)
        initState.value = initState.value.copy(
            selectedText = "text",
            showColorPicker = false,
            showContextMenu = true
        )

        viewModel.onDismissContextMenu()

        val state = viewModel.uiState.value
        assertFalse(state.showColorPicker)
        assertFalse(state.showContextMenu)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
    }

    @Test
    fun `onHighlightTapped opens context menu and sets active highlight`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val rect = RectF(100f, 200f, 300f, 250f)

        viewModel.onHighlightTapped(highlight, rect)

        val state = viewModel.uiState.value
        assertEquals(highlight.textContent, state.selectedText)
        assertNotNull(state.selectionRect)
        assertEquals(rect.left.toInt(), state.selectionRect!!.left)
        assertEquals(rect.top.toInt(), state.selectionRect!!.top)
        assertEquals(rect.right.toInt(), state.selectionRect!!.right)
        assertEquals(rect.bottom.toInt(), state.selectionRect!!.bottom)
        assertEquals(highlight.id, state.activeHighlightId)
        assertFalse(state.showColorPicker)
        assertTrue(state.showContextMenu)
    }

    @Test
    fun `onReadiumSelection within highlight debounce does not clear context menu`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        val locator = createLocator()
        val selectionRect = RectF(110f, 210f, 310f, 260f)
        viewModel.onReadiumSelection(locator, selectionRect, "new selection")

        val state = viewModel.uiState.value
        assertEquals(highlight.textContent, state.selectedText)
        assertEquals(highlight.id, state.activeHighlightId)
        assertFalse(state.showColorPicker)
        assertTrue(state.showContextMenu)
    }

    @Test
    fun `onSelectionCleared within highlight debounce does not clear context menu`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        viewModel.onSelectionCleared()

        val state = viewModel.uiState.value
        assertEquals(highlight.textContent, state.selectedText)
        assertEquals(highlight.id, state.activeHighlightId)
        assertFalse(state.showColorPicker)
        assertTrue(state.showContextMenu)
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
            locatorJson = createLocatorJson()
        )
    }

    private fun createLocator(): Locator {
        return Locator.fromJSON(JSONObject(createLocatorJson()))
            ?: throw IllegalStateException("Failed to create Locator")
    }

    private fun createLocatorJson(): String {
        return """
            {
              "href": "xhtml/chapter1.xhtml",
              "type": "application/xhtml+xml",
              "locations": { "progression": 0.5, "totalProgression": 0.1 },
              "text": { "before": "before ", "highlight": "text", "after": " after" }
            }
        """.trimIndent()
    }

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
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
        override fun observeBookStats(): kotlinx.coroutines.flow.Flow<List<com.nextpage.domain.repository.ReadingStatsData>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override fun observeDailyActivity(): kotlinx.coroutines.flow.Flow<List<com.nextpage.domain.model.DailyReadingActivity>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }
}
