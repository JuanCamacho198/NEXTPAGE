package com.nextpage.presentation.viewmodel.reader

import androidx.compose.runtime.Immutable

@Immutable
data class BookChapter(
    val index: Int,
    val id: String,
    val title: String,
    val href: String
)
