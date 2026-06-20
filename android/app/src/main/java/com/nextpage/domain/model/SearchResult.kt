package com.nextpage.domain.model

import android.graphics.Rect

/**
 * A single search result found within a book's content.
 *
 * @property text Snippet of surrounding text with the match highlighted
 * @property offset Character offset of the match within the containing element
 * @property page Approximate scroll position / page y-offset for navigation
 * @property chapterIndex Index of the chapter (EPUB) or zero (PDF)
 * @property rect Optional bounding rectangle in WebView coordinates (EPUB only)
 */
data class SearchResult(
    val text: String,
    val offset: Int,
    val page: Float = 0f,
    val chapterIndex: Int = 0,
    val rect: Rect? = null,
    val chapterTitle: String = "",
    val cfi: String = ""
)
