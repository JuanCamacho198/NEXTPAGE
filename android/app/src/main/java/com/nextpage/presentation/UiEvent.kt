package com.nextpage.presentation

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class ShowToast(val message: String) : UiEvent
    /** Emitted when the user taps Share in the context menu.
     *  [ReaderScreen] handles the intent launch. */
    data class ShareText(val text: String) : UiEvent
}
