package club.ozgur.gifland.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.model.AppTheme

/** ThemeMode used inside UI/theming layer. */

/** Generic cross-platform system theme observer interface */
private interface SystemThemeObserver {
    fun start()
    fun stop()
    fun isSystemDark(): Boolean
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
// Synchronous one-shot system theme detection used at bootstrap and on demand
private fun detectSystemDarkBootstrap(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") -> MacOsThemeObserver { }.isSystemDark()
        os.contains("win") -> WindowsThemeObserver { }.isSystemDark()
        os.contains("nux") || os.contains("nix") || os.contains("linux") -> LinuxThemeObserver { }.isSystemDark()
        else -> false
    }
}


/**
 * ThemeManager keeps the current theme mode and dark/light resolution.
 * It observes SettingsRepository for persisted preference and listens for
 * system appearance changes on macOS. Falls back gracefully.
 */
object ThemeManager {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    // Bootstrap initial dark state synchronously to avoid first-frame flash
    var isDark by mutableStateOf(detectSystemDarkBootstrap())
        private set

    var colorScheme: ColorScheme by mutableStateOf(if (isDark) DarkColorScheme else LightColorScheme)
        private set

    private var systemObserver: SystemThemeObserver? = null

    fun initialize(settingsFlow: StateFlow<AppSettings>) {
        // observe settings preference
        scope.launch {
            settingsFlow.collectLatest { settings ->
                val newMode = when (settings.theme) {
                    AppTheme.Light -> ThemeMode.LIGHT
                    AppTheme.Dark -> ThemeMode.DARK
                    AppTheme.System -> ThemeMode.SYSTEM
                }
                applyMode(newMode)
            }
        }
    }

    fun applyMode(newMode: ThemeMode) {
        if (mode == newMode) {
            // If SYSTEM is requested but observer hasn’t started yet, start it
            if (newMode == ThemeMode.SYSTEM && systemObserver == null) {
                startSystemObservation()
            }
            return
        }
        mode = newMode
        when (newMode) {
            ThemeMode.LIGHT -> updateDark(false)
            ThemeMode.DARK -> updateDark(true)
            ThemeMode.SYSTEM -> startSystemObservation()
        }
    }

    private fun startSystemObservation() {
        if (systemObserver == null) {
            val os = System.getProperty("os.name").lowercase()
            val obs: SystemThemeObserver? = when {
                os.contains("mac") -> MacOsThemeObserver { dark -> updateDark(dark) }
                os.contains("win") -> WindowsThemeObserver { dark -> updateDark(dark) }
                os.contains("nux") || os.contains("nix") || os.contains("linux") -> LinuxThemeObserver { dark -> updateDark(dark) }
                else -> null
            }
            systemObserver = obs
        }
        systemObserver?.start()
        // also set immediately
        updateDark(systemObserver?.isSystemDark() ?: false)
    }

    private fun updateDark(dark: Boolean) {
        isDark = dark
        colorScheme = if (dark) DarkColorScheme else LightColorScheme
        if (mode != ThemeMode.SYSTEM) {
            // stop observer when user forces a mode
            systemObserver?.stop()
        }
    }

    /** Public helper for synchronous system theme detection */
    fun detectSystemDarkNow(): Boolean = detectSystemDarkBootstrap()
}

/**
 * Very small macOS appearance observer. Uses polling as a safe-by-default fallback
 * to avoid fragile native callbacks. If unavailable, always returns false.
 */
private class MacOsThemeObserver(private val onChange: (Boolean) -> Unit) : SystemThemeObserver {
    @Volatile private var running = false

    private var job: Job? = null

    override fun start() {
        if (running) return
        running = true
        job = CoroutineScope(Dispatchers.IO).launch {
            var last = isSystemDark()
            onChange(last)
            while (running) {
                try {
                    val now = isSystemDark()
                    if (now != last) {
                        last = now
                        onChange(now)
                    }
                    kotlinx.coroutines.delay(1500)
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(3000)
                }
            }
        }
    }

    override fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    override fun isSystemDark(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return false
        return try {
            // `defaults read -g AppleInterfaceStyle` returns "Dark" when dark, exits non-zero otherwise
            val process = ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle").start()
            val exit = process.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }
}



/** Windows system theme observer */
private class WindowsThemeObserver(private val onChange: (Boolean) -> Unit) : SystemThemeObserver {
    @Volatile private var running = false
    private var job: Job? = null

    override fun start() {
        if (running) return
        running = true
        job = CoroutineScope(Dispatchers.IO).launch {
            var last = isSystemDark()
            onChange(last)
            while (running) {
                try {
                    val now = isSystemDark()
                    if (now != last) {
                        last = now
                        onChange(now)
                    }
                    kotlinx.coroutines.delay(2000)
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(4000)
                }
            }
        }
    }

    override fun stop() {
        running = false
        job?.cancel(); job = null
    }

    override fun isSystemDark(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return false
        return try {
            // Query registry: 0 = dark, 1 = light
            val process = ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            ).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val value = Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").find(out)?.groupValues?.get(1)
            val isLight = value?.toInt(16) == 1
            !isLight
        } catch (_: Exception) {
            false
        }
    }
}

/** Linux system theme observer (best effort for GNOME/KDE) */
private class LinuxThemeObserver(private val onChange: (Boolean) -> Unit) : SystemThemeObserver {
    @Volatile private var running = false
    private var job: Job? = null

    override fun start() {
        if (running) return
        running = true
        job = CoroutineScope(Dispatchers.IO).launch {
            var last = isSystemDark()
            onChange(last)
            while (running) {
                try {
                    val now = isSystemDark()
                    if (now != last) {
                        last = now
                        onChange(now)
                    }
                    kotlinx.coroutines.delay(2500)
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(5000)
                }
            }
        }
    }

    override fun stop() {
        running = false
        job?.cancel(); job = null
    }

    override fun isSystemDark(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (!(os.contains("nux") || os.contains("nix") || os.contains("linux"))) return false
        // Try GNOME gsettings first
        try {
            val p1 = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                .redirectErrorStream(true).start()
            val out1 = p1.inputStream.bufferedReader().readText(); p1.waitFor()
            if (out1.contains("prefer-dark")) return true
        } catch (_: Exception) { /* ignore */ }
        try {
            val p2 = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                .redirectErrorStream(true).start()
            val out2 = p2.inputStream.bufferedReader().readText(); p2.waitFor()
            if (out2.contains("-dark", ignoreCase = true)) return true
        } catch (_: Exception) { /* ignore */ }
        // Try KDE
        try {
            val p3 = ProcessBuilder("kreadconfig5", "--group", "KDE", "--key", "colorScheme")
                .redirectErrorStream(true).start()
            val out3 = p3.inputStream.bufferedReader().readText(); p3.waitFor()
            if (out3.contains("Dark", ignoreCase = true)) return true
        } catch (_: Exception) { /* ignore */ }
        return false
    }
}
