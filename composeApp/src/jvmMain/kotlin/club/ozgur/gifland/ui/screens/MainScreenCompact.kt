@file:OptIn(ExperimentalFoundationApi::class)

package club.ozgur.gifland.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.FadeTransition
import club.ozgur.gifland.LocalRecorder
import club.ozgur.gifland.LocalRecordingService
import club.ozgur.gifland.LocalWindowControl
import club.ozgur.gifland.domain.model.CaptureRegion
import club.ozgur.gifland.core.ApplicationScope
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.ui.components.AreaSelector
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.ui.components.DraggableWindowTitleBar
import club.ozgur.gifland.platform.PlatformActions
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.capture.sck.SCKBridge
import club.ozgur.gifland.ui.components.PermissionDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Compact Main Screen - Minimal floating window style
 * Small, focused interface with essential controls only
 */
object MainScreenCompact : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val recorder = LocalRecorder.current
        val recordingService = LocalRecordingService.current
        val stateRepository = koinInject<StateRepository>()
        val appState by stateRepository.state.collectAsState()

        val recordingState by recorder.state.collectAsState()
        val currentSettings by recorder.settingsFlow.collectAsState()
        var selectedArea by remember { mutableStateOf<CaptureArea?>(null) }

        val lastSavedFile by recorder.lastSavedFile.collectAsState()
        val lastError by recorder.lastError.collectAsState()

        // Pulse animation for record button
        val pulseAnimation = rememberInfiniteTransition()
        val pulse by pulseAnimation.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // Navigate to recording screen when recording starts
        LaunchedEffect(recordingState.isRecording) {
            if (recordingState.isRecording) {
                navigator.push(RecordingScreen)
            }
        }

        // Settings dialog state
        var showSettingsDialog by remember { mutableStateOf(false) }

        // Show settings dialog when triggered from system tray
        LaunchedEffect(appState) {
            if (appState is AppState.ConfiguringSettings) {
                showSettingsDialog = true
            }
        }

        // Permission check for macOS screen recording
        var showPermissionDialog by remember { mutableStateOf(false) }
        var permissionChecked by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (PlatformActions.isMac() && !permissionChecked) {
                withContext(Dispatchers.IO) {
                    val bridge = SCKBridge.loadOrNull()
                    if (bridge != null) {
                        val hasPermission = bridge.sck_check_permission() == 0
                        if (!hasPermission) {
                            showPermissionDialog = true
                        }
                    }
                }
                permissionChecked = true
            }
        }

        // Permission dialog
        if (showPermissionDialog) {
            PermissionDialog(
                onOpenSettings = {
                    scope.launch(Dispatchers.IO) {
                        val bridge = SCKBridge.loadOrNull()
                        if (bridge != null) {
                            // Request permission (shows system dialog if not determined)
                            bridge.sck_request_permission()
                            // Trigger SCShareableContent access to register app in Privacy list
                            bridge.sck_trigger_permission_registration()
                        }
                        // Open System Preferences to Screen Recording settings
                        PlatformActions.openScreenRecordingSettings()
                    }
                    showPermissionDialog = false
                },
                onDismiss = {
                    showPermissionDialog = false
                }
            )
        }

        // Local composable function for format chips
        @Composable
        fun FormatChip(
            format: OutputFormat,
            currentFormat: OutputFormat,
            onClick: () -> Unit
        ) {
            Card(
                modifier = Modifier
                    .clickable { onClick() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (format == currentFormat)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Text(
                    text = format.name,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = if (format == currentFormat) FontWeight.Bold else FontWeight.Normal,
                    color = if (format == currentFormat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Error dialog
        if (lastError != null) {
            AlertDialog(
                onDismissRequest = { recorder.clearError() },
                confirmButton = {
                    TextButton(onClick = { recorder.clearError() }) {
                        Text("OK")
                    }
                },
                title = { Text("Error") },
                text = { Text(lastError ?: "Unknown error") },
                properties = DialogProperties(dismissOnClickOutside = true)
            )
        }

        // Compact floating window style UI
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 340.dp) // Slightly wider for better fit
                        .heightIn(max = 620.dp)
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
                        // Draggable Title Bar with close button
                        val windowControl = LocalWindowControl.current
                        DraggableWindowTitleBar(
                            title = "Screen Recorder",
                            onClose = { windowControl.onMinimizeToTray() }
                        )

                        // Main content with padding - scrollable
                        val scrollState = rememberScrollState()

                        // Check if content is scrollable
                        val canScroll = scrollState.maxValue > 0

                        // Capture surface color for gradient (theme-aware)
                        val surfaceColor = MaterialTheme.colorScheme.surface

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    // Add fade effect at top and bottom when scrollable
                                    .drawWithContent {
                                        drawContent()

                                        // Only show gradients when content is scrollable
                                        if (canScroll) {
                                            // Top gradient when not at top
                                            if (scrollState.value > 0) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            surfaceColor,
                                                            surfaceColor.copy(alpha = 0f)
                                                        ),
                                                        startY = 0f,
                                                        endY = 50f
                                                    ),
                                                    topLeft = Offset.Zero,
                                                    size = Size(size.width, 50f)
                                                )
                                            }

                                            // Bottom gradient when not at bottom
                                            if (scrollState.value < scrollState.maxValue) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            surfaceColor.copy(alpha = 0f),
                                                            surfaceColor
                                                        ),
                                                        startY = size.height - 50f,
                                                        endY = size.height
                                                    ),
                                                    topLeft = Offset(0f, size.height - 50f),
                                                    size = Size(size.width, 50f)
                                                )
                                            }
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                            // App Title
                            Text(
                                text = "🎬 Screen Recorder",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Status indicator
                            AnimatedContent(
                            targetState = when {
                                recordingState.isSaving -> "Processing..."
                                lastSavedFile != null -> "✅ Ready"
                                else -> "Ready to record"
                            },
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }
                        ) { status ->
                            Text(
                                text = status,
                                fontSize = 13.sp,
                                color = when {
                                    recordingState.isSaving -> MaterialTheme.colorScheme.secondary
                                    lastSavedFile != null -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center
                            )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Main Record Button
                            Box(
                            modifier = Modifier.size(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer ring
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(if (!recordingState.isSaving) pulse else 1f)
                                    .border(
                                        width = 3.dp,
                                        color = Color(0xFFFF5252).copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )

                            // Record button
                            Button(
                                onClick = {
                                    recorder.reset()
                                    // Navigate immediately to RecordingScreen to avoid showing both
                                    navigator.push(RecordingScreen)
                                    // Use RecordingService to update StateRepository (enables system tray blink)
                                    val captureRegion = selectedArea?.let {
                                        CaptureRegion(it.x, it.y, it.width, it.height)
                                    }
                                    ApplicationScope.launch {
                                        recordingService.startRecording(captureRegion)
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .alpha(if (recordingState.isSaving) 0.5f else 1f),
                                enabled = !recordingState.isSaving,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    disabledContainerColor = Color(0xFFFF5252).copy(alpha = 0.4f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                if (recordingState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "⏺",
                                        fontSize = 32.sp,
                                        color = Color.White
                                    )
                                }
                            }
                            }

                            // Compact area selection
                            Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Full Screen
                            TextButton(
                                onClick = {
                                    selectedArea = null
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedArea == null)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🖥️", fontSize = 20.sp)
                                    Text("Full", fontSize = 11.sp)
                                }
                            }

                            // Select Area
                            TextButton(
                                onClick = {
                                    val selector = AreaSelector { area ->
                                        selectedArea = area
                                    }
                                    selector.isVisible = true
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedArea != null)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("✂️", fontSize = 20.sp)
                                    Text("Area", fontSize = 11.sp)
                                }
                            }
                            }

                            // Format selector - minimal
                            Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FormatChip(
                                format = OutputFormat.GIF,
                                currentFormat = currentSettings.format,
                                onClick = {
                                    recorder.settings = recorder.settings.copy(format = OutputFormat.GIF)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FormatChip(
                                format = OutputFormat.WEBP,
                                currentFormat = currentSettings.format,
                                onClick = {
                                    recorder.settings = recorder.settings.copy(format = OutputFormat.WEBP)
                                }
                            )
                            }

                            // Bottom actions - minimal
                            AnimatedVisibility(
                            visible = lastSavedFile != null && !recordingState.isSaving,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                TextButton(
                                    onClick = {
                                        lastSavedFile?.let { file ->
                                            try {
                                                PlatformActions.openFileLocation(file.absolutePath)
                                            } catch (e: Exception) {
                                                Log.e("MainScreen", "Error opening file: ${file.absolutePath}", e)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Text("📂 Open Last Recording", fontSize = 12.sp)
                                }
                            }
                            }

                            // Quick selectors for Duration, FPS, Quality
                            var showDurationSelector by remember { mutableStateOf(false) }
                            var showFpsSelector by remember { mutableStateOf(false) }
                            var showQualitySelector by remember { mutableStateOf(false) }

                            val durationOptions = listOf(30, 60, 120, 180, 300) // 30s, 1m, 2m, 3m, 5m
                            val fpsOptions = listOf(10, 15, 24, 30)
                            val qualityOptions = listOf(10, 20, 30, 40)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Duration selector chips
                                AnimatedVisibility(
                                    visible = showDurationSelector,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        durationOptions.forEach { duration ->
                                            val label = if (duration >= 60) "${duration / 60}m" else "${duration}s"
                                            val isSelected = currentSettings.maxDuration == duration
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    recorder.settings = recorder.settings.copy(maxDuration = duration)
                                                    showDurationSelector = false
                                                },
                                                label = { Text(label, fontSize = 10.sp) },
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // FPS selector chips
                                AnimatedVisibility(
                                    visible = showFpsSelector,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        fpsOptions.forEach { fps ->
                                            val isSelected = currentSettings.fps == fps
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    recorder.settings = recorder.settings.copy(fps = fps)
                                                    showFpsSelector = false
                                                },
                                                label = { Text("${fps}fps", fontSize = 10.sp) },
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Quality selector chips
                                AnimatedVisibility(
                                    visible = showQualitySelector,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        qualityOptions.forEach { quality ->
                                            val isSelected = currentSettings.quality == quality
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    recorder.settings = recorder.settings.copy(quality = quality)
                                                    showQualitySelector = false
                                                },
                                                label = { Text("Q$quality", fontSize = 10.sp) },
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Bottom row with settings, duration, fps, quality toggles
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Settings
                                    IconButton(
                                        onClick = { showSettingsDialog = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("⚙️", fontSize = 14.sp)
                                    }

                                    // Duration toggle button
                                    TextButton(
                                        onClick = {
                                            showDurationSelector = !showDurationSelector
                                            showFpsSelector = false
                                            showQualitySelector = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (showDurationSelector)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        val durationText = if (currentSettings.maxDuration >= 60)
                                            "${currentSettings.maxDuration / 60}m"
                                        else "${currentSettings.maxDuration}s"
                                        Text("⏱️$durationText", fontSize = 11.sp)
                                    }

                                    // FPS toggle button
                                    TextButton(
                                        onClick = {
                                            showFpsSelector = !showFpsSelector
                                            showDurationSelector = false
                                            showQualitySelector = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (showFpsSelector)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("🎞️${currentSettings.fps}", fontSize = 11.sp)
                                    }

                                    // Quality toggle button
                                    TextButton(
                                        onClick = {
                                            showQualitySelector = !showQualitySelector
                                            showDurationSelector = false
                                            showFpsSelector = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (showQualitySelector)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Q${currentSettings.quality}", fontSize = 11.sp)
                                    }
                                }
                            } // Close Column
                    } // Close Column (line 150 - main content column)

                            // Vertical scrollbar - visible when content is scrollable
                            if (canScroll) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(scrollState),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(end = 2.dp),
                                    style = ScrollbarStyle(
                                        minimalHeight = 40.dp,
                                        thickness = 6.dp,
                                        shape = RoundedCornerShape(3.dp),
                                        hoverDurationMillis = 300,
                                        unhoverColor = Color.Gray.copy(alpha = 0.3f),
                                        hoverColor = Color.Gray.copy(alpha = 0.6f)
                                    )
                                )
                            }

                            // Scroll indicator arrows (optional visual hint)
                            if (canScroll && scrollState.value < scrollState.maxValue) {
                                // Bottom scroll indicator when not at bottom
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp)
                                        .size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text("↓", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        } // Close Box (line 163)
                    } // Close Column (line 147 - card content column)
            } // Close Card (line 129)
        } // Close Box (line 125)

        // Settings Dialog Window
        if (showSettingsDialog) {
            DialogWindow(
                onCloseRequest = { showSettingsDialog = false },
                title = "Settings",
                state = rememberDialogState(
                    position = WindowPosition.PlatformDefault,
                    width = 380.dp,
                    height = 400.dp
                ),
                resizable = false
            ) {
                SettingsDialogContent(
                    onClose = { showSettingsDialog = false }
                )
            }
        }
    } // Close Surface (line 119)
} // Close Content function (line 45)
} // Close object MainScreenCompact (line 43)

/**
 * Settings dialog content - displayed in a separate window
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialogContent(onClose: () -> Unit) {
    val koin = org.koin.compose.getKoin()
    val settingsViewModel = remember { koin.get<club.ozgur.gifland.presentation.viewmodel.SettingsViewModel>() }
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()

    var selectedTab by remember { mutableStateOf(club.ozgur.gifland.domain.model.SettingsTab.Appearance) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    club.ozgur.gifland.domain.model.SettingsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.name) }
                        )
                    }
                }

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        club.ozgur.gifland.domain.model.SettingsTab.Appearance -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Light,
                                                onClick = { scope.launch { settingsViewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Light) } },
                                                label = { Text("Light") }
                                            )
                                            FilterChip(
                                                selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Dark,
                                                onClick = { scope.launch { settingsViewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Dark) } },
                                                label = { Text("Dark") }
                                            )
                                            FilterChip(
                                                selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.System,
                                                onClick = { scope.launch { settingsViewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.System) } },
                                                label = { Text("System") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        club.ozgur.gifland.domain.model.SettingsTab.About -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Spacer(Modifier.height(16.dp))
                                Text("GIF Land", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Version 1.0.11", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Coded in Ankara, grown with passion", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                TextButton(onClick = {
                                    try { java.awt.Desktop.getDesktop().browse(java.net.URI("https://www.linkedin.com/in/ozgdemirel/")) } catch (_: Exception) {}
                                }) {
                                    Text("linkedin.com/in/ozgdemirel", fontSize = 13.sp, color = Color(0xFF0A66C2))
                                }
                                Spacer(Modifier.weight(1f))
                                Text("© 2025 Ozgur Demirel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

            }
        }
    }
}