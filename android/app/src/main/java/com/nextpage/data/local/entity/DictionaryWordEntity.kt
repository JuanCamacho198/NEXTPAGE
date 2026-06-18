package com.nextpage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_words")
data class DictionaryWordEntity(
    @PrimaryKey
    val id: String,
    val word: String,
    val addedAtEpochMillis: Long,
    @ColumnInfo(name = "definition")
    val definition: String? = null
)
