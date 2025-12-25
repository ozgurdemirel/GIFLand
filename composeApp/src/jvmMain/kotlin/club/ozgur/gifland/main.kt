package club.ozgur.gifland

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.unit.sp

import club.ozgur.gifland.di.appModule
import club.ozgur.gifland.di.platformModule
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.WindowStateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.core.ApplicationScope
import club.ozgur.gifland.util.DebounceManager
import club.ozgur.gifland.platform.SystemTray
import club.ozgur.gifland.platform.TrayState
import club.ozgur.gifland.platform.PlatformActions
import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.ui.components.AreaSelector
import kotlinx.coroutines.launch
import java.io.File
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.get

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background

fun main() = application {
    // Initialize Koin dependency injection
    val koinApp = remember {
        startKoin {
            modules(appModule, platformModule)
        }
    }

    // Get dependencies
    val settingsRepository = remember { koinApp.koin.get<SettingsRepository>() }
    val appSettingsState = settingsRepository.settingsFlow.collectAsState(initial = settingsRepository.getCurrentSettings())
    val appSettings = appSettingsState.value

    val stateRepository = remember { koinApp.koin.get<StateRepository>() }
    val recordingService = remember { koinApp.koin.get<RecordingService>() }
    val windowStateRepository = remember { koinApp.koin.get<WindowStateRepository>() }
    val debounceManager = remember { DebounceManager() }

    // Observe window visibility state
    val isWindowVisible by windowStateRepository.windowVisible.collectAsState()


    var shouldExit by remember { mutableStateOf(false) }

    // App state for tray
    val appState by stateRepository.state.collectAsState()

    // Track last saved recording for tray menu
    val recorder = remember { koinApp.koin.get<club.ozgur.gifland.core.Recorder>() }
    val lastSavedFile by recorder.lastSavedFile.collectAsState()
    val lastRecordingPath = lastSavedFile?.absolutePath

    // Current format from recorder settings
    val recorderSettings by recorder.settingsFlow.collectAsState()
    val currentFormat = recorderSettings.format

    // Convert AppState to TrayState
    fun formatTime(s: Int): String = "%d:%02d".format(s / 60, s % 60)

    val trayState: TrayState = when (val st = appState) {
        is AppState.Recording -> TrayState.Recording(
            duration = st.session.duration,
            maxDuration = st.session.maxDuration,
            frameCount = st.session.frameCount,
            isPaused = st.isPaused
        )
        is AppState.Processing -> TrayState.Processing(
            progress = (st.progress * 100).toInt()
        )
        is AppState.PreparingRecording -> TrayState.Idle(
            countdownSeconds = st.countdown
        )
        else -> TrayState.Idle()
    }

    // Dynamic tooltip based on state
    val trayTooltip = when (val st = appState) {
        is AppState.Recording -> {
            val status = if (st.isPaused) "Paused" else "Recording"
            val time = "${formatTime(st.session.duration)} / ${formatTime(st.session.maxDuration)}"
            "$status $time\n${st.session.frameCount} frames"
        }
        is AppState.Processing -> "Processing... ${(st.progress * 100).toInt()}%"
        is AppState.PreparingRecording ->
            st.countdown?.let { "Recording in ${it}s..." } ?: "Preparing..."
        else -> lastRecordingPath?.let {
                                        "GIF Land\nLast: ${File(it).name}"                        } ?: "GIF Land"
    }

    val windowState = rememberWindowState(
        width = 380.dp,
        height = 580.dp,
        position = WindowPosition(Alignment.Center)
    )

    // System tray support
    if (!shouldExit) {
        SystemTray(
            trayState = trayState,
            tooltip = trayTooltip,
            defaultDelaySeconds = if (appSettings.showCountdown) appSettings.countdownDuration else 0,
            // Idle state actions
            onStartRecording = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.COUNTDOWN_START,
                        DebounceManager.Companion.Delays.COUNTDOWN
                    ) {
                        val delay = if (appSettings.showCountdown) appSettings.countdownDuration else 0
                        if (delay > 0) {
                            stateRepository.startCountdownRecording(delay) {
                                windowStateRepository.showWindow("tray_countdown_complete")
                                recordingService.startRecording(null)
                            }
                        } else {
                            windowStateRepository.showWindow("tray_start_recording")
                            recordingService.startRecording(null)
                        }
                    }
                }
            },
            onQuickRecord = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.START_RECORDING,
                        DebounceManager.Companion.Delays.RECORDING
                    ) {
                        windowStateRepository.showWindow("tray_quick_record")
                        recordingService.startRecording(null)
                    }
                }
            },
            onSelectArea = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.AREA_SELECT,
                        DebounceManager.Companion.Delays.UI
                    ) {
                        val selector = AreaSelector { area ->
                            if (area != null) {
                                ApplicationScope.launch {
                                    val region = club.ozgur.gifland.domain.model.CaptureRegion(area.x, area.y, area.width, area.height)
                                    val delay = if (appSettings.showCountdown) appSettings.countdownDuration else 0

                                    if (delay > 0) {
                                        // Countdown göster, bitince kayıt başlat
                                        stateRepository.startCountdownRecording(delay) {
                                            recordingService.startRecording(region)
                                        }
                                    } else {
                                        // Countdown kapalıysa direkt başlat
                                        recordingService.startRecording(region)
                                    }
                                }
                            }
                        }
                        selector.isVisible = true
                    }
                }
            },
            onCancelCountdown = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.COUNTDOWN_CANCEL,
                        DebounceManager.Companion.Delays.COUNTDOWN
                    ) {
                        stateRepository.cancelCountdown()
                    }
                }
            },
            // Last recording actions
            lastRecordingPath = lastRecordingPath,
            onOpenLastRecording = {
                lastRecordingPath?.let { path ->
                    PlatformActions.openFileLocation(path)
                }
            },
            onCopyToClipboard = {
                lastRecordingPath?.let { path ->
                    PlatformActions.copyToClipboard(path)
                }
            },
            // Format selection
            currentFormat = currentFormat,
            onFormatChange = { format ->
                ApplicationScope.launch {
                    // Convert from core.OutputFormat to domain.model.OutputFormat
                    val domainFormat = club.ozgur.gifland.domain.model.OutputFormat.valueOf(format.name)
                    settingsRepository.updateSetting { it.copy(defaultFormat = domainFormat) }
                }
            },
            // Duration selection
            currentDuration = appSettings.defaultMaxDuration,
            onDurationChange = { duration ->
                ApplicationScope.launch {
                    settingsRepository.updateSetting { it.copy(defaultMaxDuration = duration) }
                }
            },
            // Recording state actions
            onPauseResume = {
                ApplicationScope.launch {
                    recordingService.pauseRecording()
                }
            },
            onStopRecording = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.STOP_RECORDING,
                        DebounceManager.Companion.Delays.RECORDING
                    ) {
                        recordingService.stopRecording()
                    }
                }
            },
            onCancelRecording = {
                ApplicationScope.launch {
                    recordingService.cancelRecording()
                }
            },
            // Common actions
            onShowMainWindow = {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.SHOW_WINDOW,
                        DebounceManager.Companion.Delays.WINDOW
                    ) {
                        windowStateRepository.showWindow("tray_show_main")
                    }
                }
            },
            onOpenSettings = {
                ApplicationScope.launch {
                    windowStateRepository.showWindow("tray_open_settings")
                    stateRepository.openSettings()
                }
            },
            onExit = {
                ApplicationScope.launch {
                    ApplicationScope.shutdown()
                    shouldExit = true
                    exitApplication()
                }
            }
        )
    }
    // Countdown overlay window (shown when tray countdown is active)
    val countdownActive = (appState as? AppState.PreparingRecording)?.countdown
    if (countdownActive != null && !shouldExit) {
        Window(
            onCloseRequest = {},
            visible = true,
            resizable = false,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            state = rememberWindowState(
                width = 420.dp,
                height = 320.dp,
                position = WindowPosition(Alignment.Center)
            )
        ) {
            // Minimal themed floating overlay
            androidx.compose.material3.MaterialTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "Recording",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                            // Countdown visuals: ring progress + animated number/message
                            var startCount by remember(countdownActive != null) { mutableStateOf(if ((countdownActive ?: 1) > 0) (countdownActive ?: 1) else 1) }
                            LaunchedEffect(countdownActive) {
                                val v = countdownActive ?: 0
                                if (v > startCount) startCount = v
                            }

                            val targetProgress = if (startCount > 0) 1f - (countdownActive!!.toFloat() / startCount.toFloat()) else 1f
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = targetProgress,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "countdown-progress"
                            )


                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier.size(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    trackColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                                    strokeWidth = 8.dp,
                                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                                )

                                androidx.compose.animation.AnimatedContent(
                                    targetState = countdownActive!!,
                                    label = "countdown-animated"
                                ) { value ->
                                    if (value <= 0) {
                                        androidx.compose.foundation.layout.Spacer(
                                            modifier = androidx.compose.ui.Modifier.height(1.dp)
                                        )
                                    } else {
                                        androidx.compose.foundation.layout.Box(
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            // Background circle for contrast
                                            androidx.compose.foundation.layout.Box(
                                                modifier = androidx.compose.ui.Modifier
                                                    .size(120.dp)
                                                    .background(
                                                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                            )
                                            androidx.compose.material3.Text(
                                                text = value.toString(),
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    fontSize = 108.sp,
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                        blurRadius = 8f
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                                androidx.compose.material3.Text(
                                    text = if ((countdownActive ?: 0) > 0) "Recording starts in ${countdownActive} seconds..." else "Recording starting now!",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                )

                        }
                    }
                }
            }
        }
    }


    // Main window (can be hidden for tray-only mode)
    if (!shouldExit) {
        Window(
            onCloseRequest = {
                // Minimize to tray instead of exiting
                ApplicationScope.launch {
                    windowStateRepository.minimizeToTray()
                }
            },
            title = "GIF Land",
            state = windowState,
            resizable = false,
            visible = isWindowVisible,
            transparent = true,
            undecorated = true
        ) {
            // Track window position for multi-monitor aware recording
            LaunchedEffect(windowState.position, windowState.size) {
                val pos = windowState.position
                val size = windowState.size
                if (pos is WindowPosition.Absolute) {
                    windowStateRepository.updateWindowBounds(
                        x = pos.x.value.toInt(),
                        y = pos.y.value.toInt(),
                        width = size.width.value.toInt(),
                        height = size.height.value.toInt()
                    )
                }
            }

            App(
                windowState,
                onMinimizeToTray = {
                    ApplicationScope.launch {
                        windowStateRepository.minimizeToTray()
                    }
                },
                onHideMainWindow = {
                    ApplicationScope.launch {
                        windowStateRepository.hideWindow("app_request_hide")
                    }
                },
                onShowMainWindow = {
                    ApplicationScope.launch {
                        windowStateRepository.showWindow("app_request_show")
                    }
                }
            )
        }
    }
}