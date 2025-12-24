package club.ozgur.gifland.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.LocalRecorder
import club.ozgur.gifland.LocalRecordingService
import club.ozgur.gifland.LocalWindowControl
import club.ozgur.gifland.core.ApplicationScope
import club.ozgur.gifland.ui.components.DraggableWindowTitleBar
import club.ozgur.gifland.util.Log
import kotlinx.coroutines.launch

object RecordingScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val recorder = LocalRecorder.current
        val recordingService = LocalRecordingService.current
        val recordingState by recorder.state.collectAsState()
        val lastError by recorder.lastError.collectAsState()

        // Track if recording has started
        var hasStartedRecording by remember { mutableStateOf(recordingState.isRecording) }

        // Auto-navigate back when recording stops
        LaunchedEffect(recordingState.isRecording, recordingState.isSaving) {
            if (hasStartedRecording && !recordingState.isRecording) {
                navigator.pop()
            } else if (recordingState.isRecording) {
                hasStartedRecording = true
            }
        }

        // Error dialog
        if (lastError != null) {
            AlertDialog(
                onDismissRequest = { recorder.clearError() },
                confirmButton = {
                    TextButton(onClick = { recorder.clearError() }) { Text("OK") }
                },
                title = { Text("Error") },
                text = { Text(lastError ?: "Unknown error") },
                properties = DialogProperties(dismissOnClickOutside = true)
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Draggable title bar
                        val windowControl = LocalWindowControl.current
                        DraggableWindowTitleBar(
                            title = "Recording",
                            onClose = { windowControl.onMinimizeToTray() }
                        )

                        // Main content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Progress calculation
                            val progress = (recordingState.duration / recorder.settings.maxDuration.toFloat())
                                .coerceIn(0f, 1f)

                            // Recording Orb - The star of the show
                            RecordingOrb(
                                isRecording = recordingState.isRecording,
                                isPaused = recordingState.isPaused,
                                progress = progress,
                                modifier = Modifier.size(140.dp)
                            )

                            // Duration display with progress bar
                            DurationDisplay(
                                currentSeconds = recordingState.duration,
                                maxSeconds = recorder.settings.maxDuration,
                                progress = progress
                            )

                            // Floating frame counter
                            FloatingFrameCounter(
                                count = recordingState.frameCount,
                                isPaused = recordingState.isPaused
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Control buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Pause/Resume button
                                Button(
                                    onClick = { recorder.pauseRecording() },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (recordingState.isPaused)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        if (recordingState.isPaused) "▶ Resume" else "⏸ Pause",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Stop button
                                Button(
                                    onClick = {
                                        navigator.pop()
                                        ApplicationScope.launch {
                                            // Use RecordingService to properly update app state
                                            recordingService.stopRecording()
                                            recorder.lastSavedFile.value?.let { file ->
                                                Log.d("RecordingScreen", "Recording saved: ${file.absolutePath}")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "⏹ Stop",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Hint text
                            Text(
                                text = "Press Stop to save recording",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Recording Orb - A breathing, glowing circular indicator
 * with progress ring and pulsing center dot
 */
@Composable
private fun RecordingOrb(
    isRecording: Boolean,
    isPaused: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Breathing scale animation (pauses when recording is paused)
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Glow alpha animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Center dot pulse
    val dotPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // Theme colors
    val orbColor = if (isPaused) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.error
    }

    val glowColor = if (isPaused) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = glowAlpha)
    }

    // Progress ring color - becomes more intense near the end
    val progressRingColor = if (progress > 0.8f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val actualScale = if (isPaused) 1f else breathingScale
    val actualDotPulse = if (isPaused) 1f else dotPulse

    Box(
        modifier = modifier.scale(actualScale),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Outer glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * 0.85f

            // Glow effect - larger, semi-transparent circle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor,
                        glowColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius * 1.3f
                ),
                radius = radius * 1.3f,
                center = center
            )
        }

        // Layer 2: Progress ring (background track)
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background track
            drawCircle(
                color = Color.Gray.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = progressRingColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Layer 3: Inner orb
        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColor.copy(alpha = 0.8f),
                        orbColor.copy(alpha = 0.4f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Layer 4: Center dot (pulsing)
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(actualDotPulse)
                .background(
                    if (isPaused) MaterialTheme.colorScheme.outline
                    else Color.White,
                    CircleShape
                )
        )

        // Paused overlay
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PAUSED",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Duration display with animated progress bar
 */
@Composable
private fun DurationDisplay(
    currentSeconds: Int,
    maxSeconds: Int,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "duration-progress"
    )

    // Color changes as we approach the limit
    val progressColor = if (progress > 0.8f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Time display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatTime(currentSeconds),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "/",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(maxSeconds),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                progressColor.copy(alpha = 0.6f),
                                progressColor
                            )
                        ),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

/**
 * Floating frame counter with subtle levitation animation
 */
@Composable
private fun FloatingFrameCounter(
    count: Int,
    isPaused: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "levitate"
    )

    val actualOffset = if (isPaused) 0f else floatOffset

    Column(
        modifier = Modifier.offset(y = actualOffset.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "frames captured",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format seconds to MM:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
