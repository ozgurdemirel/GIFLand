package club.ozgur.gifland.ui.components

import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import javax.swing.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/**
 * Immutable rectangle representing a requested capture area on the screen.
 *
 * Coordinate system:
 * - `x`/`y` are absolute screen-space coordinates relative to the virtual desktop origin.
 * - Values are derived from `Component.locationOnScreen` + panel-local positions.
 * - We intentionally do not pre-scale for HiDPI. The capture layer creates a
 *   `Robot` bound to the target monitor, which interprets these coordinates
 *   correctly without double-scaling.
 */
data class CaptureArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Modern area selector with enhanced UX:
 * - Dark overlay with transparent selection area
 * - Crosshair cursor with coordinates display
 * - Grid lines and guides for precise selection
 * - Smooth animations and visual feedback
 * - Instructional text for user guidance
 */
class AreaSelector(private val onAreaSelected: (CaptureArea?) -> Unit) : JFrame() {

    private var startPoint: Point? = null
    private var endPoint: Point? = null
    private var currentMousePoint: Point? = null
    private var isSelecting = false
    private var animationAlpha = 0f
    private val animationTimer: Timer
    private var lastRepaintTime = 0L
    private val REPAINT_DELAY_MS = 33L // ~30 FPS instead of 60
    private var pulseValue = 0f
    private var cachedGradients = mutableMapOf<String, Paint>()

    init {
        // Full screen overlay with transparency
        isUndecorated = true
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 1) // Almost transparent for OS detection

        // Animation timer with reduced frequency
        animationTimer = Timer(33) { // 30 FPS instead of 60
            if (animationAlpha < 1f) {
                animationAlpha = min(1f, animationAlpha + 0.15f) // Faster alpha increment
                throttledRepaint()
            }
            // Update pulse value only when selecting
            if (isSelecting) {
                pulseValue = ((System.currentTimeMillis() % 1000) / 1000f)
            }
        }

        // Custom panel with enhanced visual feedback
        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics?) {
                super.paintComponent(g)

                val g2d = g as Graphics2D
                // Use lower quality hints for better performance
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)

                // Draw dark overlay with animation
                g2d.color = Color(0, 0, 0, (180 * animationAlpha).toInt())
                g2d.fillRect(0, 0, width, height)

                // Draw crosshair cursor when not selecting (skip if animating)
                if (!isSelecting && currentMousePoint != null && animationAlpha >= 1f) {
                    drawCrosshair(g2d, currentMousePoint!!)
                    drawCoordinates(g2d, currentMousePoint!!)
                }

                // Draw instructions at the top
                if (!isSelecting) {
                    drawInstructions(g2d)
                }

                // Draw selection area with enhanced visuals
                if (isSelecting && startPoint != null && endPoint != null) {
                    val x = min(startPoint!!.x, endPoint!!.x)
                    val y = min(startPoint!!.y, endPoint!!.y)
                    val w = abs(endPoint!!.x - startPoint!!.x)
                    val h = abs(endPoint!!.y - startPoint!!.y)

                    // Clear the selection area (make it transparent)
                    val composite = g2d.composite
                    g2d.composite = AlphaComposite.Clear
                    g2d.fillRect(x, y, w, h)
                    g2d.composite = composite

                    // Draw grid lines inside selection
                    drawGrid(g2d, x, y, w, h)

                    // Cache and reuse gradient
                    val gradientKey = "selection_$x,$y,$w,$h"
                    val gradient = cachedGradients.getOrPut(gradientKey) {
                        GradientPaint(
                            x.toFloat(), y.toFloat(), Color(0, 255, 127, 255),
                            (x + w).toFloat(), (y + h).toFloat(), Color(0, 191, 255, 255)
                        )
                    }
                    g2d.paint = gradient
                    g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2d.drawRect(x, y, w, h)

                    // Draw animated corner handles
                    drawHandles(g2d, x, y, w, h)

                    // Draw size info with better styling
                    drawSizeInfo(g2d, x, y, w, h)

                    // Draw guide lines
                    drawGuideLines(g2d, x, y, w, h)
                }
            }

            private fun drawCrosshair(g2d: Graphics2D, point: Point) {
                g2d.color = Color(255, 255, 255, 100)
                g2d.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(5f, 5f), 0f)

                // Vertical line
                g2d.drawLine(point.x, 0, point.x, height)
                // Horizontal line
                g2d.drawLine(0, point.y, width, point.y)

                // Center circle
                g2d.color = Color(0, 255, 127, 200)
                g2d.stroke = BasicStroke(2f)
                g2d.drawOval(point.x - 10, point.y - 10, 20, 20)
            }

            private fun drawCoordinates(g2d: Graphics2D, point: Point) {
                val text = "(${point.x}, ${point.y})"
                g2d.font = Font("Arial", Font.PLAIN, 12)
                val metrics = g2d.fontMetrics
                val textWidth = metrics.stringWidth(text)
                val textHeight = metrics.height

                val x = point.x + 15
                val y = point.y - 15

                // Background
                g2d.color = Color(0, 0, 0, 200)
                g2d.fillRoundRect(x, y - textHeight + 5, textWidth + 10, textHeight + 4, 8, 8)

                // Text
                g2d.color = Color(255, 255, 255, 255)
                g2d.drawString(text, x + 5, y)
            }

            private fun drawInstructions(g2d: Graphics2D) {
                val instructions = listOf(
                    "Click and drag to select recording area",
                    "Cancel: ESC  |  Confirm: Release mouse"
                )

                g2d.font = Font("Arial", Font.BOLD, 16)
                val metrics = g2d.fontMetrics
                var maxWidth = 0
                instructions.forEach { maxWidth = max(maxWidth, metrics.stringWidth(it)) }

                val boxWidth = maxWidth + 40
                val boxHeight = (instructions.size * 30) + 20
                val x = (width - boxWidth) / 2
                val y = 40

                // Simpler background without gradient
                g2d.color = Color(255, 255, 255, 20)
                g2d.fillRoundRect(x, y, boxWidth, boxHeight, 15, 15)

                // Border
                g2d.color = Color(255, 255, 255, 80)
                g2d.stroke = BasicStroke(1f)
                g2d.drawRoundRect(x, y, boxWidth, boxHeight, 15, 15)

                // Draw instruction text
                g2d.color = Color.WHITE
                instructions.forEachIndexed { index, text ->
                    val textX = x + (boxWidth - metrics.stringWidth(text)) / 2
                    val textY = y + 35 + (index * 30)
                    g2d.drawString(text, textX, textY)
                }
            }

            private fun drawGrid(g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
                // Skip grid for small selections to improve performance
                if (w > 150 && h > 150) {
                    g2d.color = Color(255, 255, 255, 20) // Less opacity
                    g2d.stroke = BasicStroke(0.5f) // Simpler stroke

                    // Draw vertical lines
                    val vSpacing = w / 3
                    for (i in 1..2) {
                        val lineX = x + (i * vSpacing)
                        g2d.drawLine(lineX, y, lineX, y + h)
                    }

                    // Draw horizontal lines
                    val hSpacing = h / 3
                    for (i in 1..2) {
                        val lineY = y + (i * hSpacing)
                        g2d.drawLine(x, lineY, x + w, lineY)
                    }
                }
            }

            private fun drawHandles(g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
                val handleSize = 12
                val innerSize = 6

                // Use cached pulse value instead of recalculating
                val pulse = pulseValue
                val size = handleSize + (pulse * 4).toInt()

                g2d.color = Color(0, 255, 127, 255)
                g2d.stroke = BasicStroke(2f)

                // Corners
                val corners = listOf(
                    Point(x, y),
                    Point(x + w, y),
                    Point(x, y + h),
                    Point(x + w, y + h)
                )

                corners.forEach { corner ->
                    // Outer circle
                    g2d.color = Color(0, 255, 127, (100 + pulse * 100).toInt())
                    g2d.fillOval(corner.x - size/2, corner.y - size/2, size, size)

                    // Inner circle
                    g2d.color = Color.WHITE
                    g2d.fillOval(corner.x - innerSize/2, corner.y - innerSize/2, innerSize, innerSize)
                }

                // Midpoints
                if (w > 60 && h > 60) {
                    val midpoints = listOf(
                        Point(x + w/2, y),
                        Point(x + w/2, y + h),
                        Point(x, y + h/2),
                        Point(x + w, y + h/2)
                    )

                    g2d.color = Color(0, 191, 255, 200)
                    midpoints.forEach { point ->
                        g2d.fillRect(point.x - 4, point.y - 4, 8, 8)
                    }
                }
            }

            private fun drawSizeInfo(g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
                if (w < 30 || h < 30) return

                val sizeText = "${w} x ${h} px"
                val aspectRatio = String.format("%.2f:1", w.toFloat() / h.toFloat())

                g2d.font = Font("Arial", Font.BOLD, 18)
                val metrics = g2d.fontMetrics
                val sizeWidth = metrics.stringWidth(sizeText)
                val sizeHeight = metrics.height

                g2d.font = Font("Arial", Font.PLAIN, 14)
                val ratioMetrics = g2d.fontMetrics
                val ratioWidth = ratioMetrics.stringWidth(aspectRatio)

                val boxWidth = max(sizeWidth, ratioWidth) + 20
                val boxHeight = sizeHeight + ratioMetrics.height + 15

                val infoX = x + (w - boxWidth) / 2
                val infoY = y + (h - boxHeight) / 2

                // Simple background without gradient
                g2d.color = Color(0, 0, 0, 200)
                g2d.fillRoundRect(infoX, infoY, boxWidth, boxHeight, 12, 12)

                // Border
                g2d.color = Color(0, 255, 127, 150)
                g2d.stroke = BasicStroke(1.5f)
                g2d.drawRoundRect(infoX, infoY, boxWidth, boxHeight, 12, 12)

                // Size text
                g2d.font = Font("Arial", Font.BOLD, 18)
                g2d.color = Color.WHITE
                val textX = infoX + (boxWidth - sizeWidth) / 2
                g2d.drawString(sizeText, textX, infoY + sizeHeight)

                // Aspect ratio
                g2d.font = Font("Arial", Font.PLAIN, 14)
                g2d.color = Color(200, 200, 200)
                val ratioX = infoX + (boxWidth - ratioWidth) / 2
                g2d.drawString(aspectRatio, ratioX, infoY + sizeHeight + ratioMetrics.height)
            }

            private fun drawGuideLines(g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
                // Skip guide lines for small selections
                if (w < 50 || h < 50) return

                // Extension lines with simpler stroke
                g2d.color = Color(0, 255, 127, 60)
                g2d.stroke = BasicStroke(0.5f)

                // Only draw if selection is large enough
                if (w > 100 && h > 100) {
                    // Top extension
                    if (y > 20) g2d.drawLine(x + w/2, 0, x + w/2, y)
                    // Bottom extension
                    if (y + h < height - 20) g2d.drawLine(x + w/2, y + h, x + w/2, height)
                    // Left extension
                    if (x > 20) g2d.drawLine(0, y + h/2, x, y + h/2)
                    // Right extension
                    if (x + w < width - 20) g2d.drawLine(x + w, y + h/2, width, y + h/2)
                }
            }
        }

        panel.isOpaque = false
        panel.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        contentPane.add(panel)

        // Mouse listeners implement the drag-to-select UX.
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                // Start selection in panel coordinates.
                startPoint = e.point
                endPoint = e.point
                isSelecting = true
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!isSelecting || startPoint == null || endPoint == null) return

                // Compute selection rectangle in panel coordinates.
                val x = min(startPoint!!.x, endPoint!!.x)
                val y = min(startPoint!!.y, endPoint!!.y)
                val w = abs(endPoint!!.x - startPoint!!.x)
                val h = abs(endPoint!!.y - startPoint!!.y)

                // Convert panel coordinates to absolute screen coordinates.
                val screenLoc = panel.locationOnScreen
                val absoluteX = screenLoc.x + x
                val absoluteY = screenLoc.y + y


                // Reset state and close overlay before notifying the listener.
                isSelecting = false
                startPoint = null
                endPoint = null
                dispose()

                // Notify listener when selection exceeds a minimal threshold.
                if (w > 10 && h > 10) {
                    onAreaSelected(CaptureArea(absoluteX, absoluteY, w, h))
                } else {
                    onAreaSelected(null)
                }
            }
        })

        panel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                endPoint = e.point
                throttledRepaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                currentMousePoint = e.point
                // Only repaint if not animating to reduce CPU usage
                if (animationAlpha >= 1f) {
                    throttledRepaint()
                }
            }
        })

        // ESC key cancels the selection.
        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    dispose()
                    onAreaSelected(null)
                }
            }
        })

        panel.isFocusable = true

        // Size overlay to the monitor determined by [getAppScreen].
        val appScreen = getAppScreen()
        setBounds(appScreen)

        // Start animation
        animationTimer.start()
    }

    /**
     * Decide which monitor to overlay.
     *
     * Order of preference:
     * 1) Monitor under the mouse cursor (matches user intent best)
     * 2) Monitor containing any visible app window
     * 3) Default screen device
     */
    private fun getAppScreen(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()

        // 1) Use the monitor under the mouse if available
        val mouseLocation = MouseInfo.getPointerInfo()?.location
        if (mouseLocation != null) {
            for (device in ge.screenDevices) {
                val bounds = device.defaultConfiguration.bounds
                if (bounds.contains(mouseLocation)) {
                    return bounds
                }
            }
        }

        // 2) Otherwise, try the monitor where a visible window resides
        val activeWindows = Window.getWindows().filter { it.isVisible }
        if (activeWindows.isNotEmpty()) {
            val appWindow = activeWindows.first()
            val windowLocation = appWindow.locationOnScreen

            for (device in ge.screenDevices) {
                val bounds = device.defaultConfiguration.bounds
                if (bounds.contains(windowLocation)) {
                    return bounds
                }
            }
        }

        // 3) Fallback to the default screen
        return ge.defaultScreenDevice.defaultConfiguration.bounds
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (visible) {
            animationAlpha = 0f
            animationTimer.restart()
            EventQueue.invokeLater {
                toFront()
                requestFocusInWindow()
                contentPane.requestFocusInWindow()
            }
        } else {
            animationTimer.stop()
        }
    }

    override fun dispose() {
        animationTimer.stop()
        cachedGradients.clear()
        super.dispose()
    }

    private fun throttledRepaint() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRepaintTime >= REPAINT_DELAY_MS) {
            repaint()
            lastRepaintTime = currentTime
        }
    }
}