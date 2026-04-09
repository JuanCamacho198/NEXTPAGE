package com.nextpage.domain.model

data class BookImportRequest(
    val sourcePath: String,
    val fallbackTitle: String? = null
)
