package club.ozgur.gifland.platform

import club.ozgur.gifland.domain.model.CaptureRegion
import club.ozgur.gifland.util.Log
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

/**
 * Utility for detecting monitors and determining which monitor contains a given point or window.
 *
 * Multi-monitor best practices:
 * 1. Use window center point to determine the "owning" monitor
 * 2. Fall back to mouse position if window position unavailable
 * 3. Fall back to primary monitor as last resort
 */
object MonitorDetector {
    private const val TAG = "MonitorDetector"

    /**
     * Data class representing a monitor with its bounds and display ID
     */
    data class MonitorInfo(
        val device: GraphicsDevice,
        val bounds: Rectangle,
        val displayId: String,
        val isPrimary: Boolean
    )

    /**
     * Get all available monitors
     */
    fun getAllMonitors(): List<MonitorInfo> {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultDevice = ge.defaultScreenDevice

        return ge.screenDevices.mapIndexed { index, device ->
            MonitorInfo(
                device = device,
                bounds = device.defaultConfiguration.bounds,
                displayId = device.iDstring ?: "display_$index",
                isPrimary = device == defaultDevice
            )
        }
    }

    /**
     * Get the monitor that contains the given point (e.g., window center)
     * Returns null if no monitor contains the point
     */
    fun getMonitorAtPoint(point: Point): MonitorInfo? {
        return getAllMonitors().find { it.bounds.contains(point) }
    }

    /**
     * Get the monitor that contains the center of the given window bounds
     */
    fun getMonitorForWindow(windowX: Int, windowY: Int, windowWidth: Int, windowHeight: Int): MonitorInfo? {
        val centerX = windowX + windowWidth / 2
        val centerY = windowY + windowHeight / 2
        return getMonitorAtPoint(Point(centerX, centerY))
    }

    /**
     * Get the monitor at the current mouse cursor position
     */
    fun getMonitorAtMouse(): MonitorInfo? {
        return try {
            val mousePos = java.awt.MouseInfo.getPointerInfo()?.location
            mousePos?.let { getMonitorAtPoint(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get mouse position", e)
            null
        }
    }

    /**
     * Get the primary monitor
     */
    fun getPrimaryMonitor(): MonitorInfo? {
        return getAllMonitors().find { it.isPrimary }
    }

    /**
     * Get the best monitor for full-screen recording based on priority:
     * 1. Monitor containing the window (if position provided)
     * 2. Monitor under mouse cursor
     * 3. Primary monitor
     * 4. First available monitor
     */
    fun getBestMonitorForRecording(
        windowX: Int? = null,
        windowY: Int? = null,
        windowWidth: Int? = null,
        windowHeight: Int? = null
    ): MonitorInfo {
        val monitors = getAllMonitors()
        if (monitors.isEmpty()) {
            throw IllegalStateException("No monitors detected")
        }

        // Priority 1: Window's monitor
        if (windowX != null && windowY != null && windowWidth != null && windowHeight != null) {
            val windowMonitor = getMonitorForWindow(windowX, windowY, windowWidth, windowHeight)
            if (windowMonitor != null) {
                Log.d(TAG, "Using window's monitor: ${windowMonitor.displayId} (${windowMonitor.bounds})")
                return windowMonitor
            }
        }

        // Priority 2: Mouse cursor's monitor
        val mouseMonitor = getMonitorAtMouse()
        if (mouseMonitor != null) {
            Log.d(TAG, "Using mouse cursor's monitor: ${mouseMonitor.displayId} (${mouseMonitor.bounds})")
            return mouseMonitor
        }

        // Priority 3: Primary monitor
        val primaryMonitor = getPrimaryMonitor()
        if (primaryMonitor != null) {
            Log.d(TAG, "Using primary monitor: ${primaryMonitor.displayId} (${primaryMonitor.bounds})")
            return primaryMonitor
        }

        // Priority 4: First available
        Log.d(TAG, "Using first available monitor: ${monitors.first().displayId}")
        return monitors.first()
    }

    /**
     * Convert MonitorInfo to CaptureRegion for recording
     */
    fun MonitorInfo.toCaptureRegion(): CaptureRegion {
        return CaptureRegion(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            displayId = displayId
        )
    }

    /**
     * Log all detected monitors for debugging
     */
    fun logAllMonitors() {
        val monitors = getAllMonitors()
        Log.d(TAG, "=== Detected ${monitors.size} monitor(s) ===")
        monitors.forEachIndexed { index, monitor ->
            Log.d(TAG, "Monitor $index: ${monitor.displayId}")
            Log.d(TAG, "  Bounds: ${monitor.bounds}")
            Log.d(TAG, "  Primary: ${monitor.isPrimary}")
        }
    }
}
