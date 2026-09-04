package com.nextpage.domain.sync

import com.nextpage.data.session.SessionManager
import com.nextpage.data.sync.SessionGateImpl
import com.nextpage.domain.model.AuthSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SessionGate] / [SessionGateImpl] (SESSION-GATE in
 * sync-layer-split/spec, id 2381).
 *
 * Covers all 6 spec scenarios via a MockK `SessionManager` and a one-shot
 * consumer on [SessionGate.sessionEvents] (no Turbine dependency).
 *
 * The impl maintains a cached `live` flag updated by `ensureFreshSession`,
 * `onSessionLost`, and `onSessionRestored`. `hasLiveSession()` is a
 * non-suspend read of that cache — no network I/O, never throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionGateTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var gate: SessionGateImpl

    @Before
    fun setUp() {
        sessionManager = mockk(relaxed = true)
        gate = SessionGateImpl(sessionManager)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    private fun authSession(): AuthSession =
        AuthSession(userId = "user-1", email = "u@example.com")

    // ─── hasLiveSession (cached flag, no manager call) ──────────────────

    @Test
    fun hasLiveSession_returnsFalseInitially() {
        // The cache starts false until the first ensureFreshSession or
        // onSessionRestored signal — no network/manager call happens here.
        assertFalse(gate.hasLiveSession())
        coVerify(exactly = 0) { sessionManager.getCurrentSession() }
    }

    @Test
    fun hasLiveSession_returnsTrueAfterSuccessfulEnsureFreshSession() = runTest {
        coEvery { sessionManager.ensureFreshSession() } returns Result.success(authSession())

        gate.ensureFreshSession()

        assertTrue(gate.hasLiveSession())
    }

    @Test
    fun hasLiveSession_returnsFalseAfterEnsureFreshSessionFailure() = runTest {
        coEvery { sessionManager.ensureFreshSession() } returns Result.failure(IllegalStateException("expired"))

        gate.ensureFreshSession()

        assertFalse(gate.hasLiveSession())
    }

    @Test
    fun hasLiveSession_returnsFalseAfterOnSessionLost() {
        gate.onSessionRestored()
        assertTrue(gate.hasLiveSession())

        gate.onSessionLost()

        assertFalse(gate.hasLiveSession())
    }

    @Test
    fun hasLiveSession_doesNotCallSessionManager() {
        // The cached contract: hasLiveSession is a local read.
        gate.hasLiveSession()
        gate.hasLiveSession()

        coVerify(exactly = 0) { sessionManager.getCurrentSession() }
        coVerify(exactly = 0) { sessionManager.ensureFreshSession() }
    }

    // ─── ensureFreshSession ────────────────────────────────────────────

    @Test
    fun ensureFreshSession_returnsSuccessOnRefresh() = runTest {
        val session = authSession()
        coEvery { sessionManager.ensureFreshSession() } returns Result.success(session)

        val result = gate.ensureFreshSession()

        assertTrue(result.isSuccess)
        assertEquals(session, result.getOrNull())
    }

    @Test
    fun ensureFreshSession_returnsFailureOnManagerFailure() = runTest {
        coEvery { sessionManager.ensureFreshSession() } returns Result.failure(IllegalStateException("expired"))

        val result = gate.ensureFreshSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun ensureFreshSession_returnsFailureOnException_doesNotThrow() = runTest {
        coEvery { sessionManager.ensureFreshSession() } throws RuntimeException("network down")

        val result = gate.ensureFreshSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun ensureFreshSession_delegatesToManager_exactlyOnce() = runTest {
        coEvery { sessionManager.ensureFreshSession() } returns Result.success(authSession())

        gate.ensureFreshSession()

        coVerify(exactly = 1) { sessionManager.ensureFreshSession() }
    }

    // ─── sessionEvents ────────────────────────────────────────────────
    // MutableSharedFlow(replay=0, extraBufferCapacity=8, DROP_OLDEST) —
    // collectors MUST be subscribed BEFORE the emission to receive it.
    // UnconfinedTestDispatcher runs eagerly so the collector's first()
    // suspension is wired before we trigger the signal.

    @Test
    fun sessionEvents_emitsLiveOnSuccessfulRefresh() = runTest(UnconfinedTestDispatcher()) {
        coEvery { sessionManager.ensureFreshSession() } returns Result.success(authSession())
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val deferred = scope.async { gate.sessionEvents().first() }

        gate.ensureFreshSession()

        val event = deferred.await()
        assertEquals(SessionEvent.Live, event)
        scope.cancel()
    }

    @Test
    fun sessionEvents_emitsLostOnSessionLostSignal() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val deferred = scope.async { gate.sessionEvents().first() }

        gate.onSessionLost()

        val event = deferred.await()
        assertEquals(SessionEvent.Lost, event)
        scope.cancel()
    }

    @Test
    fun sessionEvents_emitsExpiredWithReasonOnEnsureFailure() = runTest(UnconfinedTestDispatcher()) {
        coEvery { sessionManager.ensureFreshSession() } throws RuntimeException("refresh_token_revoked")
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val deferred = scope.async { gate.sessionEvents().first() }

        gate.ensureFreshSession()

        val event = deferred.await()
        assertTrue(event is SessionEvent.Expired)
        assertEquals("refresh_token_revoked", (event as SessionEvent.Expired).reason)
        scope.cancel()
    }

    @Test
    fun sessionEvents_emitsExpiredOnManagerFailureResult() = runTest(UnconfinedTestDispatcher()) {
        coEvery { sessionManager.ensureFreshSession() } returns Result.failure(IllegalStateException("no_session"))
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val deferred = scope.async { gate.sessionEvents().first() }

        gate.ensureFreshSession()

        val event = deferred.await()
        assertTrue(event is SessionEvent.Expired)
        assertEquals("no_session", (event as SessionEvent.Expired).reason)
        scope.cancel()
    }

    @Test
    fun sessionEvents_emitsLostThenLiveInOrder() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val lostDeferred = scope.async { gate.sessionEvents().first() }

        gate.onSessionLost()
        val lostEvent = lostDeferred.await()
        assertEquals(SessionEvent.Lost, lostEvent)

        val liveDeferred = scope.async { gate.sessionEvents().first() }
        gate.onSessionRestored()
        val liveEvent = liveDeferred.await()
        assertEquals(SessionEvent.Live, liveEvent)

        scope.cancel()
    }
}
