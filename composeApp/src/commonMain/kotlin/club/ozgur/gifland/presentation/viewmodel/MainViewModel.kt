package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the primary application interface.
 * Handles the main screen logic and coordinates between different states.
 */
class MainViewModel(
    private val stateRepository: StateRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Expose state as StateFlow
    val appState: StateFlow<AppState> = stateRepository.state
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
    val recentMedia: StateFlow<List<MediaItem>> = mediaRepository.recentItems

    init {
        // Initialize the app state when ViewModel is created
        viewModelScope.launch {
            loadInitialData()
        }
    }

    /**
     * Load initial data and transition from initializing state
     */
    private suspend fun loadInitialData() {
        try {
            val settings = settingsRepository.getCurrentSettings()
            val recentRecordings = mediaRepository.getRecentItems()
            stateRepository.initialize(settings, recentRecordings)
        } catch (e: Exception) {
            stateRepository.handleError(
                message = "Failed to initialize application",
                cause = e,
                recoverable = true
            )
        }
    }

    /**
     * Start a new recording with optional area selection
     */
    fun startRecording(area: CaptureRegion? = null) {
        viewModelScope.launch {
            try {
                if (area == null) {
                    // Show area selector
                    stateRepository.prepareRecording()
                } else {
                    // Start recording directly
                    stateRepository.startRecording(area)
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to start recording",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Quick capture - start recording immediately with default settings
     */
    fun quickCapture() {
        viewModelScope.launch {
            try {
                // Use full screen as default
                val fullScreen = CaptureRegion(
                    x = 0,
                    y = 0,
                    width = getScreenWidth(),
                    height = getScreenHeight()
                )
                stateRepository.startRecording(fullScreen)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Quick capture failed",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Open a media item for editing
     */
    fun openMediaForEditing(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                stateRepository.startEditing(mediaItem)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to open media for editing",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Delete a media item
     */
    fun deleteMedia(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                val success = mediaRepository.deleteMediaItem(mediaItem.id)
                if (success) {
                    val updatedRecordings = mediaRepository.getRecentItems()
                    stateRepository.updateRecentRecordings(updatedRecordings)
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to delete media",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Share a media item
     */
    fun shareMedia(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                // Platform-specific sharing implementation
                shareMediaPlatform(mediaItem)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to share media",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Open settings screen
     */
    fun openSettings() {
        viewModelScope.launch {
            stateRepository.openSettings()
        }
    }

    /**
     * Toggle quick access panel visibility
     */
    fun toggleQuickPanel() {
        viewModelScope.launch {
            stateRepository.toggleQuickPanel()
        }
    }

    /**
     * Refresh recent recordings
     */
    fun refreshRecentRecordings() {
        viewModelScope.launch {
            try {
                val recordings = mediaRepository.getRecentItems()
                stateRepository.updateRecentRecordings(recordings)
            } catch (e: Exception) {
                // Silently handle refresh errors
                println("Failed to refresh recordings: ${e.message}")
            }
        }
    }

    /**
     * Handle area selection completion
     */
    fun onAreaSelected(area: CaptureRegion?) {
        viewModelScope.launch {
            if (area != null) {
                stateRepository.startRecording(area)
            } else {
                // User cancelled area selection
                stateRepository.cancelCurrentOperation()
            }
        }
    }

    /**
     * Handle error recovery
     */
    fun recoverFromError() {
        viewModelScope.launch {
            stateRepository.recoverFromError()
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        viewModelScope.launch {
            stateRepository.recoverFromError()
        }
    }

    // Platform-specific functions (will be implemented with expect/actual)

    private fun getScreenWidth(): Int {
        // Platform-specific implementation
        return 1920 // Default
    }

    private fun getScreenHeight(): Int {
        // Platform-specific implementation
        return 1080 // Default
    }

    private suspend fun shareMediaPlatform(mediaItem: MediaItem) {
        // Platform-specific sharing implementation
        println("Sharing media: ${mediaItem.filePath}")
    }
}