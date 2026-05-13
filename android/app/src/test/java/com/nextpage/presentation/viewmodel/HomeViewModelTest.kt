package com.nextpage.presentation.viewmodel

import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeUiState_sessionsToday_defaultsToZero() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("sessionsToday should default to 0", 0, state.sessionsToday)
    }

    @Test
    fun homeUiState_dailyProgressPercent_defaultsToZeroFloat() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("dailyProgressPercent should default to 0f", 0f, state.dailyProgressPercent, 0.001f)
    }

    @Test
    fun homeUiState_userName_defaultsToReader() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("userName should default to Reader", "Reader", state.userName)
    }

    @Test
    fun homeUiState_minutesReadToday_defaultsToZero() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("minutesReadToday should default to 0", 0, state.minutesReadToday)
    }

    @Test
    fun homeUiState_currentBook_defaultsToNull() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("currentBook should default to null", null, state.currentBook)
    }

    @Test
    fun homeUiState_recentBooks_defaultsToEmptyList() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("recentBooks should default to empty list", emptyList<Any>(), state.recentBooks)
    }

    @Test
    fun homeUiState_isLoading_defaultsToFalse() = runTest {
        val viewModel = HomeViewModel()
        val state = viewModel.uiState.value
        assertEquals("isLoading should default to false", false, state.isLoading)
    }
}
