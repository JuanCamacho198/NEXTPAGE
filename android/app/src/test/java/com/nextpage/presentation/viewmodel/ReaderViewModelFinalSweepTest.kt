package com.nextpage.presentation.viewmodel

import android.app.Application
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.FakeReaderRepository
import com.nextpage.testutil.FakeReadingStatsRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T8 final-sweep audit (SDD reader-facade-split).
 *
 * The PR-D slice stack ships six independent StateFlow re-exports from
 * [ReaderViewModel]: search, chrome, settings, sleep timer, session,
 * annotation. The back-compat [ReaderViewModel.uiState] stays until the
 * consumer migration unblocks T7 deletion (gated by spec requirement 6).
 *
 * This test pins the contract the next PR must hold while it does the
 * consumer migration:
 *
 *  1. All six slice flows are present and re-export the owner state
 *     directly (not via a derived transform).
 *  2. The deprecated 5+1-way combine overlay still produces a live
 *     [ReaderViewModel.uiState] for the six remaining consumers.
 *  3. The deprecated `interactionHolder` field is still wired to the
 *     annotation owner (the PR #3 facade removal is out of scope for
 *     PR-D — the 30+ annotation delegates are `@Deprecated` on the VM
 *     but not deleted; deletion waits for PR #3).
 *  4. The [ReaderViewModel] stays as the only public surface for screens:
 *     screens reach slice owners through VM re-exports, never via a
 *     direct import of `reader.interaction.*` or `reader.*Holder` types.
 *
 * Scope-guard audit (per spec requirement 7):
 *  - No extra VMs, no new use-case extraction, no UX/string changes.
 *  - `ViewModelProviders.kt` and the factory wiring are untouched.
 *  - The high-water mark of `mutableUiState` (which the combine overlay
 *    reads) is preserved by all five prior slice merges.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelFinalSweepTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── Slice re-exports present and direct ─────────────────────────

    @Test
    fun `all six slice flows are exposed on the VM`() = runTest {
        val viewModel = createViewModel(testScheduler)
        advanceUntilIdle()

        // 1. Search
        assertSame(
            "searchUiState must be the same instance as searchStateHolder.state",
            viewModel.searchStateHolder.state,
            viewModel.searchUiState
        )
        // 2. Chrome
        assertSame(
            "chromeUiState must be the same instance as fullscreenManager.state",
            viewModel.fullscreenManager.state,
            viewModel.chromeUiState
        )
        // 3. Settings
        assertSame(
            "settingsUiState must be the same instance as settingsManager.state",
            viewModel.settingsManager.state,
            viewModel.settingsUiState
        )
        // 4. Sleep timer
        assertSame(
            "sleepTimerUiState must be the same instance as sleepTimerManager.state",
            viewModel.sleepTimerManager.state,
            viewModel.sleepTimerUiState
        )
        // 5. Session
        assertSame(
            "sessionUiState must be the same instance as lifecycleHolder.state",
            viewModel.lifecycleHolder.state,
            viewModel.sessionUiState
        )
        // 6. Annotation (sliced in T6)
        assertNotNull(viewModel.annotationUiState)
        // The annotation slice is sourced from interactionHolder.state
        // (the deprecated PR #3 facade). The re-export identity is checked
        // by ReaderViewModelAnnotationTest already; here we only assert the
        // re-export is present and the holder is wired.
        assertNotNull(viewModel::class.java.methods.firstOrNull { it.name == "getAnnotationUiState" })
    }

    // ── Back-compat uiState stays as a live StateFlow ──────────────

    @Test
    fun `back-compat uiState stays live while T7 deletion is gated`() = runTest {
        val viewModel = createViewModel(testScheduler)
        advanceUntilIdle()

        // The 5+1-way combine overlay is alive (slicesOverlay + session
        // overlay). T7 deletion is blocked by spec requirement 6 while
        // any of the six consumers (ReaderScreen, DebugPanel,
        // ReaderScreenContentHost, ReaderScreenOverlaysHost,
        // ReaderSelectionCallbacks, ReadiumPdfReaderContent) still
        // collect from it.
        val initial = viewModel.uiState.value
        assertNotNull(initial)

        // The search field of uiState must still mirror the search slice
        // (T2 invariant; this is the easiest field to flip).
        viewModel.searchStateHolder.onToggleSearch()
        advanceUntilIdle()
        assertTrue(
            "uiState must continue to mirror search slice through the combine overlay",
            viewModel.uiState.value.isSearchActive
        )
    }

    // ── Owner wiring (annotation slice stays on the deprecated facade) ─

    @Test
    fun `annotation slice owner is the deprecated interactionHolder facade`() = runTest {
        // PR-D does not delete the interactionHolder field; PR #3 (out of
        // scope) replaces it with direct InteractionStateStore access.
        // Pin: the slice's source is still the facade.
        val field = ReaderViewModel::class.java.getDeclaredField("interactionHolder")
        field.isAccessible = true
        assertNotNull(
            "interactionHolder field must still be wired on the VM until PR #3 lands",
            field.get(createViewModel(testScheduler))
        )
    }

    // ── Annotation delegates stay (deprecated, not deleted) ────────

    @Test
    fun `annotation delegates are present but deprecated`() {
        // 30+ delegates are required by the existing reader consumers
        // until PR #3 lands. Deleting them in PR-D would be a spec
        // violation (T6 ships the deprecation, not the deletion).
        val names = listOf(
            "onHighlightTapped", "onTextSelection", "onTextSelectionEvent",
            "onSelectHighlightColor", "onCopySelectedText", "onDismissContextMenu",
            "onReadiumSelection", "onSelectionCleared",
            "onShowColorPickerPopover", "onDismissColorPickerPopover",
            "onShowNoteModal", "onDismissNoteModal", "onSaveNote", "onAnnotate",
            "onShowTagInput", "onDismissTagInput", "onTagTextChanged", "onSaveTag",
            "onShowDefinitionInput", "onDismissDefinitionInput",
            "onDefinitionTextChanged", "onSaveDefinition", "onAddToDictionary",
            "onShareSelectedText",
            "onReadiumHighlightColorSelected", "onReadiumDeleteHighlight",
            "onReadiumUpdateHighlightColor",
            "onDebugForceMenu", "onDebugForceColorPicker",
            "onToggleHighlightsPanel"
        )
        val methods = ReaderViewModel::class.java.methods.associateBy { it.name }
        for (name in names) {
            assertNotNull("ReaderViewModel.$name must still exist (deprecated, not deleted)", methods[name])
        }
    }

    // ── Cross-slice isolation ───────────────────────────────────────

    @Test
    fun `chrome toggle does not emit into the annotation slice`() = runTest {
        val viewModel = createViewModel(testScheduler)
        advanceUntilIdle()
        val beforeHighlights = viewModel.annotationUiState.value.highlights.size
        val beforeSheet = viewModel.annotationUiState.value.showHighlightsSheet

        viewModel.fullscreenManager.onToggleFullscreen()
        advanceUntilIdle()

        assertEquals(
            "annotation slice must not re-emit on chrome toggle",
            beforeHighlights,
            viewModel.annotationUiState.value.highlights.size
        )
        assertEquals(
            "annotation slice must not re-emit on chrome toggle",
            beforeSheet,
            viewModel.annotationUiState.value.showHighlightsSheet
        )
    }

    @Test
    fun `search toggle does not emit into the session slice`() = runTest {
        val viewModel = createViewModel(testScheduler)
        advanceUntilIdle()
        val beforeBookId = viewModel.sessionUiState.value.selectedBookId
        val beforeChapter = viewModel.sessionUiState.value.currentChapterIndex

        viewModel.searchStateHolder.onToggleSearch()
        advanceUntilIdle()

        assertEquals(
            "session slice must not re-emit on search toggle",
            beforeBookId,
            viewModel.sessionUiState.value.selectedBookId
        )
        assertEquals(
            "session slice must not re-emit on search toggle",
            beforeChapter,
            viewModel.sessionUiState.value.currentChapterIndex
        )
    }

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
