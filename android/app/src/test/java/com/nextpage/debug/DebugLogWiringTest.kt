package com.nextpage.debug

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class DebugLogWiringTest {

    private lateinit var fakeWriter: FakeLogWriter
    private lateinit var scope: CoroutineScope

    companion object {
        @BeforeClass
        @JvmStatic
        fun mockAndroidLog() {
            mockkStatic(Log::class)
            every { Log.println(any(), any(), any()) } returns 0
        }

        @AfterClass
        @JvmStatic
        fun unmockAndroidLog() = unmockkAll()
    }

    @Before
    fun setUp() {
        fakeWriter = FakeLogWriter()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        DebugLog.resetForTest()
        DebugLog.clear() // clear in-memory events from previous tests
    }

    @After
    fun tearDown() {
        DebugLog.resetForTest()
        unmockkAll()
        // Re-mock Log for next test — @BeforeClass runs only once per class,
        // but unmockkAll clears it after each test.
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    @Test
    fun `log calls writer write when initialized`() {
        DebugLog.init(scope, fakeWriter)
        DebugLog.info("TestTag", "test message")

        // Dispatchers.Unconfined executes the launched coroutine synchronously
        assertEquals("Writer should have received 1 event", 1, fakeWriter.written.size)
        val line = fakeWriter.written.first()
        assertTrue("Writer event should contain the tag", line.contains("TestTag"))
        assertTrue("Writer event should contain the message", line.contains("test message"))
    }

    @Test
    fun `log does not throw when writer is null`() {
        // DebugLog.resetForTest() already called in setUp — writer and scope are null
        DebugLog.info("TestTag", "this should not crash")
        val events = DebugLog.events.value
        assertTrue("In-memory events should still work without writer", events.isNotEmpty())
        assertTrue("Event should contain the message", events.any { it.message.contains("should not crash") })
    }

    @Test
    fun `log preserves existing in-memory behavior`() {
        DebugLog.init(scope, fakeWriter)
        DebugLog.info("TestTag", "mem message")
        DebugLog.warn("TestTag2", "warn message")

        val events = DebugLog.events.value
        assertEquals("Should have 2 events in StateFlow", 2, events.size)
        // Events are stored newest-first — warn was called last
        assertEquals("First (newest) event should be WARN", DebugLog.Level.WARN, events[0].level)
        assertEquals("Second event should be INFO", DebugLog.Level.INFO, events[1].level)
    }

    @Test
    fun `log does not crash when writer throws`() {
        fakeWriter.failWrites = true
        DebugLog.init(scope, fakeWriter)
        DebugLog.error("TestTag", "this should not crash even if writer fails")

        val events = DebugLog.events.value
        assertTrue("In-memory events should still work when writer throws", events.isNotEmpty())
        assertTrue("Event content should be preserved", events.any { it.message.contains("writer fails") })
        // Writer threw before adding to written list
        assertTrue("Writer should have 0 successful writes", fakeWriter.written.isEmpty())
    }

    @Test
    fun `init sets writer and scope correctly`() {
        DebugLog.init(scope, fakeWriter)

        // Verify by calling log() and checking the writer received the event
        DebugLog.info("InitTest", "after init")

        assertNotNull("FakeLogWriter should have received events", fakeWriter.written)
        assertEquals(1, fakeWriter.written.size)
        assertTrue(fakeWriter.written.first().contains("InitTest"))
    }

    // PR 3 sentry-cross-platform: DebugLog.error must forward to Sentry.captureMessage.
    // Static-mocks Sentry via mockkStatic; the @After tearDown calls unmockkAll() per
    // android/AGENTS.md's "every mockkStatic needs an unmockkAll()" rule.
    @Test
    fun `error forwards to Sentry captureMessage when initialized`() {
        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any<String>(), any<SentryLevel>()) } returns mockk(relaxed = true)

        DebugLog.init(scope, fakeWriter)
        DebugLog.error("TestTag", "boom")

        // Verify Sentry.captureMessage was called with our message + ERROR level
        verify(exactly = 1) {
            Sentry.captureMessage("boom", SentryLevel.ERROR)
        }
        // Sanity: local persistence path still works
        assertTrue("Writer should still receive ERROR event", fakeWriter.written.any { it.contains("ERROR") && it.contains("boom") })
    }

    @Test
    fun `info does not invoke Sentry captureMessage`() {
        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any<String>(), any<SentryLevel>()) } returns mockk(relaxed = true)

        DebugLog.init(scope, fakeWriter)
        DebugLog.info("TestTag", "no boom here")

        // No ERROR → Sentry.captureMessage must not be called (only ERROR forwards)
        verify(exactly = 0) {
            Sentry.captureMessage(any<String>(), any<SentryLevel>())
        }
    }

    // PR2 reader-error-enrichment: typed DebugDual events must reach Sentry via
    // structured captureException (setExtra/setTag), not concatenated strings.
    // Runs the event through DebugDual.log, captures the ScopeCallback, applies
    // it to a real Scope, and returns it for extra/tag assertions. The @After
    // tearDown calls unmockkAll() per android/AGENTS.md's teardown rule.
    private fun captureScopeFor(event: DebugEvent): Scope {
        mockkStatic(Sentry::class)
        val callbackSlot = slot<ScopeCallback>()
        every { Sentry.captureException(any<Throwable>(), capture(callbackSlot)) } returns SentryId.EMPTY_ID

        DebugDual.log(event)

        verify(exactly = 1) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
        return Scope(SentryOptions()).also { callbackSlot.captured.run(it) }
    }

    @Test
    fun `highlightsSkipped captures structured Sentry exception`() {
        val scope = captureScopeFor(
            DebugEvent.HighlightsSkipped("hl1", "epubcfi(/6/2)", "bounds_out_of_viewport")
        )

        assertEquals("hl1", scope.extras["highlightId"])
        assertEquals("epubcfi(/6/2)", scope.extras["cfi"])
        assertEquals("bounds_out_of_viewport", scope.extras["reason"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("highlight_skipped", scope.tags["event"])
        // Local source of truth preserved: WARN entry still lands in DebugLog
        assertTrue(
            "DebugLog should still hold the local entry",
            DebugLog.events.value.any { it.message.contains("highlights.skipped") }
        )
    }

    @Test
    fun `highlightsApplied captures structured Sentry exception`() {
        val scope = captureScopeFor(
            DebugEvent.HighlightsApplied("hl2", "epubcfi(/6/4)", true)
        )

        assertEquals("hl2", scope.extras["highlightId"])
        assertEquals("epubcfi(/6/4)", scope.extras["cfi"])
        assertEquals("true", scope.extras["viaFallback"])
        assertEquals("1", scope.extras["count"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("highlight_applied", scope.tags["event"])
    }

    @Test
    fun `syncOutboxFailed captures structured Sentry exception with truncated error`() {
        val longError = "x".repeat(250)
        val scope = captureScopeFor(
            DebugEvent.SyncOutboxFailed("HIGHLIGHT", "hl1", longError)
        )

        assertEquals("HIGHLIGHT", scope.extras["entityType"])
        assertEquals("hl1", scope.extras["entityId"])
        assertEquals(longError.take(200), scope.extras["error"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("sync_outbox_failed", scope.tags["event"])
    }

    @Test
    fun `footerMismatch captures structured Sentry exception`() {
        val scope = captureScopeFor(
            DebugEvent.FooterMismatch("ch1.html", "Chapter One", "Chapter Two")
        )

        assertEquals("ch1.html", scope.extras["locatorHref"])
        assertEquals("Chapter One", scope.extras["computed"])
        assertEquals("Chapter Two", scope.extras["expected"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("footer_mismatch", scope.tags["event"])
    }

    @Test
    fun `syncReceive captures structured Sentry exception`() {
        val scope = captureScopeFor(
            DebugEvent.SyncReceive("hl3", "epubcfi(/6/6)", true)
        )

        assertEquals("hl3", scope.extras["highlightId"])
        assertEquals("epubcfi(/6/6)", scope.extras["cfi"])
        assertEquals("true", scope.extras["locatorJsonNull"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("sync_receive", scope.tags["event"])
    }

    @Test
    fun `chapterResolved captures structured Sentry exception`() {
        val scope = captureScopeFor(
            DebugEvent.ChapterResolved("ch2.html", "Chapter Two", 3)
        )

        assertEquals("ch2.html", scope.extras["locatorHref"])
        assertEquals("Chapter Two", scope.extras["chapterTitle"])
        assertEquals("3", scope.extras["index"])
        assertEquals("reader", scope.tags["source"])
        assertEquals("chapter_resolved", scope.tags["event"])
    }
}

