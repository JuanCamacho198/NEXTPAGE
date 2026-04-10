package com.nextpage.data.pdf

data class PdfMetadata(
    val title: String?,
    val author: String?,
    val pageCount: Int,
    val fileSizeBytes: Long
)