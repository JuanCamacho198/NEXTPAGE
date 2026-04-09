package com.nextpage.domain.model

data class ReadingProgress(
    val id: String,
    val bookId: String,
    val cfiLocation: String,
    val percentage: Float,
    val updatedAtEpochMillis: Long
)
