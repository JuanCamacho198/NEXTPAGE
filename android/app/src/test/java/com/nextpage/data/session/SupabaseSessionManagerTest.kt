package com.nextpage.data.session

import com.nextpage.domain.model.AuthSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseSessionManagerTest {

    @Test
    fun restoreSession_returnsPersistedSession() = runTest {
        val persisted = AuthSession(userId = "u1", email = "u1@test.com")
        val store = FakeSessionStore(initial = persisted)
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = true
        )

        val result = manager.restoreSession()

        assertTrue(result.isSuccess)
        assertEquals(persisted, result.getOrNull())
    }

    @Test
    fun restoreSession_restoresAcrossManagerInstances_fromPersistedStore() = runTest {
        val store = FakeSessionStore()
        val persisted = AuthSession(userId = "u1", email = "u1@test.com")
        val firstManager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = true
        )
        firstManager.setCurrentSession(persisted)

        val secondManager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = true
        )

        val restored = secondManager.restoreSession()

        assertTrue(restored.isSuccess)
        assertEquals(persisted, restored.getOrNull())
    }

    @Test
    fun ensureFreshSession_returnsRefreshedSession_andPersistsIt() = runTest {
        val initial = AuthSession(userId = "u1", email = "old@test.com")
        val refreshed = AuthSession(userId = "u1", email = "new@test.com")
        val store = FakeSessionStore(initial = initial)
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            refresher = { _ -> Result.success(refreshed) },
            isClientAvailable = true
        )

        val result = manager.ensureFreshSession()

        assertTrue(result.isSuccess)
        assertEquals(refreshed, result.getOrNull())
        assertEquals(refreshed, store.read())
    }

    @Test
    fun ensureFreshSession_clearsSession_whenRefreshFails() = runTest {
        val initial = AuthSession(userId = "u2", email = "u2@test.com")
        val store = FakeSessionStore(initial = initial)
        var remoteCalls = 0
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            refresher = { Result.failure(IllegalStateException("expired")) },
            remoteSignOut = {
                remoteCalls++
                Result.success(Unit)
            },
            isClientAvailable = true
        )

        val result = manager.ensureFreshSession()

        assertTrue(result.isFailure)
        val appError = result.exceptionOrNull() as com.nextpage.domain.error.AppError
        assertEquals("SUPABASE_SESSION_REFRESH_FAILED", appError.code)
        assertEquals(1, remoteCalls)
        assertNull(store.read())
        assertNull(manager.getCurrentSession().getOrNull())
    }

    @Test
    fun ensureFreshSession_clearsSession_evenWhenRemoteSignOutFails() = runTest {
        val initial = AuthSession(userId = "u4", email = "u4@test.com")
        val store = FakeSessionStore(initial = initial)
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            refresher = { Result.failure(IllegalStateException("refresh expired")) },
            remoteSignOut = { Result.failure(IllegalStateException("network down")) },
            isClientAvailable = true
        )

        val result = manager.ensureFreshSession()

        assertTrue(result.isFailure)
        val appError = result.exceptionOrNull() as com.nextpage.domain.error.AppError
        assertEquals("SUPABASE_SESSION_REFRESH_FAILED", appError.code)
        assertTrue(appError.message.orEmpty().contains("network down"))
        assertNull(store.read())
        assertNull(manager.getCurrentSession().getOrNull())
    }

    @Test
    fun signOutAll_clearsLocalAndInvokesRemoteSignOut() = runTest {
        val store = FakeSessionStore(initial = AuthSession("u3", "u3@test.com"))
        var remoteCalls = 0
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            remoteSignOut = {
                remoteCalls++
                Result.success(Unit)
            },
            isClientAvailable = true
        )

        val result = manager.signOutAll()

        assertTrue(result.isSuccess)
        assertEquals(1, remoteCalls)
        assertNull(store.read())
    }

    private class FakeSessionStore(initial: AuthSession? = null) : SessionStore {
        private var value: AuthSession? = initial

        override fun read(): AuthSession? = value

        override fun write(session: AuthSession) {
            value = session
        }

        override fun clear() {
            value = null
        }
    }
}
