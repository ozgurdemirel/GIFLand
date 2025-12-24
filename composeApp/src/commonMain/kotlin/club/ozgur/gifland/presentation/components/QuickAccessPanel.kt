package club.ozgur.gifland.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.model.MediaItem
import club.ozgur.gifland.domain.model.OutputFormat
import club.ozgur.gifland.presentation.viewmodel.QuickPanelViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.koin.compose.koinInject

/**
 * Quick Access Panel - A floating, non-blocking overlay for quick actions and recent recordings.
 * This is the hub for the lightweight agent mode.
 */
@Composable
fun QuickAccessPanel(
    visible: Boolean,
    onDismiss: () -> Unit = {},
    viewModel: QuickPanelViewModel = koinInject()
) {
    val appState by viewModel.appState.collectAsState()
    val recentMedia by viewModel.recentMedia.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
                slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
        exit = fadeOut(animationSpec = tween(150)) +
               slideOutVertically(
                   targetOffsetY = { -40 },
                   animationSpec = tween(150)
               )
    ) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = androidx.compose.ui.unit.IntOffset(0, 100),
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            QuickPanelContent(
                appState = appState,
                recentMedia = recentMedia,
                onQuickCapture = { viewModel.quickCapture() },
                onSelectArea = { viewModel.selectArea() },
                onOpenSettings = { viewModel.openSettings() },
                onOpenMainWindow = { viewModel.openMainWindow() },
                onImportMedia = { viewModel.importMedia() },
                onOpenMedia = { viewModel.openRecording(it) },
                onShareMedia = { viewModel.shareRecording(it) },
                onDeleteMedia = { viewModel.deleteRecording(it) },
                onCopyToClipboard = { viewModel.copyToClipboard(it) },
                onOpenFileLocation = { viewModel.openFileLocation(it) },
                onClose = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPanelContent(
    appState: AppState,
    recentMedia: List<MediaItem>,
    onQuickCapture: () -> Unit,
    onSelectArea: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMainWindow: () -> Unit,
    onImportMedia: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onShareMedia: (MediaItem) -> Unit,
    onDeleteMedia: (MediaItem) -> Unit,
    onCopyToClipboard: (MediaItem) -> Unit,
    onOpenFileLocation: (MediaItem) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(400.dp)
            .heightIn(min = 200.dp, max = 600.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            // Header with glassmorphism effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quick Access",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Settings button
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("⚙", color = Color.White, fontSize = 16.sp)
                        }

                        // Main window button
                        IconButton(
                            onClick = onOpenMainWindow,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🗔", color = Color.White, fontSize = 16.sp)
                        }

                        // Close button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("✕", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Quick Actions
            QuickActionsRow(
                onQuickCapture = onQuickCapture,
                onSelectArea = onSelectArea,
                onImportMedia = onImportMedia
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.Gray.copy(alpha = 0.1f)
            )

            // Recent Recordings
            if (recentMedia.isEmpty()) {
                EmptyStateView()
            } else {
                RecentRecordingsList(
                    recordings = recentMedia,
                    onOpenMedia = onOpenMedia,
                    onShareMedia = onShareMedia,
                    onDeleteMedia = onDeleteMedia,
                    onCopyToClipboard = onCopyToClipboard,
                    onOpenFileLocation = onOpenFileLocation
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onQuickCapture: () -> Unit,
    onSelectArea: () -> Unit,
    onImportMedia: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = "🎬",
            label = "Quick Capture",
            onClick = onQuickCapture,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        QuickActionButton(
            icon = "✂",
            label = "Select Area",
            onClick = onSelectArea,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        QuickActionButton(
            icon = "📁",
            label = "Import",
            onClick = onImportMedia,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f)
            }
        ),
        border = if (isHovered) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                icon,
                fontSize = 20.sp
            )
            Text(
                label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = if (isHovered) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun RecentRecordingsList(
    recordings: List<MediaItem>,
    onOpenMedia: (MediaItem) -> Unit,
    onShareMedia: (MediaItem) -> Unit,
    onDeleteMedia: (MediaItem) -> Unit,
    onCopyToClipboard: (MediaItem) -> Unit,
    onOpenFileLocation: (MediaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Recent Recordings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(recordings) { mediaItem ->
            RecordingItem(
                mediaItem = mediaItem,
                onOpen = { onOpenMedia(mediaItem) },
                onShare = { onShareMedia(mediaItem) },
                onDelete = { onDeleteMedia(mediaItem) },
                onCopyToClipboard = { onCopyToClipboard(mediaItem) },
                onOpenFileLocation = { onOpenFileLocation(mediaItem) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    mediaItem: MediaItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onOpenFileLocation: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen,
                onLongClick = { showActions = true }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                Color.Gray.copy(alpha = 0.05f)
            } else {
                Color.Transparent
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Format icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getFormatColor(mediaItem.format).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getFormatIcon(mediaItem.format),
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Media info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    mediaItem.filePath.substringAfterLast("/"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        formatFileSize(mediaItem.sizeBytes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDuration(mediaItem.durationMs),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons (visible on hover)
            AnimatedVisibility(
                visible = isHovered || showActions,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🔗", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = onCopyToClipboard,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("📋", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = onOpenFileLocation,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("📂", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🗑", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📹",
            fontSize = 48.sp,
            modifier = Modifier.alpha(0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No recordings yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start recording to see your captures here",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// Helper functions

@Composable
private fun getFormatColor(format: OutputFormat): Color {
    return when (format) {
        OutputFormat.GIF -> MaterialTheme.colorScheme.secondary
        OutputFormat.WEBP -> MaterialTheme.colorScheme.tertiary
    }
}

private fun getFormatIcon(format: OutputFormat): String {
    return when (format) {
        OutputFormat.GIF -> "GIF"
        OutputFormat.WEBP -> "WebP"
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    return when {
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}