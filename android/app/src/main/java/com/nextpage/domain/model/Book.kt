package com.nextpage.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val format: String,
    val totalPages: Int? = null,
    val chapterCount: Int? = null,
    val description: String? = null,
    val userRating: Int? = null,
    val updatedAtEpochMillis: Long,
    /** Explicit reading status set by the user; null means derive from reading time. */
    val status: String? = null,
    val readingState: String = ReadingState.TO_READ,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val progressPercentage: Float = 0f,
    val progressUpdatedAtEpochMillis: Long? = null,
    val stateVersion: Long = 0L
)

/** Persisted book status values stored in [Book.status]. */
object BookStatus {
    const val COMPLETED = "completed"
    const val PLAN_TO_READ = "plan_to_read"
    const val READING = "reading"
}

object ReadingState {
    const val TO_READ = "to_read"
    const val READING = "reading"
    const val COMPLETED = "completed"
}

fun resolveReadingState(explicitState: String?, progressPercentage: Float): String {
    val progress = progressPercentage.coerceIn(0f, 100f)
    return when {
        explicitState == ReadingState.COMPLETED || progress >= 100f -> ReadingState.COMPLETED
        explicitState == ReadingState.READING || progress > 0f -> ReadingState.READING
        else -> ReadingState.TO_READ
    }
}

/**
 * Resolves the effective reading status: an explicit [Book.status] wins; otherwise
 * the status is derived from accumulated [minutesRead] using [readingTargetMinutes].
 */
fun Book.effectiveStatus(
    minutesRead: Long,
    readingTargetMinutes: Long = READING_TARGET_MINUTES_DEFAULT
): String = when {
    status != null -> status
    minutesRead >= readingTargetMinutes -> BookStatus.COMPLETED
    minutesRead > 0L -> BookStatus.READING
    else -> PLAN_TO_READ_DERIVED
}

private const val PLAN_TO_READ_DERIVED = "pending"
private const val READING_TARGET_MINUTES_DEFAULT = 300L
