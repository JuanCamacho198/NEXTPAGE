package com.nextpage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String?,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    val format: String,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long
)
