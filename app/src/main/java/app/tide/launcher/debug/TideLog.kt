package app.tide.launcher.debug

import android.util.Log
import androidx.compose.runtime.Immutable

/**
 * Structured logging for the launcher.
 *
 * Everything goes through here rather than `android.util.Log` directly, for two
 * reasons: the release ProGuard rules strip `Log.v`/`Log.d` by name, and the
 * in-memory ring buffer feeds the debug overlay without a second logging path.
 */

@Immutable
data class LogLine(
    val timestampMs: Long,
    val tag: String,
    val message: String,
    val isError: Boolean = false,
)

object TideLog {

    private const val TAG = "Tide"
    private const val CAPACITY = 120

    /** Most recent lines, newest last. Bounded so a long session cannot grow it
     *  without limit inside a launcher that is never destroyed. */
    private val recent = ArrayDeque<LogLine>(CAPACITY)
    private val lock = Any()

    /** Populated by [DebugOverlay] so log lines trigger recomposition. */
    @Volatile
    var version: Int = 0
        private set

    fun d(tag: String, message: String) = log("D", tag, message, false)

    fun i(tag: String, message: String) = log("I", tag, message, false)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        log("W", tag, if (error == null) message else "$message: ${error.message}", true)

    fun error(tag: String, message: String, error: Throwable? = null) =
        log("E", tag, if (error == null) message else "$message: ${error.message}", true)

    private fun log(level: String, tag: String, message: String, isError: Boolean) {
        val line = LogLine(System.currentTimeMillis(), "$level/$tag", message, isError)
        synchronized(lock) {
            if (recent.size >= CAPACITY) recent.removeFirst()
            recent.addLast(line)
            version++
        }

        when (level) {
            "E" -> Log.e(TAG, "$tag: $message", null)
            "W" -> Log.w(TAG, "$tag: $message")
            "I" -> Log.i(TAG, "$tag: $message")
            else -> Log.d(TAG, "$tag: $message")
        }
    }

    fun snapshot(): List<LogLine> = synchronized(lock) { recent.toList() }

    fun clear() = synchronized(lock) {
        recent.clear()
        version++
    }
}

/** Shorthands, so call sites read `logi("grid", "...")` rather than `TideLog.i`. */
fun logd(tag: String, message: String) = TideLog.d(tag, message)
fun logi(tag: String, message: String) = TideLog.i(tag, message)
fun logw(tag: String, message: String, error: Throwable? = null) = TideLog.warn(tag, message, error)
fun loge(tag: String, message: String, error: Throwable? = null) = TideLog.error(tag, message, error)