package com.nextpage.data.remote.sync

import kotlinx.coroutines.flow.Flow

sealed class SyncState {
    data object Idle : SyncState()
    data object Disabled : SyncState()
    data object Running : SyncState()
    data class Error(val message: String) : SyncState()

    /**
     * Drive returned 401/403 and the refresh token could not re-issue access.
     * The UI should prompt the user to re-authorize Drive.
     */
    data object AuthorizationNeeded : SyncState()
}

interface SyncService {
    val syncState: Flow<SyncState>
    val pendingCount: Flow<Int>

    suspend fun bootstrap(userId: String): Result<Unit>
    suspend fun schedulePush(): Result<Unit>
    suspend fun schedulePull(): Result<Unit>
}
