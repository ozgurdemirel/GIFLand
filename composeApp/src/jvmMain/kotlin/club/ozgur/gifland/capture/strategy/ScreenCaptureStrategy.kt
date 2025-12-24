package club.ozgur.gifland.capture.strategy

import club.ozgur.gifland.ui.components.CaptureArea
import java.io.File

/**
 * Strategy interface for producing a JPEG image sequence to a directory.
 * Implementations must write files named using the pattern: ffcap_%06d.jpg
 */
interface ScreenCaptureStrategy {
    /** Start producing frames to outDir. Implementations should create outDir if needed. */
    fun start(area: CaptureArea?, fps: Int, scale: Float, jpegQuality: Int, outDir: File)

    /** Stop any background process/job. Should return when capture is fully stopped. */
    fun stop()

    /** Best-effort running indicator (true when capture is active). */
    fun isRunning(): Boolean

    /** Human-readable name for logging. */
    val name: String
}

