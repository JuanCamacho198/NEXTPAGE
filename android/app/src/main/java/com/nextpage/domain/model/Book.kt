package com.nextpage.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val format: String,
    val updatedAtEpochMillis: Long
)
