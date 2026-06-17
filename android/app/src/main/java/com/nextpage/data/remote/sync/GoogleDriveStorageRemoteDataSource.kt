package com.nextpage.data.remote.sync

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Implements [StorageSyncRemoteDataSource] using Google Drive REST API v3.
 *
 * All operations use the [appDataFolder](https://developers.google.com/drive/api/guides/appdata)
 * scope — files are hidden from the user's Drive UI.
 */
class GoogleDriveStorageRemoteDataSource(
    private val driveService: Drive,
    private val component: String = COMPONENT
) : StorageSyncRemoteDataSource {

    override suspend fun upload(path: String, bytes: ByteArray) {
        runCatching {
            // Check if file already exists via custom property "nextpagePath"
            val existing = findFileByPath(path)

            val fileMetadata = File().apply {
                name = path.substringAfterLast('/')
                parents = listOf("appDataFolder")
                appProperties = mapOf("nextpagePath" to path)
            }

            val mediaContent = ByteArrayContent("application/octet-stream", bytes)

            if (existing != null) {
                driveService.files().update(existing.id, fileMetadata, mediaContent).execute()
            } else {
                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }
        }.getOrElse { throwable ->
            throw AppError(
                category = ErrorCategory.WIRING_ERROR,
                code = "GOOGLE_DRIVE_UPLOAD_FAILED",
                message = throwable.message ?: "Failed to upload to Google Drive: $path",
                component = component
            )
        }
    }

    override suspend fun download(path: String): ByteArray {
        return runCatching {
            val file = findFileByPath(path)
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
            throw AppError(
                category = ErrorCategory.WIRING_ERROR,
                code = "GOOGLE_DRIVE_DOWNLOAD_FAILED",
                message = throwable.message ?: "Failed to download from Google Drive: $path",
                component = component
            )
        }
    }

    override suspend fun list(prefix: String): List<String> {
        return runCatching {
            val query = "appProperties has { key='nextpagePath' and value contains '$prefix' } " +
                "and trashed = false"
            val files = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, appProperties)")
                .execute()
            files.files?.mapNotNull { it.appProperties?.get("nextpagePath") } ?: emptyList()
        }.getOrElse { throwable ->
            throw AppError(
                category = ErrorCategory.WIRING_ERROR,
                code = "GOOGLE_DRIVE_LIST_FAILED",
                message = throwable.message ?: "Failed to list files in Google Drive: $prefix",
                component = component
            )
        }
    }

    /**
     * Finds a Drive file by its nextpagePath custom property.
     */
    private fun findFileByPath(path: String): File? {
        val query = "appProperties has { key='nextpagePath' and value='$path' } and trashed = false"
        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id, name, appProperties)")
            .execute()
        return files.files?.firstOrNull()
    }

    companion object {
        const val COMPONENT = "GoogleDriveStorageRemoteDataSource"
    }
}
