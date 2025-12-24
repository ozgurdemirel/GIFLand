# CLAUDE.md - AI Assistant Guide for GIF-Land

> **Comprehensive guide for AI assistants (specifically Claude) when working with the GIF-Land codebase.**
> **GIF-Land**: High-performance screen recording to GIF/WebP/MP4 for macOS and Windows

---

## MANDATORY FOR AI ASSISTANTS - READ THIS FIRST!

### 1. Quality Checks - NEVER SKIP!

After EVERY code change, you MUST:

```bash
# Build and verify compilation
./gradlew :composeApp:build

# Run the application to test changes
./gradlew :composeApp:run

# For macOS: rebuild Swift bridge if native code changed
./gradlew buildSckBridgeMac
```

### 2. State Management - SACRED RULES

**NEVER mutate state directly.** All state changes MUST go through `StateRepository`.

```kotlin
// CORRECT - Use StateRepository methods
stateRepository.startRecording(captureArea, outputFormat)
stateRepository.updateRecordingProgress(frameCount, duration, estimatedSize)
stateRepository.handleError(message, cause, recoverable)

// WRONG - Never do this
_state.value = newState  // Only inside StateRepository!
```

**State Flow Pattern:**
```
User Action → ViewModel → StateRepository.method() → StateFlow emits → UI Recomposes
```

### 3. Capture Strategy Fallback Chain - DO NOT BREAK!

The capture system uses a **priority-based fallback chain**:

```
macOS:  ScreenCaptureKit → Robot API → FFmpeg
Windows/Linux:  Robot API → FFmpeg
```

**Rules:**
- NEVER remove fallback logic in `Recorder.kt`
- ALWAYS handle capture strategy failures gracefully
- Keep the 3-second diagnostic check for frame production
- Preserve the `fallbackStep` progression logic

### 4. Platform-Specific Code - CHECK BEFORE CHANGES

Before modifying platform code, verify:

| Platform | Check | Command |
|----------|-------|---------|
| macOS | SCK bridge compiles | `./gradlew buildSckBridgeMac` |
| macOS | Entitlements valid | Review `composeApp/entitlements.plist` |
| Windows | MSI builds | `./gradlew packageMsi` (on Windows) |

**Native Swift Code Changes:**
```bash
# After modifying composeApp/native/Swift/SCKBridge.swift
./gradlew buildSckBridgeMac
# Binaries go to: src/jvmMain/resources/natives/darwin/{arm64,x64}/
```

### 5. Coroutine Discipline - USE ApplicationScope

**NEVER use `GlobalScope`.** Always use `ApplicationScope`:

```kotlin
// CORRECT
ApplicationScope.launch { /* ... */ }
ApplicationScope.ioScope.launch { /* IO operation */ }

// WRONG
GlobalScope.launch { /* ... */ }  // Never!
CoroutineScope(Dispatchers.IO).launch { /* ... */ }  // Avoid for long-lived ops
```

### 6. Critical Project Information

- **Runtime**: JVM 17+ (Kotlin/JVM)
- **Framework**: Kotlin Multiplatform + Compose Desktop
- **UI**: Material Design 3
- **DI**: Koin (NOT Dagger/Hilt)
- **Navigation**: Voyager (NOT Navigation Compose)
- **Encoding**: FFmpeg via JAVE2 (NOT JavaCV)
- **Native**: Swift ScreenCaptureKit bridge (macOS only)
- **Settings**: multiplatform-settings (Java Preferences backend)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Quick Start Commands](#2-quick-start-commands)
3. [Technology Stack](#3-technology-stack)
4. [Architecture](#4-architecture)
5. [Project Structure](#5-project-structure)
6. [State Management](#6-state-management)
7. [Recording Pipeline](#7-recording-pipeline)
8. [Native Integrations](#8-native-integrations)
9. [Dependency Injection](#9-dependency-injection)
10. [Common Tasks](#10-common-tasks)
11. [Conventions & Patterns](#11-conventions--patterns)

---

## 1. Project Overview

### 1.1 Project Name and Purpose

**Name**: GIF Land
**Purpose**: Desktop screen recording application that captures screen content directly to GIF, WebP, or MP4 formats with high performance and quality.

### 1.2 Key Features

- **Multi-Format Output**: GIF, WebP (80% smaller than GIF), MP4
- **GPU-Accelerated Capture**: ScreenCaptureKit on macOS 12.3+
- **Flexible Recording**: Full screen, area selection, configurable FPS/quality
- **Countdown Timer**: Configurable pre-recording delay with visual overlay
- **System Tray Integration**: Minimize to tray, quick controls, recording indicator
- **Global Hotkeys**: Start/stop/pause recording from anywhere
- **Cross-Platform**: macOS (ARM64 + Intel) and Windows

### 1.3 Supported Platforms

| Platform | Architecture | Capture Method | Package |
|----------|-------------|----------------|---------|
| macOS | Apple Silicon (ARM64) | ScreenCaptureKit | DMG |
| macOS | Intel (x64) | ScreenCaptureKit | DMG |
| Windows | 64-bit | Robot API / GDIgrab | MSI |
| Linux | 64-bit | Robot API / X11grab | Deb |

---

## 2. Quick Start Commands

```bash
# Development
./gradlew :composeApp:run                    # Run app with hot reload
./gradlew :composeApp:runDistributable       # Run packaged app

# Building
./gradlew :composeApp:build                  # Compile and check
./gradlew check                              # Run tests

# Packaging
./gradlew packageDmg                         # macOS DMG (run on macOS)
./gradlew packageMsi                         # Windows MSI (run on Windows)
./gradlew packageDeb                         # Linux Deb

# Native Code (macOS only)
./gradlew buildSckBridgeMac                  # Rebuild Swift bridge

# Clean
./gradlew clean                              # Clean build artifacts
```

---

## 3. Technology Stack

### 3.1 Core Technologies

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.x |
| Runtime | JVM | 17+ |
| UI Framework | Compose Desktop | Latest |
| Design System | Material Design 3 | - |

### 3.2 Key Libraries

```kotlin
// UI & Compose
compose.runtime, compose.foundation, compose.material3, compose.animation

// Async
kotlinx.coroutinesCore, kotlinx.coroutinesSwing

// Serialization & Date
kotlinx.serializationJson, kotlinx.datetime

// Navigation
cafe.adriel.voyager:voyager-navigator (1.0.0)

// Dependency Injection
io.insert-koin:koin-core (3.5.0)
io.insert-koin:koin-compose (1.1.0)

// Settings
com.russhwolf:multiplatform-settings (1.1.1)

// Global Hotkeys
com.github.kwhat:jnativehook

// FFmpeg Wrapper
ws.schild:jave-core (3.5.0)
ws.schild:jave-nativebin-* (platform-specific)

// Native Interface
net.java.dev.jna:jna (5.14.0)
```

---

## 4. Architecture

### 4.1 Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│   ViewModels: MainViewModel, RecordingViewModel, etc.       │
│   Screens: MainScreenCompact, IntegratedSettingsScreen      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│   Models: AppState, AppSettings, RecordingSession           │
│   Repositories: StateRepository, SettingsRepository         │
│   Services: RecordingController (interface)                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PLATFORM LAYER (JVM)                      │
│   Recorder, CaptureStrategies, Encoders, SystemTray         │
│   Native: SCKBridge (Swift), FFmpeg (JAVE2)                 │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Data Flow (Unidirectional)

```
┌──────────┐    ┌────────────┐    ┌─────────────────┐    ┌─────────────┐
│ UI Event │ ──▶│  ViewModel │ ──▶│ StateRepository │ ──▶│  StateFlow  │
└──────────┘    └────────────┘    └─────────────────┘    └──────┬──────┘
                                                                │
     ┌──────────────────────────────────────────────────────────┘
     │
     ▼
┌──────────────────┐
│ UI Recomposition │
└──────────────────┘
```

---

## 5. Project Structure

```
GIF-Land/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/kotlin/club/ozgur/gifland/
│   │   │   ├── di/                    # Koin modules (appModule)
│   │   │   ├── domain/
│   │   │   │   ├── model/             # AppState, AppSettings, etc.
│   │   │   │   ├── repository/        # StateRepository, SettingsRepository
│   │   │   │   └── service/           # RecordingController interface
│   │   │   ├── presentation/          # ViewModels
│   │   │   ├── core/                  # ApplicationScope, DebounceManager
│   │   │   └── util/                  # Common utilities
│   │   │
│   │   ├── jvmMain/kotlin/club/ozgur/gifland/
│   │   │   ├── main.kt                # Application entry point
│   │   │   ├── App.kt                 # Main Compose app
│   │   │   ├── core/
│   │   │   │   ├── Recorder.kt        # Core recording engine
│   │   │   │   └── RecorderSettings.kt
│   │   │   ├── capture/
│   │   │   │   ├── strategy/          # Capture strategies
│   │   │   │   │   ├── ScreenCaptureStrategy.kt (interface)
│   │   │   │   │   ├── ScreenCaptureKitStrategy.kt (macOS)
│   │   │   │   │   ├── RobotApiCaptureStrategy.kt
│   │   │   │   │   └── FFmpegCaptureStrategy.kt
│   │   │   │   └── sck/
│   │   │   │       └── SCKBridge.kt   # JNA bridge to Swift
│   │   │   ├── encoder/
│   │   │   │   ├── JAVEEncoder.kt     # JAVE2 wrapper
│   │   │   │   └── NativeEncoderSimple.kt
│   │   │   ├── di/                    # platformModule
│   │   │   ├── platform/              # System integrations
│   │   │   │   ├── GlobalHotkeyManager.kt
│   │   │   │   ├── SystemTrayManager.kt
│   │   │   │   └── PlatformActions.kt
│   │   │   └── ui/
│   │   │       ├── screens/           # UI screens
│   │   │       ├── components/        # Reusable components
│   │   │       └── theme/             # Material3 theming
│   │   │
│   │   └── jvmMain/resources/
│   │       ├── icons/                 # App icons (ICO, ICNS, PNG)
│   │       └── natives/darwin/        # Swift dylibs (arm64, x64)
│   │
│   ├── native/Swift/
│   │   └── SCKBridge.swift            # ScreenCaptureKit bridge
│   │
│   ├── build.gradle.kts               # Main build config
│   └── entitlements.plist             # macOS security entitlements
│
├── scripts/
│   ├── run/                           # Development scripts
│   └── package/                       # Distribution scripts
│
├── .github/workflows/
│   └── build.yml                      # CI/CD pipeline
│
├── build.gradle.kts                   # Root build config
├── settings.gradle.kts
└── gradle.properties
```

---

## 6. State Management

### 6.1 AppState Hierarchy

```kotlin
sealed class AppState {
    object Initializing : AppState()

    data class Idle(
        val recentRecordings: List<MediaItem>,
        val lastError: String?,
        val isQuickPanelVisible: Boolean,
        val settings: AppSettings
    ) : AppState()

    data class PreparingRecording(
        val selectedArea: CaptureRegion?,
        val countdown: Int?,           // Countdown seconds (null = no countdown)
        val settings: AppSettings
    ) : AppState()

    data class Recording(
        val session: RecordingSession,
        val isPaused: Boolean,
        val captureMethod: CaptureMethod,
        val settings: AppSettings
    ) : AppState()

    data class Processing(
        val session: RecordingSession,
        val progress: Float,           // 0.0 to 1.0
        val stage: ProcessingStage,
        val estimatedTimeRemaining: Int?,
        val settings: AppSettings
    ) : AppState()

    data class Editing(...)
    data class ConfiguringSettings(...)
    data class Error(...)
}
```

### 6.2 StateRepository Methods

```kotlin
// Lifecycle
suspend fun initialize(settings, recentRecordings)

// Recording flow
suspend fun prepareRecording(area?)
suspend fun startCountdownRecording(seconds, area?, onComplete)
suspend fun cancelCountdown()
suspend fun startRecording(captureArea, outputFormat?, maxDuration?)
suspend fun updateRecordingProgress(frameCount, duration, estimatedSize, captureMethodDetails?)
suspend fun togglePauseRecording()
suspend fun stopRecording()

// Processing
suspend fun updateProcessingProgress(progress, stage, estimatedTimeRemaining?)
suspend fun completeProcessing(mediaItem)

// Settings
suspend fun openSettings()
suspend fun updatePendingSettings(settings)
suspend fun applySettings()
suspend fun cancelSettings()

// Error handling
suspend fun handleError(message, cause?, recoverable)
suspend fun recoverFromError()

// General
suspend fun cancelCurrentOperation()
suspend fun toggleQuickPanel()
```

### 6.3 Thread Safety

StateRepository uses `Mutex` for thread-safe state updates:

```kotlin
private val stateMutex = Mutex()

suspend fun updateSomething() {
    stateMutex.withLock {
        _state.value = newState
    }
}
```

---

## 7. Recording Pipeline

### 7.1 Capture Strategies

```kotlin
interface ScreenCaptureStrategy {
    fun start(area: CaptureArea?, fps: Int, scale: Float, quality: Int, outputDir: File)
    fun stop()
    fun isRunning(): Boolean
}
```

**Implementations:**

| Strategy | Platform | Method | Performance |
|----------|----------|--------|-------------|
| `ScreenCaptureKitStrategy` | macOS 12.3+ | GPU-accelerated | Best |
| `RobotApiCaptureStrategy` | All | AWT Robot | Good |
| `FFmpegCaptureStrategy` | All | FFmpeg process | Fallback |

### 7.2 Recording Flow

```
1. User triggers recording
   │
2. Recorder.startRecording()
   │
3. Select capture strategy (SCK → Robot → FFmpeg)
   │
4. Start frame capture to temp JPEG files
   │    ├── tempDir: /tmp/gifland_{timestamp}/
   │    └── files: ffcap_000001.jpg, ffcap_000002.jpg, ...
   │
5. Collector job monitors frame production
   │    ├── Updates state (frameCount, duration, estimatedSize)
   │    ├── Fallback if no frames after 3s
   │    └── Auto-stop at maxDuration
   │
6. User stops recording
   │
7. Encode frames to target format
   │    ├── WebP: NativeEncoderSimple.encodeWebPFromFiles()
   │    ├── MP4:  NativeEncoderSimple.encodeMP4FromFiles()
   │    └── GIF:  NativeEncoderSimple.encodeGIFFromFiles()
   │
8. Cleanup temp files
   │
9. Return Result<File>
```

### 7.3 Output Formats

| Format | Codec | Quality Range | Use Case |
|--------|-------|---------------|----------|
| WebP | VP8/VP9 | 30-90 | Web, small size |
| MP4 | H.264 | CRF 0-35 | Video playback |
| GIF | Palette-based | FPS cap 12-20 | Universal compatibility |

---

## 8. Native Integrations

### 8.1 ScreenCaptureKit Bridge (macOS)

**Swift Source**: `composeApp/native/Swift/SCKBridge.swift`

**JNA Interface**: `composeApp/src/jvmMain/kotlin/club/ozgur/gifland/capture/sck/SCKBridge.kt`

```kotlin
interface SCKBridgeLibrary : Library {
    fun sck_create_capturer(): Pointer
    fun sck_start_capture(capturer: Pointer, displayId: Int,
                          x: Int, y: Int, width: Int, height: Int,
                          fps: Int, scale: Float, quality: Int,
                          outputDir: String, callback: FrameCallback): Int
    fun sck_stop_capture(capturer: Pointer)
    fun sck_destroy_capturer(capturer: Pointer)
}
```

**Build Swift Bridge:**
```bash
./gradlew buildSckBridgeMac
# Outputs:
# - src/jvmMain/resources/natives/darwin/arm64/libsck_bridge_swift.dylib
# - src/jvmMain/resources/natives/darwin/x64/libsck_bridge_swift.dylib
```

### 8.2 FFmpeg via JAVE2

JAVE2 provides signed FFmpeg binaries from Maven Central:

```kotlin
// Platform-specific dependencies (auto-selected in build.gradle.kts)
"ws.schild:jave-nativebin-osxm1:3.5.0"    // Apple Silicon
"ws.schild:jave-nativebin-osx64:3.5.0"    // Intel Mac
"ws.schild:jave-nativebin-win64:3.5.0"    // Windows
"ws.schild:jave-nativebin-linux64:3.5.0"  // Linux
```

### 8.3 macOS Entitlements

**File**: `composeApp/entitlements.plist`

Required for screen recording and FFmpeg execution:
- `com.apple.security.cs.allow-jit`
- `com.apple.security.cs.allow-unsigned-executable-memory`
- `com.apple.security.cs.disable-library-validation`

---

## 9. Dependency Injection

### 9.1 Common Module (appModule)

```kotlin
val appModule = module {
    // Repositories
    single { StateRepository() }
    single { SettingsRepository(get()) }
    single { MediaRepository() }

    // ViewModels
    single { MainViewModel(get(), get(), get()) }
    factory { RecordingViewModel(get()) }
    factory { EditorViewModel(get(), get()) }
    factory { SettingsViewModel(get(), get()) }
    single { QuickPanelViewModel(get(), get(), get()) }

    // Services
    single { ProcessingService(get()) }
    single { ThumbnailService() }
    single { ExportService(get()) }
    single { HotkeyService(get()) }
}
```

### 9.2 Platform Module (JVM)

```kotlin
val platformModule = module {
    // Platform-specific Settings backend
    single<Settings> { PreferencesSettings(Preferences.userRoot()) }

    // Core Recorder
    single { Recorder() }

    // Recording Service (implements RecordingController)
    single<RecordingController> { RecordingService(get(), get()) }
}
```

### 9.3 Initialization

```kotlin
// In main.kt
fun main() = application {
    startKoin {
        modules(appModule, platformModule)
    }
    // ...
}
```

---

## 10. Common Tasks

### 10.1 Adding a New Capture Strategy

1. Implement `ScreenCaptureStrategy` interface:
   ```kotlin
   class NewCaptureStrategy : ScreenCaptureStrategy {
       override fun start(area, fps, scale, quality, outputDir) { }
       override fun stop() { }
       override fun isRunning(): Boolean = /* ... */
   }
   ```

2. Add to fallback chain in `Recorder.kt`:
   ```kotlin
   captureStrategy = when {
       isMac -> ScreenCaptureKitStrategy() // existing
       isNewPlatform -> NewCaptureStrategy() // new
       else -> RobotApiCaptureStrategy()
   }
   ```

### 10.2 Adding a New Output Format

1. Add to `OutputFormat` enum in `AppState.kt`:
   ```kotlin
   enum class OutputFormat { GIF, WEBP, MP4, AVIF }
   ```

2. Add encoder method in `NativeEncoderSimple.kt`:
   ```kotlin
   fun encodeAVIFFromFiles(frameFiles, outputFile, quality, fps, onProgress): Result<File>
   ```

3. Add case in `Recorder.stopRecordingInternal()`:
   ```kotlin
   OutputFormat.AVIF -> {
       NativeEncoderSimple.encodeAVIFFromFiles(...)
   }
   ```

### 10.3 Modifying Settings

1. Add field to `AppSettings` in `AppState.kt`
2. Add persistence key in `SettingsRepository.kt`
3. Add UI control in `IntegratedSettingsScreen.kt`
4. Handle in relevant ViewModel

### 10.4 Adding UI Components

1. Create composable in `ui/components/`
2. Use Material3 theme colors: `MaterialTheme.colorScheme.*`
3. Follow existing patterns for state handling

---

## 11. Conventions & Patterns

### 11.1 Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `StateRepository`, `RecordingViewModel` |
| Functions | camelCase | `startRecording()`, `updateProgress()` |
| Constants | SCREAMING_SNAKE | `MAX_DURATION`, `DEFAULT_FPS` |
| Packages | lowercase | `club.ozgur.gifland.domain.model` |
| Files | PascalCase | `AppState.kt`, `Recorder.kt` |

### 11.2 Error Handling

```kotlin
// Use Result<T> for operations that can fail
suspend fun stopRecording(): Result<File> {
    return try {
        // ... encoding logic
        Result.success(outputFile)
    } catch (e: Exception) {
        Log.e("Recorder", "Encoding failed", e)
        _lastError.value = e.message
        Result.failure(e)
    }
}

// Use StateRepository for user-visible errors
stateRepository.handleError(
    message = "Recording failed: ${e.message}",
    cause = e,
    recoverable = true
)
```

### 11.3 Logging

```kotlin
import club.ozgur.gifland.util.Log

Log.d("TAG", "Debug message")
Log.i("TAG", "Info message")
Log.e("TAG", "Error message", exception)
```

### 11.4 Coroutine Usage

```kotlin
// Long-lived operations: Use ApplicationScope
ApplicationScope.launch {
    // Survives configuration changes
}

// ViewModel-scoped: Use viewModelScope
viewModelScope.launch {
    // Cancelled when ViewModel cleared
}

// IO operations: Dispatch to IO
withContext(Dispatchers.IO) {
    // File operations, network, etc.
}
```

### 11.5 Compose Best Practices

```kotlin
// State hoisting
@Composable
fun RecordButton(
    isRecording: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
)

// Collect StateFlow
val state by viewModel.state.collectAsState()

// Use remember for expensive computations
val sortedItems = remember(items) { items.sortedBy { it.name } }
```

---

## Key Files Reference

| Purpose | File Path |
|---------|-----------|
| Entry point | `composeApp/src/jvmMain/kotlin/club/ozgur/gifland/main.kt` |
| App state models | `composeApp/src/commonMain/kotlin/club/ozgur/gifland/domain/model/AppState.kt` |
| State management | `composeApp/src/commonMain/kotlin/club/ozgur/gifland/domain/repository/StateRepository.kt` |
| Core recorder | `composeApp/src/jvmMain/kotlin/club/ozgur/gifland/core/Recorder.kt` |
| SCK bridge | `composeApp/src/jvmMain/kotlin/club/ozgur/gifland/capture/sck/SCKBridge.kt` |
| Swift source | `composeApp/native/Swift/SCKBridge.swift` |
| DI modules | `composeApp/src/commonMain/kotlin/club/ozgur/gifland/di/AppModule.kt` |
| Build config | `composeApp/build.gradle.kts` |
| Entitlements | `composeApp/entitlements.plist` |

---

**End of Guide**
