package com.nextpage.debug

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
}
