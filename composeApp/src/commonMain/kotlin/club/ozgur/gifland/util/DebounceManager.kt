package club.ozgur.gifland.util

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Utility class to debounce rapid repeated actions.
 * Prevents multiple simultaneous operations and ensures a minimum time between actions.
 *
 * Usage:
 * ```
 * val debouncer = DebounceManager()
 * debouncer.debounce("start_recording", 500) {
 *     // This will only execute once even if called multiple times rapidly
 *     startRecording()
 * }
 * ```
 */
class DebounceManager {

    /**
     * Tracks active debounce jobs by key
     */
    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Tracks last execution time by key for throttling
     */
    private val lastExecutionTimes = mutableMapOf<String, Long>()

    /**
     * Mutex for thread-safe access to internal maps
     */
    private val mutex = Mutex()

    /**
     * Debounce an action with a specified delay.
     * If called multiple times, only the last call will execute after the delay.
     *
     * @param key Unique key to identify the action
     * @param delayMs Delay in milliseconds before executing
     * @param scope Coroutine scope to launch in
     * @param action The action to execute
     */
    suspend fun debounce(
        key: String,
        delayMs: Long,
        scope: CoroutineScope = GlobalScope,
        action: suspend () -> Unit
    ) {
        mutex.withLock {
            // Cancel any existing job for this key
            activeJobs[key]?.cancel()

            // Create a new job with delay
            val job = scope.launch {
                delay(delayMs)
                mutex.withLock {
                    activeJobs.remove(key)
                    lastExecutionTimes[key] = Clock.System.now().toEpochMilliseconds()
                }
                try {
                    action()
                } catch (e: Exception) {
                    println("DebounceManager: Error executing debounced action '$key': ${e.message}")
                }
            }

            activeJobs[key] = job
        }
    }

    /**
     * Throttle an action to execute at most once per specified interval.
     * Unlike debounce, this executes immediately on first call, then ignores subsequent calls within the interval.
     *
     * @param key Unique key to identify the action
     * @param intervalMs Minimum interval between executions in milliseconds
     * @param action The action to execute
     * @return true if action was executed, false if throttled
     */
    suspend fun throttle(
        key: String,
        intervalMs: Long,
        action: suspend () -> Unit
    ): Boolean {
        mutex.withLock {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val lastTime = lastExecutionTimes[key] ?: 0

            if (currentTime - lastTime >= intervalMs) {
                lastExecutionTimes[key] = currentTime
                try {
                    action()
                    return true
                } catch (e: Exception) {
                    println("DebounceManager: Error executing throttled action '$key': ${e.message}")
                    return false
                }
            }
            return false
        }
    }

    /**
     * Execute an action immediately, canceling any pending debounced execution.
     *
     * @param key Unique key to identify the action
     * @param action The action to execute
     */
    suspend fun executeNow(
        key: String,
        action: suspend () -> Unit
    ) {
        mutex.withLock {
            // Cancel any pending debounced job
            activeJobs[key]?.cancel()
            activeJobs.remove(key)
            lastExecutionTimes[key] = Clock.System.now().toEpochMilliseconds()
        }
        try {
            action()
        } catch (e: Exception) {
            println("DebounceManager: Error executing immediate action '$key': ${e.message}")
        }
    }

    /**
     * Check if an action is currently pending (debounced but not yet executed).
     *
     * @param key Unique key to identify the action
     * @return true if action is pending
     */
    suspend fun isPending(key: String): Boolean {
        mutex.withLock {
            return activeJobs[key]?.isActive == true
        }
    }

    /**
     * Cancel a pending debounced action.
     *
     * @param key Unique key to identify the action
     */
    suspend fun cancel(key: String) {
        mutex.withLock {
            activeJobs[key]?.cancel()
            activeJobs.remove(key)
        }
    }

    /**
     * Cancel all pending debounced actions.
     */
    suspend fun cancelAll() {
        mutex.withLock {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
    }

    /**
     * Get the time since last execution for a key.
     *
     * @param key Unique key to identify the action
     * @return Time in milliseconds since last execution, or null if never executed
     */
    suspend fun getTimeSinceLastExecution(key: String): Long? {
        mutex.withLock {
            val lastTime = lastExecutionTimes[key] ?: return null
            return Clock.System.now().toEpochMilliseconds() - lastTime
        }
    }

    /**
     * Clear all internal state.
     */
    suspend fun clear() {
        mutex.withLock {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            lastExecutionTimes.clear()
        }
    }

    companion object {
        /**
         * Singleton instance for global use
         */
        val global = DebounceManager()

        /**
         * Predefined debounce keys for common actions
         */
        object Keys {
            const val START_RECORDING = "start_recording"
            const val STOP_RECORDING = "stop_recording"
            const val TOGGLE_RECORDING = "toggle_recording"
            const val COUNTDOWN_START = "countdown_start"
            const val COUNTDOWN_CANCEL = "countdown_cancel"
            const val SHOW_WINDOW = "show_window"
            const val HIDE_WINDOW = "hide_window"
            const val TOGGLE_WINDOW = "toggle_window"
            const val QUICK_PANEL_TOGGLE = "quick_panel_toggle"
            const val AREA_SELECT = "area_select"
        }

        /**
         * Predefined delay values in milliseconds
         */
        object Delays {
            const val RECORDING = 500L
            const val COUNTDOWN = 300L
            const val WINDOW = 200L
            const val UI = 100L
            const val HEAVY_OPERATION = 1000L
        }
    }
}