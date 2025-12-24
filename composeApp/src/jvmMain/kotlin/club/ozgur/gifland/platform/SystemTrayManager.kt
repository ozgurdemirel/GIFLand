package club.ozgur.gifland.platform

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import club.ozgur.gifland.core.OutputFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Represents the current tray state for menu rendering
 */
sealed class TrayState {
    data class Idle(
        val countdownSeconds: Int? = null
    ) : TrayState()

    data class Recording(
        val duration: Int,
        val maxDuration: Int,
        val frameCount: Int,
        val isPaused: Boolean
    ) : TrayState()

    data class Processing(
        val progress: Int
    ) : TrayState()
}

/**
 * Composable function to integrate system tray with Compose Desktop
 * Now supports state-based menus for different app states
 */
@Composable
fun ApplicationScope.SystemTray(
    icon: Painter = painterResource("icons/tray-icon.svg"),
    trayState: TrayState = TrayState.Idle(),
    tooltip: String = "GIF Land",
    // Idle state actions
    onStartRecording: () -> Unit = {},
    onQuickRecord: () -> Unit = {},
    onSelectArea: () -> Unit = {},
    onCancelCountdown: () -> Unit = {},
    defaultDelaySeconds: Int = 3,
    // Last recording actions
    lastRecordingPath: String? = null,
    onOpenLastRecording: () -> Unit = {},
    onCopyToClipboard: () -> Unit = {},
    // Format selection
    currentFormat: OutputFormat = OutputFormat.GIF,
    onFormatChange: (OutputFormat) -> Unit = {},
    // Recording state actions
    onPauseResume: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    // Common actions
    onShowMainWindow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    // Blinking effect for recording state
    val blink = remember { mutableStateOf(false) }
    val isRecording = trayState is TrayState.Recording

    LaunchedEffect(key1 = isRecording) {
        blink.value = false
        if (isRecording) {
            try {
                while (isRecording && this.isActive) {
                    blink.value = !blink.value
                    delay(400)
                }
            } finally {
                blink.value = false
            }
        }
    }

    // Create icon with recording indicator overlay
    fun overlayRecordingPainter(base: Painter, showDot: Boolean, isPaused: Boolean = false): Painter {
        if (!showDot) return base
        return object : Painter() {
            override val intrinsicSize = base.intrinsicSize
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                with(base) { draw(size) }
                val r = (size.minDimension * 0.18f).coerceAtLeast(1f)
                val pad = size.minDimension * 0.10f
                val center = androidx.compose.ui.geometry.Offset(size.width - r - pad, size.height - r - pad)
                // White ring for contrast
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = r * 1.4f, center = center)
                // Dot color based on state
                val dotColor = if (isPaused) {
                    androidx.compose.ui.graphics.Color(0xFFFFAA00) // Orange for paused
                } else {
                    androidx.compose.ui.graphics.Color(0xFFFF3B30) // Red for recording
                }
                drawCircle(color = dotColor, radius = r, center = center)
            }
        }
    }

    // Processing icon with progress indicator
    fun overlayProcessingPainter(base: Painter, progress: Int): Painter {
        return object : Painter() {
            override val intrinsicSize = base.intrinsicSize
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                with(base) { draw(size) }
                val r = (size.minDimension * 0.18f).coerceAtLeast(1f)
                val pad = size.minDimension * 0.10f
                val center = androidx.compose.ui.geometry.Offset(size.width - r - pad, size.height - r - pad)
                // White ring
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = r * 1.4f, center = center)
                // Blue dot for processing
                drawCircle(color = androidx.compose.ui.graphics.Color(0xFF007AFF), radius = r, center = center)
            }
        }
    }

    val currentIcon = when (val state = trayState) {
        is TrayState.Recording -> overlayRecordingPainter(icon, blink.value, state.isPaused)
        is TrayState.Processing -> overlayProcessingPainter(icon, state.progress)
        is TrayState.Idle -> icon
    }

    val trayStateRemember = rememberTrayState()

    // Use key to force tray recreation when state TYPE changes
    val stateKey = when (trayState) {
        is TrayState.Idle -> "idle"
        is TrayState.Recording -> "recording"
        is TrayState.Processing -> "processing"
    }

    key(stateKey) {
        Tray(
            icon = currentIcon,
            state = trayStateRemember,
            tooltip = tooltip,
            onAction = { onShowMainWindow() },
            menu = {
                when (val state = trayState) {
                    is TrayState.Idle -> IdleMenu(
                    countdownSeconds = state.countdownSeconds,
                    defaultDelaySeconds = defaultDelaySeconds,
                    onStartRecording = onStartRecording,
                    onQuickRecord = onQuickRecord,
                    onSelectArea = onSelectArea,
                    onCancelCountdown = onCancelCountdown,
                    lastRecordingPath = lastRecordingPath,
                    onOpenLastRecording = onOpenLastRecording,
                    onCopyToClipboard = onCopyToClipboard,
                    currentFormat = currentFormat,
                    onFormatChange = onFormatChange,
                    onShowMainWindow = onShowMainWindow,
                    onOpenSettings = onOpenSettings,
                    onExit = onExit
                )
                is TrayState.Recording -> RecordingMenu(
                    duration = state.duration,
                    maxDuration = state.maxDuration,
                    frameCount = state.frameCount,
                    isPaused = state.isPaused,
                    onPauseResume = onPauseResume,
                    onStopRecording = onStopRecording,
                    onCancelRecording = onCancelRecording,
                    onShowMainWindow = onShowMainWindow
                )
                is TrayState.Processing -> ProcessingMenu(
                    progress = state.progress,
                    onShowMainWindow = onShowMainWindow
                )
            }
        }
    )
    }
}

/**
 * Menu for Idle state
 */
@Composable
private fun MenuScope.IdleMenu(
    countdownSeconds: Int?,
    defaultDelaySeconds: Int,
    onStartRecording: () -> Unit,
    onQuickRecord: () -> Unit,
    onSelectArea: () -> Unit,
    onCancelCountdown: () -> Unit,
    lastRecordingPath: String?,
    onOpenLastRecording: () -> Unit,
    onCopyToClipboard: () -> Unit,
    currentFormat: OutputFormat,
    onFormatChange: (OutputFormat) -> Unit,
    onShowMainWindow: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit
) {
    // Recording controls
    if (countdownSeconds != null) {
        Item("Cancel (${countdownSeconds}s)", onClick = onCancelCountdown)
    } else {
        if (defaultDelaySeconds > 0) {
            Item("Record (${defaultDelaySeconds}s delay)", onClick = onStartRecording)
        } else {
            Item("Record", onClick = onStartRecording)
        }
    }
    Item("Select Area", onClick = onSelectArea)

    Separator()

    // Last recording actions
    if (lastRecordingPath != null) {
        val fileName = lastRecordingPath.substringAfterLast("/").substringAfterLast("\\")
        Item("Open: $fileName", onClick = onOpenLastRecording)
        Item("Copy to Clipboard", onClick = onCopyToClipboard)
        Separator()
    }

    // Format selection submenu
    Menu("Format: ${currentFormat.name}") {
        Item(
            text = if (currentFormat == OutputFormat.GIF) "✓ GIF" else "GIF",
            onClick = { onFormatChange(OutputFormat.GIF) }
        )
        Item(
            text = if (currentFormat == OutputFormat.WEBP) "✓ WebP" else "WebP",
            onClick = { onFormatChange(OutputFormat.WEBP) }
        )
    }

    Separator()

    Item("Show Window", onClick = onShowMainWindow)
    Item("Settings", onClick = onOpenSettings)

    Separator()

    Item("Exit", onClick = onExit)
}

/**
 * Menu for Recording state
 */
@Composable
private fun MenuScope.RecordingMenu(
    duration: Int,
    maxDuration: Int,
    frameCount: Int,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onShowMainWindow: () -> Unit
) {
    // Status header
    val statusEmoji = if (isPaused) "⏸" else "●"
    val statusText = if (isPaused) "Paused" else "Recording..."
    val timeText = "${formatTime(duration)} / ${formatTime(maxDuration)}"
    Item("$statusEmoji $statusText $timeText", enabled = false, onClick = {})
    Item("$frameCount frames", enabled = false, onClick = {})

    Separator()

    // Recording controls
    Item(
        text = if (isPaused) "Resume" else "Pause",
        onClick = onPauseResume
    )
    Item("Stop & Save", onClick = onStopRecording)
    Item("Cancel", onClick = onCancelRecording)

    Separator()

    Item("Show Window", onClick = onShowMainWindow)
}

/**
 * Menu for Processing state
 */
@Composable
private fun MenuScope.ProcessingMenu(
    progress: Int,
    onShowMainWindow: () -> Unit
) {
    Item("Processing... ${progress}%", enabled = false, onClick = {})

    Separator()

    Item("Show Window", onClick = onShowMainWindow)
}

/**
 * Format seconds to MM:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

/**
 * Show macOS notification when recording is complete
 */
fun showRecordingCompleteNotification(filePath: String, fileSize: Long) {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        try {
            val fileName = filePath.substringAfterLast("/")
            val sizeText = formatFileSize(fileSize)
            Runtime.getRuntime().exec(arrayOf(
                "osascript", "-e",
                """display notification "Saved: $fileName ($sizeText)" with title "Recording Complete" sound name "Glass""""
            ))
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }
}

/**
 * Format file size to human readable
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
