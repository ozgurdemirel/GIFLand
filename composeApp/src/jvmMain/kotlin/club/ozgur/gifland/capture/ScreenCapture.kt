package club.ozgur.gifland.capture

import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.util.debugId
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Captures screenshots from a specific monitor using FFmpeg.
 */
class ScreenCapture {

    /**
     * Capture either a full-screen image of the monitor under the mouse
     * or a specific rectangle.
     *
     * @param area When non-null, the rectangle in absolute screen coordinates
     *             to capture. When null, captures the full monitor under the mouse.
     * @return Saved image file or null on failure.
     */
    fun takeScreenshot(area: CaptureArea? = null): File? {
        return takeScreenshotFFmpeg(area)
    }

    private fun takeScreenshotFFmpeg(area: CaptureArea? = null): File? {
        return runCatching {
            Log.d("ScreenCapture", "takeScreenshotFFmpeg(area=${area != null}) request")

            val os = System.getProperty("os.name").lowercase()
            val ffmpegPath = if ((os.contains("mac") || os.contains("darwin")) && club.ozgur.gifland.encoder.JAVEEncoder.isAvailable()) {
                club.ozgur.gifland.encoder.JAVEEncoder.getFFmpegPath() ?: NativeEncoderSimple.findFfmpeg()
            } else {
                NativeEncoderSimple.findFfmpeg()
            }
            val outputFile = File(
                System.getProperty("user.home") + "/Documents",
                "screen_${System.currentTimeMillis()}.png"
            )

            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val mouseLocation = MouseInfo.getPointerInfo().location
            val targetScreen = screens.find { screen ->
                val bounds = screen.defaultConfiguration.bounds
                bounds.contains(mouseLocation)
            } ?: screens.firstOrNull() ?: return@runCatching null

            val command = mutableListOf<String>()
            command += ffmpegPath

            if (os.contains("mac") || os.contains("darwin")) {
                val screenOrdinal = screens.indexOf(targetScreen).coerceAtLeast(0)
                val screenIndex = screenOrdinal + 1 // avfoundation screen devices usually start at 1 (Capture screen 0)
                command += listOf("-f", "avfoundation", "-capture_cursor", "1", "-i", "$screenIndex:none")
                if (area != null) {
                    val bounds = targetScreen.defaultConfiguration.bounds
                    val relX = (area.x - bounds.x).coerceAtLeast(0)
                    val relY = (area.y - bounds.y).coerceAtLeast(0)
                    command += listOf("-vf", "crop=${area.width}:${area.height}:${relX}:${relY}")
                }
                command += listOf("-r", "1", "-vframes", "1")
            } else if (os.contains("win")) {
                command += listOf("-f", "gdigrab", "-draw_mouse", "1")
                if (area != null) {
                    command += listOf(
                        "-offset_x", area.x.toString(),
                        "-offset_y", area.y.toString(),
                        "-video_size", "${area.width}x${area.height}",
                        "-i", "desktop"
                    )
                } else {
                    command += listOf("-i", "desktop")
                }
                command += listOf("-r", "1", "-vframes", "1")
            } else {
                // Linux (X11)
                val display = System.getenv("DISPLAY") ?: ":0.0"
                val bounds = targetScreen.defaultConfiguration.bounds
                command += listOf("-f", "x11grab", "-draw_mouse", "1")
                if (area != null) {
                    command += listOf(
                        "-framerate", "1",
                        "-video_size", "${area.width}x${area.height}",
                        "-i", "$display+${area.x},${area.y}",
                        "-vframes", "1"
                    )
                } else {
                    command += listOf(
                        "-framerate", "1",
                        "-video_size", "${bounds.width}x${bounds.height}",
                        "-i", "$display+${bounds.x},${bounds.y}",
                        "-vframes", "1"
                    )
                }
            }

            command += outputFile.absolutePath

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val success = process.waitFor(5, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }

            if (success && process.exitValue() == 0) {
                Log.d("ScreenCapture", "Screenshot saved to ${outputFile.absolutePath} size=${outputFile.length()} bytes")
                outputFile
            } else {
                Log.e("ScreenCapture", "FFmpeg failed with output: $output")
                null
            }
        }.onFailure { e ->
            Log.e("ScreenCapture", "Error while capturing with FFmpeg", e)
        }.getOrNull()
    }
}
