package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 * Manages application configuration and preferences.
 */
class SettingsViewModel(
    private val stateRepository: StateRepository,
    private val settingsRepository: SettingsRepository
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val appState: StateFlow<AppState> = stateRepository.state
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow

    /**
     * Change the active settings tab
     */
    fun selectTab(tab: SettingsTab) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.ConfiguringSettings) {
                val updated = current.copy(activeTab = tab)
                // Update state directly since this doesn't go through repository
                // In a real implementation, we'd add a method to StateRepository for this
            }
        }
    }

    /**
     * Update a general setting
     */
    fun updateGeneralSetting(update: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                val current = settings.value
                val updated = update(current)
                stateRepository.updatePendingSettings(updated)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to update setting",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Toggle launch on startup
     */
    fun toggleLaunchOnStartup(enabled: Boolean) {
        updateGeneralSetting { it.copy(launchOnStartup = enabled) }
    }

    /**
     * Toggle system tray
     */
    fun toggleSystemTray(enabled: Boolean) {
        updateGeneralSetting { it.copy(showInSystemTray = enabled) }
    }

    /**
     * Toggle minimize to tray
     */
    fun toggleMinimizeToTray(enabled: Boolean) {
        updateGeneralSetting { it.copy(minimizeToTray = enabled) }
    }

    /**
     * Change app theme.
     * Persist immediately so ThemeProvider (which observes SettingsRepository.settingsFlow)
     * recomposes and applies colors right away. Also keep pending settings in sync when
     * the Settings screen is open.
     */
    fun changeTheme(theme: AppTheme) {
        viewModelScope.launch {
            try {
                // Persist immediately
                settingsRepository.updateSetting { it.copy(theme = theme) }

                // If the Settings screen is currently open, mirror the change into pending state
                val current = appState.value
                if (current is AppState.ConfiguringSettings) {
                    stateRepository.updatePendingSettings(current.currentSettings.copy(theme = theme))
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to change theme",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Update capture settings
     */
    fun updateCaptureSettings(
        format: OutputFormat? = null,
        fps: Int? = null,
        quality: Int? = null,
        maxDuration: Int? = null
    ) {
        updateGeneralSetting { current ->
            current.copy(
                defaultFormat = format ?: current.defaultFormat,
                defaultFps = fps ?: current.defaultFps,
                defaultQuality = quality ?: current.defaultQuality,
                defaultMaxDuration = maxDuration ?: current.defaultMaxDuration
            )
        }
    }

    /**
     * Toggle countdown
     */
    fun toggleCountdown(enabled: Boolean) {
        updateGeneralSetting { it.copy(showCountdown = enabled) }
    }

    /**
     * Update countdown duration
     */
    fun updateCountdownDuration(seconds: Int) {
        updateGeneralSetting {
            it.copy(countdownDuration = seconds.coerceIn(0, 10))
        }
    }

    /**
     * Toggle mouse cursor capture
     */
    fun toggleMouseCursor(enabled: Boolean) {
        updateGeneralSetting { it.copy(captureMouseCursor = enabled) }
    }

    /**
     * Toggle audio capture
     */
    fun toggleAudioCapture(enabled: Boolean) {
        updateGeneralSetting { it.copy(captureAudio = enabled) }
    }

    /**
     * Update export settings
     */
    fun updateExportSettings(
        autoSave: Boolean? = null,
        saveLocation: String? = null,
        fileNamingPattern: String? = null
    ) {
        updateGeneralSetting { current ->
            current.copy(
                autoSave = autoSave ?: current.autoSave,
                saveLocation = saveLocation ?: current.saveLocation,
                fileNamingPattern = fileNamingPattern ?: current.fileNamingPattern
            )
        }
    }

    /**
     * Toggle auto-optimize
     */
    fun toggleAutoOptimize(enabled: Boolean) {
        updateGeneralSetting { it.copy(autoOptimize = enabled) }
    }

    /**
     * Toggle thumbnail generation
     */
    fun toggleThumbnails(enabled: Boolean) {
        updateGeneralSetting { it.copy(generateThumbnails = enabled) }
    }

    /**
     * Update max recent items
     */
    fun updateMaxRecentItems(count: Int) {
        updateGeneralSetting {
            it.copy(maxRecentItems = count.coerceIn(10, 100))
        }
    }

    /**
     * Update a hotkey binding
     */
    fun updateHotkey(action: HotkeyAction, key: String) {
        updateGeneralSetting { current ->
            val updatedHotkeys = current.globalHotkeys + (action to key)
            current.copy(globalHotkeys = updatedHotkeys)
        }
    }

    /**
     * Reset hotkeys to defaults
     */
    fun resetHotkeys() {
        updateGeneralSetting { current ->
            current.copy(globalHotkeys = getDefaultHotkeys())
        }
    }

    /**
     * Toggle hardware acceleration
     */
    fun toggleHardwareAcceleration(enabled: Boolean) {
        updateGeneralSetting { it.copy(hardwareAcceleration = enabled) }
    }

    /**
     * Update max memory usage
     */
    fun updateMaxMemory(mb: Int) {
        updateGeneralSetting {
            it.copy(maxMemoryUsageMB = mb.coerceIn(512, 8192))
        }
    }

    /**
     * Toggle debug mode
     */
    fun toggleDebugMode(enabled: Boolean) {
        updateGeneralSetting { it.copy(debugMode = enabled) }
    }

    /**
     * Apply pending settings changes
     */
    fun applySettings() {
        viewModelScope.launch {
            try {
                val current = appState.value
                if (current is AppState.ConfiguringSettings) {
                    val toApply = current.pendingChanges ?: return@launch
                    settingsRepository.saveSettings(toApply)
                    stateRepository.applySettings()
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to apply settings",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Cancel settings changes
     */
    fun cancelSettings() {
        viewModelScope.launch {
            stateRepository.cancelSettings()
        }
    }

    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                settingsRepository.resetToDefaults()
                val defaults = AppSettings()
                stateRepository.updatePendingSettings(defaults)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to reset settings",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Export settings to file
     */
    fun exportSettings(): String {
        return settingsRepository.exportSettings()
    }

    /**
     * Import settings from file
     */
    fun importSettings(jsonString: String) {
        viewModelScope.launch {
            try {
                settingsRepository.importSettings(jsonString)
                val imported = settingsRepository.getCurrentSettings()
                stateRepository.updatePendingSettings(imported)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to import settings",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    // Helper functions

    private fun getDefaultHotkeys(): Map<HotkeyAction, String> {
        return mapOf(
            HotkeyAction.StartRecording to "Ctrl+Shift+R",
            HotkeyAction.StopRecording to "Ctrl+Shift+S",
            HotkeyAction.PauseRecording to "Ctrl+Shift+P",
            HotkeyAction.CancelRecording to "Escape",
            HotkeyAction.SelectArea to "Ctrl+Shift+A",
            HotkeyAction.QuickCapture to "Ctrl+Shift+Q",
            HotkeyAction.ShowQuickPanel to "Ctrl+Shift+Space",
            HotkeyAction.ShowMainWindow to "Ctrl+Shift+M"
        )
    }
}