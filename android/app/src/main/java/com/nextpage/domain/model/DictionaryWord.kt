package com.nextpage.domain.model

data class DictionaryWord(
    val id: String,
    val word: String,
    val addedAtEpochMillis: Long,
    val definition: String? = null
)
