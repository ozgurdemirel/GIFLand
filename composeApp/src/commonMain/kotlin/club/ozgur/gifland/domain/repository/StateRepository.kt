package club.ozgur.gifland.domain.repository

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.core.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Central repository for application state management.
 * This is the single source of truth for all UI state.
 *
 * Key principles:
 * - All state mutations go through this repository
 * - State transitions are validated for consistency
 * - Thread-safe operations using Mutex
 * - Reactive updates via StateFlow
 */
class StateRepository {

    private val _state = MutableStateFlow<AppState>(AppState.Initializing)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val stateMutex = Mutex()

    /**
     * Job for managing countdown coroutine
     */
    private var countdownJob: Job? = null

    /**
     * Initialize the application state after startup
     */
    suspend fun initialize(settings: AppSettings, recentRecordings: List<MediaItem> = emptyList()) {
        stateMutex.withLock {
            _state.value = AppState.Idle(
                recentRecordings = recentRecordings,
                settings = settings
            )
        }
    }

    /**
     * Transition to preparing for recording
     */
    suspend fun prepareRecording(area: CaptureRegion? = null) {
        stateMutex.withLock {
            val currentSettings = when (val current = _state.value) {
                is AppState.Idle -> current.settings
                is AppState.Error -> (current.previousState as? AppState.Idle)?.settings ?: AppSettings()
                else -> AppSettings()
            }

            _state.value = AppState.PreparingRecording(
                selectedArea = area,
                settings = currentSettings
            )
        }
    }

    /**
     * Start recording with the specified parameters
     */
    suspend fun startRecording(
        captureArea: CaptureRegion,
        outputFormat: OutputFormat? = null,
        maxDuration: Int? = null
    ) {
        stateMutex.withLock {
            val settings = getCurrentSettings() ?: AppSettings()
            val session = RecordingSession(
                id = generateSessionId(),
                startTime = Clock.System.now(),
                captureArea = captureArea,
                outputFormat = outputFormat ?: settings.defaultFormat,
                maxDuration = maxDuration ?: settings.defaultMaxDuration
            )

            _state.value = AppState.Recording(
                session = session,
                settings = settings
            )
        }
    }

    /**
     * Update recording progress
     */
    suspend fun updateRecordingProgress(
        frameCount: Int,
        duration: Int,
        estimatedSize: Long,
        captureMethodDetails: String? = null
    ) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Recording) {
                _state.value = current.copy(
                    session = current.session.copy(
                        frameCount = frameCount,
                        duration = duration,
                        estimatedSize = estimatedSize,
                        captureMethodDetails = captureMethodDetails
                    )
                )
            }
        }
    }

    /**
     * Toggle pause state during recording
     */
    suspend fun togglePauseRecording() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Recording) {
                _state.value = current.copy(isPaused = !current.isPaused)
            }
        }
    }

    /**
     * Stop recording and transition to processing
     */
    suspend fun stopRecording() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Recording) {
                _state.value = AppState.Processing(
                    session = current.session,
                    settings = current.settings
                )
            }
        }
    }

    /**
     * Update processing progress
     */
    suspend fun updateProcessingProgress(
        progress: Float,
        stage: ProcessingStage,
        estimatedTimeRemaining: Int? = null
    ) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Processing) {
                _state.value = current.copy(
                    progress = progress.coerceIn(0f, 1f),
                    stage = stage,
                    estimatedTimeRemaining = estimatedTimeRemaining
                )
            }
        }
    }

    /**
     * Complete processing and return to idle with new media item
     */
    suspend fun completeProcessing(mediaItem: MediaItem) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Processing) {
                val idle = AppState.Idle(
                    recentRecordings = listOf(mediaItem) + getRecentRecordings(),
                    settings = current.settings
                )
                _state.value = idle
            }
        }
    }

    /**
     * Start editing a media item
     */
    suspend fun startEditing(mediaItem: MediaItem) {
        stateMutex.withLock {
            val settings = getCurrentSettings() ?: AppSettings()
            _state.value = AppState.Editing(
                mediaItem = mediaItem,
                editState = EditState(),
                settings = settings
            )
        }
    }

    /**
     * Update edit state
     */
    suspend fun updateEditState(editState: EditState) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Editing) {
                _state.value = current.copy(
                    editState = editState,
                    hasUnsavedChanges = true
                )
            }
        }
    }

    /**
     * Save edited media and return to idle
     */
    suspend fun saveEditedMedia(updatedMediaItem: MediaItem) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Editing) {
                val recordings = getRecentRecordings().map {
                    if (it.id == updatedMediaItem.id) updatedMediaItem else it
                }
                _state.value = AppState.Idle(
                    recentRecordings = recordings,
                    settings = current.settings
                )
            }
        }
    }

    /**
     * Open settings configuration
     */
    suspend fun openSettings() {
        stateMutex.withLock {
            val currentSettings = getCurrentSettings() ?: AppSettings()
            _state.value = AppState.ConfiguringSettings(
                currentSettings = currentSettings
            )
        }
    }

    /**
     * Update pending settings changes
     */
    suspend fun updatePendingSettings(settings: AppSettings) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.ConfiguringSettings) {
                _state.value = current.copy(pendingChanges = settings)
            }
        }
    }

    /**
     * Apply settings and return to idle
     */
    suspend fun applySettings() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.ConfiguringSettings) {
                val newSettings = current.pendingChanges ?: current.currentSettings
                _state.value = AppState.Idle(
                    recentRecordings = getRecentRecordings(),
                    settings = newSettings
                )
            }
        }
    }

    /**
     * Cancel settings and return to idle
     */
    suspend fun cancelSettings() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.ConfiguringSettings) {
                _state.value = AppState.Idle(
                    recentRecordings = getRecentRecordings(),
                    settings = current.currentSettings
                )
            }
        }
    }

    /**
     * Handle error state
     */
    suspend fun handleError(
        message: String,
        cause: Throwable? = null,
        recoverable: Boolean = true
    ) {
        stateMutex.withLock {
            val currentState = _state.value
            _state.value = AppState.Error(
                message = message,
                cause = cause,
                recoverable = recoverable,
                previousState = if (currentState !is AppState.Error) currentState else currentState.previousState
            )
        }
    }

    /**
     * Recover from error state
     */
    suspend fun recoverFromError() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Error && current.recoverable) {
                _state.value = current.previousState ?: AppState.Idle(settings = AppSettings())
            }
        }
    }

    /**
     * Toggle quick access panel visibility
     */
    suspend fun toggleQuickPanel() {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Idle) {
                _state.value = current.copy(isQuickPanelVisible = !current.isQuickPanelVisible)
            }
        }
    }

    /**
     * Update recent recordings list
     */
    suspend fun updateRecentRecordings(recordings: List<MediaItem>) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.Idle) {
                val maxItems = current.settings.maxRecentItems
                _state.value = current.copy(
                    recentRecordings = recordings.take(maxItems)
                )
            }
        }
    }

    /**
     * Cancel current operation and return to idle
     */
    suspend fun cancelCurrentOperation() {
        stateMutex.withLock {
            val settings = getCurrentSettings() ?: AppSettings()
            val recordings = getRecentRecordings()
            _state.value = AppState.Idle(
                recentRecordings = recordings,
                settings = settings
            )
        }
    }

    /**
     * Start a countdown before recording.
     * This manages the countdown state and automatically starts recording when finished.
     *
     * @param seconds Number of seconds to count down
     * @param area Optional capture area for the recording
     * @param onComplete Callback when recording starts after countdown
     */
    suspend fun startCountdownRecording(
        seconds: Int,
        area: CaptureRegion? = null,
        onComplete: suspend () -> Unit = {}
    ) {
        stateMutex.withLock {
            // Cancel any existing countdown
            countdownJob?.cancel()
            countdownJob = null

            // If countdown disabled or 0, start immediately
            if (seconds <= 0) {
                _state.value = AppState.PreparingRecording(
                    selectedArea = area,
                    countdown = null,
                    settings = getCurrentSettings() ?: AppSettings()
                )
                // Execute completion callback without holding the lock
                ApplicationScope.launch {
                    onComplete()
                }
                return
            }

            // Set initial countdown state
            _state.value = AppState.PreparingRecording(
                selectedArea = area,
                countdown = seconds,
                settings = getCurrentSettings() ?: AppSettings()
            )
        }

        // Start countdown coroutine (outside the lock to avoid blocking)
        countdownJob = ApplicationScope.launch {
            try {
                for (i in seconds downTo 0) {
                    // Update countdown value (including 0 for a brief "starting now" state)
                    updateCountdown(i)
                    delay(if (i == 0) 300 else 1000)
                }

                // Clear countdown and prepare for recording
                stateMutex.withLock {
                    val current = _state.value
                    if (current is AppState.PreparingRecording) {
                        _state.value = current.copy(countdown = null)
                    }
                    countdownJob = null
                }

                // Start recording
                onComplete()
            } catch (e: CancellationException) {
                // Countdown was cancelled
                println("StateRepository: Countdown cancelled")
            } catch (e: Exception) {
                println("StateRepository: Error during countdown: ${e.message}")
                handleError("Countdown failed: ${e.message}", e, true)
            }
        }
    }

    /**
     * Cancel the current countdown.
     * Returns to idle state.
     */
    suspend fun cancelCountdown() {
        stateMutex.withLock {
            countdownJob?.cancel()
            countdownJob = null

            val current = _state.value
            if (current is AppState.PreparingRecording) {
                // Return to idle state
                _state.value = AppState.Idle(
                    recentRecordings = getRecentRecordings(),
                    settings = current.settings
                )
            }
        }
    }

    /**
     * Update the countdown value.
     * Internal method used by the countdown coroutine.
     */
    private suspend fun updateCountdown(seconds: Int) {
        stateMutex.withLock {
            val current = _state.value
            if (current is AppState.PreparingRecording) {
                _state.value = current.copy(countdown = seconds)
            }
        }
    }

    /**
     * Get the current countdown value.
     * Returns null if not in countdown state.
     */
    fun getCurrentCountdown(): Int? {
        val current = _state.value
        return if (current is AppState.PreparingRecording) {
            current.countdown
        } else {
            null
        }
    }

    /**
     * Check if countdown is currently active.
     */
    fun isCountdownActive(): Boolean {
        return countdownJob?.isActive == true
    }

    // Helper functions

    private fun getCurrentSettings(): AppSettings? {
        return when (val current = _state.value) {
            is AppState.Idle -> current.settings
            is AppState.PreparingRecording -> current.settings
            is AppState.Recording -> current.settings
            is AppState.Processing -> current.settings
            is AppState.Editing -> current.settings
            is AppState.ConfiguringSettings -> current.currentSettings
            else -> null
        }
    }

    private fun getRecentRecordings(): List<MediaItem> {
        return when (val current = _state.value) {
            is AppState.Idle -> current.recentRecordings
            is AppState.Processing -> {
                // Check if there's a previous idle state we can get recordings from
                findPreviousIdleState(current)?.recentRecordings ?: emptyList()
            }
            is AppState.Editing -> {
                findPreviousIdleState(current)?.recentRecordings ?: emptyList()
            }
            is AppState.ConfiguringSettings -> {
                findPreviousIdleState(current)?.recentRecordings ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun findPreviousIdleState(from: AppState): AppState.Idle? {
        // In a real implementation, we might track state history
        // For now, return empty
        return null
    }

    private fun generateSessionId(): String {
        return "session_${Clock.System.now().toEpochMilliseconds()}"
    }
}