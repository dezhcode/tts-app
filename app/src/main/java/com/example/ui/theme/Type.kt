package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.AppFontWeight
import com.example.data.TypographySettings

/**
 * Resolved runtime font families available across the entire Compose hierarchy
 * for independent Persian, English, and technical telemetry rendering.
 */
@Immutable
data class CillaTypographyFamilies(
    val persianUi: FontFamily = CillaFontCatalog.VazirmatnFamily,
    val persianHeading: FontFamily = CillaFontCatalog.EstedadFamily,
    val englishUi: FontFamily = CillaFontCatalog.InterFamily,
    val englishHeading: FontFamily = CillaFontCatalog.PlayfairDisplayFamily,
    val technicalMono: FontFamily = CillaFontCatalog.JetBrainsMonoFamily,
    val persianBodyWeight: FontWeight = FontWeight.Normal,
    val englishBodyWeight: FontWeight = FontWeight.Normal,
)

val LocalCillaFonts = staticCompositionLocalOf { CillaTypographyFamilies() }

fun resolveCillaTypographyFamilies(
    settings: TypographySettings,
    customFonts: Map<String, FontFamily> = emptyMap(),
): CillaTypographyFamilies {
    val faUiEntry = CillaFontCatalog.findById(settings.persianUiFontId)
    val enUiEntry = CillaFontCatalog.findById(settings.englishUiFontId)

    return CillaTypographyFamilies(
        persianUi = CillaFontCatalog.resolveFontFamily(
            fontId = settings.persianUiFontId,
            customFonts = customFonts,
            fallback = CillaFontCatalog.VazirmatnFamily,
        ),
        persianHeading = CillaFontCatalog.resolveFontFamily(
            fontId = settings.persianHeadingFontId,
            customFonts = customFonts,
            fallback = CillaFontCatalog.EstedadFamily,
        ),
        englishUi = CillaFontCatalog.resolveFontFamily(
            fontId = settings.englishUiFontId,
            customFonts = customFonts,
            fallback = CillaFontCatalog.InterFamily,
        ),
        englishHeading = CillaFontCatalog.resolveFontFamily(
            fontId = settings.englishHeadingFontId,
            customFonts = customFonts,
            fallback = CillaFontCatalog.PlayfairDisplayFamily,
        ),
        technicalMono = CillaFontCatalog.JetBrainsMonoFamily,
        persianBodyWeight = CillaFontCatalog.resolveWeightFor(faUiEntry, settings.persianWeight),
        englishBodyWeight = CillaFontCatalog.resolveWeightFor(enUiEntry, settings.englishWeight),
    )
}

fun buildCillaTypography(
    settings: TypographySettings = TypographySettings(),
    customFonts: Map<String, FontFamily> = emptyMap(),
): Typography {
    val families = resolveCillaTypographyFamilies(settings, customFonts)
    val faHeadingEntry = CillaFontCatalog.findById(settings.persianHeadingFontId)
    val faUiEntry = CillaFontCatalog.findById(settings.persianUiFontId)

    val headingWeight = when (settings.persianWeight) {
        AppFontWeight.REGULAR -> CillaFontCatalog.resolveWeightFor(faHeadingEntry, AppFontWeight.BOLD)
        AppFontWeight.MEDIUM -> CillaFontCatalog.resolveWeightFor(faHeadingEntry, AppFontWeight.BOLD)
        AppFontWeight.SEMIBOLD -> CillaFontCatalog.resolveWeightFor(faHeadingEntry, AppFontWeight.BOLD)
        AppFontWeight.BOLD -> CillaFontCatalog.resolveWeightFor(faHeadingEntry, AppFontWeight.BOLD)
    }
    val subheadingWeight = when (settings.persianWeight) {
        AppFontWeight.REGULAR -> CillaFontCatalog.resolveWeightFor(faUiEntry, AppFontWeight.SEMIBOLD)
        AppFontWeight.MEDIUM -> CillaFontCatalog.resolveWeightFor(faUiEntry, AppFontWeight.SEMIBOLD)
        AppFontWeight.SEMIBOLD -> CillaFontCatalog.resolveWeightFor(faUiEntry, AppFontWeight.BOLD)
        AppFontWeight.BOLD -> CillaFontCatalog.resolveWeightFor(faUiEntry, AppFontWeight.BOLD)
    }
    val labelWeight = when (settings.persianWeight) {
        AppFontWeight.REGULAR -> CillaFontCatalog.resolveWeightFor(faUiEntry, AppFontWeight.MEDIUM)
        else -> families.persianBodyWeight
    }

    return Typography(
        displaySmall = TextStyle(
            fontFamily = families.persianHeading,
            fontWeight = headingWeight,
            fontSize = 30.sp,
            lineHeight = 40.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = families.persianHeading,
            fontWeight = headingWeight,
            fontSize = 23.sp,
            lineHeight = 33.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = families.persianHeading,
            fontWeight = headingWeight,
            fontSize = 20.sp,
            lineHeight = 29.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = subheadingWeight,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = subheadingWeight,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = families.persianBodyWeight,
            fontSize = 17.sp,
            lineHeight = 31.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = families.persianBodyWeight,
            fontSize = 14.sp,
            lineHeight = 24.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = families.persianBodyWeight,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = labelWeight,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = families.persianUi,
            fontWeight = labelWeight,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = families.technicalMono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.2.sp,
        ),
    )
}

val CillaTypography: Typography = buildCillaTypography()
