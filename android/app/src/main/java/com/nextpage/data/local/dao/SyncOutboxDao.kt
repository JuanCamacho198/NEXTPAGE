package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nextpage.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY created_at ASC")
    suspend fun getPendingItems(): List<SyncOutboxEntity>

    @Insert
    suspend fun insert(item: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sync_outbox SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun incrementRetryCount(id: String, error: String)

    @Query("DELETE FROM sync_outbox WHERE retry_count >= :maxRetries")
    suspend fun pruneFailedItems(maxRetries: Int)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int>
}