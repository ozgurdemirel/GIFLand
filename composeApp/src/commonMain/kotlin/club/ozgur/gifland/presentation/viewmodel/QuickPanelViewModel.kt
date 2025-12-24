package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.MediaRepository
import club.ozgur.gifland.domain.repository.MediaStatistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Quick Access Panel.
 * Manages the lightweight interface for quick actions and recent recordings.
 */
class QuickPanelViewModel(
    private val stateRepository: StateRepository,
    private val mediaRepository: MediaRepository,
    private val recordingController: club.ozgur.gifland.domain.service.RecordingController
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val appState: StateFlow<AppState> = stateRepository.state
    val recentMedia: StateFlow<List<MediaItem>> = mediaRepository.recentItems

    /**
     * Quick capture - start recording immediately
     */
    fun quickCapture() {
        viewModelScope.launch {
            try {
                // Hide quick panel
                stateRepository.toggleQuickPanel()

                // Start full screen (service will use platform default if null)
                recordingController.startRecording(null)
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
     * Start area selection
     */
    fun selectArea() {
        viewModelScope.launch {
            try {
                // Hide quick panel
                stateRepository.toggleQuickPanel()

                // Start area selection
                stateRepository.prepareRecording()
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Area selection failed",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Open a recent recording for editing
     */
    fun openRecording(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                // Hide quick panel
                stateRepository.toggleQuickPanel()

                // Open in editor
                stateRepository.startEditing(mediaItem)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to open recording",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Share a recent recording
     */
    fun shareRecording(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                shareMediaPlatform(mediaItem)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to share recording",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Copy recording to clipboard
     */
    fun copyToClipboard(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                copyToClipboardPlatform(mediaItem)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to copy to clipboard",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Delete a recording
     */
    fun deleteRecording(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                val success = mediaRepository.deleteMediaItem(mediaItem.id)
                if (!success) {
                    throw Exception("Failed to delete media item")
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to delete recording",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Open file location
     */
    fun openFileLocation(mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                openFileLocationPlatform(mediaItem.filePath)
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to open file location",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Open main application window
     */
    fun openMainWindow() {
        viewModelScope.launch {
            // Hide quick panel
            stateRepository.toggleQuickPanel()

            // Platform-specific: show main window
            showMainWindowPlatform()
        }
    }

    /**
     * Open settings
     */
    fun openSettings() {
        viewModelScope.launch {
            // Hide quick panel
            stateRepository.toggleQuickPanel()

            // Open settings screen
            stateRepository.openSettings()
        }
    }

    /**
     * Hide quick panel
     */
    fun hidePanel() {
        viewModelScope.launch {
            stateRepository.toggleQuickPanel()
        }
    }

    /**
     * Import media from file
     */
    fun importMedia() {
        viewModelScope.launch {
            try {
                // Platform-specific file picker
                val filePath = pickFilePlatform()
                if (filePath != null) {
                    // Import the file
                    println("Importing media from: $filePath")
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to import media",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Refresh recent recordings list
     */
    fun refreshRecordings() {
        viewModelScope.launch {
            try {
                val recordings = mediaRepository.getRecentItems(20)
                // The StateFlow in repository will automatically update
            } catch (e: Exception) {
                // Silently handle refresh errors
                println("Failed to refresh recordings: ${e.message}")
            }
        }
    }

    /**
     * Get recording statistics for display
     */
    fun getStatistics(): MediaStatistics {
        return mediaRepository.getStatistics()
    }

    // Platform-specific functions (will be implemented with expect/actual)

    private fun getScreenWidth(): Int = club.ozgur.gifland.platform.PlatformUi.getPrimaryScreenBounds().width
    private fun getScreenHeight(): Int = club.ozgur.gifland.platform.PlatformUi.getPrimaryScreenBounds().height

    private suspend fun shareMediaPlatform(mediaItem: MediaItem) {
        println("Sharing media: ${mediaItem.filePath}")
    }

    private suspend fun copyToClipboardPlatform(mediaItem: MediaItem) {
        club.ozgur.gifland.platform.PlatformUi.copyToClipboard(mediaItem.filePath)
    }

    private suspend fun openFileLocationPlatform(path: String) {
        club.ozgur.gifland.platform.PlatformUi.revealInFileManager(path)
    }

    private fun showMainWindowPlatform() {
        println("Showing main window")
    }

    private suspend fun pickFilePlatform(): String? {
        return club.ozgur.gifland.platform.PlatformUi.pickFile(title = "Import media")
    }
}