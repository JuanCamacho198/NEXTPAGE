package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextpage.data.local.entity.DictionaryWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryWordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: DictionaryWordEntity)

    @Query("SELECT * FROM dictionary_words ORDER BY addedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DictionaryWordEntity>>

    @Query("SELECT * FROM dictionary_words WHERE word LIKE '%' || :query || '%' ORDER BY addedAtEpochMillis DESC")
    fun search(query: String): Flow<List<DictionaryWordEntity>>

    @Query("DELETE FROM dictionary_words WHERE id = :wordId")
    suspend fun delete(wordId: String)

    @Query("SELECT COUNT(*) FROM dictionary_words WHERE word = :word")
    suspend fun countByWord(word: String): Int
}
