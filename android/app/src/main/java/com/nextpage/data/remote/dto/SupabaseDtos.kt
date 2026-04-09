package com.nextpage.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val author: String?,
    @SerialName("cover_url")
    val coverUrl: String?,
    @SerialName("file_path")
    val filePath: String,
    val format: String,
    @SerialName("updated_at")
    val updatedAtEpochMillis: Long
)

@Serializable
data class ReadingProgressDto(
    val id: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_location")
    val cfiLocation: String,
    val percentage: Float,
    @SerialName("updated_at")
    val updatedAtEpochMillis: Long
)

@Serializable
data class HighlightDto(
    val id: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_range")
    val cfiRange: String,
    @SerialName("text_content")
    val textContent: String,
    val note: String?,
    val color: String,
    @SerialName("updated_at")
    val updatedAtEpochMillis: Long,
    @SerialName("deleted_at")
    val deletedAtEpochMillis: Long?
)

@Serializable
data class BookmarkDto(
    val id: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_location")
    val cfiLocation: String,
    @SerialName("title_or_snippet")
    val titleOrSnippet: String,
    @SerialName("updated_at")
    val updatedAtEpochMillis: Long,
    @SerialName("deleted_at")
    val deletedAtEpochMillis: Long?
)