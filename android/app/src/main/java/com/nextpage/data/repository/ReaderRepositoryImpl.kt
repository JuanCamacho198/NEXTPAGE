package com.nextpage.data.repository

import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

// TODO: make PagingConfig config-driven (pageSize, prefetchDistance) — currently pageSize=20
// is hardcoded in RepositoryImpl. Derive from screen metrics or server page size (R13).
class ReaderRepositoryImpl(
    private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val outboxDao: SyncOutboxDao? = null
) : ReaderRepository {
    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        readingProgressDao
            .observeProgressForBook(bookId)
            .map { progress -> progress?.toDomain() }

    override suspend fun upsertProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
        outboxDao?.insert(
            SyncOutboxEntity(
                id = "outbox-${UUID.randomUUID()}",
                entityType = SyncEntityType.READING_PROGRESS.name,
                entityId = progress.bookId,
                operation = SyncOperation.UPDATE.name,
                payloadJson = "{}",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getProgressForBook(bookId: String): ReadingProgress? =
        readingProgressDao.getProgressForBook(bookId)?.toDomain()

    override fun observeAllHighlights(): Flow<List<Highlight>> =
        highlightDao
            .observeAllHighlights()
            .map { list -> list.map { it.toDomain() } }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> =
        highlightDao
            .observeHighlightsForBook(bookId)
            .map { list -> list.map { it.toDomain() } }

    override fun observeAllTags(): Flow<List<String>> =
        highlightDao.observeAllTags()

    override suspend fun upsertHighlight(highlight: Highlight) {
        highlightDao.upsert(highlight.toEntity())
        outboxDao?.insert(
            SyncOutboxEntity(
                id = "outbox-${UUID.randomUUID()}",
                entityType = SyncEntityType.HIGHLIGHT.name,
                entityId = highlight.bookId,
                operation = SyncOperation.UPDATE.name,
                payloadJson = "{}",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getHighlightsForBook(bookId: String): List<Highlight> =
        highlightDao.getHighlightsForBook(bookId).map { it.toDomain() }

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
        outboxDao?.insert(
            SyncOutboxEntity(
                id = "outbox-${UUID.randomUUID()}",
                entityType = SyncEntityType.BOOKMARK.name,
                entityId = bookmark.bookId,
                operation = SyncOperation.UPDATE.name,
                payloadJson = "{}",
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getBookmarksForBook(bookId: String): List<Bookmark> =
        bookmarkDao.getBookmarksForBook(bookId).map { it.toDomain() }

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        currentPage = currentPage,
        updatedAtEpochMillis = updatedAtEpochMillis,
        locatorJson = locatorJson
    )

    private fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        currentPage = currentPage,
        updatedAtEpochMillis = updatedAtEpochMillis,
        locatorJson = locatorJson
    )

    private fun HighlightEntity.toDomain(): Highlight = Highlight(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson,
        type = type,
        tag = tag
    )

    private fun Highlight.toEntity(): HighlightEntity = HighlightEntity(
        id = id,
        bookId = bookId,
        cfiRange = cfiRange,
        textContent = textContent,
        note = note,
        color = color,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson,
        type = type,
        tag = tag
    )

    private fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson
    )

    private fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson
    )
}
