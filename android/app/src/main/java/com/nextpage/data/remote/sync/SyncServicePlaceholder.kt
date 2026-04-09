package com.nextpage.data.remote.sync

import com.nextpage.data.local.dao.SyncOutboxDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncServicePlaceholder(
    outboxDao: SyncOutboxDao,
    private val isConfigured: Boolean
) : SyncService {

    override val syncState: Flow<SyncState> =
        if (isConfigured) {
            kotlinx.coroutines.flow.flowOf(SyncState.Idle)
        } else {
            kotlinx.coroutines.flow.flowOf(SyncState.Disabled)
        }

    override val pendingCount: Flow<Int> = outboxDao.observePendingCount().map { it }

    override suspend fun bootstrap(userId: String): Result<Unit> {
        return if (isConfigured) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
    }

    override suspend fun schedulePush(): Result<Unit> {
        return if (isConfigured) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
    }

    override suspend fun schedulePull(): Result<Unit> {
        return if (isConfigured) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
    }

    private companion object {
        const val MISSING_CONFIG_ERROR =
            "Sync service is disabled because Supabase credentials are missing."
    }
}
