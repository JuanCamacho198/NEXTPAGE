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
    @ColumnInfo(name = "cover_path")
    val coverPath: String?,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    val format: String,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int? = null,
    @ColumnInfo(name = "chapter_count")
    val chapterCount: Int? = null,
    val description: String? = null,
    @ColumnInfo(name = "user_rating")
    val userRating: Int? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAtEpochMillis: Long? = null
)
