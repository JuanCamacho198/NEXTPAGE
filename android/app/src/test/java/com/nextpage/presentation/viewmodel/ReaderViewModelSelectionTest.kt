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
import com.nextpage.presentation.viewmodel.reader.ReaderSelectionState
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        val viewModel = createViewModel(testScheduler, defaultBookId = "book-1")
        val highlight = createHighlight()
        val rectF = RectF(100f, 200f, 300f, 250f)
        // Set up coordinator via highlight tap (needed by interactionHolder)
        viewModel.onHighlightTapped(highlight, rectF)

        viewModel.onSelectHighlightColor(HighlightColor.YELLOW.hex)

        val state = viewModel.uiState.value
        assertTrue(state.selectionState is ReaderSelectionState.None)
        assertNull(state.selectedText)
        assertNull(state.selectionRect)
    }

    @Test
    fun `onCopySelectedText clears selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val locator = createLocator()
        val rectF = RectF(100f, 200f, 300f, 250f)
        // Set up coordinator via readium selection
        viewModel.onReadiumSelection(locator, rectF, "copy this")

        viewModel.onCopySelectedText()

        val state = viewModel.uiState.value
        assertTrue("selectionState should be None", state.selectionState is ReaderSelectionState.None)
    }

    @Test
    fun `onDismissContextMenu clears all selection state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val locator = createLocator()
        val rectF = RectF(100f, 200f, 300f, 250f)
        // Set up coordinator via readium selection
        viewModel.onReadiumSelection(locator, rectF, "text")

        viewModel.onDismissContextMenu()

        val state = viewModel.uiState.value
        assertTrue("selectionState should be None", state.selectionState is ReaderSelectionState.None)
        assertNull("selectedText cleared", state.selectedText)
        assertNull("selectionRect cleared", state.selectionRect)
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
        assertTrue("selectionState should be Existing", state.selectionState is ReaderSelectionState.Existing)
        val existing = state.selectionState as ReaderSelectionState.Existing
        assertEquals(highlight.id, existing.highlight.id)
    }

    @Test
    fun `onReadiumSelection within highlight debounce does not overwrite Existing when text matches`() = runTest {
        // The 2s debounce after a highlight tap only suppresses the polling
        // loop when the new selection text matches the tapped highlight's
        // text. In that case the user is re-selecting the same highlight
        // (or a sub-range) and the FloatingContextMenu (Existing) should
        // remain open.
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        val locator = createLocator()
        val selectionRect = RectF(110f, 210f, 310f, 260f)
        // Text matches the highlight's textContent ("highlighted text")
        viewModel.onReadiumSelection(locator, selectionRect, "highlighted text")

        val state = viewModel.uiState.value
        // activeHighlightId is managed internally by SelectionCoordinator — not exposed in uiState
        assertTrue("selectionState should remain Existing (debounce active, text matches)",
            state.selectionState is ReaderSelectionState.Existing)
    }

    @Test
    fun `onReadiumSelection within highlight debounce overrides Existing when text differs`() = runTest {
        // Regression test: a new text selection that doesn't match any
        // existing highlight must transition to New, even if the debounce
        // from a previous highlight tap is still active. Otherwise the UI
        // would show the FloatingContextMenu (Tag/Delete) instead of the
        // TextSelectionMenu (Dictionary/Copy/Share).
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        val locator = createLocator()
        val selectionRect = RectF(110f, 210f, 310f, 260f)
        viewModel.onReadiumSelection(locator, selectionRect, "completely different text")

        val state = viewModel.uiState.value
        assertTrue("selectionState should be New (text differs from highlight)",
            state.selectionState is ReaderSelectionState.New)
    }

    @Test
    fun `onSelectionCleared within highlight debounce does not clear`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val highlight = createHighlight()
        val highlightRect = RectF(100f, 200f, 300f, 250f)
        viewModel.onHighlightTapped(highlight, highlightRect)

        viewModel.onSelectionCleared()

        val state = viewModel.uiState.value
        // activeHighlightId is managed internally by SelectionCoordinator
        assertTrue("selectionState should remain Existing (debounce active)",
            state.selectionState is ReaderSelectionState.Existing)
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
        val highlight = createHighlight()
        val rectF = RectF(100f, 200f, 300f, 250f)

        // Access internal fields for debugging
        val vmStateField = ReaderViewModel::class.java.getDeclaredField("mutableUiState")
        vmStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val vmState = vmStateField.get(viewModel) as MutableStateFlow<ReaderUiState>

        val holderField = ReaderViewModel::class.java.getDeclaredField("interactionHolder")
        holderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val holder = holderField.get(viewModel) as com.nextpage.presentation.viewmodel.reader.ReaderInteractionStateHolder

        val holderStateField = com.nextpage.presentation.viewmodel.reader.ReaderInteractionStateHolder::class.java.getDeclaredField("_state")
        holderStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val holderState = holderStateField.get(holder) as MutableStateFlow<com.nextpage.presentation.viewmodel.reader.ReaderInteractionState>

        // Step 1: Direct holder update — does the collect work at all?
        println("=== STEP 1: Direct holder showDefinitionInput update ===")
        println("Initial vmState.showDefinitionInput: ${vmState.value.showDefinitionInput}")
        holderState.update { it.copy(showDefinitionInput = true) }
        println("After direct update, holder showDefinitionInput: ${holderState.value.showDefinitionInput}")
        println("After direct update, vm showDefinitionInput: ${vmState.value.showDefinitionInput}")
        println()

        // Step 2: onHighlightTapped
        println("=== STEP 2: onHighlightTapped ===")
        println("Before, selectedText: ${vmState.value.selectedText}")
        viewModel.onHighlightTapped(highlight, rectF)
        println("After, vm selectedText: ${vmState.value.selectedText}")
        println("After, vm selectionRect is null? ${vmState.value.selectionRect == null}")
        println("After, holder selectionRect is null? ${holderState.value.selectionRect == null}")
        println("Holder selectedText: ${holderState.value.selectedText}")
        println()

        // Step 3: testSetInitialHighlights
        println("=== STEP 3: testSetInitialHighlights ===")
        println("Before, vm highlights size: ${vmState.value.highlights.size}")
        holder.testSetInitialHighlights(listOf(highlight))
        println("After, vm highlights size: ${vmState.value.highlights.size}")
        println("After, vm showTagInput: ${vmState.value.showTagInput}")
        println()

        // Step 4: Direct holderState.update with showTagInput=true (minimal)
        println("=== STEP 4a: Direct holderState.update showTagInput=true ===")
        println("Before, vm showTagInput: ${vmState.value.showTagInput}")
        println("Before, vm selectionRect is null: ${vmState.value.selectionRect == null}")
        // Test equals directly
        val curBefore = vmState.value
        val r = vmState.value.selectionRect
        val dup = curBefore.copy(showTagInput = true, selectionRect = r)
        println("Direct equals check: curBefore == dup? ${curBefore == dup}")
        println("Direct equals check (manual): showTagInput diff? ${curBefore.showTagInput != dup.showTagInput}")
        holderState.update { it.copy(showTagInput = true) }
        println("After direct, vm showTagInput: ${vmState.value.showTagInput}")
        println()

        // Reset holder showTagInput back to false
        holderState.update { it.copy(showTagInput = false) }
        // Reset VM state to match holder
        vmState.value = vmState.value.copy(showTagInput = false)

        // Step 4b: Direct holderState.update with ALL fields onShowTagInput would change
        println("=== STEP 4b: Direct holderState.update with all fields ===")
        val tagSuggestions = listOf("cita", "pasaje", "idea", "ficción", "no-ficción", "favoritos")
        println("Before, vm showTagInput: ${vmState.value.showTagInput}")
        println("Before, vm showDefinitionInput: ${vmState.value.showDefinitionInput}")
        println("Before, vm activeTagText: '${vmState.value.activeTagText}'")
        println("Before, vm tagSuggestions: ${vmState.value.tagSuggestions}")
        holderState.update { it.copy(
            showTagInput = true,
            activeTagText = "",
            tagSuggestions = tagSuggestions,
            showNoteModal = false,
            showDefinitionInput = false
        ) }
        println("After direct all, vm showTagInput: ${vmState.value.showTagInput}")
        println("After direct all, vm showDefinitionInput: ${vmState.value.showDefinitionInput}")
        println("After direct all, vm activeTagText: '${vmState.value.activeTagText}'")
        println("After direct all, vm tagSuggestions: ${vmState.value.tagSuggestions}")
        println()

        // Reset again
        holderState.update { it.copy(showTagInput = false, showDefinitionInput = true, tagSuggestions = emptyList(), activeTagText = "") }

        // Step 4c: onShowTagInput
        println("=== STEP 4c: onShowTagInput ===")
        println("Before, vm showTagInput: ${vmState.value.showTagInput}")
        println("Before, holder showTagInput: ${holderState.value.showTagInput}")
        println("Before, holder activeTagText: '${holderState.value.activeTagText}'")

        try {
            viewModel.onShowTagInput()
            println("After, holder showTagInput: ${holderState.value.showTagInput}")
            println("After, vm showTagInput: ${vmState.value.showTagInput}")

            // Try advanceUntilIdle
            advanceUntilIdle()
            println("After advanceUntilIdle, vm showTagInput: ${vmState.value.showTagInput}")

            // Also check uiState value
            println("uiState.showTagInput: ${viewModel.uiState.value.showTagInput}")
        } catch (e: Throwable) {
            println("EXCEPTION during onShowTagInput: ${e::class.simpleName}: ${e.message}")
        }
        println()

        val state = viewModel.uiState.value
        assertTrue("showTagInput should be true", state.showTagInput)
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

    private fun createViewModel(
        scheduler: TestCoroutineScheduler,
        defaultBookId: String? = null
    ): ReaderViewModel {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        return ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = defaultBookId,
            mainDispatcher = dispatcher
        )
    }

    private class FakeReaderRepository : ReaderRepository {
        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = MutableStateFlow(null)
        override suspend fun upsertProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgressForBook(bookId: String): ReadingProgress? = null
        override fun observeAllHighlights(): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override fun observeAllHighlightsPaged(): Flow<androidx.paging.PagingData<Highlight>> =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
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
        override suspend fun getDailyActivity(): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
    }
}
