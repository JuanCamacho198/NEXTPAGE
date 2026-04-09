package com.nextpage.data.storage

interface CoverStorage {
    suspend fun saveCover(bookId: String, coverBytes: ByteArray): Result<String>
}
