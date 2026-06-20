package com.nextpage.presentation.viewmodel.reader

import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.readium.r2.shared.publication.Locator

/**
 * Tests for [SelectionCoordinator] state machine transitions,
 * exercised through [ReaderInteractionStateHolder]'s public API.
 *
 * Timing-sensitive transitions use mocked [SystemClock] so debounce
 * and ignore windows can be controlled deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectionCoordinatorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var readerRepository: ReaderRepository
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var holder: ReaderInteractionStateHolder

    private val sampleRectF = RectF(100f, 200f, 400f, 280f)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.println(any<Int>(), any<String>(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

        readerRepository = mockk(relaxed = true)
        dictionaryRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        // MockK unmocks automatically at the end of the test
    }

    @Test
    fun `Idle transitions to NewSelection on new text selection`() = runTest {
        holder = createHolder(scope = this)
        val locator = mockk<Locator>(relaxed = true)

        holder.onReadiumSelection(
            locator = locator,
            rect = sampleRectF,
            text = "selected text",
            existingHighlights = emptyList(),
            currentActiveHighlightId = null,
            currentHighlightTapDebounceUntil = 0L,
            currentMenuJustClosedAt = 0L
        )

        val state = holder.state.value
        assertTrue("selectionState should be New", state.selectionState is ReaderSelectionState.New)
        assertEquals("selectedText should be set", "selected text", state.selectedText)
    }

    @Test
    fun `Idle transitions to ExistingHighlight on highlight tap`() = runTest {
        holder = createHolder(scope = this)
        val highlight = createSampleHighlight(id = "h1")

        holder.onHighlightTapped(highlight, sampleRectF)

        val state = holder.state.value
        assertTrue("selectionState should be Existing", state.selectionState is ReaderSelectionState.Existing)
        assertEquals("selectedText should match highlight text", "highlighted text", state.selectedText)
    }

    @Test
    fun `ExistingHighlight ignores onReadiumSelection within debounce period`() = runTest {
        // Given: current time is 1000L
        every { SystemClock.elapsedRealtime() } returns 1000L
        holder = createHolder(scope = this)
        val highlight = createSampleHighlight(id = "h1")
        holder.onHighlightTapped(highlight, sampleRectF)
        // Now coordinator is ExistingHighlight with debounceUntil = 1000L + 2000 = 3000L

        // When: onReadiumSelection is called with time still at 1000L (before debounce expires)
        every { SystemClock.elapsedRealtime() } returns 1500L // Still before 3000L
        val locator = mockk<Locator>(relaxed = true)
        holder.onReadiumSelection(
            locator = locator,
            rect = sampleRectF,
            text = "new text selection",
            existingHighlights = listOf(highlight),
            currentActiveHighlightId = "h1",
            currentHighlightTapDebounceUntil = 3000L,
            currentMenuJustClosedAt = 0L
        )

        // Then: selection state should remain Existing (not overwritten by New)
        val state = holder.state.value
        assertTrue(
            "selectionState should remain Existing during debounce",
            state.selectionState is ReaderSelectionState.Existing
        )
    }

    @Test
    fun `NewSelection transitions to cleared on dismiss menu`() = runTest {
        holder = createHolder(scope = this)
        val locator = mockk<Locator>(relaxed = true)

        // Start with a New selection
        holder.onReadiumSelection(
            locator = locator,
            rect = sampleRectF,
            text = "selected text",
            existingHighlights = emptyList(),
            currentActiveHighlightId = null,
            currentHighlightTapDebounceUntil = 0L,
            currentMenuJustClosedAt = 0L
        )
        assertTrue("Should start as New", holder.state.value.selectionState is ReaderSelectionState.New)

        // When: dismiss the context menu
        holder.onDismissContextMenu()

        // Then: selection state is cleared
        val state = holder.state.value
        assertEquals("selectionState should be None", ReaderSelectionState.None, state.selectionState)
        assertNull("selectedText cleared", state.selectedText)
        assertNull("selectionRect cleared", state.selectionRect)
        assertFalse("showColorPickerPopover cleared", state.showColorPickerPopover)
        assertFalse("showNoteModal cleared", state.showNoteModal)
    }

    @Test
    fun `MenuClosed ignores onReadiumSelection within ignore window`() = runTest {
        // Given: current time is 1000L
        every { SystemClock.elapsedRealtime() } returns 1000L
        holder = createHolder(scope = this)
        val locator = mockk<Locator>(relaxed = true)

        // Start with a New selection
        holder.onReadiumSelection(
            locator = locator,
            rect = sampleRectF,
            text = "selected text",
            existingHighlights = emptyList(),
            currentActiveHighlightId = null,
            currentHighlightTapDebounceUntil = 0L,
            currentMenuJustClosedAt = 0L
        )

        // Dismiss creates MenuClosed(1000L)
        holder.onDismissContextMenu()

        // When: onReadiumSelection fires shortly after (ignore window = 1500ms)
        every { SystemClock.elapsedRealtime() } returns 1100L // Only 100ms after close
        val locator2 = mockk<Locator>(relaxed = true)
        holder.onReadiumSelection(
            locator = locator2,
            rect = sampleRectF,
            text = "new text after close",
            existingHighlights = emptyList(),
            currentActiveHighlightId = null,
            currentHighlightTapDebounceUntil = 0L,
            currentMenuJustClosedAt = 1000L
        )

        // Then: selection state should remain None (ignored)
        val state = holder.state.value
        assertEquals(
            "selectionState should stay None inside ignore window",
            ReaderSelectionState.None,
            state.selectionState
        )
    }

    @Test
    fun `ExistingHighlight allows selection after debounce expires`() = runTest {
        // Given: initial time is 1000L
        every { SystemClock.elapsedRealtime() } returns 1000L
        holder = createHolder(scope = this)
        val highlight = createSampleHighlight(id = "h1")
        holder.onHighlightTapped(highlight, sampleRectF)
        // coordinator = ExistingHighlight(debounceUntil = 1000 + 2000 = 3000L)

        // When: onReadiumSelection fires after debounce has expired
        every { SystemClock.elapsedRealtime() } returns 5000L // Past debounce
        val locator = mockk<Locator>(relaxed = true)
        holder.onReadiumSelection(
            locator = locator,
            rect = sampleRectF,
            text = "fresh selection after debounce",
            existingHighlights = emptyList(),
            currentActiveHighlightId = null,
            currentHighlightTapDebounceUntil = 0L, // Past debounce so no guard
            currentMenuJustClosedAt = 0L
        )

        // Then: selection should transition to NewSelection
        val state = holder.state.value
        assertTrue(
            "selectionState should transition to New after debounce expires",
            state.selectionState is ReaderSelectionState.New
        )
        assertEquals("new text should be set", "fresh selection after debounce", state.selectedText)
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun createHolder(scope: CoroutineScope): ReaderInteractionStateHolder {
        return ReaderInteractionStateHolder(
            readerRepository = readerRepository,
            dictionaryRepository = dictionaryRepository,
            scope = scope,
            onEvent = {}
        )
    }

    private fun createSampleHighlight(id: String): Highlight {
        return Highlight(
            id = id,
            bookId = "test-book",
            cfiRange = "epubcfi(/6/4)",
            textContent = "highlighted text",
            note = null,
            color = HighlightColor.YELLOW.hex,
            updatedAtEpochMillis = System.currentTimeMillis(),
            deletedAtEpochMillis = null,
            locatorJson = null
        )
    }
}
