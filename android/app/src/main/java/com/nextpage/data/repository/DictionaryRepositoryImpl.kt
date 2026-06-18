package com.nextpage.data.repository

import com.nextpage.data.local.dao.DictionaryWordDao
import com.nextpage.data.local.entity.DictionaryWordEntity
import com.nextpage.domain.model.DictionaryWord
import com.nextpage.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class DictionaryRepositoryImpl(
    private val dao: DictionaryWordDao
) : DictionaryRepository {

    override fun observeAll(): Flow<List<DictionaryWord>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun search(query: String): Flow<List<DictionaryWord>> =
        dao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun save(word: String): Result<DictionaryWord> = save(word, null)

    override suspend fun save(word: String, definition: String?): Result<DictionaryWord> = runCatching {
        val trimmed = word.trim()
        val entity = DictionaryWordEntity(
            id = UUID.randomUUID().toString(),
            word = trimmed,
            addedAtEpochMillis = System.currentTimeMillis(),
            definition = definition?.trim()?.takeIf { it.isNotBlank() }
        )
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun delete(wordId: String) {
        dao.delete(wordId)
    }

    override suspend fun exists(word: String): Boolean {
        return dao.countByWord(word.trim()) > 0
    }

    private fun DictionaryWordEntity.toDomain() = DictionaryWord(
        id = id,
        word = word,
        addedAtEpochMillis = addedAtEpochMillis,
        definition = definition
    )
}
