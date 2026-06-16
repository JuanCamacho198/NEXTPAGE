package com.nextpage.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory debug event log + Logcat sink.
 *
 * - Always available (no [BuildConfig.DEBUG] gate). This keeps call sites
 *   free of `if (BuildConfig.DEBUG)` checks; the visibility of the
 *   debug UI is the only runtime gate.
 * - Thread-safe: mutations go through [MutableStateFlow.update].
 * - Capped at [MAX_EVENTS] entries (oldest dropped).
 */
object DebugLog {

    enum class Level { INFO, WARN, ERROR, SUCCESS }

    data class DebugEvent(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private const val MAX_EVENTS = 100

    private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
    val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

    fun log(level: Level, tag: String, message: String) {
        val event = DebugEvent(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        _events.update { current ->
            val updated = ArrayList<DebugEvent>(minOf(current.size + 1, MAX_EVENTS))
            updated.add(event)
            val keep = current.take(MAX_EVENTS - 1)
            updated.addAll(keep)
            updated
        }
        val priority = when (level) {
            Level.INFO -> Log.INFO
            Level.WARN -> Log.WARN
            Level.ERROR -> Log.ERROR
            Level.SUCCESS -> Log.INFO
        }
        Log.println(priority, tag, message)
    }

    fun info(tag: String, message: String) = log(Level.INFO, tag, message)
    fun warn(tag: String, message: String) = log(Level.WARN, tag, message)
    fun error(tag: String, message: String) = log(Level.ERROR, tag, message)
    fun success(tag: String, message: String) = log(Level.SUCCESS, tag, message)

    fun clear() {
        _events.update { emptyList() }
    }

    /**
     * Plain-text dump of all events (newest first), for clipboard sharing.
     */
    fun toText(): String {
        val list = _events.value
        if (list.isEmpty()) return "(empty)"
        val sb = StringBuilder()
        for (e in list) {
            sb.append('[')
                .append(e.level.name)
                .append("] ")
                .append(e.tag)
                .append(": ")
                .append(e.message)
                .append('\n')
        }
        return sb.toString()
    }
}
