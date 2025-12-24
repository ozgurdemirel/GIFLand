package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing active recording operations.
 * Handles recording controls, progress updates, and state transitions.
 */
class RecordingViewModel(
    private val stateRepository: StateRepository
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val appState: StateFlow<AppState> = stateRepository.state

    /**
     * Pause or resume the current recording
     */
    fun togglePause() {
        viewModelScope.launch {
            stateRepository.togglePauseRecording()
        }
    }

    /**
     * Stop the current recording
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                stateRepository.stopRecording()
                // Recording service will handle the actual capture stop
                // and transition to processing
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to stop recording",
                    cause = e,
                    recoverable = true
                )
            }
        }
    }

    /**
     * Cancel the current recording without saving
     */
    fun cancelRecording() {
        viewModelScope.launch {
            try {
                // Stop capture without processing
                stateRepository.cancelCurrentOperation()
            } catch (e: Exception) {
                stateRepository.handleError(
                    message = "Failed to cancel recording",
                    cause = e,
                    recoverable = false
                )
            }
        }
    }

    /**
     * Update recording progress (called by recording service)
     */
    fun updateProgress(
        frameCount: Int,
        duration: Int,
        estimatedSize: Long,
        captureMethodDetails: String? = null
    ) {
        viewModelScope.launch {
            stateRepository.updateRecordingProgress(
                frameCount = frameCount,
                duration = duration,
                estimatedSize = estimatedSize,
                captureMethodDetails = captureMethodDetails
            )
        }
    }

    /**
     * Handle capture method change (e.g., fallback from ScreenCaptureKit to Robot)
     */
    fun onCaptureMethodChanged(method: CaptureMethod, details: String?) {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Recording) {
                updateProgress(
                    frameCount = current.session.frameCount,
                    duration = current.session.duration,
                    estimatedSize = current.session.estimatedSize,
                    captureMethodDetails = details
                )
            }
        }
    }

    /**
     * Check if maximum duration is approaching
     */
    fun checkDurationWarning() {
        viewModelScope.launch {
            val current = appState.value
            if (current is AppState.Recording) {
                val remaining = current.session.maxDuration - current.session.duration
                if (remaining <= 5 && remaining > 0) {
                    // Could show a warning notification
                    println("Recording will stop in $remaining seconds")
                }
            }
        }
    }

    /**
     * Get recording statistics
     */
    fun getRecordingStats(): RecordingStats? {
        val current = appState.value
        return if (current is AppState.Recording) {
            RecordingStats(
                frameCount = current.session.frameCount,
                duration = current.session.duration,
                estimatedSize = current.session.estimatedSize,
                fps = if (current.session.duration > 0) {
                    current.session.frameCount / current.session.duration
                } else 0,
                isPaused = current.isPaused,
                captureMethod = current.captureMethod
            )
        } else null
    }

    /**
     * Start countdown before recording
     */
    fun startCountdown(seconds: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            for (i in seconds downTo 1) {
                val current = appState.value
                if (current is AppState.PreparingRecording) {
                    stateRepository.prepareRecording(current.selectedArea)
                    // Update with countdown value
                }
                delay(1000)
            }
            onComplete()
        }
    }
}

/**
 * Recording statistics
 */
data class RecordingStats(
    val frameCount: Int,
    val duration: Int,
    val estimatedSize: Long,
    val fps: Int,
    val isPaused: Boolean,
    val captureMethod: CaptureMethod
)