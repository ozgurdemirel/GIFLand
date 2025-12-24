package club.ozgur.gifland.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import club.ozgur.gifland.domain.repository.SettingsRepository
import org.koin.compose.koinInject

@Composable
fun ThemeProvider(content: @Composable () -> Unit) {
    // Hook into settings for preference
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settingsFlow.collectAsState()

    // Ensure ThemeManager observes settings
    LaunchedEffect(Unit) {
        ThemeManager.initialize(settingsRepository.settingsFlow)
    }

    // Resolve desired scheme
    val isDark = when (settings.theme) {
        club.ozgur.gifland.domain.model.AppTheme.Light -> false
        club.ozgur.gifland.domain.model.AppTheme.Dark -> true
        club.ozgur.gifland.domain.model.AppTheme.System -> ThemeManager.isDark
    }

    // Animate a subset of prominent colors for smooth transitions
    val targetScheme = if (isDark) DarkColorScheme else LightColorScheme
    val animatedScheme = rememberAnimatedScheme(targetScheme)

    CompositionLocalProvider(
        LocalAppExtraColors provides extraColorsFor(isDark)
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

@Composable
private fun rememberAnimatedScheme(target: ColorScheme): ColorScheme {
    // Animate commonly used colors; others switch immediately to keep it simple and performant
    val primary by animateColorAsState(target.primary)
    val onPrimary by animateColorAsState(target.onPrimary)
    val background by animateColorAsState(target.background)
    val onBackground by animateColorAsState(target.onBackground)
    val surface by animateColorAsState(target.surface)
    val onSurface by animateColorAsState(target.onSurface)
    val secondary by animateColorAsState(target.secondary)
    val error by animateColorAsState(target.error)

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        secondary = secondary,
        error = error
    )
}

private fun extraColorsFor(dark: Boolean): AppExtraColors = if (dark) {
    AppExtraColors(
        success = Color(0xFF85D88A),
        warning = Color(0xFFFFD166),
        info = Color(0xFF82C0FF),
        divider = Color(0xFF334155),
        border = Color(0xFF334155),
        shadow = Color(0x33000000)
    )
} else {
    AppExtraColors(
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        info = Color(0xFF1C7ED6),
        divider = Color(0xFFE2E8F0),
        border = Color(0xFFE2E8F0),
        shadow = Color(0x14000000)
    )
}

