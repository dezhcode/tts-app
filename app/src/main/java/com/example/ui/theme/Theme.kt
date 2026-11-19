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
    primary = Color(0xFFAD72FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF38235F),
    onPrimaryContainer = Color(0xFFF2E8FF),
    secondary = Color(0xFF45D5FF),
    onSecondary = Color(0xFF041427),
    secondaryContainer = Color(0xFF16294D),
    onSecondaryContainer = Color(0xFFBCEFFF),
    tertiary = Color(0xFFF16CFF),
    onTertiary = Color(0xFF1A0828),
    background = Color(0xFF090E25),
    onBackground = Color(0xFFF6F2FF),
    surface = Color(0xFF171C3B),
    onSurface = Color(0xFFF6F2FF),
    surfaceVariant = Color(0xFF24294C),
    onSurfaceVariant = Color(0xFFBEC5E5),
    outline = Color(0xFF67699C),
    outlineVariant = Color(0xFF383F70),
    error = Color(0xFFFF849C),
    onError = Color(0xFF2D0611),
    errorContainer = Color(0xFF571A37),
    onErrorContainer = Color(0xFFFFD9E2),
)

@Composable
fun CillaTtsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CillaTypography,
        content = content,
    )
}
