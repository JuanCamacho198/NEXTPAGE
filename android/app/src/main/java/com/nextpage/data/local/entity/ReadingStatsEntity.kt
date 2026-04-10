package com.nextpage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_stats")
data class ReadingStatsEntity(
    @PrimaryKey
    val bookId: String,
    val totalMinutesRead: Long = 0,
    val lastReadDateEpochMillis: Long = 0,
    val sessionsCount: Int = 0
)