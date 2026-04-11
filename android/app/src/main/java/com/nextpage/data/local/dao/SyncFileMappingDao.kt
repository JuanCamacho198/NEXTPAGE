package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nextpage.data.local.entity.SyncFileMappingEntity

@Dao
interface SyncFileMappingDao {
    @Upsert
    suspend fun upsert(mapping: SyncFileMappingEntity)

    @Query("SELECT * FROM sync_file_mappings WHERE remote_path = :remotePath LIMIT 1")
    suspend fun getByRemotePath(remotePath: String): SyncFileMappingEntity?

    @Query("SELECT * FROM sync_file_mappings WHERE user_id = :userId")
    suspend fun getByUserId(userId: String): List<SyncFileMappingEntity>
}
