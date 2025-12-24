package club.ozgur.gifland.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Data class representing window position and size
 */
data class WindowBounds(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 360,
    val height: Int = 480
)

/**
 * Repository for managing window visibility state in a thread-safe manner.
 * Prevents race conditions when multiple sources try to show/hide the window simultaneously.
 * Also tracks window position for multi-monitor aware recording.
 */
class WindowStateRepository {

    /**
     * Internal mutable state for window visibility
     */
    private val _windowVisible = MutableStateFlow(true)

    /**
     * Internal mutable state for window position and size
     */
    private val _windowBounds = MutableStateFlow(WindowBounds())

    /**
     * Public read-only state for window bounds
     */
    val windowBounds: StateFlow<WindowBounds> = _windowBounds.asStateFlow()

    /**
     * Public read-only state for window visibility
     */
    val windowVisible: StateFlow<Boolean> = _windowVisible.asStateFlow()

    /**
     * Internal mutable state for minimize-to-tray preference
     */
    private val _minimizeToTray = MutableStateFlow(true)

    /**
     * Public read-only state for minimize-to-tray preference
     */
    val minimizeToTray: StateFlow<Boolean> = _minimizeToTray.asStateFlow()

    /**
     * Mutex to ensure thread-safe state updates
     */
    private val windowMutex = Mutex()

    /**
     * Tracks the reason for the last visibility change (for debugging)
     */
    private var lastVisibilityChangeReason: String = "initial"

    /**
     * Set window visibility with thread safety
     * @param visible Whether the window should be visible
     * @param reason The reason for the visibility change (for debugging)
     */
    suspend fun setWindowVisible(visible: Boolean, reason: String = "unknown") {
        windowMutex.withLock {
            if (_windowVisible.value != visible) {
                println("WindowStateRepository: Changing visibility from ${_windowVisible.value} to $visible (reason: $reason)")
                _windowVisible.value = visible
                lastVisibilityChangeReason = reason
            }
        }
    }

    /**
     * Toggle window visibility
     * @param reason The reason for toggling (for debugging)
     */
    suspend fun toggleWindowVisibility(reason: String = "user_toggle") {
        windowMutex.withLock {
            val newValue = !_windowVisible.value
            println("WindowStateRepository: Toggling visibility to $newValue (reason: $reason)")
            _windowVisible.value = newValue
            lastVisibilityChangeReason = reason
        }
    }

    /**
     * Show the window
     * @param reason The reason for showing (for debugging)
     */
    suspend fun showWindow(reason: String = "show_requested") {
        setWindowVisible(true, reason)
    }

    /**
     * Hide the window
     * @param reason The reason for hiding (for debugging)
     */
    suspend fun hideWindow(reason: String = "hide_requested") {
        setWindowVisible(false, reason)
    }

    /**
     * Minimize to system tray
     * Hides the window but keeps the app running
     */
    suspend fun minimizeToTray() {
        windowMutex.withLock {
            if (_minimizeToTray.value && _windowVisible.value) {
                println("WindowStateRepository: Minimizing to tray")
                _windowVisible.value = false
                lastVisibilityChangeReason = "minimize_to_tray"
            }
        }
    }

    /**
     * Restore from system tray
     * Shows the window after being minimized to tray
     */
    suspend fun restoreFromTray() {
        windowMutex.withLock {
            if (!_windowVisible.value) {
                println("WindowStateRepository: Restoring from tray")
                _windowVisible.value = true
                lastVisibilityChangeReason = "restore_from_tray"
            }
        }
    }

    /**
     * Set minimize-to-tray preference
     * @param enabled Whether minimize-to-tray should be enabled
     */
    suspend fun setMinimizeToTrayEnabled(enabled: Boolean) {
        windowMutex.withLock {
            _minimizeToTray.value = enabled
        }
    }

    /**
     * Get the last visibility change reason (for debugging)
     */
    fun getLastVisibilityChangeReason(): String = lastVisibilityChangeReason

    /**
     * Force show window regardless of current state
     * Use this for critical operations that must show the window
     */
    suspend fun forceShowWindow(reason: String = "forced") {
        windowMutex.withLock {
            println("WindowStateRepository: Force showing window (reason: $reason)")
            _windowVisible.value = true
            lastVisibilityChangeReason = "force_$reason"
        }
    }

    /**
     * Update window position and size
     * Called when the window is moved or resized
     * @param x Window X position in screen coordinates
     * @param y Window Y position in screen coordinates
     * @param width Window width
     * @param height Window height
     */
    fun updateWindowBounds(x: Int, y: Int, width: Int, height: Int) {
        val newBounds = WindowBounds(x, y, width, height)
        if (_windowBounds.value != newBounds) {
            _windowBounds.value = newBounds
        }
    }

    /**
     * Get the current window bounds synchronously
     */
    fun getCurrentWindowBounds(): WindowBounds = _windowBounds.value
}