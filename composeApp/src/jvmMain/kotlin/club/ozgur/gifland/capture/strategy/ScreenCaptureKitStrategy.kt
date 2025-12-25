package club.ozgur.gifland.capture.strategy

import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.capture.sck.NativeLoader
import club.ozgur.gifland.capture.sck.SCKBridge
import club.ozgur.gifland.util.Log
import com.sun.jna.Pointer
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ScreenCaptureKitStrategy : ScreenCaptureStrategy {
    override val name: String = "ScreenCaptureKitStrategy"

    private var bridge: SCKBridge? = null
    private var running = false
    private val frameIndex = AtomicInteger(0)
    @Volatile var lastFrameAt: Long = 0

    override fun start(area: CaptureArea?, fps: Int, scale: Float, jpegQuality: Int, outDir: File) {
        if (!NativeLoader.isMac()) throw IllegalStateException("ScreenCaptureKit is macOS-only")
        bridge = SCKBridge.loadOrNull() ?: throw IllegalStateException("ScreenCaptureKit bridge unavailable")

        val displayId = pickDisplayId(area)
        val targetFps = fps.coerceIn(1, 120)
        frameIndex.set(0)
        running = true

        // Simple callback - Swift handles all JPEG encoding and disk I/O
        val cb = object : SCKBridge.FrameCb {
            override fun invoke(frameIdx: Int, user: Pointer?) {
                try {
                    lastFrameAt = System.currentTimeMillis()
                    frameIndex.set(frameIdx + 1)
                    if (frameIdx % 10 == 0) {
                        Log.d(name, "Frame $frameIdx saved by Swift")
                    }
                } catch (t: Throwable) {
                    Log.e(name, "Callback error", t)
                }
            }
        }

        // Region to request from SCK (falls back to full display if null)
        val rx = area?.x ?: -1
        val ry = area?.y ?: -1
        val rw = area?.width ?: 0
        val rh = area?.height ?: 0

        var rc = bridge!!.sck_start_display_capture(
            displayId, targetFps, rx, ry, rw, rh,
            outDir.absolutePath, jpegQuality, scale,
            cb, null
        )
        if (rc == -2 || rc == -4) {
            // Selected display not found or no displays from SCK; retry with the first available display
            val fallbackId = pickDisplayId(null)
            Log.d(name, "SCK start rc=$rc; retrying with fallback displayId=$fallbackId (original=$displayId)")
            if (fallbackId != 0) {
                rc = bridge!!.sck_start_display_capture(
                    fallbackId, targetFps, rx, ry, rw, rh,
                    outDir.absolutePath, jpegQuality, scale,
                    cb, null
                )
            }
        }
        if (rc != 0) throw IllegalStateException("ScreenCaptureKit start failed rc=$rc")
        Log.d(name, "Started SCK capture on displayId=$displayId @ ${targetFps}fps (Swift-side JPEG encoding)")
    }

    override fun stop() {
        runCatching { bridge?.sck_stop_capture() }
        running = false
        Log.d(name, "Stopped SCK capture")
    }

    override fun isRunning(): Boolean = running

    // Helpers
    private fun pickDisplayId(area: CaptureArea?): Int {
        val b = bridge ?: return 0
        val ptr = b.sck_list_displays_json() ?: return 0

        // Safely read JSON from native pointer with validation
        val json = try {
            ptr.getString(0)
        } catch (e: Exception) {
            Log.e(name, "Failed to read displays JSON from native pointer", e)
            return 0
        }

        if (json.isNullOrBlank()) {
            Log.e(name, "Displays JSON is null or blank")
            return 0
        }

        if (!json.trimStart().startsWith("[")) {
            Log.e(name, "Invalid displays JSON format: ${json.take(100)}")
            return 0
        }

        Log.d(name, "SCK displays JSON: ${json.take(256)}")

        // Parse displays from JSON
        val objRegex = Regex("\\{[^}]*}")
        val idR = Regex("\"id\"\\s*:\\s*(\\d+)")
        val wR = Regex("\"width\"\\s*:\\s*(\\d+)")
        val hR = Regex("\"height\"\\s*:\\s*(\\d+)")
        val objs = objRegex.findAll(json).toList()
        if (objs.isEmpty()) { Log.d(name, "SCK displays JSON contained no objects"); return 0 }

        data class Disp(val id: Int, val w: Int, val h: Int)
        val displays = buildList {
            for (o in objs) {
                val s = o.value
                val id = idR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val w = wR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val h = hR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (id != null && w != null && h != null) add(Disp(id, w, h))
            }
        }
        if (displays.isEmpty()) { Log.d(name, "SCK displays JSON parse could not extract id/width/height"); return 0 }

        // Find which Java display contains the capture area's center point
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val javaDevice = if (area != null) {
            val centerX = area.x + area.width / 2
            val centerY = area.y + area.height / 2
            val centerPoint = java.awt.Point(centerX, centerY)
            Log.d(name, "pickDisplayId: area center point ($centerX, $centerY)")

            // Find which screen contains this point
            ge.screenDevices.find { device ->
                device.defaultConfiguration.bounds.contains(centerPoint)
            } ?: ge.defaultScreenDevice
        } else {
            ge.defaultScreenDevice
        }

        val javaBounds = javaDevice.defaultConfiguration.bounds
        Log.d(name, "pickDisplayId: Java display bounds=${javaBounds}")

        // Match Java display to SCK display by size
        var best = displays.first()
        var bestScore = Int.MAX_VALUE
        for (d in displays) {
            val score = kotlin.math.abs(d.w - javaBounds.width) + kotlin.math.abs(d.h - javaBounds.height)
            Log.d(name, "  Display id=${d.id} (${d.w}x${d.h}) score=$score")
            if (score < bestScore) {
                bestScore = score
                best = d
            }
        }
        Log.d(name, "pickDisplayId: selected displayId=${best.id} with score=$bestScore")
        return best.id
    }
}

