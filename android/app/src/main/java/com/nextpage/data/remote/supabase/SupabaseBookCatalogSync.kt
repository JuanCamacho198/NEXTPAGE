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
     * When a remote catalog change arrives via Realtime, apply it locally.
     * Currently a no-op placeholder — the download UI (PR 4) will consume
     * the catalog changes reactively. This subscription ensures the
     * Realtime channel is active so events are delivered.
     */
    private suspend fun applyRemoteBook(row: UserBookRow) {
        // Remote book catalog entries are consumed by the download UI flow.
        // Local book entities are not modified by remote catalog inserts
        // (that would add books without user consent). The Realtime
        // subscription keeps the channel alive so events are received
        // by the download catalog store in the UI layer (PR 4).
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

        val bookFormat = row.format.ifBlank { "epub" }
        val drivePath = "books/$userId/$bookId.$bookFormat"

        return try {
            val bytes = remoteDataSource.download(drivePath)
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
                    coverPath = null,
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
