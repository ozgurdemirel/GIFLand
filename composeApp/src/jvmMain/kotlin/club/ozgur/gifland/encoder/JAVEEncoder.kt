package club.ozgur.gifland.encoder

import club.ozgur.gifland.util.Log
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.encode.enums.X264_PROFILE
import ws.schild.jave.info.VideoSize
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import ws.schild.jave.progress.EncoderProgressListener
import java.io.File
import javax.imageio.ImageIO

/**
 * Encoder using JAVE2 library which provides properly signed FFmpeg binaries
 */
object JAVEEncoder {

    @Volatile private var cachedPath: String? = null

    init {
        try {
            // Warm up locator and cache path (best-effort)
            runCatching { getFFmpegPath() }.onFailure { e ->
                Log.d("JAVEEncoder", "JAVE2 init could not resolve FFmpeg yet: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("JAVEEncoder", "Failed to initialize JAVE2", e)
        }
    }

    /**
     * Check if JAVE2 is available
     */
    fun isAvailable(): Boolean = try {
        getFFmpegPath() != null
    } catch (_: Exception) {
        false
    }

    /**
     * Resolve the FFmpeg executable path provided by JAVE2.
     * - Caches the path after first successful lookup
     * - Validates the path points to an existing, executable file
     * - Throws on failure with clear guidance to check Gradle dependencies
     */
    fun getFFmpegPath(): String? {
        synchronized(this) {
            cachedPath?.let { existing ->
                val f = File(existing)
                if (f.isFile && f.canExecute()) return existing
                // If cache is stale, clear and re-resolve
                cachedPath = null
            }

            try {
                val locator = DefaultFFMPEGLocator()
                val path = locator.getExecutablePath()
                val f = File(path)
                if (!f.exists() || !f.isFile) {
                    throw IllegalStateException("JAVE2 FFmpeg path is invalid: '$path' (not a file)")
                }
                if (!f.canExecute()) {
                    throw IllegalStateException("JAVE2 FFmpeg at '$path' is not executable")
                }

                cachedPath = path
                // Best-effort diagnostics
                FFmpegDebugManager.updateFFmpegVersion("Using JAVE2 FFmpeg: $path")
                FFmpegDebugManager.updateArchitecture(System.getProperty("os.arch"))
                Log.d("JAVEEncoder", "JAVE2 FFmpeg path: $path")
                return path
            } catch (e: Exception) {
                val msg = "JAVE2 FFmpeg not available. Ensure Gradle includes: ws.schild:jave-core:3.5.0 and the correct ws.schild:jave-nativebin-* package for this platform."
                Log.e("JAVEEncoder", msg, e)
                FFmpegDebugManager.setError(msg + " (" + (e.message ?: "unknown error") + ")")
                throw IllegalStateException(msg, e)
            }
        }
    }
}