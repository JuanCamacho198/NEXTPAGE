package com.nextpage.data.local.entity

data class BookmarkEntity(
    val id: String,
    val bookId: String,
    val cfiLocation: String,
    val titleOrSnippet: String,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?
)
