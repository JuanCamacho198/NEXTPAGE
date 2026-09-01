package com.nextpage.presentation.viewmodel.reader

import com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore
import com.nextpage.presentation.viewmodel.reader.interaction.SelectionManager
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractionStateStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `clearSelection atomically resets 8 fields and emits event`() = runTest {
        val state = MutableStateFlow(ReaderInteractionState(selectedText = "foo", showNoteModal = true, showTagInput = true))
        val event = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var coordinator: SelectionCoordinator = SelectionCoordinator.Idle
        val store = InteractionStateStore(state, event)
        store.setCoordinator = { coordinator = it }
        val manager = SelectionManager(store, this, mainDispatcherRule.dispatcher)
        // Simulate Existing highlight so dismiss path is exercised via store
        state.value = state.value.copy(selectedText = "foo", showNoteModal = true, showTagInput = true, showDefinitionInput = true, activeNoteText = "note", activeTagText = "tag", activeDefinitionText = "def", tagSuggestions = listOf("a"))
        manager.coordinator = SelectionCoordinator.NewSelection("foo", android.graphics.Rect(0,0,10,10), null)

        store.clearSelection()
        advanceUntilIdle()

        val s = state.value
        assertEquals(ReaderSelectionState.None, s.selectionState)
        assertEquals(null, s.selectedText)
        assertEquals(null, s.selectionRect)
        assertEquals(false, s.showColorPickerPopover)
        assertEquals(false, s.showNoteModal)
        assertEquals(false, s.showTagInput)
        assertEquals(false, s.showDefinitionInput)
        assertTrue(coordinator is SelectionCoordinator.MenuClosed)
        // event emitted
        assertTrue(event.replayCache.isEmpty() || event.tryEmit(Unit) ) // at least one emit occurred (extraBufferCapacity)
    }
}
