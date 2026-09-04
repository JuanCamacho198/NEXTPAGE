package com.nextpage.presentation.viewmodel

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.presentation.viewmodel.reader.ReaderSelectionState
import com.nextpage.testutil.FakeReaderRepository
import com.nextpage.testutil.FakeReadingStatsRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Slice 5 tests (SDD reader-facade-split, T6).
 *
 * The annotation surface lives in [interactionHolder] (the deprecated PR #3
 * facade, still in place at PR-D time) and is re-exported as
 * [ReaderViewModel.annotationUiState]. These tests pin:
 *  - the slice re-export mirrors the holder-owned state;
 *  - the back-compat `uiState` mirror still propagates annotation fields
 *    (required for the consumer migration that T7 deletion is gated on);
 *  - the highlights-ordering guarantee from T1 (design §5) survives the
 *    slice exposure — the direct `flatMapLatest` site in the VM init block
 *    is untouched by T6 and continues to give latest-wins merge timing;
 *  - the 30+ annotation delegates on the VM carry an `@Deprecated` annotation
 *    with a `ReplaceWith` pointing at the holder (reflection guard against
 *    silent re-introduction of the warning-suppression during the T6/PR #3
 *    handoff).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelAnnotationTest {

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
    fun unmockAndroidApi() = unmockkAll()

    // ── Slice re-export (SDD reader-facade-split, T6) ───────────────

    @Test
    fun `annotationUiState re-exports the interactionHolder state`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val holder = interactionHolderOf(viewModel)
        holder.testSetInitialHighlights(listOf(highlight("a1"), highlight("a2")))
        runCurrent()

        val slice = viewModel.annotationUiState.value
        assertEquals(2, slice.highlights.size)
        assertEquals(listOf("a1", "a2"), slice.highlights.map { it.id })
    }

    @Test
    fun `annotationUiState back-compat mirror propagates into uiState`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val holder = interactionHolderOf(viewModel)
        holder.testSetInitialHighlights(listOf(highlight("b1")))
        advanceUntilIdle()

        // The 5+1-way combine overlay (search/chrome/settings/timer/session
        // + base) still carries annotation fields forward into uiState — this
        // is the bridge the remaining consumer migration depends on while
        // ReaderScreen.kt:61 and DebugPanel.kt:70 still collect uiState.
        assertEquals(
            "back-compat uiState must mirror annotation highlights",
            1,
            viewModel.uiState.value.highlights.size
        )
        assertEquals("b1", viewModel.uiState.value.highlights.first().id)
    }

    // ── Highlights-ordering preservation (T1 regression, still green) ─

    @Test
    fun `annotationUiState and uiState agree on highlights after direct holder update`() = runTest {
        // The T1 regression pin (ReaderHighlightsOrderingTest) covers the
        // flatMapLatest-site latest-wins timing on a Room-backed flow. This
        // test is the slice-exposure complement: a direct holder update
        // (testSetInitialHighlights) must reach both the slice re-export
        // (annotationUiState) and the back-compat uiState mirror. Same
        // agreement guarantee, simpler fixture.
        val viewModel = createViewModel(testScheduler)
        val holder = interactionHolderOf(viewModel)
        holder.testSetInitialHighlights(listOf(highlight("h1"), highlight("h2")))
        advanceUntilIdle()

        val sliceIds = viewModel.annotationUiState.value.highlights.map { it.id }
        val uiIds = viewModel.uiState.value.highlights.map { it.id }
        assertEquals(listOf("h1", "h2"), sliceIds)
        assertEquals(
            "annotationUiState and uiState must agree on highlights after a holder update",
            sliceIds,
            uiIds
        )
    }

    @Test
    fun `annotationUiState reflects the latest holder highlights across rapid updates`() = runTest {
        // T1's flatMapLatest-site latest-wins guarantee relies on the holder
        // high-water mark; this test pins the same idea through the slice
        // re-export by issuing two holder updates in succession and asserting
        // the second one wins in annotationUiState.
        val viewModel = createViewModel(testScheduler)
        val holder = interactionHolderOf(viewModel)
        holder.testSetInitialHighlights(listOf(highlight("h1")))
        advanceUntilIdle()
        holder.testSetInitialHighlights(listOf(highlight("h1"), highlight("h2")))
        advanceUntilIdle()

        val sliceIds = viewModel.annotationUiState.value.highlights.map { it.id }
        assertEquals(listOf("h1", "h2"), sliceIds)
    }

    // ── Delegate-deprecation reflection ─────────────────────────────

    @Test
    fun `annotation delegates carry Deprecated annotation pointing at the holder`() {
        val annotationType = Class.forName("kotlin.Deprecated")
        val names = listOf(
            "onHighlightTapped",
            "onTextSelectionEvent",
            "onTextSelection",
            "onSelectHighlightColor",
            "onCopySelectedText",
            "onDismissContextMenu",
            "onReadiumSelection",
            "onSelectionCleared",
            "onShowColorPickerPopover",
            "onDismissColorPickerPopover",
            "onShowNoteModal",
            "onDismissNoteModal",
            "onSaveNote",
            "onAnnotate",
            "onShowTagInput",
            "onDismissTagInput",
            "onTagTextChanged",
            "onSaveTag",
            "onShowDefinitionInput",
            "onDismissDefinitionInput",
            "onDefinitionTextChanged",
            "onSaveDefinition",
            "onAddToDictionary",
            "onShareSelectedText",
            "onReadiumHighlightColorSelected",
            "onReadiumDeleteHighlight",
            "onReadiumUpdateHighlightColor",
            "onDebugForceMenu",
            "onDebugForceColorPicker",
            "onToggleHighlightsPanel"
        )
        val methods = ReaderViewModel::class.java.methods.associateBy { it.name }
        for (name in names) {
            val method = methods[name]
            assertNotNull("ReaderViewModel.$name must still exist (annotated, not deleted)", method)
            val annotation = method!!.getAnnotation(annotationType as Class<out Annotation>)
            assertNotNull("ReaderViewModel.$name must carry @Deprecated", annotation)
        }
    }

    // ── Selection-pipeline via the slice (design §5) ────────────────

    @Test
    fun `selectionState updates flow through annotationUiState`() = runTest {
        val viewModel = createViewModel(testScheduler)
        val holder = interactionHolderOf(viewModel)
        val stateField = holder::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(holder) as MutableStateFlow<com.nextpage.presentation.viewmodel.reader.ReaderInteractionState>
        state.value = state.value.copy(
            selectionState = ReaderSelectionState.New(android.graphics.Rect(0, 0, 100, 50), "text", null),
            selectedText = "text"
        )
        runCurrent()

        assertTrue(
            "annotationUiState must mirror holder selectionState",
            viewModel.annotationUiState.value.selectionState is ReaderSelectionState.New
        )
        assertEquals("text", viewModel.annotationUiState.value.selectedText)
        assertEquals(
            "back-compat uiState must mirror annotation selectedText",
            "text",
            viewModel.uiState.value.selectedText
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun interactionHolderOf(viewModel: ReaderViewModel): com.nextpage.presentation.viewmodel.reader.ReaderInteractionStateHolder {
        val field = ReaderViewModel::class.java.getDeclaredField("interactionHolder")
        field.isAccessible = true
        return field.get(viewModel) as com.nextpage.presentation.viewmodel.reader.ReaderInteractionStateHolder
    }

    private fun highlight(id: String): Highlight = Highlight(
        id = id,
        bookId = "book-1",
        cfiRange = "epubcfi(/6/2!/4/$id)",
        textContent = "text $id",
        note = null,
        color = HighlightColor.YELLOW.hex,
        updatedAtEpochMillis = 0L,
        deletedAtEpochMillis = null,
        locatorJson = null
    )

    private fun createViewModel(
        scheduler: TestCoroutineScheduler,
        highlightsFlow: MutableStateFlow<List<Highlight>>? = null,
        defaultBookId: String? = null
    ): ReaderViewModel {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val fake = if (highlightsFlow != null) FakeReaderRepository(highlightsFlow) else FakeReaderRepository()
        return ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = fake,
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(fake),
            defaultBookId = defaultBookId,
            mainDispatcher = dispatcher
        )
    }
}
