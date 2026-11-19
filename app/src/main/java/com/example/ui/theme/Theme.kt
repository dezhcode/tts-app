package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = EmeraldTeal,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoftContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = WarmAmber,
    onSecondary = Color.White,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = WarmAmberOnContainer,
    tertiary = SoftIndigo,
    onTertiary = Color.White,
    tertiaryContainer = SoftIndigoContainer,
    onTertiaryContainer = Color(0xFF312E81),
    background = IvoryBg,
    onBackground = InkPrimary,
    surface = PureWhiteSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftCloudSurface,
    onSurfaceVariant = InkSecondary,
    outline = Color(0xFFCBD5E1),
    outlineVariant = HairlineBorderLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorSoftBg,
    onErrorContainer = Color(0xFF991B1B),
)

private val DarkColorScheme = darkColorScheme(
    primary = TealGlow,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = AmberGold,
    onSecondary = Color(0xFF2D1900),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    background = MidnightBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF334155),
    outlineVariant = HairlineBorderDark,
    error = ErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
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
