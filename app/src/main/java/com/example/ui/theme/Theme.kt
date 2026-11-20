package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.data.TypographySettings

private val LightColorScheme = lightColorScheme(
    primary = StudioTerracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaWash,
    onPrimaryContainer = TerracottaInk,
    secondary = StudioSlateTeal,
    onSecondary = Color.White,
    secondaryContainer = SlateTealWash,
    onSecondaryContainer = SlateTealInk,
    tertiary = WarmBronze,
    onTertiary = Color.White,
    tertiaryContainer = WarmBronzeWash,
    onTertiaryContainer = WarmBronzeInk,
    background = GalleryCanvas,
    onBackground = CarbonInk,
    surface = ArchivalPaper,
    onSurface = CarbonInk,
    surfaceVariant = LinenRecessed,
    onSurfaceVariant = MutedStone,
    outline = StoneOutline,
    outlineVariant = StoneHairline,
    error = MutedOxblood,
    onError = Color.White,
    errorContainer = OxbloodWash,
    onErrorContainer = OxbloodInk,
)

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = Color(0xFF280D07),
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = Color(0xFFF6C6BA),
    secondary = SlateTealDark,
    onSecondary = Color(0xFF0A2225),
    secondaryContainer = SlateTealDarkContainer,
    onSecondaryContainer = Color(0xFFC0DFE2),
    tertiary = BronzeDark,
    onTertiary = Color(0xFF291C07),
    tertiaryContainer = BronzeDarkContainer,
    onTertiaryContainer = Color(0xFFEDD9B3),
    background = CharcoalCanvas,
    onBackground = IvoryInk,
    surface = CharcoalSurface,
    onSurface = IvoryInk,
    surfaceVariant = CharcoalRecessed,
    onSurfaceVariant = AshMuted,
    outline = CharcoalOutline,
    outlineVariant = CharcoalHairline,
    error = OxbloodDark,
    onError = Color(0xFF3A0E0E),
    errorContainer = OxbloodDarkContainer,
    onErrorContainer = Color(0xFFF7C9C9),
)

@Composable
fun CillaTtsTheme(
    darkTheme: Boolean = false,
    typographySettings: TypographySettings = TypographySettings(),
    customFonts: Map<String, FontFamily> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val families = remember(typographySettings, customFonts) {
        resolveCillaTypographyFamilies(typographySettings, customFonts)
    }
    val typography = remember(typographySettings, customFonts) {
        buildCillaTypography(typographySettings, customFonts)
    }

    CompositionLocalProvider(LocalCillaFonts provides families) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = CillaShapes,
            content = content,
        )
    }
}
