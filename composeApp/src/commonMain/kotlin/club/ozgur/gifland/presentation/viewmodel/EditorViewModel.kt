package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the media editor screen.
 * Handles editing operations like trim, cut, and filters.
 */
class EditorViewModel(
    private val stateRepository: StateRepository,
    private val mediaRepository: MediaRepository
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val appState: StateFlow<AppState> = stateRepository.state

    /**
     * Select an editing tool
     */
    fun selectTool(tool: EditTool) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newEditState = current.editState.copy(selectedTool = tool)
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Set trim points for the media
     */
    fun setTrimPoints(start: Long? = null, end: Long? = null) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newEditState = current.editState.copy(
                    trimStart = start ?: current.editState.trimStart,
                    trimEnd = end ?: current.editState.trimEnd
                )
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Apply a filter to the media
     */
    fun applyFilter(filter: MediaFilter) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newFilters = current.editState.filters + filter
                val newEditState = current.editState.copy(filters = newFilters)
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Remove a filter from the media
     */
    fun removeFilter(filter: MediaFilter) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newFilters = current.editState.filters - filter
                val newEditState = current.editState.copy(filters = newFilters)
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Update playback position
     */
    fun updatePlaybackPosition(position: Long) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newEditState = current.editState.copy(playbackPosition = position)
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Change zoom level
     */
    fun setZoom(zoom: Float) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val newEditState = current.editState.copy(
                    zoom = zoom.coerceIn(0.1f, 10f)
                )
                stateRepository.updateEditState(newEditState)
            }
        }
    }

    /**
     * Save the edited media
     */
    fun saveEdits() {
        viewModelScope.launch {
            try {
                val current = appState.value
                if (current is AppState.Editing && current.hasUnsavedChanges) {
                    // Apply edits to the media item
                    val editedItem = applyEditsToMedia(
                        current.mediaItem,
                        current.editState
                    )

                    // Update in repository
                    mediaRepository.updateMediaItem(editedItem)

                    // Return to idle state
                    stateRepository.saveEditedMedia(editedItem)
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to save edits",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Export the edited media with specific settings
     */
    fun exportAs(
        format: OutputFormat,
        quality: Int,
        outputPath: String
    ) {
        viewModelScope.launch {
            try {
                val current = appState.value
                if (current is AppState.Editing) {
                    // Start export process
                    // This would integrate with the export service
                    println("Exporting as $format to $outputPath with quality $quality")
                }
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Export failed",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Cancel editing and discard changes
     */
    fun cancelEditing() {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing && current.hasUnsavedChanges) {
                // Could show confirmation dialog
                // For now, just cancel
                stateRepository.cancelCurrentOperation()
            } else {
                stateRepository.cancelCurrentOperation()
            }
        }
    }

    /**
     * Undo the last edit action
     */
    fun undo() {
        // Implement undo functionality
        println("Undo not yet implemented")
    }

    /**
     * Redo the last undone action
     */
    fun redo() {
        // Implement redo functionality
        println("Redo not yet implemented")
    }

    /**
     * Reset all edits
     */
    fun resetEdits() {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Editing) {
                val resetEditState = EditState()
                stateRepository.updateEditState(resetEditState)
            }
        }
    }

    // Private helper functions

    private fun applyEditsToMedia(
        original: MediaItem,
        editState: EditState
    ): MediaItem {
        // Apply the edits to create a new media item
        // This is a placeholder - actual implementation would
        // involve media processing
        return original.copy(
            metadata = original.metadata + mapOf(
                "edited" to "true",
                "trimStart" to editState.trimStart.toString(),
                "trimEnd" to editState.trimEnd.toString(),
                "filters" to editState.filters.size.toString()
            )
        )
    }
}