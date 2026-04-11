package com.nextpage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_file_mappings")
data class SyncFileMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long
)
