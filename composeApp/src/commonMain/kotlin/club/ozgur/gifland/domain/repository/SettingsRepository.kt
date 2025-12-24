package club.ozgur.gifland.domain.repository

import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.model.AppTheme
import club.ozgur.gifland.domain.model.OutputFormat
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Repository for managing application settings with persistence.
 * Uses multiplatform-settings for cross-platform storage.
 */
class SettingsRepository(
    private val settings: Settings
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private companion object {
        // Keys for settings storage
        const val KEY_LAUNCH_ON_STARTUP = "launch_on_startup"
        const val KEY_SHOW_IN_SYSTEM_TRAY = "show_in_system_tray"
        const val KEY_MINIMIZE_TO_TRAY = "minimize_to_tray"
        const val KEY_THEME = "theme"

        const val KEY_DEFAULT_FORMAT = "default_format"
        const val KEY_DEFAULT_FPS = "default_fps"
        const val KEY_DEFAULT_QUALITY = "default_quality"
        const val KEY_DEFAULT_MAX_DURATION = "default_max_duration"
        const val KEY_CAPTURE_SCALE = "capture_scale"
        const val KEY_SHOW_COUNTDOWN = "show_countdown"
        const val KEY_COUNTDOWN_DURATION = "countdown_duration"
        const val KEY_CAPTURE_MOUSE_CURSOR = "capture_mouse_cursor"
        const val KEY_CAPTURE_AUDIO = "capture_audio"

        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_SAVE_LOCATION = "save_location"
        const val KEY_FILE_NAMING_PATTERN = "file_naming_pattern"
        const val KEY_AUTO_OPTIMIZE = "auto_optimize"
        const val KEY_GENERATE_THUMBNAILS = "generate_thumbnails"
        const val KEY_MAX_RECENT_ITEMS = "max_recent_items"

        const val KEY_HOTKEYS = "hotkeys"
        const val KEY_CLOUD_SYNC = "cloud_sync"
        const val KEY_SHARE_TARGETS = "share_targets"

        const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration"
        const val KEY_MAX_MEMORY_USAGE = "max_memory_usage"
        const val KEY_TEMP_DIRECTORY = "temp_directory"
        const val KEY_DEBUG_MODE = "debug_mode"
    }

    /**
     * Load settings from persistent storage
     */
    private fun loadSettings(): AppSettings {
        return AppSettings(
            // General
            launchOnStartup = settings[KEY_LAUNCH_ON_STARTUP, false],
            showInSystemTray = settings[KEY_SHOW_IN_SYSTEM_TRAY, true],
            minimizeToTray = settings[KEY_MINIMIZE_TO_TRAY, true],
            theme = settings.getStringOrNull(KEY_THEME)?.let {
                try { AppTheme.valueOf(it) } catch (e: Exception) { AppTheme.System }
            } ?: AppTheme.System,

            // Capture
            defaultFormat = settings.getStringOrNull(KEY_DEFAULT_FORMAT)?.let {
                try { OutputFormat.valueOf(it) } catch (e: Exception) { OutputFormat.GIF }
            } ?: OutputFormat.GIF,
            defaultFps = settings[KEY_DEFAULT_FPS, 15],
            defaultQuality = settings[KEY_DEFAULT_QUALITY, 30],
            defaultMaxDuration = settings[KEY_DEFAULT_MAX_DURATION, 30],
            captureScale = settings[KEY_CAPTURE_SCALE, 1.0f],
            showCountdown = settings[KEY_SHOW_COUNTDOWN, true],
            countdownDuration = settings[KEY_COUNTDOWN_DURATION, 3],
            captureMouseCursor = settings[KEY_CAPTURE_MOUSE_CURSOR, true],
            captureAudio = settings[KEY_CAPTURE_AUDIO, false],

            // Export
            autoSave = settings[KEY_AUTO_SAVE, true],
            saveLocation = settings.getStringOrNull(KEY_SAVE_LOCATION)
                ?: "${System.getProperty("user.home")}/Documents/Recordings",
            fileNamingPattern = settings[KEY_FILE_NAMING_PATTERN, "recording_{timestamp}"],
            autoOptimize = settings[KEY_AUTO_OPTIMIZE, true],
            generateThumbnails = settings[KEY_GENERATE_THUMBNAILS, true],
            maxRecentItems = settings[KEY_MAX_RECENT_ITEMS, 50],

            // Advanced
            hardwareAcceleration = settings[KEY_HARDWARE_ACCELERATION, true],
            maxMemoryUsageMB = settings[KEY_MAX_MEMORY_USAGE, 2048],
            tempDirectory = settings.getStringOrNull(KEY_TEMP_DIRECTORY)
                ?: System.getProperty("java.io.tmpdir"),
            debugMode = settings[KEY_DEBUG_MODE, false]
        )
    }

    /**
     * Save settings to persistent storage
     */
    suspend fun saveSettings(appSettings: AppSettings) {
        // Save individual settings
        settings[KEY_LAUNCH_ON_STARTUP] = appSettings.launchOnStartup
        settings[KEY_SHOW_IN_SYSTEM_TRAY] = appSettings.showInSystemTray
        settings[KEY_MINIMIZE_TO_TRAY] = appSettings.minimizeToTray
        settings[KEY_THEME] = appSettings.theme.name

        settings[KEY_DEFAULT_FORMAT] = appSettings.defaultFormat.name
        settings[KEY_DEFAULT_FPS] = appSettings.defaultFps
        settings[KEY_DEFAULT_QUALITY] = appSettings.defaultQuality
        settings[KEY_DEFAULT_MAX_DURATION] = appSettings.defaultMaxDuration
        settings[KEY_CAPTURE_SCALE] = appSettings.captureScale
        settings[KEY_SHOW_COUNTDOWN] = appSettings.showCountdown
        settings[KEY_COUNTDOWN_DURATION] = appSettings.countdownDuration
        settings[KEY_CAPTURE_MOUSE_CURSOR] = appSettings.captureMouseCursor
        settings[KEY_CAPTURE_AUDIO] = appSettings.captureAudio

        settings[KEY_AUTO_SAVE] = appSettings.autoSave
        settings[KEY_SAVE_LOCATION] = appSettings.saveLocation
        settings[KEY_FILE_NAMING_PATTERN] = appSettings.fileNamingPattern
        settings[KEY_AUTO_OPTIMIZE] = appSettings.autoOptimize
        settings[KEY_GENERATE_THUMBNAILS] = appSettings.generateThumbnails
        settings[KEY_MAX_RECENT_ITEMS] = appSettings.maxRecentItems

        settings[KEY_HARDWARE_ACCELERATION] = appSettings.hardwareAcceleration
        settings[KEY_MAX_MEMORY_USAGE] = appSettings.maxMemoryUsageMB
        settings[KEY_TEMP_DIRECTORY] = appSettings.tempDirectory
        settings[KEY_DEBUG_MODE] = appSettings.debugMode

        // Update flow
        _settingsFlow.value = appSettings
    }

    /**
     * Update a specific setting
     */
    suspend fun updateSetting(update: (AppSettings) -> AppSettings) {
        val current = _settingsFlow.value
        val updated = update(current)
        saveSettings(updated)
    }

    /**
     * Reset settings to defaults
     */
    suspend fun resetToDefaults() {
        settings.clear()
        val defaults = AppSettings()
        saveSettings(defaults)
    }

    /**
     * Export settings to JSON string
     */
    fun exportSettings(): String {
        return json.encodeToString(_settingsFlow.value)
    }

    /**
     * Import settings from JSON string
     */
    suspend fun importSettings(jsonString: String) {
        try {
            val imported = json.decodeFromString<AppSettings>(jsonString)
            saveSettings(imported)
        } catch (e: Exception) {
            // Handle import error
            println("Failed to import settings: ${e.message}")
        }
    }

    /**
     * Get current settings synchronously
     */
    fun getCurrentSettings(): AppSettings = _settingsFlow.value
}