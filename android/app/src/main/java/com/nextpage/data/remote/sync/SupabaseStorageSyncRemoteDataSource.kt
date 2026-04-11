package com.nextpage.data.remote.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

class SupabaseStorageSyncRemoteDataSource(
    private val client: SupabaseClient,
    private val bucket: String
) : StorageSyncRemoteDataSource {
    override suspend fun upload(path: String, bytes: ByteArray) {
        client.storage.from(bucket).upload(path = path, data = bytes, upsert = true)
    }

    override suspend fun download(path: String): ByteArray {
        return client.storage.from(bucket).downloadAuthenticated(path)
    }

    override suspend fun list(prefix: String): List<String> {
        return client.storage
            .from(bucket)
            .list(prefix = prefix)
            .mapNotNull { item ->
                item.name
                    .takeIf { it.isNotBlank() }
                    ?.let { "$prefix$it" }
            }
    }
}
