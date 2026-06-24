package com.nextpage.presentation.viewmodel.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FullscreenState(
    val isFullscreen: Boolean = false
)

class FullscreenManager {
    private val _state = MutableStateFlow(FullscreenState())
    val state: StateFlow<FullscreenState> = _state.asStateFlow()

    fun onToggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /**
     * Idempotently enters fullscreen (immersive) mode. Used to auto-enter
     * the reader's immersive reading mode when a book is opened.
     */
    fun enterFullscreen() {
        _state.update { it.copy(isFullscreen = true) }
    }

    fun reset() {
        _state.value = FullscreenState()
    }
}
