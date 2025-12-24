# Developer Documentation
# GIF/WebP Screen Recorder

A high-performance screen recording application for macOS and Windows built with Kotlin Multiplatform and Compose Desktop. Records screen content directly to WebP or GIF formats using JAVE2 (FFmpeg wrapper).

## ✨ Features

- **Multiple Recording Modes**: Full screen, area selection, window capture
- **Multiple Output Formats**: WebP (animated), GIF
- **GPU-Accelerated Capture**: ScreenCaptureKit on macOS 12.3+
- **High Performance**: Optimized frame capture and encoding via JAVE2
- **Cross-Platform**: macOS (Apple Silicon) & Windows
- **Customizable Settings**: FPS (1-60), quality, scale factor
- **Countdown Timer**: Configurable pre-recording delay
- **System Tray**: Minimize to tray with quick controls
- **Pause/Resume**: Control recording without losing frames
- **Smart Fallback**: ScreenCaptureKit → Robot API → FFmpeg

## 🚀 Quick Start

```bash
# Run in development mode
./gradlew :composeApp:run

# Build DMG for distribution
./scripts/package/create-full-package.sh
```

## 📋 Requirements

- **macOS**: 12.3 (Monterey) or higher for ScreenCaptureKit (Apple Silicon recommended)
- **Windows**: 10/11 (64-bit)
- **JDK**: 17 or higher
- **Memory**: 8GB RAM recommended
- **Disk Space**: 5GB for build

## 🏗️ Architecture

- **Kotlin/Compose Desktop**: Modern UI framework with declarative UI
- **JAVE2 Encoder**: FFmpeg wrapper with signed binaries from Maven Central
- **ScreenCaptureKit Bridge**: Swift native library for GPU-accelerated capture (macOS)
- **Capture Fallback Chain**: ScreenCaptureKit → Robot API → FFmpeg
- **State Management**: Unidirectional data flow via StateRepository
- **DI**: Koin for dependency injection

## 📂 Project Structure

```
GIF-Land/
├── composeApp/                 # Main application
│   ├── src/
│   │   ├── commonMain/kotlin/club/ozgur/gifland/
│   │   │   ├── di/             # Koin modules
│   │   │   ├── domain/         # Models, repositories, services
│   │   │   ├── presentation/   # ViewModels
│   │   │   └── core/           # ApplicationScope, utilities
│   │   │
│   │   ├── jvmMain/kotlin/club/ozgur/gifland/
│   │   │   ├── core/           # Recorder, RecorderSettings
│   │   │   ├── capture/        # Capture strategies
│   │   │   │   ├── strategy/   # ScreenCaptureKit, Robot, FFmpeg
│   │   │   │   └── sck/        # Swift bridge interface
│   │   │   ├── encoder/        # JAVE2 wrapper, NativeEncoderSimple
│   │   │   ├── platform/       # SystemTray
│   │   │   └── ui/             # Screens, components, theme
│   │   │
│   │   └── jvmMain/resources/
│   │       ├── icons/          # App icons (ICO, ICNS, PNG)
│   │       └── natives/darwin/ # Swift dylibs (arm64, x64)
│   │
│   ├── native/Swift/           # ScreenCaptureKit bridge source
│   │   └── SCKBridge.swift
│   │
│   └── entitlements.plist      # macOS security entitlements
│
├── scripts/                    # Build and utility scripts
│   ├── build/                  # Build scripts
│   ├── package/                # Packaging scripts
│   └── run/                    # Development scripts
│
└── docs/                       # Documentation
```

## 🛠️ Building from Source

### Development Build

```bash
# Clone the repository
git clone https://github.com/ozgurdemirel/GIF-Land.git
cd gif-land

# Quick start - run in development mode
./scripts/run/run-dev.sh

# Or use Gradle directly
./gradlew :composeApp:run
```

### Production Build

```bash
# macOS DMG
./gradlew packageDmg

# Windows MSI
./gradlew packageMsi

# Build Swift bridge (macOS only, if modifying native code)
./gradlew buildSckBridgeMac
```

### Gradle Commands

| Command | Description |
|---------|-------------|
| `./gradlew :composeApp:run` | Run application in development mode |
| `./gradlew :composeApp:build` | Compile and check |
| `./gradlew packageDmg` | Create macOS DMG (run on macOS) |
| `./gradlew packageMsi` | Create Windows MSI (run on Windows) |
| `./gradlew buildSckBridgeMac` | Rebuild Swift ScreenCaptureKit bridge |
| `./gradlew clean` | Clean all build artifacts |

## 🎮 Usage

1. **Launch the application**
2. **Select recording mode**:
   - Full Screen: Records entire display
   - Area Selection: Draw rectangle to record
   - Window Capture: Select specific window
3. **Configure settings**:
   - Format: WebP or GIF
   - FPS: 1-60 frames per second
   - Quality: 1-100 (higher is better)
   - Scale: 0.1-1.0 (resize output)
4. **Start recording** with the record button
5. **Stop recording** to save the file to Documents folder

## ⚙️ Configuration

The application saves recordings to `~/Documents/` by default.

### Recording Settings

- **FPS**: Higher FPS = smoother video, larger file size
- **Quality**:
  - WebP: 80-100 recommended for best quality
  - GIF: 30-50 for reasonable file sizes
- **Scale**: Reduce to create smaller files
- **Max Duration**: Automatic stop after specified seconds

## 🔧 Troubleshooting

### Application won't start
```bash
# Check Java version (requires 17+)
java -version

# Clean and rebuild
./gradlew clean build
```

### Screen recording not working (macOS)
- Ensure macOS 12.3+ for ScreenCaptureKit
- Grant screen recording permission in System Preferences
- The app will fallback to Robot API if ScreenCaptureKit fails

### Out of memory errors
```bash
# Increase JVM memory
export GRADLE_OPTS="-Xmx4g"
./gradlew :composeApp:run
```

### Black screen in recordings (Windows)
- Run as Administrator
- Disable Hardware Acceleration in the application being recorded

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is for demonstration purposes. Commercial use requires proper licensing.

## 🙏 Acknowledgments

- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) - UI Framework
- [JAVE2](https://github.com/a-schild/jave2) - FFmpeg wrapper with signed binaries
- [Koin](https://insert-koin.io/) - Dependency Injection
- [Voyager](https://voyager.adriel.cafe/) - Navigation
- [Kotlin](https://kotlinlang.org/) - Programming language
- [ScreenCaptureKit](https://developer.apple.com/documentation/screencapturekit) - macOS capture API

## 📞 Support

For issues and questions:
- Open an issue on GitHub
- Check existing issues for solutions

---

Made with ❤️ using Kotlin/Compose Desktop