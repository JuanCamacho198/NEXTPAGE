package com.nextpage.data.mapper

import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.data.remote.dto.BookDto
import com.nextpage.data.remote.dto.BookmarkDto
import com.nextpage.data.remote.dto.HighlightDto
import com.nextpage.data.remote.dto.ReadingProgressDto

object EntityMappers {
    fun BookEntity.toDto(userId: String): BookDto = BookDto(
        id = id,
        userId = userId,
        title = title,
        author = author,
        coverUrl = coverPath,
        filePath = filePath,
        format = format,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun BookDto.toEntity(): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        coverPath = coverUrl,
        filePath = filePath,
        format = format,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun ReadingProgressEntity.toDto(): ReadingProgressDto = ReadingProgressDto(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun ReadingProgressDto.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun HighlightEntity.toDto(): HighlightDto = HighlightDto(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    fun HighlightDto.toEntity(): HighlightEntity = HighlightEntity(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    fun BookmarkEntity.toDto(): BookmarkDto = BookmarkDto(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    fun BookmarkDto.toEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )
}