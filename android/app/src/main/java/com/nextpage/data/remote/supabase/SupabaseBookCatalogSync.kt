package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.remote.drive.SyncErrorCodes
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.session.SessionManager
import com.nextpage.debug.DebugLog
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
import java.security.MessageDigest

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
        val session = sessionManager.ensureFreshSession().getOrNull() ?: run {
            DebugLog.warn(TAG, "processOutbox: no fresh session — skipping book outbox processing")
            return
        }

        _state.value = State.Running
        val pendingItems = outboxDao.getPendingItems()
        DebugLog.info(TAG, "processOutbox: ${pendingItems.size} pending items for user ${session.userId}")

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
                    val localBook = bookDao.getBookById(bookId)
                    val remoteBook = dataSource.getUserBook(userId, bookId)
                    val tombstone = (localBook?.toUserBookRow(userId) ?: remoteBook
                        ?: UserBookRow(
                            id = bookId, userId = userId, title = "", format = "unknown",
                            importedAt = dateFormat.format(Date()), updatedAt = dateFormat.format(Date())
                        )).copy(
                        lifecycle = "deleted", filePath = null,
                        catalogVersion = maxOf(localBook?.remoteCatalogVersion ?: 0L,
                            remoteBook?.catalogVersion ?: 0L) + 1
                    )
                    dataSource.upsertBook(tombstone)
                }
                else -> {
                    val localBook = bookDao.getBookById(bookId)
                    if (localBook == null) {
                        DebugLog.warn(TAG, "processBookItem: local book $bookId not found — deleting outbox entry")
                        outboxDao.deleteById(item.id)
                        return
                    }
                    val row = localBook.toUserBookRow(userId)
                    DebugLog.info(TAG, "processBookItem: pushing book '${row.title}' ($bookId) to Supabase — catalogVersion=${row.catalogVersion}, hash=${row.contentHash?.take(16)}…")

                    // Content-hash dedup: skip upsert if same SHA-256 hash
                    // already exists in the catalog for this user.
                    if (row.contentHash != null) {
                        val existing = dataSource.getUserBookByHash(userId, row.contentHash!!)
                        if (existing != null) {
                            DebugLog.info(TAG, "processBookItem: duplicate hash ${row.contentHash!!.take(16)}… already in catalog — skipping")
                            outboxDao.deleteById(item.id)
                            return  // Already in catalog from other device
                        }
                    }

                    dataSource.upsertBook(row)
                    DebugLog.success(TAG, "processBookItem: book '${row.title}' upserted to Supabase OK")
                }
            }
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            DebugLog.error(TAG, "processBookItem: FAILED for book $bookId (${item.operation}) — ${e.javaClass.simpleName}: ${e.message}")
            runCatching { Log.w(TAG, "processBookItem: failed for book $bookId", e) }
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
        val session = sessionManager.ensureFreshSession().getOrNull() ?: run {
            DebugLog.warn(TAG, "reconcileLocalBooks: no fresh session — skipping reconcile")
            return
        }
        val userId = session.userId

        val localBooks = bookDao.observeAllBooks().first()
        DebugLog.info(TAG, "reconcileLocalBooks: ${localBooks.size} local books, user $userId")
        val remoteBooks = try {
            dataSource.listUserBooks(userId)
        } catch (e: Exception) {
            DebugLog.error(TAG, "reconcileLocalBooks: failed to list remote catalog — ${e.javaClass.simpleName}: ${e.message}")
            runCatching { Log.w(TAG, "reconcileLocalBooks: failed to list remote catalog, aborting reconcile", e) }
            return
        }
        val remoteIds = remoteBooks.map { it.id }.toSet()
        DebugLog.info(TAG, "reconcileLocalBooks: ${remoteBooks.size} remote books, ${localBooks.count { it.id !in remoteIds }} to push")

        for (book in localBooks) {
            if (book.id !in remoteIds) {
                try {
                    val row = book.toUserBookRow(userId)
                    DebugLog.info(TAG, "reconcileLocalBooks: pushing '${book.title}' (id=${book.id}) catalogVersion=${row.catalogVersion}")
                    dataSource.upsertBook(row)
                    DebugLog.success(TAG, "reconcileLocalBooks: '${book.title}' upserted OK")
                } catch (e: Exception) {
                    // A single book must never crash the reconcile pass; the
                    // outbox/reconcile will retry it later.
                    DebugLog.error(TAG, "reconcileLocalBooks: FAILED to push '${book.title}' (${book.id}) — ${e.javaClass.simpleName}: ${e.message}")
                    runCatching { Log.w(TAG, "reconcileLocalBooks: failed to push book ${book.id} (${book.title})", e) }
                }
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
     * When a remote catalog change arrives via Realtime, apply it locally using
     * monotonic version ordering (PR5 convergence):
     * - Missing local state never becomes deletion: no local row means no-op.
     * - A locally tombstoned book is never resurrected.
     * - Stale events (lower version) are ignored; equal events are idempotent.
     * - A newer explicit tombstone wins and tombstones the local book (never hard-delete).
     * - Otherwise newer metadata/cover fields are merged into the local row.
     */
    internal suspend fun applyRemoteBook(row: UserBookRow) {
        val existing = bookDao.getBookById(row.id) ?: return
        if (existing.deletedAtEpochMillis != null) return

        if (row.catalogVersion < existing.remoteCatalogVersion) return
        if (row.catalogVersion == existing.remoteCatalogVersion) return

        if (row.lifecycle == "deleted") {
            bookDao.deleteBook(row.id, System.currentTimeMillis())
            return
        }

        val updated = existing.copy(
            // Preserve a working local cover; only fill from the remote URL when
            // the local one is missing (D5: never clobber a working local cover
            // with a remote URL that may be dead).
            coverPath = if (existing.coverPath.isNullOrBlank()) row.coverUrl ?: existing.coverPath else existing.coverPath,
            remoteFileId = row.remoteFileId ?: existing.remoteFileId,
            remotePath = row.remotePath ?: row.filePath ?: existing.remotePath,
            remoteLifecycle = row.lifecycle,
            remoteCatalogVersion = row.catalogVersion,
            remoteCoverRef = row.coverObjectPath ?: existing.remoteCoverRef
        )
        if (updated != existing) {
            bookDao.upsert(updated)
        }
    }

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
        DebugLog.info(TAG, "bootstrap: starting catalog sync bootstrap")
        reconcileLocalBooks()
        startProcessing()
        subscribeToCatalogChanges()
        DebugLog.info(TAG, "bootstrap: catalog sync bootstrap complete")
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
            // A remote book is "downloadable" when it is not deleted/unavailable and
            // not already local. Desktop uploads with lifecycle "imported"; the strict
            // `available`-only filter hid those books from the cross-device section.
            val downloadable = catalog.filter {
                it.lifecycle != "deleted" &&
                    it.lifecycle != "unavailable" &&
                    it.id !in localBookIds
            }
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
        if (row.lifecycle != "available" || row.filePath.isNullOrBlank()) {
            return Result.failure(
                AppError(ErrorCategory.NOT_FOUND, if (row.lifecycle == "unavailable") "UNAVAILABLE" else "BOOK_NOT_IN_CATALOG", "Book $bookId is not available for import", "SupabaseBookCatalogSync")
            )
        }

        val bookFormat = row.format.ifBlank { "epub" }
        val drivePath = row.remotePath ?: "books/$userId/$bookId.$bookFormat"

        return try {
            // D4: 401/403 -> refresh token -> retry once. Refresh failure surfaces
            // an authorization-needed result instead of failing silently.
            val bytes = if (bookId.isNotBlank()) downloadWithRetry(drivePath) else return Result.failure(
                AppError(ErrorCategory.VALIDATION, "INVALID_BOOK_ID", "Invalid book id", "SupabaseBookCatalogSync")
            )
            val targetFile = File(localBooksDir, "$bookId.$bookFormat")
            val tempFile = File(localBooksDir, ".${targetFile.name}.part")
            val backupFile = File(localBooksDir, ".${targetFile.name}.backup")

            if (!localBooksDir.exists()) localBooksDir.mkdirs()
            recoverInterruptedImport(targetFile, tempFile, backupFile)
            tempFile.writeBytes(bytes)
            val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val expectedHash = row.contentHash?.removePrefix("sha256:")?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw AppError(ErrorCategory.VALIDATION, "HASH_REQUIRED", "Catalog SHA-256 is required", "SupabaseBookCatalogSync")
            if (!expectedHash.matches(Regex("[0-9a-fA-F]{64}")) || actualHash != expectedHash.lowercase()) {
                throw AppError(ErrorCategory.VALIDATION, "HASH_MISMATCH", "Downloaded content does not match catalog", "SupabaseBookCatalogSync")
            }
            val previousBook = bookDao.getBookById(bookId)
            if (targetFile.exists() && !targetFile.renameTo(backupFile)) throw IOException("Import backup failed")
            try {
                bookDao.upsert(BookEntity(
                    id = bookId,
                    title = row.title,
                    author = row.author,
                    format = bookFormat,
                    filePath = targetFile.absolutePath,
                    description = row.description,
                    totalPages = row.totalPages ?: 0,
                    coverPath = row.coverUrl, // D5: cover from public URL when present
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    contentHash = row.contentHash,
                    remoteFileId = row.remoteFileId,
                    remotePath = row.remotePath ?: row.filePath,
                    remoteLifecycle = row.lifecycle,
                    remoteCatalogVersion = row.catalogVersion,
                    remoteCoverRef = row.coverObjectPath,
                    remoteProvider = row.remoteProvider,
                    remoteProtocolVersion = row.protocolVersion
                ))
                if (!tempFile.renameTo(targetFile)) throw IOException("Atomic import rename failed")
                backupFile.delete()
            } catch (failure: Exception) {
                tempFile.delete()
                targetFile.delete()
                if (backupFile.exists()) backupFile.renameTo(targetFile)
                if (previousBook != null) bookDao.upsert(previousBook) else bookDao.deleteById(bookId)
                throw failure
            }
            Result.success(Unit)
        } catch (e: AppError) {
            File(localBooksDir, ".${bookId}.${bookFormat}.part").delete()
            File(localBooksDir, ".${bookId}.${bookFormat}.backup").delete()
            if (e.category == ErrorCategory.AUTH) Result.failure(
                AppError(ErrorCategory.AUTH, "DOWNLOAD_ERROR", e.message, "SupabaseBookCatalogSync")
            ) else Result.failure(e)
        } catch (e: IOException) {
            File(localBooksDir, ".${bookId}.${bookFormat}.part").delete()
            File(localBooksDir, ".${bookId}.${bookFormat}.backup").delete()
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

    private fun recoverInterruptedImport(target: File, temp: File, backup: File) {
        temp.delete()
        if (!target.exists() && backup.exists()) backup.renameTo(target) else backup.delete()
    }

    /**
     * Upload a cover image to Supabase Storage and return the public URL.
     * Path: covers/{userId}/{bookId}.jpg
     * Non-blocking: failure is mapped to the stable COVER_FAILED code
     * (spec REQ-07) and returns null so book import never blocks.
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
            DebugLog.warn(TAG, "Cover upload failed for book $bookId (${SyncErrorCodes.COVER_FAILED}): ${e.message}")
            runCatching { Log.w(TAG, "Cover upload failed for book $bookId (${SyncErrorCodes.COVER_FAILED})", e) }
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
            updatedAt = dateFormat.format(Date(updatedAtEpochMillis)),
            lifecycle = remoteLifecycle,
            // The DB enforces CHECK (catalog_version > 0); a locally imported book
            // has remoteCatalogVersion = 0 until the first remote write, so clamp
            // to a valid minimum instead of violating the constraint.
            catalogVersion = remoteCatalogVersion.coerceAtLeast(1L),
            remoteProvider = "google_drive",
            remoteFileId = remoteFileId,
            remotePath = remotePath,
            coverObjectPath = remoteCoverRef,
            protocolVersion = remoteProtocolVersion ?: 1
        )
    }

    private companion object {
        private const val TAG = "SupabaseBookCatalogSync"
    }
}
