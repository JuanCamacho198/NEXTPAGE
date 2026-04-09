package com.nextpage.data.local.entity

data class ReadingProgressEntity(
    val id: String,
    val bookId: String,
    val cfiLocation: String,
    val percentage: Float,
    val updatedAtEpochMillis: Long
)
