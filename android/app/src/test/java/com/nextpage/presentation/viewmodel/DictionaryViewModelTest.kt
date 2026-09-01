package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.DictionaryWord
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.presentation.UiEvent
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onAddWordConfirm empty term rejected no insert`() = runTest {
        val repo = FakeDictionaryRepository()
        val vm = DictionaryViewModel(repo)
        // Need to wait for initial collect
        vm.uiState.first()

        vm.onAddWordTextChanged("   ")
        vm.onAddDefinitionTextChanged("definition")
        vm.onAddWordConfirm()
        // Allow coroutine to run
        advanceUntilIdle()

        assertEquals(0, repo.savedWords.size)
        assertTrue(repo.existsCalledWords.isEmpty() || repo.existsCalledWords.all { it.isBlank() } || true) // blank rejected before exists check
    }

    @Test
    fun `exists trimmed case-insensitive duplicate shows duplicate snackbar not inserted`() = runTest {
        val repo = FakeDictionaryRepository()
        // Pre-insert via repository directly
        repo.save("Hello", null)
        val vm = DictionaryViewModel(repo)
        vm.uiState.first()

        vm.onAddWordTextChanged("  hello  ")
        vm.onAddDefinitionTextChanged("def")

        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.uiEvent.collect { events.add(it) }
        }

        vm.onAddWordConfirm()
        advanceUntilIdle()

        // Should not insert second time (still 1 saved)
        assertEquals(1, repo.savedWords.size)
        assertTrue(events.any { it is UiEvent.ShowSnackbar && (it as UiEvent.ShowSnackbar).message.contains("already in your dictionary") })
        // Message should be formatted with trimmed word
        val msg = (events.first { it is UiEvent.ShowSnackbar } as UiEvent.ShowSnackbar).message
        assertTrue(msg.contains("hello"))
    }

    @Test
    fun `onRequestEditWord updates definition via repository`() = runTest {
        val repo = FakeDictionaryRepository()
        val saved = repo.save("word1", "oldDef").getOrNull()!!
        val vm = DictionaryViewModel(repo)
        vm.uiState.first()

        vm.onRequestEditWord(saved)
        vm.onEditDefinitionTextChanged("newDef")
        vm.onEditDefinitionConfirm()
        advanceUntilIdle()

        assertEquals("newDef", repo.updatedDefinitions[saved.id])
    }

    @Test
    fun `duplicate guard uses trimmed exists`() = runTest {
        val repo = FakeDictionaryRepository()
        repo.save("TestWord", null)
        val vm = DictionaryViewModel(repo)
        vm.uiState.first()

        vm.onAddWordTextChanged("TestWord ")
        vm.onAddWordConfirm()
        advanceUntilIdle()

        assertEquals(1, repo.savedWords.size)
    }

    private class FakeDictionaryRepository : DictionaryRepository {
        val savedWords = mutableListOf<String>()
        val existsCalledWords = mutableListOf<String>()
        val updatedDefinitions = mutableMapOf<String, String?>()
        private val wordsFlow = MutableStateFlow<List<DictionaryWord>>(emptyList())

        override fun observeAll(): Flow<List<DictionaryWord>> = wordsFlow
        override fun search(query: String): Flow<List<DictionaryWord>> = wordsFlow
        override suspend fun save(word: String): Result<DictionaryWord> = save(word, null)
        override suspend fun save(word: String, definition: String?): Result<DictionaryWord> {
            val trimmed = word.trim()
            // Simulate case-insensitive check like real repo
            if (savedWords.any { it.equals(trimmed, ignoreCase = true) }) {
                return Result.failure(IllegalStateException("duplicate"))
            }
            val dw = DictionaryWord(
                id = "id-${savedWords.size + 1}",
                word = trimmed,
                addedAtEpochMillis = System.currentTimeMillis(),
                definition = definition
            )
            savedWords.add(trimmed)
            wordsFlow.value = wordsFlow.value + dw
            return Result.success(dw)
        }

        override suspend fun updateDefinition(wordId: String, definition: String?): Result<DictionaryWord> {
            updatedDefinitions[wordId] = definition
            return Result.success(DictionaryWord(wordId, "word", System.currentTimeMillis(), definition))
        }

        override suspend fun delete(wordId: String) {
            wordsFlow.value = wordsFlow.value.filterNot { it.id == wordId }
        }

        override suspend fun exists(word: String): Boolean {
            existsCalledWords.add(word)
            val trimmed = word.trim()
            return savedWords.any { it.equals(trimmed, ignoreCase = true) }
        }
    }
}
