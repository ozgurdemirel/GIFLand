package club.ozgur.gifland.capture

import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.ui.components.CaptureArea
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.io.File

/**
 * Cross-platform FFmpeg-based frame capture that writes an image sequence to disk.
 * Uses NativeEncoderSimple.findFfmpeg() and ProcessBuilder, consistent with encoding paths.
 */
object FFmpegFrameCapture {
    data class FFmpegCaptureSession(
        val process: Process,
        val outDir: File,
        val outputPattern: String,
        val logFile: File
    )

    /**
     * Start an FFmpeg process capturing the given area (or full screen) at the specified fps.
     * - scale: if not 1.0f, applies a scale filter (iw*scale:ih*scale)
     * - qscale: FFmpeg JPEG quality (2..31, lower is better)
     */
    fun start(area: CaptureArea?, fps: Int, scale: Float, qscale: Int, outDir: File): FFmpegCaptureSession {
        val os = System.getProperty("os.name").lowercase()
        val ffmpeg = club.ozgur.gifland.encoder.JAVEEncoder.getFFmpegPath()
            ?: throw IllegalStateException("JAVE2 FFmpeg not available - check Gradle dependencies")
        outDir.mkdirs()
        val outputPattern = File(outDir, "ffcap_%06d.jpg").absolutePath
        val logFile = File(outDir, "ffmpeg_capture.log")

        // macOS: Log available AVFoundation devices to aid debugging (permissions/indexing)
        if (os.contains("mac") || os.contains("darwin")) {
            runCatching {
                val listProc = ProcessBuilder(ffmpeg, "-f", "avfoundation", "-list_devices", "true", "-i", "").redirectErrorStream(true).start()
                listProc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                val devicesOut = listProc.inputStream.bufferedReader().use { it.readText() }
                Log.d("FFmpegFrameCapture", "AVFoundation devices:\n$devicesOut")
                runCatching { logFile.appendText("=== AVFoundation devices ===\n$devicesOut\n") }
            }.onFailure { e -> Log.d("FFmpegFrameCapture", "Could not list avfoundation devices: ${e.message}") }
        }

        // Determine target screen. Prefer the screen containing the selection's center when area != null.
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val mouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        val areaCenter: Point? = area?.let { Point(it.x + it.width / 2, it.y + it.height / 2) }
        val targetScreen = (areaCenter?.let { center ->
            screens.find { s -> s.defaultConfiguration.bounds.contains(center) }
        }) ?: screens.find { s -> mouse?.let { s.defaultConfiguration.bounds.contains(it) } == true } ?: screens.firstOrNull()
        val sb = targetScreen?.defaultConfiguration?.bounds
        Log.d("FFmpegFrameCapture", "targetScreen=${screens.indexOf(targetScreen)} sb=$sb area=$area mouse=$mouse areaCenter=$areaCenter")

        val base: MutableList<String> = mutableListOf(ffmpeg)
        var filter: String? = null

        when {
            os.contains("mac") || os.contains("darwin") -> {
                // avfoundation screen capture devices typically start at index 1 (Capture screen 0)
                val screenOrdinal = screens.indexOf(targetScreen).coerceAtLeast(0)
                val screenIndex = screenOrdinal + 1
                base.addAll(listOf("-f", "avfoundation", "-capture_cursor", "1", "-framerate", fps.toString(), "-i", "$screenIndex:none"))
                if (area != null) {
                    val sbRect = sb ?: Rectangle(0, 0, 0, 0)
                    val selRect = Rectangle(area.x, area.y, area.width, area.height)
                    val intersection = sbRect.intersection(selRect)

                    // Relative coords in logical points
                    val relX = (intersection.x - sbRect.x).coerceAtLeast(0)
                    val relY = (intersection.y - sbRect.y).coerceAtLeast(0)
                    val cropW = intersection.width
                    val cropH = intersection.height

                    // HiDPI: map logical points -> device pixels for avfoundation
                    val tx = targetScreen?.defaultConfiguration?.defaultTransform
                    val scaleX = tx?.getScaleX() ?: 1.0
                    val scaleY = tx?.getScaleY() ?: 1.0
                    val relXpx = kotlin.math.round(relX * scaleX).toInt()
                    val relYpx = kotlin.math.round(relY * scaleY).toInt()
                    val cropWpx = kotlin.math.round(cropW * scaleX).toInt()
                    val cropHpx = kotlin.math.round(cropH * scaleY).toInt()

                    Log.d("FFmpegFrameCapture", "macOS crop debug: area=$area sb=$sbRect intersection=$intersection " +
                        "relLogical=($relX,$relY) cropLogical=${cropW}x${cropH} scale=(${String.format("%.2f", scaleX)},${String.format("%.2f", scaleY)}) " +
                        "relPx=($relXpx,$relYpx) cropPx=${cropWpx}x${cropHpx}")

                    // Guard against invalid sizes
                    val safeW = cropWpx.coerceAtLeast(2)
                    val safeH = cropHpx.coerceAtLeast(2)
                    val safeX = relXpx.coerceAtLeast(0)
                    val safeY = relYpx.coerceAtLeast(0)

                    filter = "crop=${safeW}:${safeH}:${safeX}:${safeY}"
                }
            }
            os.contains("win") -> {
                base.addAll(listOf("-f", "gdigrab", "-draw_mouse", "1", "-framerate", fps.toString()))
                if (area != null) {
                    base.addAll(listOf("-offset_x", area.x.toString(), "-offset_y", area.y.toString(), "-video_size", "${area.width}x${area.height}", "-i", "desktop"))
                } else {
                    base.addAll(listOf("-i", "desktop"))
                }
            }
            else -> { // Linux (assume X11)
                val display = System.getenv("DISPLAY") ?: ":0.0"
                val drawMouse = listOf("-draw_mouse", "1")
                base.addAll(listOf("-f", "x11grab", "-framerate", fps.toString()))
                if (area != null) {
                    base.addAll(drawMouse + listOf("-video_size", "${area.width}x${area.height}", "-i", "$display+${area.x},${area.y}"))
                } else {
                    val w = sb?.width ?: 1920
                    val h = sb?.height ?: 1080
                    val ox = sb?.x ?: 0
                    val oy = sb?.y ?: 0
                    base.addAll(drawMouse + listOf("-video_size", "${w}x${h}", "-i", "$display+${ox},${oy}"))
                }
            }
        }

        if (scale != 1.0f) {
            filter = (filter?.let { "$it," } ?: "") + "scale=iw*${scale}:ih*${scale}"
        }
        // Build a single filter chain to avoid overriding previous -vf
        val filterChain = buildString {
            if (filter != null) append(filter)
            if (length > 0) append(",")
            append("fps=${fps}")
        }
        Log.d("FFmpegFrameCapture", "filters='$filterChain'")
        base.addAll(listOf("-vf", filterChain, "-vsync", "cfr"))
        base.addAll(listOf("-q:v", qscale.coerceIn(2, 31).toString(), "-f", "image2", outputPattern))

        Log.d("FFmpegFrameCapture", "Starting FFmpeg: ${base.joinToString(" ")}")
        val process = ProcessBuilder(base)
            .redirectErrorStream(true)
            .start()

        // Drain ffmpeg output to avoid blocking and aid diagnostics
        kotlin.concurrent.thread(start = true, isDaemon = true, name = "ffmpeg-capture-logger") {
            runCatching {
                logFile.printWriter().use { pw ->
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            Log.d("FFmpegFrameCapture", it)
                            pw.println(it)
                        }
                    }
                }
            }.onFailure { e -> Log.d("FFmpegFrameCapture", "Failed to read ffmpeg output: ${e.message}") }
        }

        return FFmpegCaptureSession(process, outDir, outputPattern, logFile)
    }

    fun stop(session: FFmpegCaptureSession, waitMs: Long = 3000) {
        runCatching {
            if (session.process.isAlive) {
                Log.d("FFmpegFrameCapture", "Stopping FFmpeg gracefully (q)")
                session.process.outputStream.use { os ->
                    os.write('q'.code)
                    os.flush()
                }
                val waited = session.process.waitFor(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!waited && session.process.isAlive) {
                    Log.d("FFmpegFrameCapture", "FFmpeg did not exit gracefully, destroying...")
                    session.process.destroy()
                }
            }
        }.onFailure { e ->
            Log.e("FFmpegFrameCapture", "Error stopping FFmpeg", e)
            runCatching { session.process.destroyForcibly() }
        }
    }
}

