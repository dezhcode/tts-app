package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppFontWeight
import com.example.data.TypographySettings
import com.example.ui.theme.CillaFontCatalog
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaStroke
import com.example.ui.theme.FontAvailability
import com.example.ui.theme.FontCatalogEntry
import com.example.ui.theme.FontLanguage
import com.example.ui.theme.LocalCillaFonts

/**
 * Live bilingual typography specimen stage displaying Persian (RTL) and English (LTR)
 * sample sentences simultaneously using the currently selected UI, heading, weight, and mono fonts.
 */
@Composable
fun LiveBilingualSpecimenCard(
    typographySettings: TypographySettings,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalCillaFonts.current
    val faUiName = CillaFontCatalog.findById(typographySettings.persianUiFontId)?.displayName ?: "Vazirmatn"
    val enUiName = CillaFontCatalog.findById(typographySettings.englishUiFontId)?.displayName ?: "Inter"

    val faHeadingEntry = CillaFontCatalog.findById(typographySettings.persianHeadingFontId)
    val enHeadingEntry = CillaFontCatalog.findById(typographySettings.englishHeadingFontId)
    val faHeadingWeight = CillaFontCatalog.resolveWeightFor(faHeadingEntry, AppFontWeight.BOLD)
    val enHeadingWeight = CillaFontCatalog.resolveWeightFor(enHeadingEntry, AppFontWeight.SEMIBOLD)

    Surface(
        shape = RoundedCornerShape(CillaRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_bilingual_preview"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Active Specimen Metadata Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "پیش‌نمایش زنده",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "FA $faUiName · EN $enUiName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            CillaHairline()

            // Persian RTL Specimen
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "زیبایی در سادگی است.",
                        fontFamily = fonts.persianHeading,
                        fontWeight = faHeadingWeight,
                        fontSize = 21.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "صدای خود را با کیفیتی متفاوت تجربه کنید.",
                        fontFamily = fonts.persianUi,
                        fontWeight = fonts.persianBodyWeight,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CillaHairline()

            // English LTR Specimen
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Elegance in every detail.",
                        fontFamily = fonts.englishHeading,
                        fontWeight = enHeadingWeight,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Your voice, your style.",
                        fontFamily = fonts.englishUi,
                        fontWeight = fonts.englishBodyWeight,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Font weight segmented selector showing Regular, Medium, SemiBold, and Bold,
 * clearly differentiating natively supported weights for the currently active typeface.
 */
@Composable
fun FontWeightSelectorRow(
    selectedWeight: AppFontWeight,
    supportedWeights: List<AppFontWeight>,
    onSelectWeight: (AppFontWeight) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "وزن فونت",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "${selectedWeight.labelEn} · ${selectedWeight.numericValue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CillaRadius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppFontWeight.entries.forEach { weight ->
                val isSupported = weight in supportedWeights
                val isSelected = selectedWeight == weight
                val chipBg by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.onBackground
                        else -> Color.Transparent
                    },
                    animationSpec = CillaMotion.quickTween(),
                    label = "weight_chip_bg",
                )
                val chipFg by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.surface
                        isSupported -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    },
                    animationSpec = CillaMotion.quickTween(),
                    label = "weight_chip_fg",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CillaRadius.sm))
                        .background(chipBg)
                        .clickable(enabled = isSupported) { onSelectWeight(weight) }
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 8.dp)
                        .testTag("weight_${weight.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = weight.labelFa,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = chipFg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Reusable editorial font specimen card rendering real sample copy in the card's actual
 * typeface with clear Installed / Available / Selected indicators and optional local .ttf import.
 */
@Composable
fun FontSpecimenCard(
    entry: FontCatalogEntry,
    isInstalled: Boolean,
    isSelected: Boolean,
    previewWeight: AppFontWeight,
    customFonts: Map<String, FontFamily>,
    onSelect: () -> Unit,
    onRequestImportFile: () -> Unit,
    onRemoveImportedFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPersian = entry.language == FontLanguage.PERSIAN
    val resolvedFamily = CillaFontCatalog.resolveFontFamily(
        fontId = entry.id,
        customFonts = customFonts,
        fallback = if (isPersian) CillaFontCatalog.NotoNaskhArabicFamily else CillaFontCatalog.InterFamily,
    )
    val resolvedWeight = CillaFontCatalog.resolveWeightFor(entry, previewWeight)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = CillaMotion.standardTween(),
        label = "font_card_border",
    )
    val cardBg by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        animationSpec = CillaMotion.standardTween(),
        label = "font_card_bg",
    )

    Surface(
        shape = RoundedCornerShape(CillaRadius.lg),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) CillaStroke.emphasis else CillaStroke.hairline,
            color = borderColor,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CillaRadius.lg))
            .clickable(enabled = isInstalled) { onSelect() }
            .testTag("font_card_${entry.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Row: Typeface Name + Status Pill (Selected / Installed / Available)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (isPersian) entry.nativeName else entry.displayName,
                            fontFamily = resolvedFamily,
                            fontWeight = CillaFontCatalog.resolveWeightFor(entry, AppFontWeight.BOLD),
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isPersian) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isPersian) entry.styleDescriptionFa else entry.styleDescriptionEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(10.dp))

                FontStatusPill(
                    isSelected = isSelected,
                    isInstalled = isInstalled,
                )
            }

            CillaHairline()

            // Realistic Live Typography Specimen rendered in actual font resource
            if (isInstalled) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = if (isPersian) "زیبایی در سادگی است." else "Elegance in every detail.",
                            fontFamily = resolvedFamily,
                            fontWeight = CillaFontCatalog.resolveWeightFor(entry, AppFontWeight.SEMIBOLD),
                            fontSize = if (isPersian) 19.sp else 20.sp,
                            lineHeight = if (isPersian) 30.sp else 27.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isPersian) {
                                "صدای خود را با کیفیتی متفاوت تجربه کنید."
                            } else {
                                "Your voice, your style."
                            },
                            fontFamily = resolvedFamily,
                            fontWeight = resolvedWeight,
                            fontSize = if (isPersian) 14.sp else 14.sp,
                            lineHeight = if (isPersian) 24.sp else 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Clear, honest notice for proprietary font (B Nazanin) awaiting local .ttf file
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "فایل BNazanin.ttf را از حافظه دستگاه انتخاب کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onRequestImportFile,
                        shape = RoundedCornerShape(CillaRadius.md),
                        border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("import_font_${entry.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "افزودن فایل فونت (.ttf)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Bottom technical metadata: license + weights + optional remove button for locally imported fonts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = entry.licenseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = entry.supportedWeights.joinToString(" / ") { it.numericValue.toString() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }

                if (entry.availability == FontAvailability.LOCAL_IMPORT_REQUIRED && isInstalled) {
                    IconButton(
                        onClick = onRemoveImportedFile,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "حذف فایل فونت محلی",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontStatusPill(
    isSelected: Boolean,
    isInstalled: Boolean,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isInstalled -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val fgColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isInstalled -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val label = when {
        isSelected -> "انتخاب‌شده"
        isInstalled -> "نصب‌شده"
        else -> "قابل افزودن"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(fgColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
            )
        }
    }
}
