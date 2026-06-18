package com.nextpage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_words")
data class DictionaryWordEntity(
    @PrimaryKey
    val id: String,
    val word: String,
    val addedAtEpochMillis: Long
)
