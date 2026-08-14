package com.nextpage.presentation.viewmodel.library

import com.nextpage.domain.model.Book

/**
 * UI-level action state for the library — tracks in-progress modal dialogs
 * for delete and share operations.
 * Managed by [BookActionStateHolder].
 */
data class BookActionState(
    val bookToDelete: Book? = null,
    val bookToShare: Book? = null
)
