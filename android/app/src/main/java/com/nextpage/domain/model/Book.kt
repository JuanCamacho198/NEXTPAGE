package com.nextpage.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val format: String,
    val totalPages: Int? = null,
    val chapterCount: Int? = null,
    val description: String? = null,
    val userRating: Int? = null,
    val updatedAtEpochMillis: Long
)
