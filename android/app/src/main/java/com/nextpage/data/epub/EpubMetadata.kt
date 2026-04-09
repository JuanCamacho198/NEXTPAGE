package com.nextpage.data.epub

data class EpubMetadata(
    val title: String,
    val author: String?,
    val coverImageBytes: ByteArray?
)
