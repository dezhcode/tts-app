package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VermilionAccent,
    onPrimary = Color.White,
    primaryContainer = VermilionSoft,
    onPrimaryContainer = VermilionDeep,
    secondary = PersianTeal,
    onSecondary = Color.White,
    secondaryContainer = PersianTealSoft,
    onSecondaryContainer = PersianTealDeep,
    tertiary = WarmOchre,
    onTertiary = Color.White,
    tertiaryContainer = WarmOchreSoft,
    onTertiaryContainer = WarmOchreDeep,
    background = PaperCanvas,
    onBackground = EspressoInk,
    surface = PaperSheet,
    onSurface = EspressoInk,
    surfaceVariant = PaperRecessed,
    onSurfaceVariant = SlateStone,
    outline = Color(0xFFCFC8BA),
    outlineVariant = PaperHairline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorSoft,
    onErrorContainer = Color(0xFF881313),
)

private val DarkColorScheme = darkColorScheme(
    primary = VermilionGlow,
    onPrimary = Color(0xFF2B0A04),
    primaryContainer = VermilionDarkTint,
    onPrimaryContainer = Color(0xFFFFC5B5),
    secondary = TurquoiseGlow,
    onSecondary = Color(0xFF032330),
    secondaryContainer = TurquoiseDarkTint,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = OchreGlow,
    onTertiary = Color(0xFF2E1A00),
    tertiaryContainer = OchreDarkTint,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ObsidianCanvas,
    onBackground = ChalkInk,
    surface = ObsidianSheet,
    onSurface = ChalkInk,
    surfaceVariant = ObsidianRecessed,
    onSurfaceVariant = WarmAsh,
    outline = Color(0xFF47433B),
    outlineVariant = ObsidianHairline,
    error = ErrorDarkGlow,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorDarkTint,
    onErrorContainer = Color(0xFFFECACA),
)

@Composable
fun CillaTtsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CillaTypography,
        content = content,
    )
}
