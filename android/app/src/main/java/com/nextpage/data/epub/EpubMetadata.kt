package com.nextpage.data.epub

data class EpubMetadata(
    val title: String,
    val author: String?,
    val description: String? = null,
    val chapterCount: Int = 0,
    val coverImageBytes: ByteArray?
)
