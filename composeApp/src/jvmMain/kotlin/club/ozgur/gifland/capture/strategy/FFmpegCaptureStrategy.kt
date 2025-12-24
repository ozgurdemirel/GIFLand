package club.ozgur.gifland.capture.strategy

import club.ozgur.gifland.capture.FFmpegFrameCapture
import club.ozgur.gifland.capture.FFmpegFrameCapture.FFmpegCaptureSession
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.Log
import java.io.File

/** Wrap existing FFmpegFrameCapture in the strategy interface. */
class FFmpegCaptureStrategy : ScreenCaptureStrategy {
    override val name: String = "FFmpegCaptureStrategy"

    private var session: FFmpegCaptureSession? = null

    override fun start(area: CaptureArea?, fps: Int, scale: Float, jpegQuality: Int, outDir: File) {
        // Map JPEG quality [0..100] to FFmpeg qscale [2..31] (lower is better)
        val qscale = when {
            jpegQuality >= 90 -> 2
            jpegQuality >= 75 -> 3
            jpegQuality >= 60 -> 5
            jpegQuality >= 45 -> 7
            jpegQuality >= 30 -> 10
            jpegQuality >= 20 -> 12
            else -> 16
        }
        Log.d(name, "Starting FFmpeg capture fps=$fps scale=$scale qscale=$qscale out=${outDir.absolutePath}")
        outDir.mkdirs()
        session = FFmpegFrameCapture.start(area = area, fps = fps, scale = scale, qscale = qscale, outDir = outDir)
    }

    override fun stop() {
        val s = session
        if (s != null) {
            Log.d(name, "Stopping FFmpeg session")
            runCatching { FFmpegFrameCapture.stop(s) }.onFailure { e -> Log.e(name, "Stop error", e) }
            session = null
        }
    }

    override fun isRunning(): Boolean {
        val alive = session?.process?.isAlive ?: false
        return alive
    }
}

