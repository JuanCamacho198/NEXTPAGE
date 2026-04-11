package com.nextpage.data.remote.sync
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.SyncFileMappingDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncFileMappingEntity
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

class SupabaseSyncService(
    private val outboxDao: SyncOutboxDao,
    private val bookDao: BookDao,
    private val mappingDao: SyncFileMappingDao,
    private val sessionManager: SessionManager,
    private val remoteDataSource: StorageSyncRemoteDataSource,
    private val localBooksDir: File,
    private val isEnabled: Boolean,
    private val diagnosticError: AppError? = null,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES
) : SyncService {

    private val state = MutableStateFlow<SyncState>(if (isEnabled) SyncState.Idle else SyncState.Disabled)

    override val syncState: Flow<SyncState> = state.asStateFlow()
    override val pendingCount: Flow<Int> = outboxDao.observePendingCount()

    override suspend fun bootstrap(userId: String): Result<Unit> {
        if (!isEnabled) {
            val disabledError = diagnosticError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DISABLED",
                message = "Sync service is disabled due to Supabase configuration.",
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
                message = "Sync service is disabled due to Supabase configuration.",
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
            if (item.entityType != SyncEntityType.BOOK.name) {
                continue
            }
            if (item.operation == SyncOperation.DELETE.name) {
                outboxDao.deleteById(item.id)
                continue
            }

            val book = bookDao.getBookById(item.entityId)
            if (book == null) {
                outboxDao.deleteById(item.id)
                continue
            }
            if (book.deletedAtEpochMillis != null) {
                outboxDao.deleteById(item.id)
                continue
            }

            val localFile = File(book.filePath)
            if (!localFile.exists()) {
                val missingError = AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SYNC_LOCAL_FILE_MISSING",
                    message = "Local file is missing for book ${book.id}.",
                    component = COMPONENT
                )
                outboxDao.incrementRetryCount(item.id, missingError.message)
                outboxDao.pruneFailedItems(maxRetries)
                state.value = SyncState.Error(missingError.message)
                return Result.failure(missingError)
            }

            val remotePath = remotePathFor(session.userId, book.id, extensionFor(book))
            val uploadResult = retryable {
                remoteDataSource.upload(remotePath, localFile.readBytes())
            }

            if (uploadResult.isFailure) {
                val mapped = mapError(uploadResult.exceptionOrNull(), defaultCode = "SYNC_UPLOAD_FAILED")
                outboxDao.incrementRetryCount(item.id, mapped.message)
                outboxDao.pruneFailedItems(maxRetries)
                state.value = SyncState.Error(mapped.message)
                return Result.failure(mapped)
            }

            mappingDao.upsert(
                SyncFileMappingEntity(
                    remotePath = remotePath,
                    userId = session.userId,
                    bookId = book.id,
                    localPath = book.filePath,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
            outboxDao.deleteById(item.id)
        }

        state.value = SyncState.Idle
        return Result.success(Unit)
    }

    override suspend fun schedulePull(): Result<Unit> {
        if (!isEnabled) {
            val disabledError = diagnosticError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DISABLED",
                message = "Sync service is disabled due to Supabase configuration.",
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
            val mapping = mappingDao.getByRemotePath(remotePath)
            val parsed = parseRemotePath(remotePath)
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
                    remotePath = remotePath,
                    userId = session.userId,
                    bookId = bookId,
                    localPath = localFile.absolutePath,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
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
            error is HttpRequestException ||
            error is HttpRequestTimeoutException
    }

    private fun mapError(error: Throwable?, defaultCode: String): AppError {
        if (error is AppError) {
            return error
        }
        val category = when (error) {
            null -> ErrorCategory.WIRING_ERROR
            is IllegalStateException -> ErrorCategory.WIRING_ERROR
            is IllegalArgumentException -> ErrorCategory.CONFIG_ERROR
            is RestException,
            is HttpRequestException,
            is HttpRequestTimeoutException,
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

    private fun remotePathFor(userId: String, bookId: String, extension: String): String {
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

    private fun parseRemotePath(remotePath: String): ParsedRemotePath? {
        val segments = remotePath.split('/')
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
        return ParsedRemotePath(bookId = bookId, extension = sanitizeToken(extension).ifBlank { DEFAULT_EXTENSION })
    }

    private data class ParsedRemotePath(
        val bookId: String,
        val extension: String
    )

    private companion object {
        const val COMPONENT = "SupabaseSyncService"
        const val DEFAULT_EXTENSION = "bin"
        const val MAX_ATTEMPTS = 3
        const val DEFAULT_MAX_RETRIES = 3
        val NON_ALNUM_REGEX = Regex("[^a-z0-9]")
        val NON_PATH_SAFE_REGEX = Regex("[^a-z0-9_-]")
    }
}
