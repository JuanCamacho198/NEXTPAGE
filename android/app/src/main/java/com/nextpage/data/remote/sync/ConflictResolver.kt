package com.nextpage.data.remote.sync

import android.util.Log
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.ReadingProgressEntity

object ConflictResolver {
    private const val TAG = "ConflictResolver"

    fun resolveProgress(local: ReadingProgressEntity?, remote: ReadingProgressEntity): ReadingProgressEntity {
        if (local == null) return remote

        return if (local.updatedAtEpochMillis >= remote.updatedAtEpochMillis) local else remote
    }

    fun resolveHighlight(local: HighlightEntity?, remote: HighlightEntity): HighlightEntity {
        if (local == null) return remote

        if (local.deletedAtEpochMillis != null && remote.deletedAtEpochMillis == null) {
            return local.copy(deletedAtEpochMillis = System.currentTimeMillis())
        }
        if (local.deletedAtEpochMillis != null && remote.deletedAtEpochMillis != null) {
            return if (local.deletedAtEpochMillis > remote.deletedAtEpochMillis) local else remote
        }
        if (local.deletedAtEpochMillis == null && remote.deletedAtEpochMillis != null) {
            return remote
        }

        return if (local.updatedAtEpochMillis >= remote.updatedAtEpochMillis) local else remote
    }

    fun resolveBookmark(local: BookmarkEntity?, remote: BookmarkEntity): BookmarkEntity {
        if (local == null) return remote

        if (local.deletedAtEpochMillis != null && remote.deletedAtEpochMillis == null) {
            return local.copy(deletedAtEpochMillis = System.currentTimeMillis())
        }
        if (local.deletedAtEpochMillis != null && remote.deletedAtEpochMillis != null) {
            return if (local.deletedAtEpochMillis > remote.deletedAtEpochMillis) local else remote
        }
        if (local.deletedAtEpochMillis == null && remote.deletedAtEpochMillis != null) {
            return remote
        }

        return if (local.updatedAtEpochMillis >= remote.updatedAtEpochMillis) local else remote
    }

    fun resolveTimestamp(local: Long, remote: Long): Long {
        if (local > remote) return local
        if (remote > local) return remote

        return if (local.toString().compareTo(remote.toString()) >= 0) local else remote
    }
}