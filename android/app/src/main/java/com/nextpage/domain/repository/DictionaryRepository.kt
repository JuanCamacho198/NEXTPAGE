package com.nextpage.domain.repository

import com.nextpage.domain.model.DictionaryWord
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    fun observeAll(): Flow<List<DictionaryWord>>
    fun search(query: String): Flow<List<DictionaryWord>>
    suspend fun save(word: String): Result<DictionaryWord>
    suspend fun save(word: String, definition: String?): Result<DictionaryWord>
    suspend fun updateDefinition(wordId: String, definition: String?): Result<DictionaryWord>
    suspend fun delete(wordId: String)
    suspend fun exists(word: String): Boolean
}
