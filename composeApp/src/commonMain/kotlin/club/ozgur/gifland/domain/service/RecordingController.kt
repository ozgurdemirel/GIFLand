package club.ozgur.gifland.domain.service

import club.ozgur.gifland.domain.model.CaptureRegion

/**
 * Cross-platform recording controller interface for UI/ViewModels.
 * Implemented on JVM by RecordingService and exposed via DI.
 */
interface RecordingController {
    suspend fun startRecording(captureArea: CaptureRegion? = null)
    suspend fun pauseRecording()
    suspend fun stopRecording()
    suspend fun cancelRecording()
}

