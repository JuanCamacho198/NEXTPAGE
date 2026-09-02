package com.nextpage.presentation.viewmodel

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FakePerformanceDataSourceTest {

    @Test
    fun generateTimings_deterministicWithSeed0() {
        val fake1 = FakePerformanceDataSource(Random(0))
        val fake2 = FakePerformanceDataSource(Random(0))

        val timings1 = fake1.generateTimings()
        val timings2 = fake2.generateTimings()

        assertEquals(timings1.size, timings2.size)
        assertEquals(timings1.map { it.avgMs }, timings2.map { it.avgMs })
        assertEquals(timings1.map { it.samples }, timings2.map { it.samples })
    }

    @Test
    fun loadSyncStatus_deterministicWithSeed0() {
        val fake1 = FakePerformanceDataSource(Random(0))
        val fake2 = FakePerformanceDataSource(Random(0))
        val s1 = fake1.loadSyncStatus()
        val s2 = fake2.loadSyncStatus()
        assertEquals(s1.outboxPending, s2.outboxPending)
        assertEquals(s1.realtimeConnected, s2.realtimeConnected)
    }

    @Test
    fun loadDiagnostics_deterministicWithSeed0() {
        val fake1 = FakePerformanceDataSource(Random(0))
        val fake2 = FakePerformanceDataSource(Random(0))
        val d1 = fake1.loadDiagnostics()
        val d2 = fake2.loadDiagnostics()
        assertEquals(d1.fpsScroll, d2.fpsScroll)
        assertEquals(d1.anrCount, d2.anrCount)
        assertEquals(d1.crashes.size, d2.crashes.size)
    }

    @Test
    fun loadResources_returnsFakeWhenNoContext() = runTest {
        val fake = FakePerformanceDataSource(Random(0), appContext = null)
        val res = fake.loadResources()
        assertNotNull(res)
        assertEquals(4_820_000L, res.dbSizeBytes)
        assertEquals(2_340_000L, res.cacheSizeBytes)
    }
}
