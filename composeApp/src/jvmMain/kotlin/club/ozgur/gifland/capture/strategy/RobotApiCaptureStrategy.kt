package club.ozgur.gifland.capture.strategy

import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.Log
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.*

/**
 * Pure-Java fallback using AWT Robot to capture frames and save as JPEGs.
 * Produces files following the same pattern: ffcap_%06d.jpg
 */
class RobotApiCaptureStrategy : ScreenCaptureStrategy {
    override val name: String = "RobotApiCaptureStrategy"

    private var job: Job? = null
    private var running = false

    override fun start(area: CaptureArea?, fps: Int, scale: Float, jpegQuality: Int, outDir: File) {
        if (running) return
        outDir.mkdirs()

        val captureRect = resolveCaptureRect(area)
        val device = pickDeviceForRect(captureRect)
        val robot = Robot(device)

        val delayMs = (1000L / fps.coerceIn(1, 60)).coerceAtLeast(1)
        val quality = mapQualityToJpeg(jpegQuality)

        job = CoroutineScope(Dispatchers.IO).launch {
            running = true
            var index = 0
            val scaledW = (captureRect.width * scale).toInt().coerceAtLeast(1)
            val scaledH = (captureRect.height * scale).toInt().coerceAtLeast(1)

            while (isActive) {
                try {
                    var frame = robot.createScreenCapture(captureRect)
                    // Draw a simple cursor (arrow) if within rect
                    drawCursor(frame, captureRect)
                    if (scale != 1.0f) {
                        frame = scaleFrame(frame, scaledW, scaledH)
                    }
                    val outFile = File(outDir, String.format("ffcap_%06d.jpg", index++))
                    saveJpeg(frame, outFile, quality)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(name, "Capture error", e)
                    break
                }
                delay(delayMs)
            }
            running = false
        }
    }

    override fun stop() {
        job?.cancel()
        runBlocking { job?.join() }
        job = null
        running = false
    }

    override fun isRunning(): Boolean = running && (job?.isActive == true)

    // Utilities
    private fun resolveCaptureRect(area: CaptureArea?): Rectangle {
        return if (area != null) Rectangle(area.x, area.y, area.width, area.height) else getFullScreenBounds()
    }

    private fun pickDeviceForRect(rect: Rectangle): GraphicsDevice {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val center = Point(rect.x + rect.width / 2, rect.y + rect.height / 2)
        return ge.screenDevices.find { it.defaultConfiguration.bounds.contains(center) } ?: ge.defaultScreenDevice
    }

    private fun getFullScreenBounds(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val mouse = MouseInfo.getPointerInfo().location
        val device = ge.screenDevices.find { it.defaultConfiguration.bounds.contains(mouse) } ?: ge.defaultScreenDevice
        return device.defaultConfiguration.bounds
    }

    private fun drawCursor(img: BufferedImage, rect: Rectangle) {
        val mousePos = try { MouseInfo.getPointerInfo()?.location } catch (_: Throwable) { null }
        if (mousePos == null || !rect.contains(mousePos)) return
        val g2d = img.createGraphics()
        try {
            g2d.color = Color.BLACK
            val relX = mousePos.x - rect.x
            val relY = mousePos.y - rect.y
            val poly = Polygon()
            poly.addPoint(relX, relY)
            poly.addPoint(relX, relY + 16)
            poly.addPoint(relX + 4, relY + 12)
            poly.addPoint(relX + 8, relY + 20)
            poly.addPoint(relX + 10, relY + 18)
            poly.addPoint(relX + 6, relY + 10)
            poly.addPoint(relX + 12, relY + 10)
            g2d.fillPolygon(poly)
            g2d.color = Color.WHITE
            g2d.drawPolygon(poly)
        } finally {
            g2d.dispose()
        }
    }

    private fun scaleFrame(image: BufferedImage, newWidth: Int, newHeight: Int): BufferedImage {
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, newWidth, newHeight)
        g.drawImage(image, 0, 0, newWidth, newHeight, null)
        g.dispose()
        return scaled
    }

    private fun saveJpeg(image: BufferedImage, file: File, quality: Float) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
        if (writer != null) {
            file.outputStream().use { os ->
                writer.output = ImageIO.createImageOutputStream(os)
                val params = writer.defaultWriteParam
                params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = quality
                writer.write(null, javax.imageio.IIOImage(image, null, null), params)
                writer.dispose()
            }
        } else {
            ImageIO.write(image, "jpg", file)
        }
    }

    private fun mapQualityToJpeg(q: Int): Float = when {
        q >= 95 -> 0.98f
        q >= 85 -> 0.96f
        q >= 75 -> 0.94f
        q >= 60 -> 0.92f
        q >= 45 -> 0.9f
        q >= 30 -> 0.85f
        q >= 20 -> 0.8f
        else -> 0.75f
    }
}

