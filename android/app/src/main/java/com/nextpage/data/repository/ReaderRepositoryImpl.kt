package com.nextpage.data.repository

import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderRepositoryImpl(
    private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao
) : ReaderRepository {
    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        readingProgressDao
            .observeProgressForBook(bookId)
            .map { progress -> progress?.toDomain() }

    override suspend fun upsertProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    override fun observeAllHighlights(): Flow<List<Highlight>> =
        highlightDao
            .observeAllHighlights()
            .map { list -> list.map { it.toDomain() } }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> =
        highlightDao
            .observeHighlightsForBook(bookId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsertHighlight(highlight: Highlight) {
        highlightDao.upsert(highlight.toEntity())
    }

    override fun observeAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao
            .observeAllBookmarks()
            .map { list -> list.map { it.toDomain() } }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        bookmarkDao
            .observeBookmarksForBook(bookId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsertBookmark(bookmark: Bookmark) {
        bookmarkDao.upsert(bookmark.toEntity())
    }

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun HighlightEntity.toDomain(): Highlight = Highlight(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    private fun Highlight.toEntity(): HighlightEntity = HighlightEntity(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    private fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )

    private fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )
}
