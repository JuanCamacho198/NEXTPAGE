package com.nextpage.data.remote.sync

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import java.io.ByteArrayOutputStream

/**
 * Implements [StorageSyncRemoteDataSource] using Google Drive REST API v3,
 * unifying Android on the **desktop protocol**.
 *
 * Files live in a single shared visible folder `NextPage/Books` and are named
 * `{bookId}.{ext}` (no per-user subfolders). Lookup is by
 * `name='{bookId}.{ext}' and trashed=false`. Uses the `drive.file` scope.
 *
 * The interface still talks in **logical paths** of the form
 * `books/{userId}/{bookId}.{ext}` so [GoogleDriveSyncService] parsing stays
 * intact. Inside [GoogleDriveStorageRemoteDataSource]:
 * - upload/download receive the logical path and map it to the physical file `{bookId}.{ext}`.
 * - [list] reconstructs logical paths `books/{userId}/{fileName}` using the user id
 *   parsed from the supplied logical [prefix], so the caller keeps working unchanged.
 */
class GoogleDriveStorageRemoteDataSource(
    private val driveService: Drive,
    private val component: String = COMPONENT
) : StorageSyncRemoteDataSource {

    override suspend fun upload(path: String, bytes: ByteArray) {
        runCatching {
            val physicalName = path.substringAfterLast('/')
            val folder = ensureBooksFolder()

            val existing = findFileByName(folderId = folder, name = physicalName)

            val fileMetadata = File().apply {
                name = physicalName
                parents = listOf(folder)
            }
            val mediaContent = ByteArrayContent("application/octet-stream", bytes)

            if (existing != null) {
                driveService.files().update(existing.id, fileMetadata, mediaContent).execute()
            } else {
                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("name")
                    .execute()
            }
        }.getOrElse { throwable ->
            throw mapDriveError(throwable, "GOOGLE_DRIVE_UPLOAD_FAILED", "Failed to upload to Google Drive: $path")
        }
    }

    override suspend fun download(path: String): ByteArray {
        return runCatching {
            val physicalName = path.substringAfterLast('/')
            val folder = booksFolderIdOrNull()
                ?: throw AppError(
                    category = ErrorCategory.NOT_FOUND,
                    code = "GOOGLE_DRIVE_FILE_NOT_FOUND",
                    message = "File not found in Drive: $path",
                    component = component
                )
            val file = findFileByName(folderId = folder, name = physicalName)
                ?: throw AppError(
                    category = ErrorCategory.NOT_FOUND,
                    code = "GOOGLE_DRIVE_FILE_NOT_FOUND",
                    message = "File not found in Drive: $path",
                    component = component
                )

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(file.id)
                .executeMediaAndDownloadTo(outputStream)
            outputStream.toByteArray()
        }.getOrElse { throwable ->
            if (throwable is AppError) throw throwable
            throw mapDriveError(throwable, "GOOGLE_DRIVE_DOWNLOAD_FAILED", "Failed to download from Google Drive: $path")
        }
    }

    override suspend fun list(prefix: String): List<String> {
        return runCatching {
            val folder = booksFolderIdOrNull() ?: return@runCatching emptyList()

            // Every file in the shared NextPage/Books folder belongs to this
            // Drive account, so map physical names back under the caller's prefix.
            val userId = prefix.trim('/').substringAfter("books/").substringBefore('/')

            val files = driveService.files().list()
                .setQ("'$folder' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("files(name)")
                .execute()
            files.files.orEmpty()
                .mapNotNull { it.name }
                .map { name -> logicalPath(userId, name) }
        }.getOrElse { throwable ->
            throw mapDriveError(throwable, "GOOGLE_DRIVE_LIST_FAILED", "Failed to list files in Google Drive: $prefix")
        }
    }

    /**
     * Store paths [logical] (books/{userId}/{file}) so Sync parsing stays intact.
     */
    private fun logicalPath(userId: String, physicalName: String): String =
        "books/${userId}/${physicalName}".replace("//", "/")

    /**
     * Finds the shared `NextPage/Books` folder id, creating it if missing.
     */
    private suspend fun ensureBooksFolder(): String {
        return booksFolderIdOrNull() ?: createBooksFolder()
    }

    private fun booksFolderIdOrNull(): String? {
        val parent = findFolder("NextPage") ?: return null
        return findFolder("Books", parentId = parent)
    }

    /**
     * Locates the `NextPage` root folder (or its `Books` subfolder) by name.
     */
    private fun findFolder(name: String, parentId: String? = null): String? {
        val query = buildString {
            append("name='$name' and mimeType='application/vnd.google-apps.folder' and trashed = false")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val files = driveService.files().list()
            .setSpaces("drive")
            .setQ(query)
            .setFields("files(id, name, parents)")
            .execute()
        return files.files?.firstOrNull()?.id
    }

    private fun createBooksFolder(): String {
        // Create NextPage root if missing
        val nextPageId = findFolder("NextPage")
            ?: driveService.files().create(
                File().apply {
                    name = "NextPage"
                    mimeType = "application/vnd.google-apps.folder"
                }
            ).setFields("id").execute().id

        // Create Books subfolder if missing
        val booksId = findFolder("Books", parentId = nextPageId)
            ?: driveService.files().create(
                File().apply {
                    name = "Books"
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(nextPageId!!)
                }
            ).setFields("id").execute().id
        return booksId
    }

    /**
     * Find a (non-trashed) file by name within the given parent folder.
     */
    private fun findFileByName(folderId: String, name: String): File? {
        val query = "name='$name' and '$folderId' in parents and trashed = false"
        val files = driveService.files().list()
            .setSpaces("drive")
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
        return files.files?.firstOrNull { it.name == name }
    }

    /**
     * Maps a Drive API failure to an [AppError], tagging HTTP 401/403 as an
     * AUTH error so sync can refresh the token and retry (see D4).
     */
    private fun mapDriveError(throwable: Throwable, code: String, message: String): AppError {
        val statusCode = (throwable as? HttpResponseException)?.statusCode
        val unauthorized = statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN
        val category = if (unauthorized) ErrorCategory.AUTH else ErrorCategory.WIRING_ERROR
        val errorCode = if (unauthorized) "GOOGLE_DRIVE_UNAUTHORIZED" else code
        return AppError(
            category = category,
            code = errorCode,
            message = throwable.message ?: message,
            component = component
        )
    }

    companion object {
        const val COMPONENT = "GoogleDriveStorageRemoteDataSource"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}