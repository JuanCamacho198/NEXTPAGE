package com.nextpage.presentation.viewmodel.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FullscreenState(
    val isFullscreen: Boolean = false
)

class FullscreenManager {
    private val _state = MutableStateFlow(FullscreenState())
    val state: StateFlow<FullscreenState> = _state.asStateFlow()

    fun onToggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    fun reset() {
        _state.value = FullscreenState()
    }
}
