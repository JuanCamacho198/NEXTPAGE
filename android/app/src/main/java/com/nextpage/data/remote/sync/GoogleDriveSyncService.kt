package com.nextpage.data.remote.sync

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.SyncFileMappingDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncFileMappingEntity
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Implements [SyncService] using Google Drive REST API v3 for file storage
 * and [GoogleDriveJsonStateSync] for reading progress, highlights, and bookmark sync.
 *
 * Handles all 4 outbox types: BOOK, READING_PROGRESS, HIGHLIGHT, BOOKMARK.
 */
class GoogleDriveSyncService(
    private val outboxDao: SyncOutboxDao,
    private val bookDao: BookDao,
    private val mappingDao: SyncFileMappingDao,
    private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val sessionManager: SessionManager,
    private val remoteDataSource: StorageSyncRemoteDataSource,
    private val localBooksDir: File,
    private val isEnabled: Boolean,
    private val diagnosticError: AppError? = null,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val jsonStateSync: GoogleDriveJsonStateSync = GoogleDriveJsonStateSync(remoteDataSource)
) : SyncService {

    private val state = MutableStateFlow<SyncState>(if (isEnabled) SyncState.Idle else SyncState.Disabled)

    override val syncState: Flow<SyncState> = state.asStateFlow()
    override val pendingCount: Flow<Int> = outboxDao.observePendingCount()

    override suspend fun bootstrap(userId: String): Result<Unit> {
        if (!isEnabled) {
            val disabledError = diagnosticError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DISABLED",
                message = "Sync service is disabled due to Google Drive configuration.",
                component = COMPONENT
            )
            state.value = SyncState.Disabled
            return Result.failure(disabledError)
        }
        if (userId.isBlank()) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SYNC_BOOTSTRAP_INVALID_USER",
                    message = "Sync bootstrap requires a non-empty user id.",
                    component = COMPONENT
                )
            )
        }
        if (!localBooksDir.exists()) {
            localBooksDir.mkdirs()
        }
        state.value = SyncState.Idle
        return Result.success(Unit)
    }

    override suspend fun schedulePush(): Result<Unit> {
        if (!isEnabled) {
            val disabledError = diagnosticError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DISABLED",
                message = "Sync service is disabled due to Google Drive configuration.",
                component = COMPONENT
            )
            state.value = SyncState.Disabled
            return Result.failure(disabledError)
        }
        val session = sessionManager.ensureFreshSession().getOrElse { error ->
            val mapped = mapError(error, defaultCode = "SYNC_SESSION_REQUIRED")
            state.value = SyncState.Error(mapped.message)
            return Result.failure(mapped)
        }

        state.value = SyncState.Running
        val pendingItems = outboxDao.getPendingItems()

        for (item in pendingItems) {
            when (item.entityType) {
                SyncEntityType.BOOK.name -> {
                    val pushResult = pushBook(item, session.userId)
                    if (pushResult.isFailure) {
                        val mapped = mapError(pushResult.exceptionOrNull(), defaultCode = "SYNC_BOOK_PUSH_FAILED")
                        outboxDao.incrementRetryCount(item.id, mapped.message)
                        outboxDao.pruneFailedItems(maxRetries)
                        state.value = SyncState.Error(mapped.message)
                        return Result.failure(mapped)
                    }
                    outboxDao.deleteById(item.id)
                }
                SyncEntityType.READING_PROGRESS.name,
                SyncEntityType.HIGHLIGHT.name,
                SyncEntityType.BOOKMARK.name -> {
                    val pushResult = pushState(item, session.userId)
                    if (pushResult.isFailure) {
                        val mapped = mapError(pushResult.exceptionOrNull(), defaultCode = "SYNC_STATE_PUSH_FAILED")
                        outboxDao.incrementRetryCount(item.id, mapped.message)
                        outboxDao.pruneFailedItems(maxRetries)
                        state.value = SyncState.Error(mapped.message)
                        return Result.failure(mapped)
                    }
                    outboxDao.deleteById(item.id)
                }
                else -> {
                    // Unknown types are silently skipped and removed
                    outboxDao.deleteById(item.id)
                }
            }
        }

        state.value = SyncState.Idle
        return Result.success(Unit)
    }

    private suspend fun pushBook(item: com.nextpage.data.local.entity.SyncOutboxEntity, userId: String): Result<Unit> {
        if (item.operation == SyncOperation.DELETE.name) {
            // DELETE ops for books are handled by removing the outbox entry;
            // the actual Drive file can be cleaned up lazily.
            return Result.success(Unit)
        }

        // entityId is nullable: FK is ON DELETE SET NULL, so a deleted book leaves the outbox
        // row with a null entity_id. Treat that as "nothing to push" and ack the row.
        val entityId = item.entityId ?: return Result.success(Unit)

        val book = bookDao.getBookById(entityId)
            ?: return Result.success(Unit) // Book deleted locally, skip

        if (book.deletedAtEpochMillis != null) {
            return Result.success(Unit)
        }

        val localFile = File(book.filePath)
        if (!localFile.exists()) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SYNC_LOCAL_FILE_MISSING",
                    message = "Local file is missing for book ${book.id}.",
                    component = COMPONENT
                )
            )
        }

        val drivePath = drivePathFor(userId, book.id, extensionFor(book))
        val uploadResult = retryable {
            remoteDataSource.upload(drivePath, localFile.readBytes())
        }

        if (uploadResult.isFailure) {
            return uploadResult.map { }
        }

        // Look up existing mapping to get driveFileId, or use the path as ID
        val existingMapping = mappingDao.getByDriveFileId(drivePath)
        val driveFileId = existingMapping?.driveFileId ?: drivePath

        mappingDao.upsert(
            SyncFileMappingEntity(
                driveFileId = driveFileId,
                userId = userId,
                bookId = book.id,
                localPath = book.filePath,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    private suspend fun pushState(item: com.nextpage.data.local.entity.SyncOutboxEntity, userId: String): Result<Unit> {
        // entityId is nullable: FK is ON DELETE SET NULL, so a deleted book leaves the outbox
        // row with a null entity_id. State (progress/highlights/bookmarks) is keyed by bookId,
        // so without a bookId there's nothing meaningful to push — ack the row.
        val bookId = item.entityId ?: return Result.success(Unit)
        val progress = readingProgressDao.getProgressForBook(bookId)?.toDomain()
        val highlights = highlightDao.getHighlightsForBook(bookId).map { it.toDomain() }
        val bookmarks = bookmarkDao.getBookmarksForBook(bookId).map { it.toDomain() }

        val pushResult = retryable {
            jsonStateSync.pushState(userId, bookId, progress, highlights, bookmarks)
        }

        if (pushResult.isFailure) {
            return pushResult.map { }
        }
        return Result.success(Unit)
    }

    override suspend fun schedulePull(): Result<Unit> {
        if (!isEnabled) {
            val disabledError = diagnosticError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DISABLED",
                message = "Sync service is disabled due to Google Drive configuration.",
                component = COMPONENT
            )
            state.value = SyncState.Disabled
            return Result.failure(disabledError)
        }
        val session = sessionManager.ensureFreshSession().getOrElse { error ->
            val mapped = mapError(error, defaultCode = "SYNC_SESSION_REQUIRED")
            state.value = SyncState.Error(mapped.message)
            return Result.failure(mapped)
        }

        state.value = SyncState.Running
        val userPrefix = "books/${session.userId}/"
        val remotePaths = retryable { remoteDataSource.list(prefix = userPrefix) }
            .getOrElse { error ->
                val mapped = mapError(error, defaultCode = "SYNC_LIST_FAILED")
                state.value = SyncState.Error(mapped.message)
                return Result.failure(mapped)
            }

        for (remotePath in remotePaths.distinct()) {
            if (remotePath.endsWith("/state.json")) {
                // State JSON files are handled via pullState below
                continue
            }

            val mapping = mappingDao.getByDriveFileId(remotePath)
            val parsed = parseDrivePath(remotePath)
                ?: continue
            val bookId = mapping?.bookId ?: parsed.bookId
            val extension = parsed.extension

            val existingBook = bookDao.getBookById(bookId)
            val localPath = mapping?.localPath
                ?: existingBook?.filePath
                ?: File(localBooksDir, "$bookId.$extension").absolutePath
            val localFile = File(localPath)

            if (!localFile.exists()) {
                val bytes = retryable { remoteDataSource.download(remotePath) }
                    .getOrElse { error ->
                        val mapped = mapError(error, defaultCode = "SYNC_DOWNLOAD_FAILED")
                        state.value = SyncState.Error(mapped.message)
                        return Result.failure(mapped)
                    }
                localFile.parentFile?.mkdirs()
                localFile.writeBytes(bytes)
            }

            val mergedBook = mergeBook(
                existing = existingBook,
                bookId = bookId,
                localPath = localFile.absolutePath,
                extension = extension
            )
            bookDao.upsert(mergedBook)
            mappingDao.upsert(
                SyncFileMappingEntity(
                    driveFileId = remotePath,
                    userId = session.userId,
                    bookId = bookId,
                    localPath = localFile.absolutePath,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }

        // Pull state JSON for each known book
        val allMappings = mappingDao.getByUserId(session.userId)
        val bookIds = allMappings.map { it.bookId }.distinct()
        for (bookId in bookIds) {
            val localProgress = readingProgressDao.getProgressForBook(bookId)?.toDomain()
            val localHighlights = highlightDao.getHighlightsForBook(bookId).map { it.toDomain() }
            val localBookmarks = bookmarkDao.getBookmarksForBook(bookId).map { it.toDomain() }

            val pullResult = retryable {
                jsonStateSync.pullState(session.userId, bookId, localProgress, localHighlights, localBookmarks)
            }

            if (pullResult.isSuccess) {
                val innerResult = pullResult.getOrThrow()
                if (innerResult.isSuccess) {
                    val resolved = innerResult.getOrThrow()
                    if (resolved.progress != null) {
                        readingProgressDao.upsert(resolved.progress.toEntity())
                    }
                    resolved.highlights.forEach { highlightDao.upsert(it.toEntity()) }
                    resolved.bookmarks.forEach { bookmarkDao.upsert(it.toEntity()) }
                }
            }
        }

        state.value = SyncState.Idle
        return Result.success(Unit)
    }

    private fun mergeBook(
        existing: BookEntity?,
        bookId: String,
        localPath: String,
        extension: String
    ): BookEntity {
        return if (existing != null) {
            existing.copy(
                filePath = localPath,
                updatedAtEpochMillis = System.currentTimeMillis(),
                deletedAtEpochMillis = null
            )
        } else {
            BookEntity(
                id = bookId,
                title = "Recovered $bookId",
                author = null,
                coverPath = null,
                filePath = localPath,
                format = extension,
                updatedAtEpochMillis = System.currentTimeMillis(),
                deletedAtEpochMillis = null
            )
        }
    }

    private suspend fun <T> retryable(block: suspend () -> T): Result<T> {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < MAX_ATTEMPTS) {
            val result = runCatching { block() }
            if (result.isSuccess) {
                return result
            }
            val error = result.exceptionOrNull()
            if (!isTransient(error)) {
                return Result.failure(error ?: IllegalStateException("Unknown sync failure"))
            }
            lastError = error
            attempt++
        }
        return Result.failure(lastError ?: IllegalStateException("Sync retries exhausted"))
    }

    private fun isTransient(error: Throwable?): Boolean {
        return error is IOException ||
            error is AppError
    }

    private fun mapError(error: Throwable?, defaultCode: String): AppError {
        if (error is AppError) {
            return error
        }
        val category = when (error) {
            null -> ErrorCategory.WIRING_ERROR
            is IllegalStateException -> ErrorCategory.WIRING_ERROR
            is IllegalArgumentException -> ErrorCategory.CONFIG_ERROR
            is IOException -> ErrorCategory.WIRING_ERROR
            else -> ErrorCategory.WIRING_ERROR
        }
        return AppError(
            category = category,
            code = defaultCode,
            message = error?.message ?: "Sync operation failed.",
            component = COMPONENT
        )
    }

    private fun drivePathFor(userId: String, bookId: String, extension: String): String {
        val userToken = sanitizeIdToken(userId)
        val bookToken = sanitizeIdToken(bookId)
        return "books/$userToken/$bookToken.$extension"
    }

    private fun extensionFor(book: BookEntity): String {
        return sanitizeToken(book.format)
            .ifBlank {
                File(book.filePath).extension.lowercase().ifBlank { DEFAULT_EXTENSION }
            }
    }

    private fun sanitizeToken(raw: String): String {
        return raw.lowercase().replace(NON_ALNUM_REGEX, "")
    }

    private fun sanitizeIdToken(raw: String): String {
        val sanitized = raw.lowercase().replace(NON_PATH_SAFE_REGEX, "-").trim('-')
        return if (sanitized.isBlank()) "unknown" else sanitized
    }

    private fun parseDrivePath(drivePath: String): ParsedDrivePath? {
        val segments = drivePath.split('/')
        if (segments.size != 3 || segments.first() != "books") {
            return null
        }
        val fileName = segments.last()
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == fileName.lastIndex) {
            return null
        }
        val bookId = fileName.substring(0, dotIndex)
        val extension = fileName.substring(dotIndex + 1)
        return ParsedDrivePath(bookId = bookId, extension = sanitizeToken(extension).ifBlank { DEFAULT_EXTENSION })
    }

    private data class ParsedDrivePath(
        val bookId: String,
        val extension: String
    )

    companion object {
        const val COMPONENT = "GoogleDriveSyncService"
        const val DEFAULT_EXTENSION = "bin"
        const val MAX_ATTEMPTS = 3
        const val DEFAULT_MAX_RETRIES = 3
        val NON_ALNUM_REGEX = Regex("[^a-z0-9]")
        val NON_PATH_SAFE_REGEX = Regex("[^a-z0-9_-]")
    }
}

// Extension functions for Entity ↔ Domain conversion used in state sync
// These mirror the conversions in ReaderRepositoryImpl

private fun com.nextpage.data.local.entity.ReadingProgressEntity.toDomain(): com.nextpage.domain.model.ReadingProgress =
    com.nextpage.domain.model.ReadingProgress(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        currentPage = currentPage,
        updatedAtEpochMillis = updatedAtEpochMillis,
        locatorJson = locatorJson
    )

private fun com.nextpage.domain.model.ReadingProgress.toEntity(): com.nextpage.data.local.entity.ReadingProgressEntity =
    com.nextpage.data.local.entity.ReadingProgressEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        currentPage = currentPage,
        updatedAtEpochMillis = updatedAtEpochMillis,
        locatorJson = locatorJson
    )

private fun com.nextpage.data.local.entity.HighlightEntity.toDomain(): com.nextpage.domain.model.Highlight =
    com.nextpage.domain.model.Highlight(
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

private fun com.nextpage.domain.model.Highlight.toEntity(): com.nextpage.data.local.entity.HighlightEntity =
    com.nextpage.data.local.entity.HighlightEntity(
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

private fun com.nextpage.data.local.entity.BookmarkEntity.toDomain(): com.nextpage.domain.model.Bookmark =
    com.nextpage.domain.model.Bookmark(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson
    )

private fun com.nextpage.domain.model.Bookmark.toEntity(): com.nextpage.data.local.entity.BookmarkEntity =
    com.nextpage.data.local.entity.BookmarkEntity(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        titleOrSnippet = titleOrSnippet,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        locatorJson = locatorJson
    )
