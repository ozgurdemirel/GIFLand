package club.ozgur.gifland.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * Custom draggable title bar for transparent windows
 */
@Composable
fun DraggableWindowTitleBar(
    title: String = "Screen Recorder",
    onClose: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .draggableWindowArea(),  // Apply drag to the entire card
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }

            // Title
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Close button
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.error,
                onClick = onClose
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "×",
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-1).dp)
                    )
                }
            }
        }
    }
}

/**
 * Modifier extension to make any composable draggable like a window title bar
 */
fun Modifier.draggableWindowArea(): Modifier {
    return this.pointerInput(Unit) {
        // Store initial mouse and window positions when drag starts
        var initialMousePos: Point? = null
        var initialWindowPos: Point? = null

        detectDragGestures(
            onDragStart = { _ ->
                // Get the current window
                val window = Window.getWindows().firstOrNull { it.isVisible }
                window?.let { w ->
                    // Get initial positions
                    initialMousePos = MouseInfo.getPointerInfo()?.location
                    initialWindowPos = w.location
                }
            },
            onDrag = { _, _ ->
                val window = Window.getWindows().firstOrNull { it.isVisible }
                window?.let { w ->
                    initialMousePos?.let { initMouse ->
                        initialWindowPos?.let { initWindow ->
                            // Get current mouse position
                            val currentMousePos = MouseInfo.getPointerInfo()?.location
                            currentMousePos?.let { currentMouse ->
                                // Calculate the delta from initial positions
                                val deltaX = currentMouse.x - initMouse.x
                                val deltaY = currentMouse.y - initMouse.y

                                // Apply delta to initial window position
                                val newX = initWindow.x + deltaX
                                val newY = initWindow.y + deltaY

                                // Update window position directly (no SwingUtilities.invokeLater)
                                w.setLocation(newX, newY)
                            }
                        }
                    }
                }
            },
            onDragEnd = {
                initialMousePos = null
                initialWindowPos = null
            }
        )
    }
}