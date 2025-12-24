package club.ozgur.gifland.domain.model

import kotlinx.datetime.Instant

/**
 * Represents all possible states of the application following the state-driven architecture.
 * This is the single source of truth for UI rendering.
 */
sealed class AppState {

    /**
     * Initial state when the app is starting up
     */
    object Initializing : AppState()

    /**
     * Idle state - app is ready but not actively doing anything
     */
    data class Idle(
        val recentRecordings: List<MediaItem> = emptyList(),
        val lastError: String? = null,
        val isQuickPanelVisible: Boolean = false,
        val settings: AppSettings = AppSettings()
    ) : AppState()

    /**
     * Preparing to record - showing area selector or countdown
     */
    data class PreparingRecording(
        val selectedArea: CaptureRegion? = null,
        val countdown: Int? = null,
        val settings: AppSettings = AppSettings()
    ) : AppState()

    /**
     * Actively recording the screen
     */
    data class Recording(
        val session: RecordingSession,
        val isPaused: Boolean = false,
        val captureMethod: CaptureMethod = CaptureMethod.Auto,
        val settings: AppSettings = AppSettings()
    ) : AppState()

    /**
     * Processing captured frames (encoding, optimizing)
     */
    data class Processing(
        val session: RecordingSession,
        val progress: Float = 0f,
        val stage: ProcessingStage = ProcessingStage.Encoding,
        val estimatedTimeRemaining: Int? = null,
        val settings: AppSettings = AppSettings()
    ) : AppState()

    /**
     * Editing a recorded media item
     */
    data class Editing(
        val mediaItem: MediaItem,
        val editState: EditState,
        val hasUnsavedChanges: Boolean = false,
        val settings: AppSettings = AppSettings()
    ) : AppState()

    /**
     * Showing settings/configuration screen
     */
    data class ConfiguringSettings(
        val currentSettings: AppSettings,
        val pendingChanges: AppSettings? = null,
        val activeTab: SettingsTab = SettingsTab.Appearance
    ) : AppState()

    /**
     * Error state with recovery options
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val recoverable: Boolean = true,
        val previousState: AppState? = null
    ) : AppState()
}

/**
 * Represents an active or completed recording session
 */
data class RecordingSession(
    val id: String,
    val startTime: Instant,
    val captureArea: CaptureRegion,
    val frameCount: Int = 0,
    val duration: Int = 0, // seconds
    val estimatedSize: Long = 0L,
    val captureMethodDetails: String? = null,
    val outputFormat: OutputFormat = OutputFormat.GIF,
    val maxDuration: Int = 30
)

/**
 * Represents a media item (recording) that can be viewed/edited
 */
data class MediaItem(
    val id: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val format: OutputFormat,
    val sizeBytes: Long,
    val durationMs: Long,
    val dimensions: Dimensions,
    val createdAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Capture region definition
 */
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val displayId: String? = null
)

/**
 * Dimensions for media
 */
data class Dimensions(
    val width: Int,
    val height: Int
)

/**
 * Available capture methods
 */
enum class CaptureMethod {
    Auto,
    ScreenCaptureKit,
    RobotApi,
    FFmpeg
}

/**
 * Processing stages for user feedback
 */
enum class ProcessingStage {
    CollectingFrames,
    Encoding,
    Optimizing,
    Saving,
    GeneratingThumbnail,
    UpdatingIndex
}

/**
 * Output formats
 */
enum class OutputFormat {
    GIF,
    WEBP
}

/**
 * Edit state for the editor
 */
data class EditState(
    val selectedTool: EditTool = EditTool.None,
    val trimStart: Long? = null,
    val trimEnd: Long? = null,
    val playbackPosition: Long = 0,
    val zoom: Float = 1f,
    val filters: List<MediaFilter> = emptyList()
)

/**
 * Available editing tools
 */
enum class EditTool {
    None,
    Trim,
    Cut,
    Crop,
    Annotate,
    Filter
}

/**
 * Media filters/effects
 */
data class MediaFilter(
    val type: FilterType,
    val intensity: Float = 1f,
    val parameters: Map<String, Any> = emptyMap()
)

enum class FilterType {
    Brightness,
    Contrast,
    Saturation,
    Blur,
    Sharpen
}

/**
 * Settings tabs for organization
 */
enum class SettingsTab {
    Appearance,
    About
}

/**
 * Application settings
 */
data class AppSettings(
    // General
    val launchOnStartup: Boolean = false,
    val showInSystemTray: Boolean = true,
    val minimizeToTray: Boolean = true,
    val theme: AppTheme = AppTheme.System,

    // Capture
    val defaultFormat: OutputFormat = OutputFormat.GIF,
    val defaultFps: Int = 15,
    val defaultQuality: Int = 30,
    val defaultMaxDuration: Int = 30,
    val captureScale: Float = 1.0f,
    val showCountdown: Boolean = true,
    val countdownDuration: Int = 3,
    val captureMouseCursor: Boolean = true,
    val captureAudio: Boolean = false,

    // Export
    val autoSave: Boolean = true,
    val saveLocation: String = System.getProperty("user.home") + "/Documents/Recordings",
    val fileNamingPattern: String = "recording_{timestamp}",
    val autoOptimize: Boolean = true,
    val generateThumbnails: Boolean = true,
    val maxRecentItems: Int = 50,

    // Shortcuts
    val globalHotkeys: Map<HotkeyAction, String> = defaultHotkeys(),

    // Integrations
    val cloudSync: CloudSyncSettings? = null,
    val shareTargets: List<ShareTarget> = emptyList(),

    // Advanced
    val hardwareAcceleration: Boolean = true,
    val maxMemoryUsageMB: Int = 2048,
    val tempDirectory: String = System.getProperty("java.io.tmpdir"),
    val debugMode: Boolean = false
)

/**
 * App theme options
 */
enum class AppTheme {
    Light,
    Dark,
    System
}

/**
 * Hotkey actions that can be configured
 */
enum class HotkeyAction {
    StartRecording,
    StopRecording,
    PauseRecording,
    CancelRecording,
    SelectArea,
    QuickCapture,
    ShowQuickPanel,
    ShowMainWindow
}

/**
 * Cloud sync configuration
 */
data class CloudSyncSettings(
    val provider: CloudProvider,
    val enabled: Boolean = false,
    val syncPath: String,
    val autoUpload: Boolean = false
)

enum class CloudProvider {
    GoogleDrive,
    Dropbox,
    OneDrive,
    iCloud
}

/**
 * Share target configuration
 */
data class ShareTarget(
    val id: String,
    val name: String,
    val icon: String? = null,
    val enabled: Boolean = true
)

/**
 * Default hotkey configuration
 */
private fun defaultHotkeys(): Map<HotkeyAction, String> = mapOf(
    HotkeyAction.StartRecording to "Ctrl+Shift+R",
    HotkeyAction.StopRecording to "Ctrl+Shift+S",
    HotkeyAction.PauseRecording to "Ctrl+Shift+P",
    HotkeyAction.CancelRecording to "Escape",
    HotkeyAction.SelectArea to "Ctrl+Shift+A",
    HotkeyAction.QuickCapture to "Ctrl+Shift+Q",
    HotkeyAction.ShowQuickPanel to "Ctrl+Shift+Space",
    HotkeyAction.ShowMainWindow to "Ctrl+Shift+M"
)