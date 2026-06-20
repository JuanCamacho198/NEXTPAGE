package com.nextpage.presentation.viewmodel.reader

import com.nextpage.domain.model.ReaderSettings
import com.nextpage.data.session.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReaderSettingsState(
    val readerSettings: ReaderSettings = ReaderSettings(),
    val showSplitSettings: Boolean = false
)

class ReaderSettingsManager(
    private val readerPreferences: ReaderPreferences?
) {
    private val _state = MutableStateFlow(ReaderSettingsState())
    val state: StateFlow<ReaderSettingsState> = _state.asStateFlow()

    init {
        // Load persisted settings on creation
        readerPreferences?.load()?.let { settings ->
            _state.update { it.copy(readerSettings = settings) }
        }
    }

    fun updateReaderSettings(settings: ReaderSettings) {
        readerPreferences?.save(settings)
        _state.update { it.copy(readerSettings = settings) }
    }

    fun onToggleSplitSettings() {
        _state.update { it.copy(showSplitSettings = !it.showSplitSettings) }
    }

    fun onUpdateCustomHighlightColor(index: Int, hex: String) {
        _state.update { state ->
            val colors = (state.readerSettings.customHighlightColors ?: emptyList()).toMutableList()
            if (index in colors.indices) {
                colors[index] = hex
                val updated = state.readerSettings.copy(customHighlightColors = colors)
                readerPreferences?.save(updated)
                state.copy(readerSettings = updated)
            } else state
        }
    }

    fun onResetCustomHighlightColors() {
        _state.update { state ->
            val default = ReaderSettings()
            val updated = state.readerSettings.copy(customHighlightColors = default.customHighlightColors)
            readerPreferences?.save(updated)
            state.copy(readerSettings = updated)
        }
    }
}
