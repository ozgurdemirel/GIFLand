package club.ozgur.gifland.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Material3 color schemes
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1C7ED6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),

    secondary = Color(0xFF5C677D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE2EA),
    onSecondaryContainer = Color(0xFF111418),

    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEACD),
    onTertiaryContainer = Color(0xFF0C2710),

    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0x66000000),

    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFE2E8F0),
    inversePrimary = Color(0xFF82C0FF)
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF82C0FF),
    onPrimary = Color(0xFF001D36),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD0E4FF),

    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFDDE2EA),

    tertiary = Color(0xFF85D88A),
    onTertiary = Color(0xFF0C2710),
    tertiaryContainer = Color(0xFF1E3A1E),
    onTertiaryContainer = Color(0xFFCDEACD),

    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFFCBD5E1),

    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1F2937),
    scrim = Color(0x99000000),

    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0B1220),
    inversePrimary = Color(0xFF1C7ED6)
)

@Immutable
data class AppExtraColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val divider: Color,
    val border: Color,
    val shadow: Color,
)

val LocalAppExtraColors = staticCompositionLocalOf {
    AppExtraColors(
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        info = Color(0xFF0288D1),
        divider = Color(0xFFE2E8F0),
        border = Color(0xFFE2E8F0),
        shadow = Color(0x14000000)
    )
}

