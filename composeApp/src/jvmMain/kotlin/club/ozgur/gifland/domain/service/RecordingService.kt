package club.ozgur.gifland.domain.service

import club.ozgur.gifland.core.ApplicationScope
import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.repository.WindowStateRepository
import club.ozgur.gifland.platform.MonitorDetector

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

/**
 * Service that bridges the existing Recorder with the new state-driven architecture.
 * Now supports multi-monitor aware recording - uses the monitor where the app window is located.
 */
class RecordingService(
    private val stateRepository: StateRepository,
    private val settingsRepository: SettingsRepository,
    private val recorder: Recorder,
    private val windowStateRepository: WindowStateRepository? = null
) : club.ozgur.gifland.domain.service.RecordingController {
    private var recordingJob: Job? = null

    /**
     * Mutex to prevent concurrent recording operations
     */
    private val recordingMutex = Mutex()

    /**
     * Track last update time to prevent out-of-order updates
     */
    private var lastUpdateTime = 0L

    init {
        // Sync recorder settings with app settings using ApplicationScope
        ApplicationScope.launchIO {
            settingsRepository.settingsFlow.collect { appSettings ->
                recorder.settings = RecorderSettings(
                    fps = appSettings.defaultFps,
                    quality = appSettings.defaultQuality,
                    format = mapOutputFormat(appSettings.defaultFormat),
                    maxDuration = appSettings.defaultMaxDuration,
                    scale = appSettings.captureScale,
                    fastGifPreview = false // TODO: Add to AppSettings if needed
                )
            }
        }
    }

    override suspend fun startRecording(captureArea: CaptureRegion?) = recordingMutex.withLock {
        val appState = stateRepository.state.value

        // Only start if in idle state or preparing state
        if (appState !is AppState.Idle && appState !is AppState.PreparingRecording) {
            println("RecordingService: Cannot start recording - not in idle/preparing state (current: $appState)")
            return@withLock
        }

        // Cancel any existing recording job
        recordingJob?.cancel()
        recordingJob = null

        // Get current settings
        val settings = settingsRepository.getCurrentSettings()

        // Determine capture region
        // If no area specified, use full screen of the monitor where the app window is located
        val captureRegion = captureArea ?: run {
            // Get window bounds for multi-monitor detection
            val windowBounds = windowStateRepository?.getCurrentWindowBounds()

            // Find the best monitor based on window position
            val monitor = MonitorDetector.getBestMonitorForRecording(
                windowX = windowBounds?.x,
                windowY = windowBounds?.y,
                windowWidth = windowBounds?.width,
                windowHeight = windowBounds?.height
            )

            // Convert monitor bounds to CaptureRegion
            with(MonitorDetector) { monitor.toCaptureRegion() }
        }

        // Start recording through StateRepository
        stateRepository.startRecording(
            captureArea = captureRegion,
            outputFormat = settings.defaultFormat,
            maxDuration = settings.defaultMaxDuration
        )

        // Reset last update time
        lastUpdateTime = 0L

        // Start actual recording with synchronized callbacks
        recordingJob = ApplicationScope.launchIO {
            val areaForRecorder = captureRegion.let { cr ->
                club.ozgur.gifland.ui.components.CaptureArea(cr.x, cr.y, cr.width, cr.height)
            }
            recorder.startRecording(
                area = areaForRecorder,
                onUpdate = { recorderState ->
                    val updateTime = System.currentTimeMillis()

                    // Prevent out-of-order updates
                    if (updateTime > lastUpdateTime) {
                        lastUpdateTime = updateTime

                        // Use runBlocking to ensure updates complete before next callback
                        runBlocking {
                            try {
                                stateRepository.updateRecordingProgress(
                                    frameCount = recorderState.frameCount,
                                    duration = recorderState.duration,
                                    estimatedSize = recorderState.estimatedSize,
                                    captureMethodDetails = recorderState.captureMethodDetails
                                )

                                // Handle pause state
                                if (recorderState.isPaused) {
                                    val currentState = stateRepository.state.value
                                    if (currentState is AppState.Recording && !currentState.isPaused) {
                                        stateRepository.togglePauseRecording()
                                    }
                                }
                            } catch (e: Exception) {
                                println("RecordingService: Error updating recording progress: ${e.message}")
                            }
                        }
                    }
                },
                onComplete = { result ->
                    runBlocking {
                        try {
                            handleRecordingComplete(result)
                        } catch (e: Exception) {
                            println("RecordingService: Error handling recording completion: ${e.message}")
                        }
                    }
                }
            )
        }
    }

    override suspend fun pauseRecording() {
        recorder.pauseRecording()
        stateRepository.togglePauseRecording()
    }

    override suspend fun stopRecording() {
        val currentState = stateRepository.state.value
        val recorderState = recorder.state.value

        // Stop if either AppState is Recording OR recorder is actually recording
        if (currentState is AppState.Recording || recorderState.isRecording) {
            // Transition to processing state if AppState is Recording
            if (currentState is AppState.Recording) {
                stateRepository.stopRecording()
            }

            // Stop and wait for the result
            val result = recorder.stopRecording()
            handleRecordingComplete(result)
        }
    }

    override suspend fun cancelRecording() {
        recordingJob?.cancel()
        recorder.reset()
        stateRepository.cancelCurrentOperation()
    }

    private suspend fun handleRecordingComplete(result: Result<File>) {
        result.fold(
            onSuccess = { file ->
                // Get the current recording session
                val currentState = stateRepository.state.value
                val session = when (currentState) {
                    is AppState.Recording -> currentState.session
                    is AppState.Processing -> currentState.session
                    else -> null
                }

                if (session != null) {
                    // Create media item
                    val mediaItem = MediaItem(
                        id = generateMediaId(),
                        filePath = file.absolutePath,
                        format = mapToAppFormat(recorder.settings.format),
                        createdAt = Clock.System.now(),
                        durationMs = session.duration * 1000L,
                        sizeBytes = file.length(),
                        thumbnailPath = null,
                        dimensions = Dimensions(
                            width = session.captureArea.width,
                            height = session.captureArea.height
                        ),
                        metadata = mapOf(
                            "fps" to recorder.settings.fps.toString(),
                            "quality" to recorder.settings.quality.toString()
                        )
                    )

                    // Complete processing and return to idle with new media
                    stateRepository.completeProcessing(mediaItem)

                    // Open the media in editor if auto-open is enabled
                    val settings = settingsRepository.getCurrentSettings()
                    if (settings.autoSave) {
                        // Auto-saved, possibly open editor
                        stateRepository.startEditing(mediaItem)
                    }
                }
            },
            onFailure = { error ->
                stateRepository.handleError(
                    message = error.message ?: "Recording failed",
                    cause = error,
                    recoverable = true
                )
            }
        )
    }

    private fun mapOutputFormat(format: OutputFormat): club.ozgur.gifland.core.OutputFormat {
        return when (format) {
            OutputFormat.GIF -> club.ozgur.gifland.core.OutputFormat.GIF
            OutputFormat.WEBP -> club.ozgur.gifland.core.OutputFormat.WEBP
        }
    }

    private fun mapToAppFormat(format: club.ozgur.gifland.core.OutputFormat): OutputFormat {
        return when (format) {
            club.ozgur.gifland.core.OutputFormat.GIF -> OutputFormat.GIF
            club.ozgur.gifland.core.OutputFormat.WEBP -> OutputFormat.WEBP
        }
    }

    private fun generateMediaId(): String {
        return "media_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    fun clearError() {
        recorder.clearError()
    }

    val lastError: StateFlow<String?> = recorder.lastError
    val lastSavedFile: StateFlow<File?> = recorder.lastSavedFile
}