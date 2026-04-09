package com.nextpage.data.local.entity

data class BookEntity(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val format: String,
    val updatedAtEpochMillis: Long
)
