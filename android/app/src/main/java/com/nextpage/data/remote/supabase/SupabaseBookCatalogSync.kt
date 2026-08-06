package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Processes the local outbox for BOOK entries and upserts them
 * to Supabase via [SupabaseBookCatalogDataSource].
 *
 * Also manages Realtime subscription so incoming remote catalog changes
 * (books imported from other devices) are detected.
 *
 * Includes a reconciliation pass that pushes local books missing from
 * the remote catalog — this covers books imported before the feature existed.
 *
 * Only acts when a valid Supabase session is active.
 *
 * PR 4: Added [downloadRemoteBook] for cross-device download from Drive.
 * Pass [remoteDataSource] and [localBooksDir] to enable Drive downloads.
 */
class SupabaseBookCatalogSync(
    private val outboxDao: SyncOutboxDao,
    private val bookDao: BookDao,
    private val sessionManager: SessionManager,
    private val dataSource: SupabaseBookCatalogDataSource = SupabaseBookCatalogDataSource(),
    private val remoteDataSource: StorageSyncRemoteDataSource? = null,
    private val localBooksDir: File? = null,
    private val driveTokenRefresher: suspend () -> Result<String> = { Result.failure(AppError(ErrorCategory.CONFIG_ERROR, "SYNC_NO_REFRESHER", "Drive token refresher not configured.", "SupabaseBookCatalogSync")) },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processJob: Job? = null
    private var reconciliationJob: Job? = null
    private var realtimeJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    sealed class State {
        data object Idle : State()
        data object Running : State()
        data class Error(val message: String) : State()
    }

    private val dateFormat: SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /**
     * Start periodic outbox processing. Processes all pending BOOK
     * outbox entries and upserts them to Supabase.
     */
    fun startProcessing() {
        if (processJob?.isActive == true) return
        _state.value = State.Idle

        processJob = scope.launch {
            processOutbox()
        }
    }

    private suspend fun processOutbox() {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return

        _state.value = State.Running
        val pendingItems = outboxDao.getPendingItems()

        for (item in pendingItems) {
            if (item.entityType == SyncEntityType.BOOK.name) {
                processBookItem(item, session.userId)
            }
        }

        _state.value = State.Idle
    }

    private suspend fun processBookItem(
        item: com.nextpage.data.local.entity.SyncOutboxEntity,
        userId: String
    ) {
        val bookId = item.entityId ?: return
        val operation = try {
            SyncOperation.valueOf(item.operation)
        } catch (_: IllegalArgumentException) {
            SyncOperation.UPDATE
        }

        try {
            when (operation) {
                SyncOperation.DELETE -> {
                    dataSource.deleteUserBook(userId, bookId)
                }
                else -> {
                    val localBook = bookDao.getBookById(bookId)
                    if (localBook == null) {
                        outboxDao.deleteById(item.id)
                        return
                    }
                    val row = localBook.toUserBookRow(userId)

                    // Content-hash dedup: skip upsert if same SHA-256 hash
                    // already exists in the catalog for this user.
                    if (row.contentHash != null) {
                        val existing = dataSource.getUserBookByHash(userId, row.contentHash!!)
                        if (existing != null) {
                            outboxDao.deleteById(item.id)
                            return  // Already in catalog from other device
                        }
                    }

                    dataSource.upsertBook(row)
                }
            }
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
            outboxDao.pruneFailedItems(3)
        }
    }

    /**
     * Reconciliation pass: pushes local books that are missing from
     * the remote catalog. This covers books that were imported before
     * the book catalog sync feature existed.
     *
     * Designed to be called once per session during sync bootstrap.
     */
    suspend fun reconcileLocalBooks() {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return
        val userId = session.userId

        val localBooks = bookDao.observeAllBooks().first()
        val remoteBooks = dataSource.listUserBooks(userId)
        val remoteIds = remoteBooks.map { it.id }.toSet()

        for (book in localBooks) {
            if (book.id !in remoteIds) {
                val row = book.toUserBookRow(userId)
                dataSource.upsertBook(row)
            }
        }
    }

    /**
     * Subscribe to Realtime changes on the `user_books` table
     * so new books imported from other devices are detected live.
     */
    fun subscribeToCatalogChanges() {
        if (realtimeJob?.isActive == true) return

        realtimeJob = scope.launch {
            val session = sessionManager.ensureFreshSession().getOrNull() ?: return@launch

            dataSource.subscribeToCatalogChanges(session.userId).collect { action ->
                when (action) {
                    is PostgresAction.Insert,
                    is PostgresAction.Update -> {
                        val row = action.decodeRecord<UserBookRow>()
                        applyRemoteBook(row)
                    }
                    is PostgresAction.Delete,
                    is PostgresAction.Select -> { /* no-op */ }
                }
            }
        }
    }

    /**
     * When a remote catalog change arrives via Realtime, apply it locally:
     * upsert the local cover/coverPath from the remote row so the cover is
     * surfaced in the library (D5). Books marked deleted in the catalog are
     * skipped — only a fresh re-push/import revives a book.
     */
    private suspend fun applyRemoteBook(row: UserBookRow) {
        if (row.isCatalogDeleted()) return

        val existing = bookDao.getBookById(row.id) ?: return

        // Skip DELETE-marked local books: a tombstoned book stays deleted.
        if (existing.deletedAtEpochMillis != null) return

        val updated = if (row.coverUrl != null) existing.copy(coverPath = row.coverUrl) else existing
        if (updated != existing) {
            bookDao.upsert(updated)
        }
    }

    /** True when a remote row represents a catalog deletion (drive file absent). */
    private fun UserBookRow.isCatalogDeleted(): Boolean = filePath.isNullOrBlank()

    /**
     * Fetch the full catalog from Supabase.
     * Returns all book rows for the current user.
     */
    suspend fun fetchCatalog(): List<UserBookRow> {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return emptyList()
        return dataSource.listUserBooks(session.userId)
    }

    /**
     * Bootstrap: run reconciliation once, then start outbox processing
     * and subscribe to Realtime changes.
     */
    suspend fun bootstrap() {
        reconcileLocalBooks()
        startProcessing()
        subscribeToCatalogChanges()
    }

    /**
     * Stop periodic processing and unsubscribe from Realtime.
     */
    suspend fun stop() {
        processJob?.cancel()
        processJob = null
        reconciliationJob?.cancel()
        reconciliationJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        dataSource.unsubscribe()
        _state.value = State.Idle
    }

    /**
     * Returns book rows in the catalog that are NOT yet downloaded locally.
     * @return List of [UserBookRow] from remote devices, excluding locally-owned books,
     *         or failure if no session is active.
     */
    suspend fun getDownloadableBooks(): Result<List<UserBookRow>> {
        val session = sessionManager.getCurrentSession().getOrNull()
            ?: return Result.failure(
                AppError(ErrorCategory.AUTH, "NO_SESSION", "User must sign in to see downloadable books", "SupabaseBookCatalogSync")
            )
        val userId = session.userId
        return try {
            val catalog = dataSource.listUserBooks(userId)
            val localBooks = bookDao.observeAllBooks().first()
            val localBookIds = localBooks.map { it.id }.toSet()
            val downloadable = catalog.filterNot { it.id in localBookIds }
            Result.success(downloadable)
        } catch (e: Exception) {
            Result.failure(AppError(ErrorCategory.STORAGE, "CATALOG_FETCH", "Failed to fetch downloadable books: ${e.message}", "SupabaseBookCatalogSync"))
        }
    }

    /**
     * Downloads a remote book from Drive storage, saves it locally,
     * and creates a [BookEntity] so it appears in the user's library.
     *
     * Requires [remoteDataSource] and [localBooksDir] to be non-null.
     *
     * @param bookId The catalog book ID to download.
     */
    suspend fun downloadRemoteBook(bookId: String): Result<Unit> {
        val session = sessionManager.getCurrentSession().getOrNull()
            ?: return Result.failure(
                AppError(ErrorCategory.AUTH, "NO_SESSION", "User must sign in to download books", "SupabaseBookCatalogSync")
            )
        val userId = session.userId

        if (remoteDataSource == null || localBooksDir == null) {
            return Result.failure(
                AppError(ErrorCategory.CONFIG_ERROR, "DRIVE_NOT_CONFIGURED", "Drive download not configured", "SupabaseBookCatalogSync")
            )
        }

        // D6: never resurrect a locally DELETE-marked book.
        if (bookDao.getBookById(bookId)?.deletedAtEpochMillis != null) {
            return Result.failure(
                AppError(ErrorCategory.NOT_FOUND, "BOOK_TOMBSTONED", "Book $bookId was deleted and cannot be downloaded again without re-importing", "SupabaseBookCatalogSync")
            )
        }

        val catalog = try {
            dataSource.listUserBooks(userId)
        } catch (e: Exception) {
            return Result.failure(
                AppError(ErrorCategory.STORAGE, "CATALOG_FETCH", "Failed to fetch catalog: ${e.message}", "SupabaseBookCatalogSync")
            )
        }
        val row = catalog.firstOrNull { it.id == bookId }
            ?: return Result.failure(
                AppError(ErrorCategory.NOT_FOUND, "BOOK_NOT_IN_CATALOG", "Book $bookId not found in catalog", "SupabaseBookCatalogSync")
            )

        // A catalog row without a remote drive file is effectively deleted — don't download.
        if (row.filePath.isNullOrBlank()) {
            return Result.failure(
                AppError(ErrorCategory.NOT_FOUND, "BOOK_NOT_IN_CATALOG", "Book $bookId has no remote file in the catalog", "SupabaseBookCatalogSync")
            )
        }

        val bookFormat = row.format.ifBlank { "epub" }
        val drivePath = "books/$userId/$bookId.$bookFormat"

        return try {
            // D4: 401/403 -> refresh token -> retry once. Refresh failure surfaces
            // an authorization-needed result instead of failing silently.
            val bytes = if (bookId.isNotBlank()) downloadWithRetry(drivePath) else return Result.failure(
                AppError(ErrorCategory.VALIDATION, "INVALID_BOOK_ID", "Invalid book id", "SupabaseBookCatalogSync")
            )
            val targetFile = File(localBooksDir, "$bookId.$bookFormat")

            if (!localBooksDir.exists()) localBooksDir.mkdirs()
            targetFile.writeBytes(bytes)

            bookDao.upsert(
                BookEntity(
                    id = bookId,
                    title = row.title,
                    author = row.author,
                    format = bookFormat,
                    filePath = targetFile.absolutePath,
                    description = row.description,
                    totalPages = row.totalPages ?: 0,
                    coverPath = row.coverUrl, // D5: cover from public URL when present
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(
                AppError(ErrorCategory.STORAGE, "DOWNLOAD_FAILED", "Failed to download/save book $bookId: ${e.message}", "SupabaseBookCatalogSync")
            )
        } catch (e: Exception) {
            Result.failure(
                AppError(ErrorCategory.UNKNOWN, "DOWNLOAD_ERROR", "Unexpected error downloading book $bookId: ${e.message}", "SupabaseBookCatalogSync")
            )
        }
    }

    /**
     * Downloads [drivePath] from Drive, refreshing the access token once on
     * 401/403 and retrying (D4). Throws on final failure.
     */
    private suspend fun downloadWithRetry(drivePath: String): ByteArray {
        val firstAttempt = try {
            remoteDataSource?.download(drivePath)
        } catch (unauthorized: AppError) {
            if (unauthorized.category == ErrorCategory.AUTH || unauthorized.code == "GOOGLE_DRIVE_UNAUTHORIZED") {
                val refreshed = runCatching { driveTokenRefresher() }.getOrNull()
                if (refreshed?.isSuccess == true) {
                    remoteDataSource?.download(drivePath)
                } else {
                    throw unauthorized
                }
            } else {
                throw unauthorized
            }
        } ?: throw IOException("Drive download not configured")
        return firstAttempt
    }

    /**
     * Upload a cover image to Supabase Storage and return the public URL.
     * Path: covers/{userId}/{bookId}.jpg
     * Non-blocking: failure logs warning and returns null.
     */
    private suspend fun uploadCover(userId: String, bookId: String, coverPath: String?): String? {
        if (coverPath == null) return null
        val coverFile = File(coverPath)
        if (!coverFile.exists()) return null
        return try {
            val bytes = coverFile.readBytes()
            val path = "covers/$userId/$bookId.jpg"
            SupabaseClientProvider.client.storage.from("book-covers").upload(
                path = path,
                data = bytes,
                options = { upsert = true }
            )
            SupabaseClientProvider.client.storage.from("book-covers").publicUrl(path)
        } catch (e: Exception) {
            Log.w(TAG, "Cover upload failed for book $bookId", e)
            null
        }
    }

    private suspend fun BookEntity.toUserBookRow(userId: String): UserBookRow {
        val coverUrl = uploadCover(userId, id, coverPath)
        return UserBookRow(
            id = id,
            userId = userId,
            title = title,
            author = author,
            format = format,
            contentHash = contentHash,
            filePath = filePath,
            coverUrl = coverUrl,
            description = description,
            totalPages = totalPages,
            sourceDevice = "android",
            importedAt = dateFormat.format(Date(updatedAtEpochMillis)),
            updatedAt = dateFormat.format(Date(updatedAtEpochMillis))
        )
    }

    private companion object {
        private const val TAG = "SupabaseBookCatalogSync"
    }
}
