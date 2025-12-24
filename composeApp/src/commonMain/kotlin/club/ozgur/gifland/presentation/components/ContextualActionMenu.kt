package club.ozgur.gifland.presentation.components

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.model.CaptureMethod
import club.ozgur.gifland.presentation.viewmodel.RecordingViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Contextual Action Menu - A floating control panel that appears during active recording.
 * Provides essential controls and live data display with glassmorphism effect.
 */
@Composable
fun ContextualActionMenu(
    visible: Boolean,
    viewModel: RecordingViewModel = koinInject()
) {
    val appState by viewModel.appState.collectAsState()

    // Only show when recording
    val shouldShow = visible && appState is AppState.Recording

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(animationSpec = tween(300)) +
                scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
        exit = fadeOut(animationSpec = tween(200)) +
               scaleOut(
                   targetScale = 0.8f,
                   animationSpec = tween(200)
               )
    ) {
        val currentState = appState
        if (currentState is AppState.Recording) {
            ContextualMenuContent(
                recordingState = currentState,
                onPauseResume = { viewModel.togglePause() },
                onStop = { viewModel.stopRecording() },
                onCancel = { viewModel.cancelRecording() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualMenuContent(
    recordingState: AppState.Recording,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    // Animation states
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnimation.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowAnimation = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glowAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Popup(
        alignment = Alignment.BottomEnd,
        offset = androidx.compose.ui.unit.IntOffset(-20, -20),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // Glassmorphism container
        Box(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
        ) {
            // Glow effect background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 4.dp)
                    .blur(20.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = glowAlpha * 0.3f),
                        RoundedCornerShape(20.dp)
                    )
            )

            // Main card with glassmorphism
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.1f),
                        spotColor = Color.Black.copy(alpha = 0.15f)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.9f),
                                    Color.White.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Recording indicator with pulse
                    RecordingIndicator(
                        isPaused = recordingState.isPaused,
                        pulse = pulse
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live data display
                    LiveDataDisplay(
                        frameCount = recordingState.session.frameCount,
                        duration = recordingState.session.duration,
                        estimatedSize = recordingState.session.estimatedSize,
                        maxDuration = recordingState.session.maxDuration,
                        captureMethod = recordingState.captureMethod
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = Color.Gray.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Control buttons
                    ControlButtons(
                        isPaused = recordingState.isPaused,
                        onPauseResume = onPauseResume,
                        onStop = onStop,
                        onCancel = onCancel
                    )

                    // Warning if approaching max duration
                    if (recordingState.session.duration > recordingState.session.maxDuration * 0.8) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DurationWarning(
                            remainingSeconds = recordingState.session.maxDuration - recordingState.session.duration
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(
    isPaused: Boolean,
    pulse: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated recording dot
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(if (isPaused) 1f else pulse)
                .clip(CircleShape)
                .background(
                    if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
        )

        Text(
            text = if (isPaused) "PAUSED" else "RECORDING",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun LiveDataDisplay(
    frameCount: Int,
    duration: Int,
    estimatedSize: Long,
    maxDuration: Int,
    captureMethod: CaptureMethod
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { duration.toFloat() / maxDuration },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
        )

        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(duration),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTime(maxDuration),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Frames",
                value = frameCount.toString(),
                icon = "🎬"
            )
            StatItem(
                label = "Size",
                value = formatFileSize(estimatedSize),
                icon = "💾"
            )
            StatItem(
                label = "FPS",
                value = if (duration > 0) (frameCount / duration).toString() else "0",
                icon = "⚡"
            )
        }

        // Capture method indicator
        CaptureMethodBadge(captureMethod)
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 16.sp)
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun CaptureMethodBadge(method: CaptureMethod) {
    Card(
        modifier = Modifier.padding(top = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = getMethodColor(method).copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = getMethodName(method),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = getMethodColor(method),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ControlButtons(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pause/Resume button
        OutlinedButton(
            onClick = onPauseResume,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.tertiary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
        ) {
            Text(
                text = if (isPaused) "▶ Resume" else "⏸ Pause",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Stop button
        Button(
            onClick = onStop,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "⏹ Stop",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Text(
                "✕",
                fontSize = 18.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun DurationWarning(remainingSeconds: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚠",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Recording stops in ${remainingSeconds}s",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Floating mini version for minimized state
@Composable
fun MiniContextualMenu(
    frameCount: Int,
    duration: Int,
    isPaused: Boolean,
    onExpand: () -> Unit,
    onStop: () -> Unit
) {
    val pulseAnimation = rememberInfiniteTransition(label = "mini-pulse")
    val pulse by pulseAnimation.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mini-pulse"
    )

    Card(
        modifier = Modifier
            .clickable { onExpand() }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recording indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(if (isPaused) 1f else pulse)
                    .clip(CircleShape)
                    .background(
                        if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
            )

            // Time
            Text(
                formatTime(duration),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Frame count
            Text(
                "$frameCount",
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Stop button
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(28.dp)
            ) {
                Text("⏹", fontSize = 16.sp)
            }
        }
    }
}

// Helper functions

@Composable
private fun getMethodColor(method: CaptureMethod): Color {
    return when (method) {
        CaptureMethod.ScreenCaptureKit -> MaterialTheme.colorScheme.primary
        CaptureMethod.RobotApi -> MaterialTheme.colorScheme.tertiary
        CaptureMethod.FFmpeg -> MaterialTheme.colorScheme.secondary
        CaptureMethod.Auto -> MaterialTheme.colorScheme.outlineVariant
    }
}

private fun getMethodName(method: CaptureMethod): String {
    return when (method) {
        CaptureMethod.ScreenCaptureKit -> "ScreenCaptureKit"
        CaptureMethod.RobotApi -> "Robot API"
        CaptureMethod.FFmpeg -> "FFmpeg"
        CaptureMethod.Auto -> "Auto"
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}