package com.nextpage.data.local.entity

data class HighlightEntity(
    val id: String,
    val bookId: String,
    val cfiRange: String,
    val textContent: String,
    val note: String?,
    val color: String,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?
)
