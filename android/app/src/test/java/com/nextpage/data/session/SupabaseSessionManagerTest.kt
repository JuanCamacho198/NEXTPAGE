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
    fun ensureFreshSession_returnsCurrentSession_asFallbackRefresh() = runTest {
        val initial = AuthSession(userId = "u1", email = "test@test.com")
        val store = FakeSessionStore(initial = initial)
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = true
        )

        val result = manager.ensureFreshSession()

        // Fallback implementation returns the current session
        assertTrue(result.isSuccess)
        assertEquals(initial, result.getOrNull())
    }

    @Test
    fun ensureFreshSession_failsWithConfigError_whenClientNotAvailable() = runTest {
        val store = FakeSessionStore()
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = false
        )

        val result = manager.ensureFreshSession()

        assertTrue(result.isFailure)
        val appError = result.exceptionOrNull() as com.nextpage.domain.error.AppError
        assertEquals("SUPABASE_CLIENT_NOT_AVAILABLE", appError.code)
    }

    @Test
    fun signOutAll_clearsLocalSession() = runTest {
        val store = FakeSessionStore(initial = AuthSession("u3", "u3@test.com"))
        val manager = SupabaseSessionManager(
            client = null,
            diagnosticError = null,
            sessionStore = store,
            isClientAvailable = true
        )

        val result = manager.signOutAll()

        assertTrue(result.isSuccess)
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
