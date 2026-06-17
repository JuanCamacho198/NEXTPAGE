package com.nextpage.data.remote.sync

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress

/**
 * JSON state file format stored as a single file per book in Google Drive.
 *
 * One JSON file at `books/{userId}/{bookId}/state.json` containing all 3 sync types.
 */
data class BookStateJson(
    val progress: ProgressStateJson? = null,
    val highlights: List<HighlightStateJson> = emptyList(),
    val bookmarks: List<BookmarkStateJson> = emptyList()
)

data class ProgressStateJson(
    val id: String,
    @SerializedName("book_id")
    val bookId: String,
    @SerializedName("cfi_location")
    val cfiLocation: String,
    val percentage: Float,
    @SerializedName("current_page")
    val currentPage: Int? = null,
    @SerializedName("updated_at")
    val updatedAtEpochMillis: Long
)

data class HighlightStateJson(
    val id: String,
    @SerializedName("book_id")
    val bookId: String,
    @SerializedName("cfi_range")
    val cfiRange: String,
    @SerializedName("text_content")
    val textContent: String,
    val note: String?,
    val color: String,
    @SerializedName("updated_at")
    override val updatedAtEpochMillis: Long,
    @SerializedName("deleted_at")
    override val deletedAtEpochMillis: Long? = null
) : VersionedSyncRecord {
    override val recordId: String get() = id
}

data class BookmarkStateJson(
    val id: String,
    @SerializedName("book_id")
    val bookId: String,
    @SerializedName("cfi_location")
    val cfiLocation: String,
    @SerializedName("title_or_snippet")
    val titleOrSnippet: String,
    @SerializedName("updated_at")
    override val updatedAtEpochMillis: Long,
    @SerializedName("deleted_at")
    override val deletedAtEpochMillis: Long? = null
) : VersionedSyncRecord {
    override val recordId: String get() = id
}

/**
 * Handles serialization/deserialization of per-book state JSON to/from Google Drive.
 *
 * Uses a single JSON file per book (`books/{userId}/{bookId}/state.json`) containing
 * reading progress, highlights, and bookmarks. Conflict resolution uses
 * [LastWriteWinsConflictResolver] for highlights and bookmarks at the record level.
 */
class GoogleDriveJsonStateSync(
    private val remoteDataSource: StorageSyncRemoteDataSource,
    private val highlightResolver: ConflictResolver<HighlightStateJson> = LastWriteWinsConflictResolver(),
    private val bookmarkResolver: ConflictResolver<BookmarkStateJson> = LastWriteWinsConflictResolver(),
    private val gson: Gson = Gson()
) {
    /**
     * Serializes local state for a book and uploads the JSON to Drive.
     */
    suspend fun pushState(
        userId: String,
        bookId: String,
        progress: ReadingProgress?,
        highlights: List<Highlight>,
        bookmarks: List<Bookmark>
    ): Result<ByteArray> {
        return runCatching {
            val json = BookStateJson(
                progress = progress?.let {
                    ProgressStateJson(
                        id = it.id,
                        bookId = it.bookId,
                        cfiLocation = it.cfiLocation,
                        percentage = it.percentage,
                        currentPage = it.currentPage,
                        updatedAtEpochMillis = it.updatedAtEpochMillis
                    )
                },
                highlights = highlights.map { it.toStateJson() },
                bookmarks = bookmarks.map { it.toStateJson() }
            )
            val jsonBytes = gson.toJson(json).toByteArray(Charsets.UTF_8)
            val path = statePathFor(userId, bookId)
            remoteDataSource.upload(path, jsonBytes)
            jsonBytes
        }
    }

    /**
     * Downloads remote state JSON, deserializes it, and resolves conflicts
     * against the provided local state. Returns the resolved state.
     */
    suspend fun pullState(
        userId: String,
        bookId: String,
        localProgress: ReadingProgress?,
        localHighlights: List<Highlight>,
        localBookmarks: List<Bookmark>
    ): Result<PullResult> {
        return runCatching {
            val path = statePathFor(userId, bookId)
            val jsonBytes = try {
                remoteDataSource.download(path)
            } catch (e: AppError) {
                if (e.code == "GOOGLE_DRIVE_FILE_NOT_FOUND") {
                    // No remote state yet — return local as-is
                    return Result.success(
                        PullResult(
                            progress = localProgress,
                            highlights = localHighlights,
                            bookmarks = localBookmarks
                        )
                    )
                }
                throw e
            }
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            val remoteState = gson.fromJson(jsonString, BookStateJson::class.java)

            // Resolve progress: simple last-write-wins (newer updatedAtEpochMillis wins)
            val resolvedProgress = resolveProgress(localProgress, remoteState.progress)

            // Resolve highlights with LastWriteWinsConflictResolver
            val resolvedHighlights = resolveRecords(
                local = localHighlights.map { it.toStateJson() },
                remote = remoteState.highlights,
                resolver = highlightResolver
            )

            // Resolve bookmarks with LastWriteWinsConflictResolver
            val resolvedBookmarks = resolveRecords(
                local = localBookmarks.map { it.toStateJson() },
                remote = remoteState.bookmarks,
                resolver = bookmarkResolver
            )

            PullResult(
                progress = resolvedProgress,
                highlights = resolvedHighlights.map { it.toDomain(bookId) },
                bookmarks = resolvedBookmarks.map { it.toDomain(bookId) }
            )
        }
    }

    private fun resolveProgress(
        local: ReadingProgress?,
        remote: ProgressStateJson?
    ): ReadingProgress? {
        if (remote == null) return local
        if (local == null) return remote.toDomain()
        if (remote.updatedAtEpochMillis > local.updatedAtEpochMillis) return remote.toDomain()
        return local
    }

    private fun <T : VersionedSyncRecord> resolveRecords(
        local: List<T>,
        remote: List<T>,
        resolver: ConflictResolver<T>
    ): List<T> {
        val allIds = (local.map { it.recordId } + remote.map { it.recordId }).distinct()
        val localById = local.associateBy { it.recordId }
        val remoteById = remote.associateBy { it.recordId }

        return allIds.mapNotNull { id ->
            val localRecord = localById[id]
            val remoteRecord = remoteById[id]
            when {
                remoteRecord == null -> localRecord
                localRecord == null -> remoteRecord
                else -> resolver.resolve(localRecord, remoteRecord)
            }
        }
    }

    data class PullResult(
        val progress: ReadingProgress?,
        val highlights: List<Highlight>,
        val bookmarks: List<Bookmark>
    )

    private fun statePathFor(userId: String, bookId: String): String {
        return "books/$userId/$bookId/state.json"
    }

    companion object {
        const val COMPONENT = "GoogleDriveJsonStateSync"
    }
}

// Extension functions for domain ↔ state JSON conversion

private fun Highlight.toStateJson(): HighlightStateJson = HighlightStateJson(
    id = id,
    bookId = bookId,
    cfiRange = cfiRange,
    textContent = textContent,
    note = note,
    color = color,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis
)

private fun HighlightStateJson.toDomain(bookId: String): Highlight = Highlight(
    id = id,
    bookId = bookId,
    cfiRange = cfiRange,
    textContent = textContent,
    note = note,
    color = color,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis
)

private fun Bookmark.toStateJson(): BookmarkStateJson = BookmarkStateJson(
    id = id,
    bookId = bookId,
    cfiLocation = cfiLocation,
    titleOrSnippet = titleOrSnippet,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis
)

private fun BookmarkStateJson.toDomain(bookId: String): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    cfiLocation = cfiLocation,
    titleOrSnippet = titleOrSnippet,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis
)

private fun ProgressStateJson.toDomain(): ReadingProgress = ReadingProgress(
    id = id,
    bookId = bookId,
    cfiLocation = cfiLocation,
    percentage = percentage,
    currentPage = currentPage,
    updatedAtEpochMillis = updatedAtEpochMillis
)
