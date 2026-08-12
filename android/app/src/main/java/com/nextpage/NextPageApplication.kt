package com.nextpage

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.debug.CrashLogStore
import com.nextpage.debug.DebugLog
import com.nextpage.presentation.theme.CoilModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Application entry point.
 *
 * - Implements [ImageLoaderFactory] so Coil's singleton uses the tuned
 *   [CoilModule.imageLoader] (15s connect, 30s read, retry-on-connection-failure,
 *   25% memory cache, 64MB disk cache) for every `AsyncImage` call.
 * - Installs an [Thread.UncaughtExceptionHandler] (always active) that
 *   captures the last crash (timestamp, thread, message, stack trace)
 *   to `SharedPreferences` (for notification path) and writes a crash
 *   file with a log snapshot to `cacheDir/crashes/`.
 * - Chains to the previous default handler so the OS still gets the
 *   crash report (and the process still dies as expected).
 */
class NextPageApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "NextPageApplication"
        const val PREFS_NAME = "nextpage_debug_crash"
        const val KEY_LAST_CRASH = "last_crash"
    }

    private lateinit var debugLogScope: CoroutineScope
    private lateinit var crashLogStore: CrashLogStore
    private lateinit var crashDir: File

    private val supabaseWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        crashDir = File(cacheDir, "crashes").also { it.mkdirs() }
        val logDir = File(cacheDir, "logs").also { it.mkdirs() }
        debugLogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        crashLogStore = CrashLogStore(logDir)
        crashLogStore.cleanup(crashDir)
        DebugLog.init(debugLogScope, crashLogStore)
        installCrashHandler()

        // Warm the Supabase client on a background thread so the first Activity
        // frame never pays the ~2s client-construction cost on the main thread
        // (measured via logcat: AppContainer fully initialized in ~1950ms).
        // The client is created lazily on first use if this warm-up races.
        supabaseWarmupScope.launch {
            runCatching { SupabaseClientProvider.client }
        }
    }

    override fun newImageLoader(): ImageLoader = CoilModule.imageLoader(this)

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val crashJson = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("threadName", thread.name)
                    put("message", throwable.message ?: throwable::class.java.simpleName)
                    put("stackTrace", stackTrace)
                }
                // Save to SharedPreferences (existing — for notification)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, crashJson.toString())
                    .apply()

                // Save crash + log snapshot to file
                runCatching {
                    val logs = crashLogStore.snapshot()
                    val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
                    crashFile.bufferedWriter().use { out ->
                        out.write("Timestamp: ${crashJson.optLong("timestamp")}\n")
                        out.write("Thread: ${crashJson.optString("threadName")}\n")
                        out.write("Message: ${crashJson.optString("message")}\n")
                        out.write("--- Stack Trace ---\n")
                        out.write(crashJson.optString("stackTrace"))
                        if (logs.isNotEmpty()) {
                            out.write("\n--- Logs ---\n")
                            logs.forEach { out.write("$it\n") }
                        }
                    }
                }

                DebugLog.error(TAG, "Captured crash on thread ${thread.name}: ${throwable.message}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to capture crash", t)
            }
            previous?.uncaughtException(thread, throwable)
        }
        DebugLog.info("CrashHandler", "Installed")
    }
}
