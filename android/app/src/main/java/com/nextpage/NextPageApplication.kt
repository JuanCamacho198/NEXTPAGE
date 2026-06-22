package com.nextpage

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nextpage.debug.DebugLog
import com.nextpage.presentation.theme.CoilModule
import org.json.JSONObject

/**
 * Application entry point.
 *
 * - Implements [ImageLoaderFactory] so Coil's singleton uses the tuned
 *   [CoilModule.imageLoader] (15s connect, 30s read, retry-on-connection-failure,
 *   25% memory cache, 64MB disk cache) for every `AsyncImage` call.
 * - Installs an [Thread.UncaughtExceptionHandler] in debug builds that
 *   captures the last crash (timestamp, thread, message, stack trace)
 *   to `SharedPreferences` so [MainActivity] can post a notification
 *   on the next launch.
 * - Chains to the previous default handler so the OS still gets the
 *   crash report (and the process still dies as expected).
 */
class NextPageApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "NextPageApplication"
        const val PREFS_NAME = "nextpage_debug_crash"
        const val KEY_LAST_CRASH = "last_crash"
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            installCrashHandler()
        }
    }

    override fun newImageLoader(): ImageLoader = CoilModule.imageLoader(this)

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val payload = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("threadName", thread.name)
                    put("message", throwable.message ?: throwable::class.java.simpleName)
                    put("stackTrace", stackTrace)
                }
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, payload.toString())
                    .apply()
                DebugLog.error(TAG, "Captured crash on thread ${thread.name}: ${throwable.message}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to capture crash", t)
            }
            // Chain to the previous handler so the OS still gets the report.
            previous?.uncaughtException(thread, throwable)
        }
        DebugLog.info("CrashHandler", "Installed")
    }
}
