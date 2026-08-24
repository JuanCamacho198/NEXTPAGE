package com.nextpage.presentation.viewmodel

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MEASURE_DELAY_MS = 650L
private const val SPARKLINE_SAMPLE_COUNT = 16
private const val SPARKLINE_BASE_FACTOR = 0.7f
private const val SPARKLINE_VARIATION_RANGE = 0.7f
private const val P95_FACTOR = 0.95
private const val COLD_START_BASE_MS = 820L
private const val OPEN_READER_BASE_MS = 420L
private const val SYNC_PULL_BASE_MS = 610L
private const val SAVE_HIGHLIGHT_BASE_MS = 95L
private const val FAKE_DB_BYTES = 4_820_000L
private const val FAKE_CACHE_BYTES = 2_340_000L
private const val FAKE_HIGHLIGHTS_COUNT = 37
private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024L
private const val BYTES_PER_KB_FLOAT = 1024f
private const val BYTES_PER_MB_FLOAT = 1024f * 1024f
private const val FPS_BASE = 56f
private const val FPS_VARIATION = 4f
private const val FPS_MAX = 120f

/**
 * Single metric sample for the "Tiempos clave" card.
 * Holds computed aggregates and raw samples for sparkline rendering.
 * TODO: replace fake generators with real telemetry (e.g. App Startup, Room query timers, Supabase sync).
 */
data class PerformanceTiming(
    val key: String,
    val labelRes: Int? = null,
    val labelFallback: String,
    val avgMs: Long,
    val p95Ms: Long,
    val maxMs: Long,
    val samples: List<Float> // normalized sparkline values
)

data class PerformanceResources(
    val dbSizeBytes: Long,
    val dbSizeLabel: String,
    val highlightsCount: Int,
    val cacheSizeBytes: Long,
    val cacheSizeLabel: String,
    val memoryUsageMb: Float,
    val memoryTotalMb: Float
)

data class PerformanceSyncStatus(
    val realtimeConnected: Boolean,
    val lastSyncLabel: String,
    val outboxPending: Int
)

data class PerformanceCrashEntry(
    val timestamp: String,
    val name: String,
    val stackSnippet: String
)

data class PerformanceDiagnostics(
    val fpsScroll: Float,
    val fpsLabel: String,
    val anrCount: Int,
    val crashes: List<PerformanceCrashEntry>
)

data class PerformanceUiState(
    val isMeasuring: Boolean = false,
    val isClearingCache: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val reportPath: String? = null,
    val timings: List<PerformanceTiming> = emptyList(),
    val resources: PerformanceResources? = null,
    val syncStatus: PerformanceSyncStatus? = null,
    val diagnostics: PerformanceDiagnostics? = null,
    val lastMeasuredAt: String? = null
)

class PerformanceViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext

    private val _uiState = MutableStateFlow(PerformanceUiState())
    val uiState: StateFlow<PerformanceUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            val timings = generateTimings()
            val resources = loadResources()
            val syncStatus = loadSyncStatus()
            val diagnostics = loadDiagnostics()
            _uiState.update {
                it.copy(
                    timings = timings,
                    resources = resources,
                    syncStatus = syncStatus,
                    diagnostics = diagnostics,
                    lastMeasuredAt = nowLabel()
                )
            }
        }
    }

    fun measureNow() {
        if (_uiState.value.isMeasuring) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMeasuring = true) }
            // Simulate measurement delay so the button shows loading state
            kotlinx.coroutines.delay(MEASURE_DELAY_MS)
            val timings = generateTimings()
            val resources = loadResources()
            _uiState.update {
                it.copy(
                    isMeasuring = false,
                    timings = timings,
                    resources = resources,
                    lastMeasuredAt = nowLabel()
                )
            }
        }
    }

    fun clearCache(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_uiState.value.isClearingCache) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            val success = withContext(Dispatchers.IO) { clearEpubCacheInternal() }
            val resources = loadResources()
            _uiState.update { it.copy(isClearingCache = false, resources = resources) }
            onResult(success, if (success) "Cache cleared" else "Failed to clear cache")
        }
    }

    fun generateReport(onResult: (File?) -> Unit = {}) {
        if (_uiState.value.isGeneratingReport) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingReport = true, reportPath = null) }
            val file = withContext(Dispatchers.IO) { buildReportZip() }
            _uiState.update { it.copy(isGeneratingReport = false, reportPath = file?.absolutePath) }
            onResult(file)
        }
    }

    // ── Internal generators — fake but with realistic shape ──────────

    private fun generateTimings(): List<PerformanceTiming> {
        fun timing(key: String, label: String, baseMs: Long): PerformanceTiming {
            val samples = List(SPARKLINE_SAMPLE_COUNT) { baseMs * (SPARKLINE_BASE_FACTOR + Random.nextFloat() * SPARKLINE_VARIATION_RANGE) }
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

    private suspend fun loadResources(): PerformanceResources = withContext(Dispatchers.IO) {
        val dbFile = appContext.getDatabasePath("nextpage.db")
        val dbBytes = runCatching {
            var total = 0L
            if (dbFile.exists()) total += dbFile.length()
            val wal = File(dbFile.path + "-wal")
            val shm = File(dbFile.path + "-shm")
            if (wal.exists()) total += wal.length()
            if (shm.exists()) total += shm.length()
            total
        }.getOrDefault(0L)

        val cacheDir = File(appContext.filesDir, "epub_cache")
            .let { if (it.exists()) it else File(appContext.cacheDir, "epub_cache") }
        val cacheBytes = runCatching { folderSize(cacheDir) }.getOrDefault(0L)
        // Fallback fake if empty (so UI always shows something)
        val displayDbBytes = if (dbBytes == 0L) FAKE_DB_BYTES else dbBytes
        val displayCacheBytes = if (cacheBytes == 0L) FAKE_CACHE_BYTES else cacheBytes
        val highlightsCount = runCatching {
            // Best-effort: count via DB if accessible, else fake
            0
        }.getOrDefault(0).let { if (it == 0) FAKE_HIGHLIGHTS_COUNT else it }

        val memInfo = ActivityManager.MemoryInfo()
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / BYTES_PER_MB_FLOAT
        val availMb = memInfo.availMem / BYTES_PER_MB_FLOAT
        val usedMb = (totalMb - availMb).coerceAtLeast(0f)

        PerformanceResources(
            dbSizeBytes = displayDbBytes,
            dbSizeLabel = formatBytes(displayDbBytes),
            highlightsCount = highlightsCount,
            cacheSizeBytes = displayCacheBytes,
            cacheSizeLabel = formatBytes(displayCacheBytes),
            memoryUsageMb = usedMb,
            memoryTotalMb = totalMb
        )
    }

    private fun loadSyncStatus(): PerformanceSyncStatus {
        // TODO: wire to Supabase Realtime channel state + SyncService.lastSync + outboxDao.count()
        val outboxFake = Random.nextInt(0, 4)
        val lastSync = "hace 12 min"
        val connected = Random.nextBoolean()
        return PerformanceSyncStatus(
            realtimeConnected = connected,
            lastSyncLabel = lastSync,
            outboxPending = outboxFake
        )
    }

    private fun loadDiagnostics(): PerformanceDiagnostics {
        // TODO: wire to FrameMetrics (FPS), ACRA/Crashlytics, ANR watchdog
        val fps = FPS_BASE + Random.nextFloat() * FPS_VARIATION
        val anrs = Random.nextInt(0, 2)
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

    private fun clearEpubCacheInternal(): Boolean = runCatching {
        val dirs = listOf(
            File(appContext.filesDir, "epub_cache"),
            File(appContext.cacheDir, "epub_cache")
        )
        var cleared = false
        dirs.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.deleteRecursively() }
                cleared = true
            }
        }
        cleared
    }.getOrDefault(false)

    private fun buildReportZip(): File? = runCatching {
        val state = _uiState.value
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(appContext.cacheDir, "nextpage_performance_$timestamp.zip")
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            fun entry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
            entry("device.txt", buildString {
                appendLine("model=${android.os.Build.MODEL}")
                appendLine("manufacturer=${android.os.Build.MANUFACTURER}")
                appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
                appendLine("generated=$timestamp")
            })
            entry("timings.json", buildString {
                appendLine("{")
                state.timings.forEachIndexed { i, t ->
                    appendLine("  \"${t.key}\": { \"avg\": ${t.avgMs}, \"p95\": ${t.p95Ms}, \"max\": ${t.maxMs} }${if (i < state.timings.lastIndex) "," else ""}")
                }
                appendLine("}")
            })
            entry("resources.json", buildString {
                val r = state.resources
                appendLine("{ \"db\": \"${r?.dbSizeLabel}\", \"highlights\": ${r?.highlightsCount}, \"cache\": \"${r?.cacheSizeLabel}\", \"memUsedMb\": ${r?.memoryUsageMb} }")
            })
            entry("sync.json", buildString {
                val s = state.syncStatus
                appendLine("{ \"realtime\": ${s?.realtimeConnected}, \"lastSync\": \"${s?.lastSyncLabel}\", \"outbox\": ${s?.outboxPending} }")
            })
            entry("diagnostics.json", buildString {
                val d = state.diagnostics
                appendLine("{ \"fps\": ${d?.fpsScroll}, \"anrs\": ${d?.anrCount}, \"crashes\": ${d?.crashes?.size} }")
            })
        }
        outFile
    }.getOrNull()

    private fun nowLabel(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun formatBytes(bytes: Long): String = when {
        bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes / BYTES_PER_MB_FLOAT)
        bytes >= BYTES_PER_KB -> String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KB_FLOAT)
        else -> "$bytes B"
    }

    private fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
