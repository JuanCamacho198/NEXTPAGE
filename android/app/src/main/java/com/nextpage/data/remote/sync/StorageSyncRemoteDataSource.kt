package com.nextpage.data.remote.sync

interface StorageSyncRemoteDataSource {
    suspend fun upload(path: String, bytes: ByteArray)
    suspend fun download(path: String): ByteArray
    suspend fun list(prefix: String): List<String>
}
