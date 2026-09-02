package com.nextpage.presentation.viewmodel

import android.app.ActivityManager
import android.content.Context
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FakePerformanceDataSource(
    private val random: Random = Random.Default,
    private val appContext: Context? = null
) : PerformanceFakeDataSource {

    override fun generateTimings(): List<PerformanceTiming> {
        fun timing(key: String, label: String, baseMs: Long): PerformanceTiming {
            val samples = List(SPARKLINE_SAMPLE_COUNT) { baseMs * (SPARKLINE_BASE_FACTOR + random.nextFloat() * SPARKLINE_VARIATION_RANGE) }
            val sorted = samples.sorted()
            val avg = samples.average().toLong()
            val p95 = sorted[(sorted.size * P95_FACTOR).toInt().coerceAtMost(sorted.size - 1)].toLong()
            val max = sorted.maxOrNull()?.toLong() ?: baseMs
            return PerformanceTiming(
                key = key,
                labelFallback = label,
                avgMs = avg,
                p95Ms = p95,
                maxMs = max,
                samples = samples
            )
        }
        return listOf(
            timing("cold_start", "Cold start", COLD_START_BASE_MS),
            timing("open_reader", "Abrir lector", OPEN_READER_BASE_MS),
            timing("sync_pull", "Sync pull", SYNC_PULL_BASE_MS),
            timing("save_highlight", "Guardar resaltado", SAVE_HIGHLIGHT_BASE_MS)
        )
    }

    override suspend fun loadResources(): PerformanceResources = withContext(Dispatchers.IO) {
        if (appContext == null) {
            // Pure fake when no context (test)
            return@withContext PerformanceResources(
                dbSizeBytes = FAKE_DB_BYTES,
                dbSizeLabel = formatBytesCompat(FAKE_DB_BYTES),
                highlightsCount = FAKE_HIGHLIGHTS_COUNT,
                cacheSizeBytes = FAKE_CACHE_BYTES,
                cacheSizeLabel = formatBytesCompat(FAKE_CACHE_BYTES),
                memoryUsageMb = 512f,
                memoryTotalMb = 1024f
            )
        }
        val dbFile = appContext.getDatabasePath("nextpage.db")
        val dbBytes = runCatching {
            var total = 0L
            if (dbFile.exists()) total += dbFile.length()
            val wal = java.io.File(dbFile.path + "-wal")
            val shm = java.io.File(dbFile.path + "-shm")
            if (wal.exists()) total += wal.length()
            if (shm.exists()) total += shm.length()
            total
        }.getOrDefault(0L)

        val cacheDir = java.io.File(appContext.filesDir, "epub_cache")
            .let { if (it.exists()) it else java.io.File(appContext.cacheDir, "epub_cache") }
        val cacheBytes = runCatching { folderSize(cacheDir) }.getOrDefault(0L)
        val displayDbBytes = if (dbBytes == 0L) FAKE_DB_BYTES else dbBytes
        val displayCacheBytes = if (cacheBytes == 0L) FAKE_CACHE_BYTES else cacheBytes
        val highlightsCount = FAKE_HIGHLIGHTS_COUNT

        val memInfo = ActivityManager.MemoryInfo()
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / BYTES_PER_MB_FLOAT
        val availMb = memInfo.availMem / BYTES_PER_MB_FLOAT
        val usedMb = (totalMb - availMb).coerceAtLeast(0f)

        PerformanceResources(
            dbSizeBytes = displayDbBytes,
            dbSizeLabel = formatBytesCompat(displayDbBytes),
            highlightsCount = highlightsCount,
            cacheSizeBytes = displayCacheBytes,
            cacheSizeLabel = formatBytesCompat(displayCacheBytes),
            memoryUsageMb = usedMb,
            memoryTotalMb = totalMb
        )
    }

    override fun loadSyncStatus(): PerformanceSyncStatus {
        val outboxFake = random.nextInt(0, 4)
        val lastSync = "hace 12 min"
        val connected = random.nextBoolean()
        return PerformanceSyncStatus(
            realtimeConnected = connected,
            lastSyncLabel = lastSync,
            outboxPending = outboxFake
        )
    }

    override fun loadDiagnostics(): PerformanceDiagnostics {
        val fps = FPS_BASE + random.nextFloat() * FPS_VARIATION
        val anrs = random.nextInt(0, 2)
        val crashes = listOf(
            PerformanceCrashEntry(
                timestamp = "2026-08-23 18:42",
                name = "NullPointerException · ReaderViewModel",
                stackSnippet = "at ReaderViewModel.loadBook(ReaderViewModel.kt:142)"
            ),
            PerformanceCrashEntry(
                timestamp = "2026-08-22 09:11",
                name = "SQLiteConstraintException · HighlightDao",
                stackSnippet = "at HighlightDao.insert(HighlightDao.kt:31)"
            )
        ).take(if (anrs == 0) 2 else 1)
        return PerformanceDiagnostics(
            fpsScroll = fps.coerceIn(0f, FPS_MAX),
            fpsLabel = String.format(Locale.US, "%.1f fps", fps.coerceIn(0f, FPS_MAX)),
            anrCount = anrs,
            crashes = crashes
        )
    }

    private fun formatBytesCompat(bytes: Long): String = when {
        bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes / BYTES_PER_MB_FLOAT)
        bytes >= BYTES_PER_KB -> String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KB_FLOAT)
        else -> "$bytes B"
    }

    private fun folderSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    companion object {
        const val SPARKLINE_SAMPLE_COUNT = 16
        const val SPARKLINE_BASE_FACTOR = 0.7f
        const val SPARKLINE_VARIATION_RANGE = 0.7f
        const val P95_FACTOR = 0.95
        const val COLD_START_BASE_MS = 820L
        const val OPEN_READER_BASE_MS = 420L
        const val SYNC_PULL_BASE_MS = 610L
        const val SAVE_HIGHLIGHT_BASE_MS = 95L
        const val FAKE_DB_BYTES = 4_820_000L
        const val FAKE_CACHE_BYTES = 2_340_000L
        const val FAKE_HIGHLIGHTS_COUNT = 37
        const val BYTES_PER_KB = 1024L
        const val BYTES_PER_MB = 1024L * 1024L
        const val BYTES_PER_KB_FLOAT = 1024f
        const val BYTES_PER_MB_FLOAT = 1024f * 1024f
        const val FPS_BASE = 56f
        const val FPS_VARIATION = 4f
        const val FPS_MAX = 120f
    }
}
